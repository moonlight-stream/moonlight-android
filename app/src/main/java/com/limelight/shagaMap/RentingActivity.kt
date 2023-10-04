package com.limelight.shagaMap

import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.slider.Slider
import com.limelight.PcView
import com.limelight.R
import com.limelight.nvstream.http.ComputerDetails
import com.limelight.nvstream.http.LimelightCryptoProvider
import com.limelight.nvstream.http.NvHTTP
import com.limelight.nvstream.http.PairingManager
import com.limelight.solanaWallet.SolanaApi
import com.limelight.solanaWallet.SolanaPreferenceManager
import com.limelight.solanaWallet.WalletManager
import com.solana.core.PublicKey
import org.bitcoinj.core.Base58
import org.xmlpull.v1.XmlPullParserException
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Singleton to hold AffairsData
object AffairsDataHolder {
    val affairsMap: HashMap<String, SolanaApi.AffairsData> = HashMap()
}

class RentingActivity : AppCompatActivity() {
    private lateinit var walletManager: WalletManager
    private lateinit var nvHTTP: NvHTTP
    private var computerDetails: ComputerDetails? = null
    private lateinit var pairingManager: PairingManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_renting)
        walletManager = WalletManager.getInstance() // initialization

        // Fetch the Solana Lender Public Key and latency from the Intent
        val solanaLenderPublicKey = intent.getStringExtra("solanaLenderPublicKey")
        val latency = intent.getLongExtra("latency", -1L)


        // Log and check the received values
        Log.d("RentingActivity", "Received solanaLenderPublicKey: $solanaLenderPublicKey")
        Log.d("RentingActivity", "Received latency: $latency")

        if (solanaLenderPublicKey != null && latency != null) {
            // Fetch the affair data from the singleton
            val affairData = AffairsDataHolder.affairsMap[solanaLenderPublicKey] ?: return

            // Initialize the NvHTTP object here
            initializeServer(affairData.ipAddress, solanaLenderPublicKey) // Creates a ComputerDetails object with server's ip & pubkey

           val userPublicKey = SolanaPreferenceManager.getStoredPublicKey()
           walletManager.fetchAndDisplayBalance(userPublicKey) // Trigger fetching Solana balance


            // Populate Text Views and setup slider
            populateTextViews(latency)
            populateTextViews(latency, affairData)
            setupSlider(affairData)

        } else {
            Log.e("RentingActivity", "Required data not found.")
            // Handle this case by finishing the current activity and going back to the map UI
            finish()
        }
    }

    private fun startShagaPairingProcedure(view: View) {  // view parameter is necessary for XML onClick
        // Read the already fetched and stored Solana public key from SolanaPreferenceManager
        val solanaPublicKey: PublicKey? = SolanaPreferenceManager.getStoredPublicKey()
        // Check if the fetched public key is null
        solanaPublicKey?.let { key ->
            // Trigger fetching Solana balance, if needed
            walletManager.fetchAndDisplayBalance(key)
            // Check if computerDetails is not null
            computerDetails?.let { details ->
                val pcViewInstance = PcView()
                pcViewInstance.publicDoPairShaga(details, key)
            } ?: run {
                Log.e("RentingActivity", "ComputerDetails is null. Cannot proceed.")
            }
        } ?: run {
            Log.e("RentingActivity", "Solana Public Key is null. Cannot proceed.")
        }
    }

    private fun fetchServerInfoAndUpdateComputerDetails() {
        try {
            val serverInfo = nvHTTP.getServerInfo(true)
            computerDetails = nvHTTP.getComputerDetails(serverInfo)
            Log.i("RentingActivity", "Fetched server info: $serverInfo")
        } catch (e: IOException) {
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


    private fun populateTextViews(latency: Long) {
        // Update Latency TextView
        findViewById<TextView>(R.id.latency).text = "Latency: $latency"
        // Update Solana Balance TextView
        val storedBalance = SolanaPreferenceManager.getStoredBalance()
        val solanaBalanceLabel = getString(R.string.solana_balance)
        findViewById<TextView>(R.id.solanaBalance).text = "$solanaBalanceLabel $storedBalance"
    }


    private fun setupSlider(data: SolanaApi.AffairsData) {
        // Step 1: Get current time in milliseconds
        val currentTime = System.currentTimeMillis()

        // Step 2: Calculate the maximum and minimum time for renting in milliseconds
        val minTimeMillis = 600000 // 10 minutes in milliseconds
        var maxTimeMillis = (data.affairTerminationTime * 1000 - currentTime).coerceAtLeast(minTimeMillis.toLong())

        // Safety check
        if (maxTimeMillis <= minTimeMillis) {
            maxTimeMillis = minTimeMillis + 1000000L // Add extra time for safety
        }

        // Convert the time from milliseconds to minutes for the slider
        val minTimeMinutes = minTimeMillis / (60 * 1000)
        val maxTimeMinutes = maxTimeMillis / (60 * 1000)

        // Setup slider
        val rentalTimeSlider: Slider = findViewById(R.id.rentalTimeSlider)
        rentalTimeSlider.valueFrom = minTimeMinutes.toFloat()
        rentalTimeSlider.valueTo = maxTimeMinutes.toFloat()
        rentalTimeSlider.value = minTimeMinutes.toFloat() // Set the initial value

        var selectedRentTimeMinutes: Float = minTimeMinutes.toFloat() // Initialize with minTimeMinutes

        // Step 3: Calculate live value and update UI
        rentalTimeSlider.addOnChangeListener { _, value, _ ->
            selectedRentTimeMinutes = value // Update the selected time in minutes
            val selectedTimeHours = (value / 60).toInt()
            val remainingTimeMinutes = (value % 60).toInt()

            // Convert back to milliseconds for cost calculation
            val selectedRentTimeMillis = value * 60 * 1000
            val expectedRentCost = (selectedRentTimeMillis / (60 * 60 * 1000.0)) * data.usdcPerHour // Calculate the cost in USDC

            // Update the UI
            findViewById<TextView>(R.id.expectedRentCost).text = "Expected Rent Cost: ${String.format("%.2f", expectedRentCost)} USDC"
            findViewById<TextView>(R.id.selectedRentTime).text = "Selected Time: ${selectedTimeHours}h ${remainingTimeMinutes}m"
        }

        val proceedButton: Button = findViewById(R.id.proceedButton)
        proceedButton.setOnClickListener { clickedView ->
            // Convert the selected time back to milliseconds for the transaction
            val selectedRentTimeMillis = selectedRentTimeMinutes * 60 * 1000
            // Add these two functions
            //startSolanaTransaction(selectedRentTimeMillis)
            startShagaPairingProcedure(view = clickedView)
        }
    }

    //startSolanaTransaction(selectedRentTimeMillis) {
    //    return
   // }

    private fun populateTextViews(latency: Long, data: SolanaApi.AffairsData) {
        // Decode Base64 encoded values
        val decodedGpuName = String(android.util.Base64.decode(data.gpuName, android.util.Base64.DEFAULT))
        val decodedCpuName = String(android.util.Base64.decode(data.cpuName, android.util.Base64.DEFAULT))
        val decodedAuthority = Base58.encode(String(android.util.Base64.decode(data.authority, android.util.Base64.DEFAULT)).toByteArray())

        // Existing logic for latency
        findViewById<TextView>(R.id.latency).text = "Latency: $latency ms"

        // New logic to populate decoded and formatted values
        findViewById<TextView>(R.id.gpuName).text = "GPU Model: $decodedGpuName"
        findViewById<TextView>(R.id.cpuName).text = "CPU Model: $decodedCpuName"

        val totalRamGb = data.totalRamMb / 1024.0  // Convert MB to GB
        findViewById<TextView>(R.id.totalRamGb).text = "Total RAM: ${String.format("%.2f", totalRamGb)} GB"

        findViewById<TextView>(R.id.usdcPerHour).text = "Cost per hour: ${data.usdcPerHour} USDC"

        // Convert Unix timestamp to a human-readable date
        val affairTerminationTimeInMillis = data.affairTerminationTime * 1000
        val affairTerminationDate = Date(affairTerminationTimeInMillis)
        val format = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
        findViewById<TextView>(R.id.affairTerminationTime).text = "Rent available until: ${format.format(affairTerminationDate)}"

        findViewById<TextView>(R.id.solanaLenderPublicKey).text = "Lender's ID: $decodedAuthority"
    }

}
