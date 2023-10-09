package com.limelight.solanaWallet

import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.WriterException
import com.google.zxing.common.BitMatrix
import com.limelight.R
import com.limelight.shagaProtocol.MapActivity
import com.limelight.utils.Loggatore
import com.solana.core.PublicKey

@Throws(WriterException::class)
fun encodeAsBitmap(str: String, width: Int, height: Int): Bitmap? {
    val result: BitMatrix
    try {
        result = MultiFormatWriter().encode(str, BarcodeFormat.QR_CODE, width, height, null)
    } catch (iae: IllegalArgumentException) {
        // Unsupported format
        return null
    }

    val w = result.width
    val h = result.height
    val pixels = IntArray(w * h)
    for (y in 0 until h) {
        val offset = y * w
        for (x in 0 until w) {
            pixels[offset + x] = if (result.get(x, y)) -0x1000000 else -0x1
        }
    }
    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    bitmap.setPixels(pixels, 0, width, 0, 0, w, h)
    return bitmap
}
class WalletActivity : AppCompatActivity() {

    private lateinit var walletManager: WalletManager
    private lateinit var solanaBalanceTextView: TextView
    private lateinit var walletPublicKeyTextView: TextView
    private var isQRCodeVisible = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wallet)
        initializeViews()
        walletManager = WalletManager.getInstance()
        setupWallet()
        syncWithSolana()

        // Initialize Buttons
        val syncWithSolanaButton = findViewById<Button>(R.id.syncWithSolanaButton)
        val qrCodeDepositButton = findViewById<Button>(R.id.qrCodeDepositButton)
        val createWalletButton = findViewById<Button>(R.id.createWalletButton)
        val backButton = findViewById<Button>(R.id.backButton)
        val mapButton = findViewById<Button>(R.id.mapButton)

        // Check if Wallet is Initialized
        val isWalletInitialized = SolanaPreferenceManager.getIsWalletInitialized()

        // Toggle Button Visibility
        syncWithSolanaButton.visibility = if (isWalletInitialized) View.VISIBLE else View.GONE
        qrCodeDepositButton.visibility = if (isWalletInitialized) View.VISIBLE else View.GONE
        createWalletButton.visibility = if (!isWalletInitialized) View.VISIBLE else View.GONE

        // Set onClick Listeners
        syncWithSolanaButton.setOnClickListener { syncWithSolana() }
        qrCodeDepositButton.setOnClickListener { toggleQRCodeVisibility() }
        createWalletButton.setOnClickListener {
            WalletInitializer.createNewWalletAccount(this)
            // Refresh UI
            syncWithSolanaButton.visibility = View.VISIBLE
            qrCodeDepositButton.visibility = View.VISIBLE
            createWalletButton.visibility = View.GONE
            setupWallet()
        }
        backButton.setOnClickListener {
            finish()
        }
        mapButton.setOnClickListener {
            val intent = Intent(this, MapActivity::class.java)
            startActivity(intent)
        }
    }

    private fun toggleQRCodeVisibility() {
        val qrCodeImageView = findViewById<ImageView>(R.id.qrCodeImageView)
        if (isQRCodeVisible) {
            // Hide the QR Code
            qrCodeImageView.visibility = View.GONE
        } else {
            // Show the QR Code
            SolanaPreferenceManager.getStoredPublicKey()?.let { publicKey ->
                val publicKeyBase58 = publicKey.toBase58()
                try {
                    val qrCodeBitmap = encodeAsBitmap(publicKeyBase58, 500, 500)
                    if (qrCodeBitmap != null) {
                        qrCodeImageView.setImageBitmap(qrCodeBitmap)
                        qrCodeImageView.visibility = View.VISIBLE
                    } else {
                        Toast.makeText(this, "Failed to generate QR code", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: WriterException) {
                    Toast.makeText(this, "An error occurred while generating QR code", Toast.LENGTH_SHORT).show()
                }
            } ?: run {
                Loggatore.d("WalletDebug", "Public key not found in SharedPreferences.")
                Toast.makeText(this, "Public key not found. Please initialize your wallet.", Toast.LENGTH_SHORT).show()
            }
        }
        // Toggle the flag
        isQRCodeVisible = !isQRCodeVisible
    }

    private fun initializeViews() {
        solanaBalanceTextView = findViewById(R.id.solanaBalanceTextView)
        walletPublicKeyTextView = findViewById(R.id.walletPublicKeyTextView)
    }

    private fun setupWallet() {
        walletManager.setup(
            this,
            { balance -> walletManager.updateUIWithBalance() },
            solanaBalanceTextView,
            walletPublicKeyTextView
        )

        WalletManager.initializeUIWithPlaceholderBalance()
        SolanaPreferenceManager.initialize(this)

        if (!SolanaPreferenceManager.getIsWalletInitialized()) {
            WalletInitializer.initializeWallet(this)
            SolanaPreferenceManager.setIsWalletInitialized(true)
        }
    }

    private fun syncWithSolana() {
        val publicKey: PublicKey? = SolanaPreferenceManager.getStoredPublicKey()
        publicKey?.let {
            walletManager?.fetchAndDisplayBalance(it)
            Loggatore.d("WalletDebug", "Public Key: $it")
        } ?: run {
            Loggatore.d("WalletDebug", "Public key not found in SharedPreferences.")
        }
    }
}
