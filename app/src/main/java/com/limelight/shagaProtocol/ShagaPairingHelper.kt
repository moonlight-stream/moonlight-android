package com.limelight.shagaProtocol

import android.app.Activity
import android.content.Context
import android.util.Log
import android.widget.Toast
import com.limelight.R
import com.limelight.computers.ComputerManagerService
import com.limelight.nvstream.http.ComputerDetails
import com.limelight.nvstream.http.ComputerDetails.AddressTuple
import com.limelight.nvstream.http.NvHTTP
import com.limelight.nvstream.jni.MoonBridge
import com.limelight.utils.Dialog
import com.limelight.utils.ServerHelper
import com.limelight.utils.SpinnerDialog
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.URI
import java.net.URISyntaxException
import java.util.Collections
import java.util.concurrent.LinkedBlockingQueue
import com.limelight.nvstream.http.LimelightCryptoProvider
import com.limelight.nvstream.http.PairingManager
import com.solana.core.PublicKey
import okio.IOException
import org.xmlpull.v1.XmlPullParserException

class ShagaPairingHelper(
    private val context: Context,
    private val managerBinder: ComputerManagerService.ComputerManagerBinder,
    private val activityContext: Activity) {

    private lateinit var nvHTTP: NvHTTP
    private lateinit var pairingManager: PairingManager
    private var computerDetails: ComputerDetails? = null

    companion object {
        const val DEFAULT_HTTP_PORT = NvHTTP.DEFAULT_HTTP_PORT
    }

    private val computersToAdd = LinkedBlockingQueue<String>()
    private var addThread: Thread? = null

    init {
        startAddThread()
    }

    private fun startAddThread() {
        addThread = Thread {
            while (!Thread.currentThread().isInterrupted) {
                try {
                    val computer = computersToAdd.take()
                    doAddPc(computer, activityContext)
                } catch (e: InterruptedException) {
                    return@Thread
                }
            }
        }
        addThread?.name = "UI - ShagaPairingHelper"
        addThread?.start()
    }

    fun getComputerDetails(): ComputerDetails? {
        return computerDetails
    }


    fun startShagaPairingProcedure(solanaPublicKey: PublicKey?, computerDetails: ComputerDetails?) {
        solanaPublicKey?.let { key ->
            // Do something with the key
            computerDetails?.let { details ->
                // Do pairing
            } ?: run {
                Log.e("ShagaPairingHelper", "ComputerDetails is null. Cannot proceed.")
            }
        } ?: run {
            Log.e("ShagaPairingHelper", "Solana Public Key is null. Cannot proceed.")
        }
    }


    private fun fetchServerInfoAndUpdateComputerDetails() {
        try {
            val serverInfo = nvHTTP.getServerInfo(true)
            computerDetails = nvHTTP.getComputerDetails(serverInfo)
            Log.i("RentingActivity", "Fetched server info: $serverInfo")
        } catch (e: java.io.IOException) {
            Log.e("RentingActivity", "Network error while fetching server info: ${e.message}")
        } catch (e: XmlPullParserException) {
            Log.e("RentingActivity", "Parsing error while fetching server info: ${e.message}")
        }
    }

    private fun initializeServer(ipAddress: String, uniqueId: String) {
        val httpsPort = 47984  // Standard port for HTTPS; change if your case is different
        // Validate the incoming IP address and unique ID before proceeding.
        if (ipAddress.isBlank() || uniqueId.isBlank()) {
            Log.e("RentingActivity", "IP address or Unique ID is blank. Cannot proceed.")
            return
        }
        // Initialize AddressTuple
        val addressTuple = try {
            ComputerDetails.AddressTuple(ipAddress, httpsPort)
        } catch (e: IllegalArgumentException) {
            Log.e("RentingActivity", "Invalid AddressTuple parameters: ${e.message}")
            return
        }
        // Initialize cryptoProvider; replace with actual object if you have one
        val cryptoProvider: LimelightCryptoProvider? = null
        // Initialize NvHTTP
        try {
            nvHTTP = NvHTTP(addressTuple, httpsPort, uniqueId, null, cryptoProvider)
            Log.i("RentingActivity", "NvHTTP initialized with IP: $ipAddress and ID: $uniqueId")
        } catch (e: IOException) {
            Log.e("RentingActivity", "Failed to initialize NvHTTP with IP: $ipAddress and ID: $uniqueId. Error: ${e.message}")
            return
        } catch (e: NullPointerException) {
            Log.e("RentingActivity", "Null pointer exception during NvHTTP initialization: ${e.message}")
            return
        } catch (e: Exception) {
            Log.e("RentingActivity", "General exception during NvHTTP initialization: ${e.message}")
            return
        }
        // Fetch server information and update ComputerDetails object.
        fetchServerInfoAndUpdateComputerDetails()
        pairingManager = nvHTTP.pairingManager
    }

    private fun isWrongSubnetSiteLocalAddress(address: String): Boolean {
        return try {
            val targetAddress = InetAddress.getByName(address)
            if (targetAddress !is Inet4Address || !targetAddress.isSiteLocalAddress()) {
                return false
            }

            // We have a site-local address. Look for a matching local interface.
            for (iface in Collections.list(NetworkInterface.getNetworkInterfaces())) {
                for (addr in iface.interfaceAddresses) {
                    if (addr.address !is Inet4Address || !addr.address.isSiteLocalAddress) {
                        // Skip non-site-local or non-IPv4 addresses
                        continue
                    }
                    val targetAddrBytes = targetAddress.getAddress()
                    val ifaceAddrBytes = addr.address.address

                    // Compare prefix to ensure it's the same
                    var addressMatches = true
                    for (i in 0 until addr.networkPrefixLength) {
                        if (ifaceAddrBytes[i / 8].toInt() and (1 shl i % 8) != targetAddrBytes[i / 8].toInt() and (1 shl i % 8)) {
                            addressMatches = false
                            break
                        }
                    }
                    if (addressMatches) {
                        return false
                    }
                }
            }

            // Couldn't find a matching interface
            true
        } catch (e: Exception) {
            // Catch all exceptions because some broken Android devices
            // will throw an NPE from inside getNetworkInterfaces().
            e.printStackTrace()
            false
        }
    }

    private fun parseRawUserInputToUri(rawUserInput: String): URI? {
        try {
            // Try adding a scheme and parsing the remaining input.
            // This handles input like 127.0.0.1:47989, [::1], [::1]:47989, and 127.0.0.1.
            val uri = URI("moonlight://$rawUserInput")
            if (uri.host != null && !uri.host.isEmpty()) {
                return uri
            }
        } catch (ignored: URISyntaxException) {
        }
        try {
            // Attempt to escape the input as an IPv6 literal.
            // This handles input like ::1.
            val uri = URI("moonlight://[$rawUserInput]")
            if (uri.host != null && !uri.host.isEmpty()) {
                return uri
            }
        } catch (ignored: URISyntaxException) {
        }
        return null
    }
    @Throws(InterruptedException::class)
    private fun doAddPc(rawUserInput: String, activityContext: Activity): ComputerDetails? {
        var wrongSiteLocal = false
        var invalidInput = false
        var success = false
        var portTestResult = MoonBridge.ML_TEST_RESULT_INCONCLUSIVE

        val dialog = SpinnerDialog.displayDialog(
            activityContext,
            activityContext.resources.getString(R.string.title_add_pc),
            activityContext.resources.getString(R.string.msg_add_pc),
            false
        )

        // Declare 'details' and initialize it to null
        var details: ComputerDetails? = null

        try {
            // Assign a new ComputerDetails object here
            details = ComputerDetails()

            val uri = parseRawUserInputToUri(rawUserInput)

            if (uri != null && uri.host != null && uri.host.isNotEmpty()) {
                val host = uri.host
                var port = uri.port
                if (port == -1) {
                    port = NvHTTP.DEFAULT_HTTP_PORT
                }

                details.manualAddress = AddressTuple(host, port)
                success = managerBinder.addComputerBlocking(details)
                if (!success) {
                    wrongSiteLocal = isWrongSubnetSiteLocalAddress(host)
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

        portTestResult = if (!success && !wrongSiteLocal && !invalidInput) {
            MoonBridge.testClientConnectivity(
                ServerHelper.CONNECTION_TEST_SERVER, 443,
                MoonBridge.ML_PORT_FLAG_TCP_47984 or MoonBridge.ML_PORT_FLAG_TCP_47989
            )
        } else {
            MoonBridge.ML_TEST_RESULT_INCONCLUSIVE
        }

        dialog.dismiss()

        if (invalidInput) {
            Dialog.displayDialog(
                activityContext,
                activityContext.resources.getString(R.string.conn_error_title),
                activityContext.resources.getString(R.string.addpc_unknown_host),
                false
            )
            return null
        } else if (wrongSiteLocal) {
            Dialog.displayDialog(
                activityContext,
                activityContext.resources.getString(R.string.conn_error_title),
                activityContext.resources.getString(R.string.addpc_wrong_sitelocal),
                false
            )
            return null
        } else if (!success) {
            val dialogText = if (portTestResult != MoonBridge.ML_TEST_RESULT_INCONCLUSIVE && portTestResult != 0) {
                activityContext.resources.getString(R.string.nettest_text_blocked)
            } else {
                activityContext.resources.getString(R.string.addpc_fail)
            }
            Dialog.displayDialog(
                activityContext,
                activityContext.resources.getString(R.string.conn_error_title),
                dialogText,
                false
            )
            return null
        } else {
            activityContext.runOnUiThread {
                Toast.makeText(
                    activityContext,
                    activityContext.resources.getString(R.string.addpc_success),
                    Toast.LENGTH_LONG
                ).show()
                if (!(activityContext as Activity).isFinishing) {
                    activityContext.finish()
                }
            }
            return details  // Now 'details' is accessible here
        }
    }


    fun enqueueComputerForAdding(ipAddress: String) {
        computersToAdd.add(ipAddress)
    }

    fun proceedWithPairing(solanaPublicKey: String, ipAddress: String) {
        val computerDetails = doAddPc(ipAddress, activityContext)

        if (computerDetails != null) {
            // Perform Shaga pairing
            publicDoPairShaga(computerDetails, solanaPublicKey)
        } else {
            // Make sure that 'activityContext' is of type 'Activity' and is non-null
            // Make sure that the string resources are correctly defined and accessible
            Dialog.displayDialog(
                activityContext,  // <-- make sure this is non-null and of type Activity
                activityContext.getString(R.string.conn_error_title),
                activityContext.getString(R.string.addpc_fail),
                false
            )
        }
    }

    // Function to perform the Shaga pairing
    private fun publicDoPairShaga(computerDetails: ComputerDetails, solanaPublicKey: String) {
        // Your existing Shaga pairing logic here
        // Replace this comment with your implementation
    }
}
