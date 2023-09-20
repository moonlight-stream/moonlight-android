package com.limelight.solanaWallet

import android.util.Base64
import android.util.Log
import com.iwebpp.crypto.TweetNaclFast
import java.security.SecureRandom

object EncryptionHelper {

    private val randKey: ByteArray

    private fun generateNewEncryptionKey(): ByteArray {
        return ByteArray(32).also { SecureRandom().nextBytes(it) }
    }

    init {
        randKey = SolanaPreferenceManager.encryptionKeyFromPrefs ?: generateNewEncryptionKey().also {
            SolanaPreferenceManager.storeEncryptionKeyInPrefs(it)
        }
    }

    @JvmStatic
    fun encrypt(data: String): String {
        val nonce = TweetNaclFast.randombytes(24)
        val box = TweetNaclFast.SecretBox(SolanaPreferenceManager.encryptionKeyFromPrefs)
        val encrypted = box.box(data.toByteArray(Charsets.UTF_8), nonce)
        val cipherText = nonce + encrypted
        return Base64.encodeToString(cipherText, Base64.DEFAULT)
    }

    @JvmStatic
    fun decrypt(data: String): String {
        Log.d("DecryptDebug", "Data to be decrypted: $data")
        val decodedData = Base64.decode(data, Base64.DEFAULT)
        val nonce = decodedData.sliceArray(0 until 24)
        val encrypted = decodedData.sliceArray(24 until decodedData.size)
        val box = TweetNaclFast.SecretBox(SolanaPreferenceManager.encryptionKeyFromPrefs)
        val decryptedBytes = box.open(encrypted, nonce) ?: throw DecryptionFailedException("Failed to decrypt data")
        return String(decryptedBytes, Charsets.UTF_8)
    }

    class DecryptionFailedException(message: String) : Exception(message)
}

