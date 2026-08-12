package com.limelight.preferences

import java.io.IOException
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.URI
import java.net.URISyntaxException
import java.util.Collections
import java.util.concurrent.LinkedBlockingQueue

import com.limelight.R
import com.limelight.computers.ComputerManagerService
import com.limelight.nvstream.http.ComputerDetails
import com.limelight.nvstream.http.NvHTTP
import com.limelight.nvstream.jni.MoonBridge
import com.limelight.utils.Dialog
import com.limelight.utils.NetHelper
import com.limelight.utils.ServerHelper
import com.limelight.utils.SpinnerDialog
import com.limelight.utils.UiHelper

import android.app.Activity
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.res.Configuration
import android.os.Bundle
import android.os.IBinder
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.core.view.WindowCompat

class AddComputerManually : Activity() {
    companion object {
        const val EXTRA_ADDED_COMPUTER_UUID = "com.limelight.extra.ADDED_COMPUTER_UUID"
    }

    private lateinit var hostText: EditText
    private lateinit var portText: EditText
    private lateinit var addPcButton: Button
    private var managerBinder: ComputerManagerService.ComputerManagerBinder? = null
    private val computersToAdd = LinkedBlockingQueue<String>()
    private var addThread: Thread? = null

    private val serviceConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, binder: IBinder) {
            managerBinder = binder as ComputerManagerService.ComputerManagerBinder
            startAddThread()
        }

        override fun onServiceDisconnected(className: ComponentName) {
            joinAddThread()
            managerBinder = null
        }
    }

    private fun isIPv6Address(address: String): Boolean {
        return try {
            val inetAddress = InetAddress.getByName(address)
            inetAddress.address.size == 16
        } catch (e: Exception) {
            address.count { it == ':' } >= 2
        }
    }

    private fun isWrongSubnetSiteLocalAddress(address: String): Boolean {
        try {
            val targetAddress = InetAddress.getByName(address)
            if (targetAddress !is Inet4Address || !targetAddress.isSiteLocalAddress) {
                return false
            }

            for (iface in Collections.list(NetworkInterface.getNetworkInterfaces())) {
                for (addr in iface.interfaceAddresses) {
                    if (addr.address !is Inet4Address || !addr.address.isSiteLocalAddress) {
                        continue
                    }

                    val targetAddrBytes = targetAddress.address
                    val ifaceAddrBytes = addr.address.address

                    var addressMatches = true
                    for (i in 0 until addr.networkPrefixLength) {
                        if ((ifaceAddrBytes[i / 8].toInt() and (1 shl (i % 8))) != (targetAddrBytes[i / 8].toInt() and (1 shl (i % 8)))) {
                            addressMatches = false
                            break
                        }
                    }

                    if (addressMatches) {
                        return false
                    }
                }
            }

            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    private fun parseRawUserInputToUri(rawUserInput: String): URI? {
        val colonCount = rawUserInput.count { it == ':' }
        val likelyIPv6 = colonCount >= 2

        try {
            val uri = URI("moonlight://$rawUserInput")
            if (!uri.host.isNullOrEmpty()) {
                return uri
            }
        } catch (ignored: URISyntaxException) {}

        if (likelyIPv6 && !rawUserInput.startsWith("[")) {
            try {
                val lastColonIndex = rawUserInput.lastIndexOf(':')

                var hasPort = false
                if (lastColonIndex > 0 && lastColonIndex < rawUserInput.length - 1) {
                    val possiblePort = rawUserInput.substring(lastColonIndex + 1)
                    try {
                        val port = possiblePort.toInt()
                        if (port in 1..65535) {
                            hasPort = true
                        }
                    } catch (e: NumberFormatException) {}
                }

                val addressWithBrackets = if (hasPort) {
                    val address = rawUserInput.substring(0, lastColonIndex)
                    val port = rawUserInput.substring(lastColonIndex + 1)
                    "[$address]:$port"
                } else {
                    "[$rawUserInput]"
                }

                val uri = URI("moonlight://$addressWithBrackets")
                if (!uri.host.isNullOrEmpty()) {
                    return uri
                }
            } catch (ignored: URISyntaxException) {}
        }

        if (!rawUserInput.startsWith("[")) {
            try {
                val uri = URI("moonlight://[$rawUserInput]")
                if (!uri.host.isNullOrEmpty()) {
                    return uri
                }
            } catch (ignored: URISyntaxException) {}
        }

        return null
    }

    @Throws(InterruptedException::class)
    private fun doAddPc(rawUserInput: String) {
        var wrongSiteLocal = false
        var activeNetworkIsVpn = false
        var invalidInput = false
        var success: Boolean
        var portTestResult: Int
        var hostAddress: String? = null
        var isIPv6 = false
        var addedComputerUuid: String? = null

        val dialog = SpinnerDialog.displayDialog(this, resources.getString(R.string.title_add_pc),
                resources.getString(R.string.msg_add_pc), false)

        try {
            val details = ComputerDetails()

            val uri = parseRawUserInputToUri(rawUserInput)
            if (uri != null && !uri.host.isNullOrEmpty()) {
                val host = uri.host
                var port = uri.port

                hostAddress = host
                isIPv6 = isIPv6Address(host)

                if (port == -1) {
                    port = NvHTTP.DEFAULT_HTTP_PORT
                }

                details.manualAddress = ComputerDetails.AddressTuple(host, port)
                success = managerBinder!!.addComputerBlocking(details)
                if (success) {
                    addedComputerUuid = details.uuid
                }
                if (!success) {
                    activeNetworkIsVpn = NetHelper.isActiveNetworkVpn(this)
                    // A VPN may route private subnets that don't match any local
                    // interface address, so the LAN subnet heuristic is invalid.
                    wrongSiteLocal = !activeNetworkIsVpn && isWrongSubnetSiteLocalAddress(host)
                }
            } else {
                success = false
                invalidInput = true
            }
        } catch (e: InterruptedException) {
            dialog.dismiss()
            throw e
        } catch (e: IllegalArgumentException) {
            e.printStackTrace()
            success = false
            invalidInput = true
        }

        if (!success && !wrongSiteLocal && !invalidInput && !activeNetworkIsVpn) {
            portTestResult = MoonBridge.testClientConnectivity(ServerHelper.CONNECTION_TEST_SERVER, 443,
                    MoonBridge.ML_PORT_FLAG_TCP_47984 or MoonBridge.ML_PORT_FLAG_TCP_47989)
        } else {
            portTestResult = MoonBridge.ML_TEST_RESULT_INCONCLUSIVE
        }

        dialog.dismiss()

        if (invalidInput) {
            setAddingState(false)
            Dialog.displayDialog(this, resources.getString(R.string.conn_error_title), resources.getString(R.string.addpc_unknown_host), false)
        } else if (wrongSiteLocal) {
            setAddingState(false)
            Dialog.displayDialog(this, resources.getString(R.string.conn_error_title), resources.getString(R.string.addpc_wrong_sitelocal), false)
        } else if (!success) {
            setAddingState(false)
            var dialogText = when {
                activeNetworkIsVpn -> resources.getString(R.string.addpc_fail_vpn)
                portTestResult != MoonBridge.ML_TEST_RESULT_INCONCLUSIVE && portTestResult != 0 ->
                    resources.getString(R.string.nettest_text_blocked)
                else -> resources.getString(R.string.addpc_fail)
            }

            if (isIPv6) {
                dialogText += "\n\n提示：如果您使用的是IPv6地址，请检查：\n" +
                        "1. 光猫防火墙是否放行了IPv6流量\n" +
                        "2. 路由器是否启用了IPv6端口转发\n" +
                        "3. 目标主机的IPv6防火墙设置"
            }

            Dialog.displayDialog(this, resources.getString(R.string.conn_error_title), dialogText, false)
        } else {
            this@AddComputerManually.runOnUiThread {
                Toast.makeText(this@AddComputerManually, resources.getString(R.string.addpc_success), Toast.LENGTH_LONG).show()

                if (!isFinishing) {
                    setResult(
                        RESULT_OK,
                        Intent().putExtra(EXTRA_ADDED_COMPUTER_UUID, addedComputerUuid)
                    )
                    this@AddComputerManually.finish()
                }
            }
        }
    }

    private fun startAddThread() {
        addThread = Thread {
            while (!Thread.currentThread().isInterrupted) {
                try {
                    val computer = computersToAdd.take()
                    doAddPc(computer)
                } catch (e: InterruptedException) {
                    return@Thread
                }
            }
        }.apply {
            name = "UI - AddComputerManually"
            start()
        }
    }

    private fun joinAddThread() {
        if (addThread != null) {
            addThread!!.interrupt()

            try {
                addThread!!.join()
            } catch (e: InterruptedException) {
                e.printStackTrace()
                Thread.currentThread().interrupt()
            }

            addThread = null
        }
    }

    override fun onStop() {
        super.onStop()

        Dialog.closeDialogs()
        SpinnerDialog.closeDialogs(this)
    }

    override fun onDestroy() {
        super.onDestroy()

        if (managerBinder != null) {
            joinAddThread()
            unbindService(serviceConnection)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        UiHelper.setLocale(this)

        setContentView(R.layout.activity_add_computer_manually)
        applySystemBarAppearance()

        UiHelper.notifyNewRootView(this)

        hostText = findViewById(R.id.hostTextView)
        portText = findViewById(R.id.portTextView)
        addPcButton = findViewById(R.id.addPcButton)
        hostText.imeOptions = EditorInfo.IME_ACTION_NEXT
        hostText.setOnEditorActionListener { _, actionId, keyEvent ->
            if (actionId == EditorInfo.IME_ACTION_NEXT || isEnterKeyDown(keyEvent)) {
                portText.requestFocus()
                true
            } else {
                false
            }
        }
        portText.imeOptions = EditorInfo.IME_ACTION_DONE
        portText.setOnEditorActionListener { _, actionId, keyEvent ->
            if (actionId == EditorInfo.IME_ACTION_DONE || isEnterKeyDown(keyEvent)) {
                handleDoneEvent()
            } else if (actionId == EditorInfo.IME_ACTION_PREVIOUS) {
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(portText.windowToken, 0)
                false
            } else {
                false
            }
        }

        findViewById<android.view.View>(R.id.backButton).setOnClickListener {
            finish()
        }

        addPcButton.setOnClickListener {
            handleDoneEvent()
        }

        hostText.post {
            hostText.requestFocus()
        }

        bindService(Intent(this@AddComputerManually,
                ComputerManagerService::class.java), serviceConnection, Service.BIND_AUTO_CREATE)
    }

    private fun isNightMode(): Boolean {
        return (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
    }

    private fun applySystemBarAppearance() {
        val useDarkSystemIcons = !isNightMode()
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = useDarkSystemIcons
            isAppearanceLightNavigationBars = useDarkSystemIcons
        }
    }

    private fun isEnterKeyDown(keyEvent: KeyEvent?): Boolean {
        return keyEvent != null &&
                keyEvent.action == KeyEvent.ACTION_DOWN &&
                keyEvent.keyCode == KeyEvent.KEYCODE_ENTER
    }

    private fun showLongToast(messageResId: Int) {
        Toast.makeText(this@AddComputerManually, resources.getString(messageResId), Toast.LENGTH_LONG).show()
    }

    private fun buildConnectionTarget(hostAddress: String, portTextValue: String): String {
        return if (portTextValue.isEmpty()) {
            hostAddress
        } else if (hostAddress.startsWith("[") || hostAddress.count { it == ':' } < 2) {
            "$hostAddress:$portTextValue"
        } else {
            "[$hostAddress]:$portTextValue"
        }
    }

    private fun handleDoneEvent(): Boolean {
        val hostAddress = hostText.text.toString().trim()
        val portTextValue = portText.text.toString().trim()

        hostText.error = null
        portText.error = null

        if (hostAddress.isEmpty()) {
            hostText.error = resources.getString(R.string.addpc_enter_ip)
            showLongToast(R.string.addpc_enter_ip)
            return true
        }

        if (portTextValue.isNotEmpty()) {
            val port = portTextValue.toIntOrNull()
            if (port == null || port !in 1..65535) {
                portText.error = resources.getString(R.string.addpc_invalid_port)
                showLongToast(R.string.addpc_invalid_port)
                return true
            }
        }

        setAddingState(true)
        computersToAdd.add(buildConnectionTarget(hostAddress, portTextValue))
        return false
    }

    private fun setAddingState(adding: Boolean) {
        runOnUiThread {
            if (!::addPcButton.isInitialized) return@runOnUiThread
            addPcButton.isEnabled = !adding
            addPcButton.text = getString(
                if (adding) R.string.addpc_action_connecting else R.string.addpc_action_connect
            )
        }
    }
}
