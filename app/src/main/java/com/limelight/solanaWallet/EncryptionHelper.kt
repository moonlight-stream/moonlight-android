package com.limelight.solanaWallet

import android.util.Base64
import android.util.Log
import com.iwebpp.crypto.TweetNaclFast
import java.security.SecureRandom
import org.libsodium.jni.Sodium
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec


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
    fun mapPublicEd25519ToX25519(ed25519PublicKey: ByteArray): ByteArray {
        val x25519PublicKey = ByteArray(32) // Initialize to proper size
        // Convert Ed25519 public key to Curve25519 public key
        val conversionResultPublic = Sodium.crypto_sign_ed25519_pk_to_curve25519(x25519PublicKey, ed25519PublicKey)
        // Check for conversion success (usually, 0 means success)
        if (conversionResultPublic != 0) {
            throw Exception("Public key conversion failed")
        }
        return x25519PublicKey
    }

    @JvmStatic
    fun encryptPinWithX25519PublicKey(pin: String, x25519PublicKey: ByteArray, x25519PrivateKey: ByteArray): ByteArray {
        val sharedSecret = ByteArray(32)
        Sodium.crypto_scalarmult(sharedSecret, x25519PrivateKey, x25519PublicKey)
        val aesKey = sharedSecret.sliceArray(0..15)
        return encryptAES(aesKey, pin.toByteArray())
    }

    private fun encryptAES(key: ByteArray, data: ByteArray): ByteArray {
        val skeySpec = SecretKeySpec(key, "AES")
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, skeySpec)
        return cipher.doFinal(data)
    }

    @JvmStatic
    fun mapSecretEd25519ToX25519(ed25519PrivateKey: ByteArray): ByteArray {
        val x25519PrivateKey = ByteArray(32) // Initialize to proper size
        // Convert Ed25519 private key to Curve25519 private key
        val conversionResultPrivate = Sodium.crypto_sign_ed25519_sk_to_curve25519(x25519PrivateKey, ed25519PrivateKey)
        // Check for conversion success (usually, 0 means success)
        if (conversionResultPrivate != 0) {
            throw Exception("Secret key conversion failed")
        }
        return x25519PrivateKey
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

