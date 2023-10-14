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
    fun encryptPinWithX25519PublicKey(pin: String, x25519PublicKey: ByteArray, x25519PrivateKey: ByteArray): ByteArray {
        val sharedSecret = ByteArray(32)
        // Log the input public and private keys for debugging
        // Make sure to remove or secure these logs for production
        Log.d("shagaEncryptionHelper", "x25519PublicKey: ${x25519PublicKey.joinToString("") { "%02x".format(it) }}")
        Log.d("shagaEncryptionHelper", "x25519PrivateKey: ${x25519PrivateKey.joinToString("") { "%02x".format(it) }}")

        // Generate shared secret
        Sodium.crypto_scalarmult(sharedSecret, x25519PrivateKey, x25519PublicKey)
        Log.d("shagaEncryptionHelper", "Shared Secret: ${sharedSecret.joinToString("") { "%02x".format(it) }}")

        // Use the full 32-byte shared secret for AES-256
        val aesKey = sharedSecret
        Log.d("shagaEncryptionHelper", "AES Key: ${aesKey.joinToString("") { "%02x".format(it) }}")
        // Log AES Key in Base64
        val aesKeyBase64 = Base64.encodeToString(aesKey, Base64.DEFAULT).trim()
        Log.d("shagaEncryptionHelper", "AES Key (Base64): $aesKeyBase64")

        // Initialize cipher and encrypt
        val skeySpec = SecretKeySpec(aesKey, "AES")
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, skeySpec)
        val encryptedData = cipher.doFinal(pin.toByteArray())

        Log.d("shagaEncryptionHelper", "Encrypted Data: ${encryptedData.joinToString("") { "%02x".format(it) }}")
        return encryptedData
    }


    @JvmStatic
    fun mapPublicEd25519ToX25519(ed25519PublicKey: ByteArray): ByteArray {
        val x25519PublicKey = ByteArray(32)
        // Convert Ed25519 public key to Curve25519 public key
        val conversionResultPublic = Sodium.crypto_sign_ed25519_pk_to_curve25519(x25519PublicKey, ed25519PublicKey)
        // Check for conversion success (usually, 0 means success)
        if (conversionResultPublic != 0) {
            throw Exception("Public key conversion failed")
        }
        return x25519PublicKey
    }

    @JvmStatic
    fun mapSecretEd25519ToX25519(ed25519PrivateKey: ByteArray): ByteArray {
        val x25519PrivateKey = ByteArray(32)
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
        Log.d("shagaEncryptionHelper", "Data to be decrypted: $data")
        val decodedData = Base64.decode(data, Base64.DEFAULT)
        val nonce = decodedData.sliceArray(0 until 24)
        val encrypted = decodedData.sliceArray(24 until decodedData.size)
        val box = TweetNaclFast.SecretBox(SolanaPreferenceManager.encryptionKeyFromPrefs)
        val decryptedBytes = box.open(encrypted, nonce) ?: throw DecryptionFailedException("Failed to decrypt data")
        return String(decryptedBytes, Charsets.UTF_8)
    }

    class DecryptionFailedException(message: String) : Exception(message)
}

