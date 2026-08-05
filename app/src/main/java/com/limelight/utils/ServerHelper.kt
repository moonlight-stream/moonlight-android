package com.limelight.utils

import android.app.Activity
import android.content.Intent
import android.widget.Toast
import com.limelight.AppView
import com.limelight.Game
import com.limelight.R
import com.limelight.ShortcutTrampoline
import com.limelight.binding.PlatformBinding
import com.limelight.computers.ComputerManagerService
import com.limelight.nvstream.http.ComputerDetails
import com.limelight.nvstream.http.HostHttpResponseException
import com.limelight.nvstream.http.NvApp
import com.limelight.nvstream.http.NvHTTP
import com.limelight.nvstream.jni.MoonBridge
import com.limelight.preferences.PreferenceConfiguration
import org.xmlpull.v1.XmlPullParserException
import java.io.FileNotFoundException
import java.io.IOException
import java.net.UnknownHostException
import java.security.cert.CertificateEncodingException

object ServerHelper {
    const val CONNECTION_TEST_SERVER = "android.conntest.moonlight-stream.org"

    @Throws(IOException::class)
    fun getCurrentAddressFromComputer(computer: ComputerDetails): ComputerDetails.AddressTuple {
        return computer.activeAddress ?: throw IOException("No active address for ${computer.name}")
    }

    fun createPcShortcutIntent(parent: Activity, computer: ComputerDetails): Intent {
        return Intent(parent, ShortcutTrampoline::class.java).apply {
            putExtra(AppView.NAME_EXTRA, computer.name)
            putExtra(AppView.UUID_EXTRA, computer.uuid)
            action = Intent.ACTION_DEFAULT
        }
    }

    fun createAppShortcutIntent(parent: Activity, computer: ComputerDetails, app: NvApp): Intent {
        return Intent(parent, ShortcutTrampoline::class.java).apply {
            putExtra(AppView.NAME_EXTRA, computer.name)
            putExtra(AppView.UUID_EXTRA, computer.uuid)
            putExtra(Game.EXTRA_APP_NAME, app.appName)
            putExtra(Game.EXTRA_APP_ID, "" + app.appId)
            putExtra(Game.EXTRA_APP_HDR, app.isHdrSupported())
            action = Intent.ACTION_DEFAULT
        }
    }

    fun createStartIntent(
        parent: Activity,
        app: NvApp,
        computer: ComputerDetails,
        managerBinder: ComputerManagerService.ComputerManagerBinder,
        lastSettings: PreferenceConfiguration? = null,
        screenCombinationMode: Int = -1,
        useVdd: Boolean? = null,
        forceResumeCurrentSession: Boolean = false
    ): Intent {
        return Intent(parent, Game::class.java).apply {
            putExtra(Game.EXTRA_HOST, computer.activeAddress!!.address)
            putExtra(Game.EXTRA_PORT, computer.activeAddress!!.port)
            putExtra(Game.EXTRA_HTTPS_PORT, computer.httpsPort)
            putExtra(Game.EXTRA_APP_NAME, app.appName)
            putExtra(Game.EXTRA_APP_ID, app.appId)
            putExtra(Game.EXTRA_APP_HDR, app.isHdrSupported())
            putExtra(Game.EXTRA_UNIQUEID, managerBinder.getUniqueId())
            putExtra(Game.EXTRA_PC_UUID, computer.uuid)
            putExtra(Game.EXTRA_PC_NAME, computer.name)
            putExtra(Game.EXTRA_PAIR_NAME, computer.getPairName(parent))
            useVdd?.let { putExtra(Game.EXTRA_PC_USEVDD, it) }
            app.cmdList?.let { putExtra(Game.EXTRA_APP_CMD, it.toString()) }
            try {
                computer.serverCert?.let { putExtra(Game.EXTRA_SERVER_CERT, it.encoded) }
            } catch (e: CertificateEncodingException) {
                e.printStackTrace()
            }
            if (lastSettings != null) {
                AppSettingsManager.addLastSettingsToIntent(this, lastSettings)
            }
            if (screenCombinationMode != -1) {
                putExtra(Game.EXTRA_SCREEN_COMBINATION_MODE, screenCombinationMode)
            }
            if (forceResumeCurrentSession) {
                putExtra(Game.EXTRA_FORCE_RESUME_CURRENT_SESSION, true)
            }
        }
    }

    fun doStart(
        parent: Activity,
        app: NvApp,
        computer: ComputerDetails,
        managerBinder: ComputerManagerService.ComputerManagerBinder,
        forceResumeCurrentSession: Boolean = false
    ) {
        if (computer.state == ComputerDetails.State.OFFLINE || computer.activeAddress == null) {
            Toast.makeText(parent, parent.resources.getString(R.string.pair_pc_offline), Toast.LENGTH_SHORT).show()
            return
        }
        parent.startActivity(createStartIntent(parent, app, computer, managerBinder, forceResumeCurrentSession = forceResumeCurrentSession))
    }

    fun doNetworkTest(parent: Activity) {
        Thread {
            val spinnerDialog = SpinnerDialog.displayDialog(
                parent,
                parent.resources.getString(R.string.nettest_title_waiting),
                parent.resources.getString(R.string.nettest_text_waiting),
                false
            )

            val ret = MoonBridge.testClientConnectivity(CONNECTION_TEST_SERVER, 443, MoonBridge.ML_PORT_FLAG_ALL)
            spinnerDialog.dismiss()

            val dialogSummary = when (ret) {
                MoonBridge.ML_TEST_RESULT_INCONCLUSIVE ->
                    parent.resources.getString(R.string.nettest_text_inconclusive)

                0 ->
                    parent.resources.getString(R.string.nettest_text_success)

                else -> parent.resources.getString(R.string.nettest_text_failure) + MoonBridge.stringifyPortFlags(ret, "\n")
            }

            Dialog.displayDialog(
                parent,
                parent.resources.getString(R.string.nettest_title_done),
                dialogSummary,
                false
            )
        }.start()
    }

    fun pcSleep(
        parent: Activity,
        computer: ComputerDetails,
        managerBinder: ComputerManagerService.ComputerManagerBinder,
        onComplete: Runnable?
    ) {
        Toast.makeText(
            parent,
            parent.getString(R.string.pcview_menu_sleep_sending, computer.name),
            Toast.LENGTH_SHORT
        ).show()

        Thread {
            var message: String
            try {
                val httpConn = NvHTTP(
                    getCurrentAddressFromComputer(computer), computer.httpsPort,
                    managerBinder.getUniqueId(), "", computer.serverCert, PlatformBinding.getCryptoProvider(parent)
                )
                message = if (httpConn.pcSleep()) {
                    parent.resources.getString(R.string.pcview_menu_sleep_success)
                } else {
                    parent.resources.getString(R.string.pcview_menu_sleep_fail)
                }
            } catch (e: HostHttpResponseException) {
                message = e.message
            } catch (e: UnknownHostException) {
                message = parent.resources.getString(R.string.error_unknown_host)
            } catch (e: FileNotFoundException) {
                message = parent.resources.getString(R.string.error_404)
            } catch (e: Exception) {
                when (e) {
                    is IOException, is XmlPullParserException -> {
                        message = e.message?.takeIf { it.isNotBlank() }
                            ?: parent.getString(R.string.pcview_menu_sleep_fail)
                        e.printStackTrace()
                    }
                    is InterruptedException -> {
                        message = parent.resources.getString(R.string.error_interrupted)
                    }
                    else -> throw e
                }
            } finally {
                onComplete?.run()
            }

            val toastMessage = message
            parent.runOnUiThread { Toast.makeText(parent, toastMessage, Toast.LENGTH_LONG).show() }
        }.start()
    }

    fun doQuit(
        parent: Activity,
        computer: ComputerDetails,
        app: NvApp,
        managerBinder: ComputerManagerService.ComputerManagerBinder,
        onComplete: Runnable?
    ) {
        Toast.makeText(
            parent,
            parent.resources.getString(R.string.applist_quit_app) + " " + app.appName + "...",
            Toast.LENGTH_SHORT
        ).show()

        Thread {
            var message: String
            try {
                val httpConn = NvHTTP(
                    getCurrentAddressFromComputer(computer), computer.httpsPort,
                    managerBinder.getUniqueId(), "", computer.serverCert, PlatformBinding.getCryptoProvider(parent)
                )
                message = if (httpConn.quitApp()) {
                    parent.resources.getString(R.string.applist_quit_success) + " " + app.appName
                } else {
                    parent.resources.getString(R.string.applist_quit_fail) + " " + app.appName
                }
            } catch (e: HostHttpResponseException) {
                message = if (e.getErrorCode() == 599) {
                    "This session wasn't started by this device," +
                            " so it cannot be quit. End streaming on the original " +
                            "device or the PC itself. (Error code: ${e.getErrorCode()})"
                } else {
                    e.message
                }
            } catch (e: UnknownHostException) {
                message = parent.resources.getString(R.string.error_unknown_host)
            } catch (e: FileNotFoundException) {
                message = parent.resources.getString(R.string.error_404)
            } catch (e: Exception) {
                when (e) {
                    is IOException, is XmlPullParserException -> {
                        message = e.message ?: ""
                        e.printStackTrace()
                    }
                    is InterruptedException -> {
                        message = parent.resources.getString(R.string.error_interrupted)
                    }
                    else -> throw e
                }
            } finally {
                onComplete?.run()
            }

            val toastMessage = message
            parent.runOnUiThread { Toast.makeText(parent, toastMessage, Toast.LENGTH_LONG).show() }
        }.start()
    }
}
