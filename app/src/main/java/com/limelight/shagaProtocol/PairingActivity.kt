package com.limelight.shagaProtocol

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.limelight.R
import com.limelight.computers.ComputerManagerService
import com.limelight.nvstream.http.ComputerDetails
import com.limelight.nvstream.http.NvHTTP
import com.limelight.nvstream.jni.MoonBridge
import com.limelight.utils.Dialog
import com.limelight.utils.ServerHelper
import com.limelight.utils.SpinnerDialog
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import com.limelight.PcView
import com.solana.core.PublicKey


class PairingActivity : AppCompatActivity() {

    private var managerBinder: ComputerManagerService.ComputerManagerBinder? = null
    private var addThread: Thread? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            managerBinder = service as ComputerManagerService.ComputerManagerBinder
            startAddThread()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            joinAddThread()
            managerBinder = null
        }
    }

    private fun isWrongSubnetSiteLocalAddress(address: String): Boolean {
        return try {
            val targetAddress = InetAddress.getByName(address)
            if (targetAddress !is Inet4Address || !targetAddress.isSiteLocalAddress) {
                false
            } else {
                var foundMatchingInterface = false
                for (iface in NetworkInterface.getNetworkInterfaces()) {
                    for (addr in iface.interfaceAddresses) {
                        if (addr.address !is Inet4Address || !addr.address.isSiteLocalAddress) {
                            continue
                        }

                        val targetAddrBytes = targetAddress.address
                        val ifaceAddrBytes = addr.address.address

                        var addressMatches = true
                        for (i in 0 until addr.networkPrefixLength.toInt()) {
                            if (ifaceAddrBytes[i / 8].toInt() and (1 shl (i % 8)) !=
                                targetAddrBytes[i / 8].toInt() and (1 shl (i % 8))
                            ) {
                                addressMatches = false
                                break
                            }
                        }

                        if (addressMatches) {
                            foundMatchingInterface = true
                            break
                        }
                    }

                    if (foundMatchingInterface) break
                }

                !foundMatchingInterface
            }
        } catch (e: Exception) {
            // Handle exceptions
            e.printStackTrace()
            false
        }
    }

    private fun doAddPc() {
        var wrongSiteLocal = false
        var invalidInput = false
        var success = false
        var portTestResult: Int // In Java, this was an int, initialize later just like Java

        val details = ComputerDetails() // Initialize ComputerDetails, same as Java

        // Create a spinner dialog, assuming you have a similar mechanism in Kotlin
        val dialog = SpinnerDialog.displayDialog(
            this,
            getString(R.string.title_add_pc),
            getString(R.string.msg_add_pc),
            false
        )

        try {
            val host =
                intent.getStringExtra("ipAddress") // Directly using the IP address, not from user input
            val port = NvHTTP.DEFAULT_HTTP_PORT // Default port, same as Java

            if (host != null && host.isNotEmpty()) { // Check for a valid host, similar to the URI check in Java
                details.manualAddress = ComputerDetails.AddressTuple(host, port)
                success = managerBinder?.addComputerBlocking(details) ?: false

                if (!success) {
                    wrongSiteLocal = isWrongSubnetSiteLocalAddress(host)
                }
            } else {
                // Invalid IP address, this replaces the invalid user input scenario in Java
                success = false
                invalidInput = true
            }
        } catch (e: InterruptedException) { // InterruptedException catch block
            dialog.dismiss()
            throw e // Propagate the exception, same as Java
        } catch (e: IllegalArgumentException) { // IllegalArgumentException catch block
            e.printStackTrace()
            success = false
            invalidInput = true
        }

        // Keep the SpinnerDialog open while testing connectivity
        if (!success && !wrongSiteLocal && !invalidInput) {
            portTestResult = MoonBridge.testClientConnectivity(
                ServerHelper.CONNECTION_TEST_SERVER,
                443,
                MoonBridge.ML_PORT_FLAG_TCP_47984 or MoonBridge.ML_PORT_FLAG_TCP_47989
            )
        } else {
            portTestResult = MoonBridge.ML_TEST_RESULT_INCONCLUSIVE
        }

        dialog.dismiss()

        when {
            invalidInput -> {
                Dialog.displayDialog(
                    this,
                    getString(R.string.conn_error_title),
                    getString(R.string.addpc_unknown_host),
                    false
                )
            }
            wrongSiteLocal -> {
                Dialog.displayDialog(
                    this,
                    getString(R.string.conn_error_title),
                    getString(R.string.addpc_wrong_sitelocal),
                    false
                )
            }
            !success -> {
                val dialogText = if (portTestResult != MoonBridge.ML_TEST_RESULT_INCONCLUSIVE && portTestResult != 0) {
                    getString(R.string.nettest_text_blocked)
                } else {
                    getString(R.string.addpc_fail)
                }
                Dialog.displayDialog(
                    this,
                    getString(R.string.conn_error_title),
                    dialogText,
                    false
                )
            }
            else -> {
                runOnUiThread {
                    Toast.makeText(this, getString(R.string.addpc_success), Toast.LENGTH_LONG).show()

                    // Call publicDoPairShaga here
                    val pcViewInstance = PcView.getInstance()
                    if (pcViewInstance != null) {
                        val authority = intent.getStringExtra("authority")
                        pcViewInstance.publicDoPairShaga(details, authority?.let { PublicKey(it) }) // pass authority as publicKey if not null
                    } else {
                        // Handle the error case where PcView instance is null
                        Log.e("PairingActivity", "PcView instance is null. Cannot proceed with publicDoPairShaga.")
                        Toast.makeText(this, "Failed to initiate pairing; internal error.", Toast.LENGTH_LONG).show()
                    }

                    if (!isFinishing) {
                        finish()
                    }
                }
            }
        }
    }

    // Kotlin Version in PairingActivity.kt
    private fun startAddThread() {
        addThread = Thread {
            while (!Thread.currentThread().isInterrupted) {
                try {
                    doAddPc() // Directly calling doAddPc
                    break // Break loop since only one IP is being added
                } catch (e: InterruptedException) {
                    return@Thread // Stop the thread if interrupted
                }
            }
        }.apply {
            name = "UI - PairingActivity" // Set the name to match Java version
            start() // Start the thread
        }
    }

    private fun joinAddThread() {
        if (addThread != null) {
            addThread?.interrupt()

            try {
                addThread?.join()
            } catch (e: InterruptedException) {
                e.printStackTrace()
                // Since we can't handle the InterruptedException here,
                // we will re-interrupt the thread to set the interrupt status back to true.
                Thread.currentThread().interrupt()
            }

            addThread = null
        }
    }

    override fun onStop() {
        super.onStop()
        // Close dialogs when the activity stops
        Dialog.closeDialogs()
        SpinnerDialog.closeDialogs(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        // Unbind from the service and join the thread
        if (managerBinder != null) {
            joinAddThread()
            unbindService(serviceConnection)
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pairing)

        // Retrieve the clientAccount, authority, and IP address from the intent
        val clientAccount = intent.getStringExtra("clientAccount")
        val ipAddress = intent.getStringExtra("ipAddress")
        val authority = intent.getStringExtra("authority")

        // Log the retrieved values for debugging
        Log.d("PairingActivity", "Received clientAccount: $clientAccount, authority: $authority, and ipAddress: $ipAddress") //TODO: CLEAN

        // Bind to the ComputerManagerService
        bindService(Intent(this, ComputerManagerService::class.java), serviceConnection, Context.BIND_AUTO_CREATE)
    }


}
