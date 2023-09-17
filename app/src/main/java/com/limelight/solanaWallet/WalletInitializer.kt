package com.limelight.solanaWallet

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import org.libsodium.jni.NaCl
import org.libsodium.jni.Sodium
import org.libsodium.jni.crypto.Random

import java.security.SecureRandom

import android.util.Base64


object WalletInitializer {

    private const val PREFS_NAME = "com.limelight.solanaWallet"
    private const val IS_INITIALIZED = "is_initialized"
    private const val ENCRYPTED_KEY_PAIR = "encrypted_key_pair"

    private lateinit var sharedPreferences: SharedPreferences
    private val gson = Gson()

    init {
        NaCl.sodium()  // Initialize the sodium library
    }

    fun initialize(context: Context) {
        sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun isWalletInitialized(): Boolean {
        return sharedPreferences.getBoolean(IS_INITIALIZED, false)
    }

    fun setupWallet() {
        if (isWalletInitialized()) return

        val account = SolanaAccount.create()
        val keyPairJson = serializeKeyPair(account.getKeyPair())  // Now using the getKeyPair function
        val encryptedKeyPairJson = encrypt(keyPairJson)

        with(sharedPreferences.edit()) {
            putBoolean(IS_INITIALIZED, true)
            putString(ENCRYPTED_KEY_PAIR, encryptedKeyPairJson)
            apply()
        }
    }


    fun getAccount(): SolanaAccount {
        val encryptedKeyPairJson = sharedPreferences.getString(ENCRYPTED_KEY_PAIR, null) ?: throw IllegalStateException("Wallet not initialized")
        val keyPairJson = decrypt(encryptedKeyPairJson)
        val keyPair = deserializeKeyPair(keyPairJson)
        return SolanaAccount(keyPair)
    }


    private val key: ByteArray

    init {
        NaCl.sodium()  // Initialize the sodium library
        key = ByteArray(Sodium.crypto_secretbox_keybytes())
        Sodium.randombytes(key, key.size)
    }


    private fun encrypt(data: String): String {
        val nonce = ByteArray(Sodium.crypto_secretbox_noncebytes())
        Sodium.randombytes(nonce, nonce.size)

        val dataBytes = data.toByteArray(Charsets.UTF_8)
        val cipherText = ByteArray(dataBytes.size + Sodium.crypto_secretbox_macbytes())

        val result = Sodium.crypto_secretbox_easy(cipherText, dataBytes, dataBytes.size, nonce, key)
        check(result == 0) { "Encryption failed" }

        // Combine nonce and cipherText for storage
        val cipherTextWithNonce = nonce + cipherText
        return Base64.encodeToString(cipherTextWithNonce, Base64.DEFAULT)
    }


    private fun decrypt(data: String): String {
        val cipherTextWithNonce = Base64.decode(data, Base64.DEFAULT)

        val nonce = cipherTextWithNonce.sliceArray(0 until Sodium.crypto_secretbox_noncebytes())
        val cipherText = cipherTextWithNonce.sliceArray(Sodium.crypto_secretbox_noncebytes() until cipherTextWithNonce.size)

        val decryptedData = ByteArray(cipherText.size - Sodium.crypto_secretbox_macbytes())

        val result = Sodium.crypto_secretbox_open_easy(decryptedData, cipherText, cipherText.size - Sodium.crypto_secretbox_macbytes(), nonce, key)
        check(result == 0) { "Decryption failed" }

        return String(decryptedData, Charsets.UTF_8)
    }


    private fun serializeKeyPair(keyPair: TweetNaclFast.Signature.KeyPair): String {
        return gson.toJson(keyPair)
    }

    private fun deserializeKeyPair(serializedKeyPairString: String): TweetNaclFast.Signature.KeyPair {
        return gson.fromJson(serializedKeyPairString, TweetNaclFast.Signature.KeyPair::class.java)
    }
}
