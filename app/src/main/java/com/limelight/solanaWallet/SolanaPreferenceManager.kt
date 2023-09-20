package com.limelight.solanaWallet

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import android.util.Log
import com.solana.core.DerivationPath
import com.solana.core.HotAccount
import com.solana.core.PublicKey




object SolanaPreferenceManager {
    private var sharedPreferences: SharedPreferences? = null
    private const val PREFS_NAME = "com.limelight.solanaWallet"
    private const val ENCRYPTION_KEY = "encryption_key"
    private const val IS_INITIALIZED = "is_initialized"
    private const val PUBLIC_KEY = "public_key"
    private const val ENCRYPTED_MNEMONIC = "encrypted_mnemonic"
    //private const val ENCRYPTED_SECRET_KEY = "encrypted_secret_key"

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


    @JvmStatic
    fun getStoredPublicKey(): PublicKey? {
        val publicKeyString = sharedPreferences?.getString(PUBLIC_KEY, null) ?: return null
        return PublicKey(publicKeyString) // Create a PublicKey object using the base58 encoded string
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
