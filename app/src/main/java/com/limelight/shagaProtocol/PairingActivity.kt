package com.limelight.shagaProtocol

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
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
import com.limelight.binding.PlatformBinding
import com.limelight.nvstream.http.PairingManager
import com.limelight.solanaWallet.EncryptionHelper
import com.solana.core.PublicKey
import okio.FileNotFoundException
import okio.IOException
import org.xmlpull.v1.XmlPullParserException
import java.net.UnknownHostException


class PairingActivity : AppCompatActivity() {

    private lateinit var pairingStatusTextView: TextView
    private lateinit var pairingMessageTextView: TextView
    private lateinit var retryButton: Button

    private var managerBinder: ComputerManagerService.ComputerManagerBinder? = null
    private var addThread: Thread? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.d("ShagaPair", "Service connected")
            managerBinder = service as ComputerManagerService.ComputerManagerBinder
            startAddThread()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.d("ShagaPair", "Service disconnected")
            joinAddThread()
            managerBinder = null
        }
    }

    private fun isWrongSubnetSiteLocalAddress(address: String): Boolean {
        Log.d("ShagaPair", "Checking subnet for address: $address")
        return try {
            val targetAddress = InetAddress.getByName(address)
            if (targetAddress !is Inet4Address || !targetAddress.isSiteLocalAddress) {
                Log.d("ShagaPair", "Address is not an IPv4 or not a site local address")
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
                            Log.d("ShagaPair", "Found matching interface")
                            foundMatchingInterface = true
                            break
                        }
                    }

                    if (foundMatchingInterface) break
                }

                if (!foundMatchingInterface) {
                    Log.d("ShagaPair", "Did not find a matching interface")
                }

                !foundMatchingInterface
            }
        } catch (e: Exception) {
            // Handle exceptions
            Log.e("ShagaPair", "Exception occurred: ${e.message}")
            e.printStackTrace()
            false
        }
    }

    private fun doAddPc() {
        Log.d("ShagaPair", "Entered doAddPc function")
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
            Log.d("ShagaPair", "Attempting to retrieve host and port")
            val host =
                intent.getStringExtra("ipAddress") // Directly using the IP address, not from user input
            val port = NvHTTP.DEFAULT_HTTP_PORT // Default port, same as Java

            if (host != null && host.isNotEmpty()) { // Check for a valid host
                Log.d("ShagaPair", "Got a valid host: $host")
                details.manualAddress = ComputerDetails.AddressTuple(host, port)
                details.name = "SunshineLender"
                success = managerBinder?.addComputerBlocking(details) ?: false

                if (!success) {
                    wrongSiteLocal = isWrongSubnetSiteLocalAddress(host)
                }
            } else {
                // Invalid IP address
                Log.d("ShagaPair", "Invalid host")
                success = false
                invalidInput = true
            }
        } catch (e: InterruptedException) { // InterruptedException catch block
            Log.e("ShagaPair", "InterruptedException occurred: ${e.message}")
            dialog.dismiss()
            throw e // Propagate the exception, same as Java
        } catch (e: IllegalArgumentException) { // IllegalArgumentException catch block
            Log.e("ShagaPair", "IllegalArgumentException occurred: ${e.message}")
            e.printStackTrace()
            success = false
            invalidInput = true
        }

        // Keep the SpinnerDialog open while testing connectivity
        Log.d("ShagaPair", "Checking connectivity conditions")
        if (!success && !wrongSiteLocal && !invalidInput) {
            Log.d("ShagaPair", "Testing client connectivity")
            portTestResult = MoonBridge.testClientConnectivity(
                ServerHelper.CONNECTION_TEST_SERVER,
                443,
                MoonBridge.ML_PORT_FLAG_TCP_47984 or MoonBridge.ML_PORT_FLAG_TCP_47989
            )
        } else {
            Log.d("ShagaPair", "Skipping client connectivity test")
            portTestResult = MoonBridge.ML_TEST_RESULT_INCONCLUSIVE
        }

        Log.d("ShagaPair", "Dismissing spinner dialog")
        dialog.dismiss()

        Log.d("ShagaPair", "Evaluating conditions for displaying dialogs")
        when {
            invalidInput -> {
                Log.d("ShagaPair", "Showing invalid input dialog")
                Dialog.displayDialog(
                    this,
                    getString(R.string.conn_error_title),
                    getString(R.string.addpc_unknown_host),
                    false
                )
            }
            wrongSiteLocal -> {
                Log.d("ShagaPair", "Showing wrong site local dialog")
                Dialog.displayDialog(
                    this,
                    getString(R.string.conn_error_title),
                    getString(R.string.addpc_wrong_sitelocal),
                    false
                )
            }
            !success -> {
                Log.d("ShagaPair", "Showing failure dialog")
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
                Log.d("ShagaPair", "Operation successful, showing success toast and proceeding with pairing")
                runOnUiThread {
                    Toast.makeText(this, getString(R.string.addpc_success), Toast.LENGTH_LONG).show()
                    // Call publicDoPairShaga here
                    val pcViewInstance = PcView.getInstance()
                    if (pcViewInstance != null) {
                        val authority = intent.getStringExtra("authority")
                        authority?.let { PublicKey(it) }?.let { doPairShaga(details, it) } // pass authority as publicKey if not null
                    } else {
                        Log.e("ShagaPair", "PcView instance is null. Cannot proceed with publicDoPairShaga.")
                        Toast.makeText(this, "Failed to initiate pairing; internal error.", Toast.LENGTH_LONG).show()
                    }

                    if (!isFinishing) {
                        finish()
                    }
                }
            }
        }
        Log.d("ShagaPair", "Exiting doAddPc function")
    }


    private fun doPairShaga(computer: ComputerDetails, sunshinePublicKey: PublicKey) {
        // Logging entry into the function
        Log.d("ShagaPair", "Entered doPairShaga")

        // Check if the computer is offline or active address is null
        if (computer.state == ComputerDetails.State.OFFLINE || computer.activeAddress == null) {
            Log.d("ShagaPair", "Computer is OFFLINE or activeAddress is null")
            runOnUiThread {
                pairingMessageTextView.text = "Computer is OFFLINE or activeAddress is null"
                retryButton.visibility = View.VISIBLE
            }
            return
        }

        // Check if managerBinder is null
        if (managerBinder == null) {
            Log.d("ShagaPair", "managerBinder is null")
            runOnUiThread {
                pairingMessageTextView.text = "Manager Binder is null"
                retryButton.visibility = View.VISIBLE
            }
            return
        }

        // Initiate a new thread for the pairing logic
        Thread {
            var message: String? = null
            var success = false

            try {
                // Logging the stopping of computer updates
                Log.d("ShagaPair", "Stopping computer updates")
                PcView.publicStopComputerUpdates(true)

                // Initialize NvHTTP object
                val httpConn = NvHTTP(
                    ServerHelper.getCurrentAddressFromComputer(computer),
                    computer.httpsPort,
                    managerBinder!!.getUniqueId(),
                    computer.serverCert,
                    PlatformBinding.getCryptoProvider(this@PairingActivity) // Using PairingActivity's context
                )

                // Check the current pair state
                when (httpConn.getPairState()) {
                    PairingManager.PairState.PAIRED -> {
                        Log.d("ShagaPair", "Already Paired")
                        message = null
                        success = true
                    }
                    else -> {
                        // Generate a PIN string for pairing
                        val pinStr = PairingManager.generatePinString()
                        Log.d("ShagaPair", "Generated PIN String: $pinStr")

                        // Initialize PairingManager
                        val pm = httpConn.getPairingManager()
                        Log.d("ShagaPair", "PairingManager initialized: $pm")  // Debug log to check if PairingManager is initialized correctly

                        // Convert the public key
                        val ed25519PublicKey = sunshinePublicKey.toByteArray()
                        Log.d("ShagaPair", "ED25519 Public Key: ${ed25519PublicKey.joinToString(", ") { it.toString() }}")  // Debug log to check the ED25519 Public Key

                        val x25519PublicKey = EncryptionHelper.mapPublicEd25519ToX25519(ed25519PublicKey)
                        Log.d("ShagaPair", "X25519 Public Key: ${x25519PublicKey.joinToString(", ") { it.toString() }}")  // Debug log to check the X25519 Public Key

                        // Get Server Info
                        val serverInfo = httpConn.getServerInfo(true)
                        Log.d("ShagaPair", "Server Info: $serverInfo")  // Debug log to check the Server Info

                        // Execute the pairing process
                        val pairState = pm.publicPairShaga(serverInfo, pinStr, x25519PublicKey)
                        Log.d("ShagaPair", "Pairing State: $pairState")  // Debug log to check the Pairing State


                        // Determine the result of the pairing process
                        message = when (pairState) {
                            PairingManager.PairState.PIN_WRONG -> "Incorrect PIN"
                            PairingManager.PairState.FAILED -> if (computer.runningGameId != 0) "Computer is in-game" else "Pairing failed"
                            PairingManager.PairState.ALREADY_IN_PROGRESS -> "Pairing already in progress"
                            PairingManager.PairState.PAIRED -> {
                                managerBinder!!.getComputer(computer.uuid).serverCert = pm.getPairedCert()
                                managerBinder!!.invalidateStateForComputer(computer.uuid)
                                null
                            }
                            else -> null
                        }
                        success = (pairState == PairingManager.PairState.PAIRED)
                        Log.d("ShagaPair", "Pairing state: $pairState, Message: $message")
                    }
                }
            } catch (e: UnknownHostException) {
                Log.e("ShagaPair", "UnknownHostException: ${e.message}")
                message = "Unknown Host"
            } catch (e: FileNotFoundException) {
                Log.e("ShagaPair", "FileNotFoundException: ${e.message}")
                message = "File Not Found"
            } catch (e: XmlPullParserException) {
                Log.e("ShagaPair", "XmlPullParserException: ${e.message}")
                message = e.message
            } catch (e: IOException) {
                Log.e("ShagaPair", "IOException: ${e.message}")
                message = e.message
            } finally {
                runOnUiThread {
                    if (success) {
                        pairingMessageTextView.text = "Pairing successful"
                        retryButton.visibility = View.GONE
                    } else {
                        pairingMessageTextView.text = message ?: "Unknown error"
                        retryButton.visibility = View.VISIBLE
                    }
                }
                Log.d("ShagaPair", "Exiting Thread with Success=$success, Message=$message")
            }
        }.start()
    }

    private fun startAddThread() {
        Log.d("ShagaPair", "Starting add thread")
        addThread = Thread {
            while (!Thread.currentThread().isInterrupted) {
                try {
                    Log.d("ShagaPair", "Calling doAddPc from startAddThread")
                    doAddPc()
                    Log.d("ShagaPair", "Successfully executed doAddPc, breaking loop")
                    break
                } catch (e: InterruptedException) {
                    Log.e("ShagaPair", "Thread interrupted: ${e.message}")
                    return@Thread
                }
            }
        }.apply {
            name = "UI - PairingActivity"
            Log.d("ShagaPair", "Starting thread with name: $name")
            start()
        }
    }

    private fun joinAddThread() {
        Log.d("ShagaPair", "Entering joinAddThread()")
        if (addThread != null) {
            Log.d("ShagaPair", "Interrupting addThread")
            addThread?.interrupt()

            try {
                Log.d("ShagaPair", "Joining addThread")
                addThread?.join()
            } catch (e: InterruptedException) {
                Log.e("ShagaPair", "InterruptedException while joining addThread", e)
                e.printStackTrace()
                // Since we can't handle the InterruptedException here,
                // we will re-interrupt the thread to set the interrupt status back to true.
                Thread.currentThread().interrupt()
            }

            Log.d("ShagaPair", "Setting addThread to null")
            addThread = null
        }
    }

    override fun onStop() {
        super.onStop()
        Log.d("ShagaPair", "onStop() called")
        // Close dialogs when the activity stops
        Dialog.closeDialogs()
        SpinnerDialog.closeDialogs(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("ShagaPair", "onDestroy() called")
        // Unbind from the service and join the thread
        if (managerBinder != null) {
            Log.d("ShagaPair", "Unbinding service and joining thread")
            joinAddThread()
            unbindService(serviceConnection)
        }
    }

    private fun updateUI(success: Boolean, message: String?) {
        runOnUiThread {
            if (success) {
                pairingStatusTextView.text = "Pairing Status: Success"
                pairingMessageTextView.text = ""
                retryButton.visibility = View.GONE
            } else {
                pairingStatusTextView.text = "Pairing Status: Failed"
                pairingMessageTextView.text = message ?: "Unknown Error"
                retryButton.visibility = View.VISIBLE
            }
        }
    }



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("ShagaPair", "onCreate() called")
        setContentView(R.layout.activity_pairing)

        // Initialize UI Components
        pairingStatusTextView = findViewById(R.id.pairingStatus)
        pairingMessageTextView = findViewById(R.id.pairingMessage)
        retryButton = findViewById(R.id.retryButton)

        // Retrieve the clientAccount, authority, and IP address from the intent
        val clientAccount = intent.getStringExtra("clientAccount")
        val ipAddress = intent.getStringExtra("ipAddress")
        val authority = intent.getStringExtra("authority")

        if (authority != null) {
            // Log the retrieved values for debugging
            Log.d("ShagaPair", "Received clientAccount: $clientAccount, authority: $authority, and ipAddress: $ipAddress")

            // Bind to the ComputerManagerService
            Log.d("ShagaPair", "Binding to ComputerManagerService")
            bindService(Intent(this, ComputerManagerService::class.java), serviceConnection, Context.BIND_AUTO_CREATE)

            // Retry button click listener
            retryButton.setOnClickListener {
                doAddPc()
            }
        } else {
            // Handle the null case for authority
            Log.e("ShagaPair", "Authority is null. Cannot proceed.")
            // You could also update the UI here to inform the user
            updateUI(false, "Authority is missing. Cannot proceed.")
        }
    }


}
