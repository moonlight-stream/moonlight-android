package com.limelight.shagaMap

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.slider.Slider
import com.limelight.R
import com.limelight.computers.ComputerManagerService
import com.limelight.solanaWallet.SolanaApi
import com.limelight.solanaWallet.SolanaPreferenceManager
import com.limelight.solanaWallet.WalletInitializer
import com.limelight.solanaWallet.WalletManager
import com.limelight.shagaMap.ShagaTransactions
import com.mapbox.maps.extension.style.expressions.dsl.generated.string
import com.solana.api.Api
import com.solana.api.getRecentBlockhash
import com.solana.api.sendTransaction
import com.solana.core.Account
import com.solana.core.AccountMeta
import com.solana.core.PublicKey
import com.solana.core.TransactionBuilder
import com.solana.core.TransactionInstruction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.serialization.Serializable
import java.nio.ByteBuffer

// Singleton to hold AffairsData
object AffairsDataHolder {
    val affairsMap: HashMap<String, DecodedAffairsData> = HashMap()
}

data class DecodedAffairsData(
    val authority: PublicKey,
    val client: PublicKey,
    val rental: PublicKey?,
    val ipAddress: String,
    val cpuName: String,
    val gpuName: String,
    val totalRamMb: UInt,
    val solPerHour: Double,
    val affairState: SolanaApi.AffairState,
    val affairTerminationTime: ULong,
    val activeRentalStartTime: ULong,
    val dueRentAmount: ULong
) {
    val affairStateString: String
        get() = when (affairState) {
            SolanaApi.AffairState.Available -> "Available"
            SolanaApi.AffairState.Unavailable -> "Unavailable"
        }
    }

class RentingActivity : AppCompatActivity() {
    private lateinit var walletManager: WalletManager
    private lateinit var solanaBalanceTextView: TextView
    private lateinit var proceedButton: Button
    private lateinit var startPairingButton: Button
    private lateinit var rentalTimeSlider: Slider
    private lateinit var shagaPairingHelper: ShagaPairingHelper
    private lateinit var managerBinder: ComputerManagerService.ComputerManagerBinder // make sure to initialize this


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_renting)

        shagaPairingHelper = ShagaPairingHelper(this, managerBinder, this)


        // Initialize other views and managers
        proceedButton = findViewById(R.id.proceedButton)
        startPairingButton = findViewById(R.id.startPairingButton)
        initializeViews()
        handleIntentExtras()
        setupWalletManager()
        syncWithSolana()
        setupButtonListeners()
    }

    private fun setupButtonListeners() {
        startPairingButton.setOnClickListener {
            // Fetch the required details
            val solanaPublicKey = SolanaPreferenceManager.getStoredPublicKey()

            // Using the instance of ShagaPairingHelper to get computer details
            val computerDetails = shagaPairingHelper.getComputerDetails()

            // Error Handling: Check if any of the required details are null
            if (solanaPublicKey == null || computerDetails == null) {
                Log.e("RentingActivity", "Required details for pairing are missing. Cannot proceed.")
                return@setOnClickListener
            }

            // Invoke the pairing procedure through the ShagaPairingHelper instance
            shagaPairingHelper.startShagaPairingProcedure(solanaPublicKey, computerDetails)

            // TODO: Add any additional logic here, if needed
        }
    }


    private fun initializeViews() {
        solanaBalanceTextView = findViewById(R.id.solanaBalance)
        proceedButton = findViewById(R.id.proceedButton)
        rentalTimeSlider = findViewById(R.id.rentalTimeSlider)
    }

    private fun setupWalletManager() {
        walletManager = WalletManager.getInstance()
        SolanaPreferenceManager.initialize(this)

        if (!SolanaPreferenceManager.getIsWalletInitialized()) {
            WalletInitializer.initializeWallet(this)
            SolanaPreferenceManager.setIsWalletInitialized(true)
        }
    }

    private fun syncWithSolana() {
        val userPublicKey = SolanaPreferenceManager.getStoredPublicKey()
        userPublicKey?.let {
            walletManager.fetchAndDisplayBalance(it)
            Log.d("RentingActivity", "Public Key: $it")
        } ?: run {
            Log.e("RentingActivity", "Public key not found in SharedPreferences.")
        }
    }

    private fun handleIntentExtras() {
        val solanaLenderPublicKey = intent.getStringExtra("solanaLenderPublicKey") // <<<<< THIS IS THE AUTHORITY OF THE AFFAIR, NOT THE LENDER
        val latency = intent.getLongExtra("latency", -1L)

        if (solanaLenderPublicKey != null && latency != -1L) {
            val affairData = AffairsDataHolder.affairsMap[solanaLenderPublicKey] ?: return
            populateTextViews(latency, affairData)
            setupSlider(affairData)
        } else {
            Log.e("RentingActivity", "Required data not found.")
            finish()
        }
    }

    private fun updateSliderLabels(slider: Slider, value: Float) {
        val hours = (value / 60).toInt()
        val minutes = (value % 60).toInt()

        slider.setLabelFormatter {
            return@setLabelFormatter "${hours}h ${minutes}m"
        }
    }


    private fun populateTextViews(latency: Long, data: DecodedAffairsData) {
        // Convert List<Byte> to ByteArray for decoding
        val decodedGpuName = String(data.gpuName.toByteArray())
        val decodedCpuName = String(data.cpuName.toByteArray())
        val storedBalance = SolanaPreferenceManager.getStoredBalance()
        val solanaBalanceLabel = getString(R.string.solana_balance)

        // Assuming you have a method to convert PublicKey to String
        val decodedAuthority = data.authority.toString()

        // Populate the views
        findViewById<TextView>(R.id.solanaBalance).text = "$solanaBalanceLabel $storedBalance"
        findViewById<TextView>(R.id.latency).text = "Latency: $latency ms"
        findViewById<TextView>(R.id.gpuName).text = "GPU Model: $decodedGpuName"
        findViewById<TextView>(R.id.cpuName).text = "CPU Model: $decodedCpuName"

        // Convert UInt to Double for division
        val totalRamGb = data.totalRamMb.toDouble() / 1024.0
        findViewById<TextView>(R.id.totalRamGb).text = "Total RAM: ${String.format("%.2f", totalRamGb)} GB"

        // Convert ULong to String for SOL per hour
        findViewById<TextView>(R.id.usdcPerHour).text = "Cost per hour: ${data.solPerHour.toString()} SOL"

        // Convert ULong to Long for date conversion
        val affairTerminationTimeInMillis = data.affairTerminationTime.toLong() * 1000
        val affairTerminationDate = Date(affairTerminationTimeInMillis)
        val format = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
        findViewById<TextView>(R.id.affairTerminationTime).text = "Rent available until: ${format.format(affairTerminationDate)}"

        findViewById<TextView>(R.id.solanaLenderPublicKey).text = "Lender's ID: $decodedAuthority" // IT'S THE AFFAIR AUTHORITY, NOT THE LENDER PUBKEY, IT'S OK
    }

    private fun setupSlider(data: DecodedAffairsData) {
        // Step 1: Get current time in milliseconds
        val currentTime = System.currentTimeMillis()
        // Step 2: Calculate the maximum and minimum time for renting in milliseconds
        val minTimeMillis: ULong = 600000uL // 10 minutes in milliseconds, explicitly set as ULong for type safety
        val affairTerminationMillis: ULong = data.affairTerminationTime * 1000uL // Assuming affairTerminationTime is in seconds, convert it to milliseconds
        // Check for any time discrepancies and correct them
        var maxTimeMillis: ULong = if (affairTerminationMillis > currentTime.toULong()) {
            affairTerminationMillis - currentTime.toULong()
        } else {
            0uL
        }
        // Safety check
        maxTimeMillis = maxTimeMillis.coerceAtLeast(minTimeMillis)

        if (maxTimeMillis <= minTimeMillis) {
            maxTimeMillis = minTimeMillis + 1000000uL // Add extra 1,000,000 milliseconds (or 1,000 seconds) for safety
        }
        // Convert the time from milliseconds to minutes for the slider
        val minTimeMinutes = (minTimeMillis / 60000uL).toFloat() // 60 * 1000 = 60000 for milliseconds to minutes conversion
        val maxTimeMinutes = (maxTimeMillis / 60000uL).toFloat()
        // Setup slider
        val rentalTimeSlider: Slider = findViewById(R.id.rentalTimeSlider)
        rentalTimeSlider.valueFrom = minTimeMinutes
        rentalTimeSlider.valueTo = maxTimeMinutes
        rentalTimeSlider.value = minTimeMinutes // Set the initial value
        // Initialize the labels
        updateSliderLabels(rentalTimeSlider, minTimeMinutes)

        var selectedRentTimeMinutes: Float = minTimeMinutes // Initialize with minTimeMinutes
        // Step 3: Calculate live value and update UI
        rentalTimeSlider.addOnChangeListener { _, value, _ ->
            selectedRentTimeMinutes = value // Update the selected time in minutes
            val selectedTimeHours = (value / 60).toInt()
            val remainingTimeMinutes = (value % 60).toInt()
            // Convert back to milliseconds for cost calculation
            val selectedRentTimeMillis = (value * 60 * 1000).toLong()  // Float to Long conversion
            val expectedRentCost = (selectedRentTimeMillis / (60 * 60 * 1000.0)) * data.solPerHour.toDouble()  // Assuming solPerHour is now in SOL after conversion

            updateSliderLabels(rentalTimeSlider, value)
            // Update the UI
            findViewById<TextView>(R.id.expectedRentCost).text = "Expected Rent Cost: ${String.format("%.2f", expectedRentCost)} SOL"
            findViewById<TextView>(R.id.selectedRentTime).text = "Selected Time: ${selectedTimeHours}h ${remainingTimeMinutes}m"
        }

        proceedButton.setOnClickListener { clickedView ->
            // Use a coroutine to perform the network request to fetch the block hash
            CoroutineScope(Dispatchers.Main).launch {
                // Step 1: Calculate the selected rent time in seconds
                val selectedRentTimeMillis = (selectedRentTimeMinutes * 60 * 1000).toLong()
                val selectedRentTimeSeconds = (selectedRentTimeMillis / 1000).toULong()

                // Step 2: Fetch the Solana public key for the client
                val solanaClientPublicKey = SolanaPreferenceManager.getStoredPublicKey()
                if (solanaClientPublicKey == null) {
                    Log.e("RentingActivity", "Solana public key is missing. Cannot proceed with the transaction.")
                    return@launch
                }

                // Step 3: Obtain the Account object for feePayer
                val shagaTransactions = ShagaTransactions()
                val feePayerAccount = shagaTransactions.getHotAccountForFeePayer()
                if (feePayerAccount == null) {
                    Log.e("RentingActivity", "Failed to obtain fee payer account. Cannot proceed with the transaction.")
                    return@launch
                }

                // Step 4: Prepare arguments for the startRental function
                val rentalArgs = SolanaApi.StartRentalInstructionArgs(rentalTerminationTime = selectedRentTimeSeconds)
                val programIDString = getString(R.string.shaga_program_address)

                // Step 5: Generate the transaction instruction
                val txInstruction = shagaTransactions.startRental(
                    authority = data.authority,
                    client = solanaClientPublicKey,
                    programId = PublicKey(programIDString),
                    args = rentalArgs
                )

                // Step 6: Fetch the recent block hash
                val recentBlockHashResult = withContext(Dispatchers.IO) { SolanaApi.getRecentBlockHashFromApi() }

                if (recentBlockHashResult.isFailure) {
                    Log.e("RentingActivity", "Failed to fetch recent blockhash.")
                    return@launch
                }

                val recentBlockHash = recentBlockHashResult.getOrNull() ?: return@launch

                // Step 7: Build the transaction
                val transaction = TransactionBuilder()
                    .addInstruction(txInstruction)
                    .setRecentBlockHash(recentBlockHash)
                    .setSigners(listOf(feePayerAccount))
                    .build()

                // Send the transaction
                val sendTransactionResult = withContext(Dispatchers.IO) {
                    SolanaApi.solana.api.sendTransaction(
                        transaction = transaction,
                        signers = listOf(feePayerAccount),
                        recentBlockHash = recentBlockHash
                    )
                }

                // Check if sending was successful
                if (sendTransactionResult.isFailure) {
                    Log.e("RentingActivity", "Failed to send transaction.")
                    return@launch
                }

                val transactionId = sendTransactionResult.getOrNull() ?: return@launch
                Log.d("RentingActivity", "Transaction successful, ID: $transactionId")
            }
        }
    }
}
