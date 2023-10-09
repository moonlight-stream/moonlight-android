package com.limelight.solanaWallet

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyProperties
import android.security.keystore.KeyProtection
import android.util.Base64
import android.util.Log
import com.google.gson.Gson
import com.limelight.utils.Loggatore
import com.solana.core.DerivationPath
import com.solana.core.HotAccount
import com.solana.core.PublicKey
import java.security.KeyStore
import javax.crypto.spec.SecretKeySpec


object SolanaPreferenceManager {
    private var sharedPreferences: SharedPreferences? = null
    private const val PREFS_NAME = "com.limelight.solanaWallet"
    private const val ENCRYPTION_KEY = "encryption_key"
    private const val IS_INITIALIZED = "is_initialized"
    private const val PUBLIC_KEY = "public_key"
    private const val ENCRYPTED_MNEMONIC = "encrypted_mnemonic"
    private const val HOT_ACCOUNT_KEY = "hot_account"
    private const val AUTHORITY_KEY = "authority"

    var authority: String?
        get() = sharedPreferences?.getString(AUTHORITY_KEY, null)
        set(value) {
            sharedPreferences?.edit()?.putString(AUTHORITY_KEY, value)?.commit()
        }

    fun getStoredAuthority(): String? {
        val storedValue = sharedPreferences?.getString(AUTHORITY_KEY, null)
        return storedValue ?: "8KKsyoLrs5yuLSWgcUVqgZk3Ss3onA6AdrxK8LdAfLe8"
    }


    fun clearStoredAuthority() {
        sharedPreferences?.edit()?.remove(AUTHORITY_KEY)?.apply()
    }

    fun storeAuthority(authority: String) {
        sharedPreferences?.edit()?.putString(AUTHORITY_KEY, authority)?.apply()
    }


    var isWalletInitialized: Boolean
        get() = sharedPreferences?.getBoolean(IS_INITIALIZED, false) ?: false
        set(value) {
            sharedPreferences?.edit()?.putBoolean(IS_INITIALIZED, value)?.commit()
        }

    @JvmStatic
    fun initialize(context: Context) {
        if (sharedPreferences == null) {
            sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    fun storeHotAccount(account: HotAccount) {
        val editor = sharedPreferences?.edit()
        val gson = Gson()
        // Step 1: Serialize HotAccount to JSON string
        val accountJsonString = gson.toJson(account)
        // Step 2: Encrypt the serialized HotAccount using EncryptionHelper
        val encryptedAccountString = EncryptionHelper.encrypt(accountJsonString)
        // Step 3: Store encrypted HotAccount
        editor?.putString(HOT_ACCOUNT_KEY, encryptedAccountString)
        editor?.apply()
    }

    fun getStoredHotAccount(): HotAccount? {
        val gson = Gson()
        // Step 1: Retrieve encrypted HotAccount
        val encryptedAccountString = sharedPreferences?.getString(HOT_ACCOUNT_KEY, null)
        // Step 2: Return null if nothing is stored
        if (encryptedAccountString == null) {
            return null
        }
        // Step 3: Decrypt the stored HotAccount
        val decryptedAccountString = EncryptionHelper.decrypt(encryptedAccountString)
        // Step 4: Deserialize decrypted HotAccount from JSON string
        return gson.fromJson(decryptedAccountString, HotAccount::class.java)
    }


    @JvmStatic
    fun getIsWalletInitialized(): Boolean {
        return isWalletInitialized
    }

    @JvmStatic
    fun setIsWalletInitialized(value: Boolean) {
        isWalletInitialized = value
    }

    fun storePublicKey(publicKey: PublicKey) {
        val publicKeyString = publicKey.toBase58() // Convert PublicKey to a base58 encoded string
        sharedPreferences?.edit()?.putString(PUBLIC_KEY, publicKeyString)?.apply()
    }

    fun getStoredBalance(): Float {
        return sharedPreferences?.getFloat("user_balance", 0.0f) ?: 0.0f
    }

    @JvmStatic
    fun getStoredPublicKey(): PublicKey? {
        val publicKeyString = sharedPreferences?.getString(PUBLIC_KEY, null) ?: return null
        return PublicKey(publicKeyString) // Create a PublicKey object using the base58 encoded string
    }

    fun storePrivateKey(secretKey: ByteArray, context: Context) {
        try {
            val keyStore = KeyStore.getInstance("AndroidKeyStore")
            keyStore.load(null)

            val secretKeySpec = SecretKeySpec(secretKey, "AES")

            val keyProtection = KeyProtection.Builder(KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()

            keyStore.setEntry("PrivateKeyAlias", KeyStore.SecretKeyEntry(secretKeySpec), keyProtection)
        } catch (e: Exception) {
            Loggatore.d("WalletDebug", "Exception while storing private key: ${e.message}")
        }
    }

    @JvmStatic
    fun loadPrivateKeyFromKeyStore(): ByteArray? {
        try {
            val keyStore = KeyStore.getInstance("AndroidKeyStore")
            keyStore.load(null)
            val secretKeyEntry = keyStore.getEntry("PrivateKeyAlias", null) as? KeyStore.SecretKeyEntry
            return secretKeyEntry?.secretKey?.encoded
        } catch (e: Exception) {
            Loggatore.d("WalletDebug", "Exception while retrieving private key: ${e.message}")
        }
        return null
    }


    var encryptedMnemonic: String?
        get() = sharedPreferences?.getString(ENCRYPTED_MNEMONIC, null)

        set(encryptedMnemonic) {
            sharedPreferences?.edit()?.putString(ENCRYPTED_MNEMONIC, encryptedMnemonic)?.apply()
        }
    val encryptionKeyFromPrefs: ByteArray?
        get() {
            val base64Key = sharedPreferences?.getString(ENCRYPTION_KEY, null)
            return if (base64Key != null) {
                Base64.decode(base64Key, Base64.DEFAULT)
            } else {
                Log.e("PreferenceManager", "Encryption key not found in preferences.")
                null
            }
        }

    fun storeEncryptionKeyInPrefs(key: ByteArray?) {
        sharedPreferences?.edit()?.putString(ENCRYPTION_KEY, Base64.encodeToString(key, Base64.DEFAULT))?.apply()
    }

    fun getHotAccountFromMnemonic(words: List<String>, passphrase: String, derivationPath: DerivationPath): HotAccount {
        return HotAccount.fromMnemonic(words, passphrase, derivationPath)
    }

}
