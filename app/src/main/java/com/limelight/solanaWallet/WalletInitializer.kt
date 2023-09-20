package com.limelight.solanaWallet

import android.content.Context
import com.limelight.utils.Loggatore
import com.solana.core.DerivationPath
import com.solana.core.HotAccount
import com.solana.vendor.bip39.Mnemonic
import com.solana.vendor.bip39.WordCount


object WalletInitializer {

    const val PREFS_NAME = "com.limelight.solanaWallet"

    fun initializeWallet(context: Context): HotAccount? {
        Loggatore.d("WalletDebug", "initializeWallet entered")
        SolanaPreferenceManager.initialize(context)

        val mnemonic = try {
            WalletUtils.getStoredMnemonic(context)
        } catch (e: IllegalStateException) {
            null
        }

        return if (mnemonic != null) {
            SolanaPreferenceManager.isWalletInitialized = true
            WalletUtils.getAccountFromMnemonic(mnemonic)
        } else {
            Loggatore.d("WalletDebug", "Wallet not initialized. Setting up...")
            SolanaPreferenceManager.isWalletInitialized = false
            createNewWalletAccount(context)
        }
    }




    fun createNewWalletAccount(context: Context): HotAccount? {
        if (SolanaPreferenceManager.isWalletInitialized) {
            Loggatore.d("WalletDebug", "Wallet already exists. Aborting creation of a new account.")
            return WalletUtils.getAccount(context)
        }

        val mnemonicPhrase = Mnemonic(WordCount.COUNT_24).phrase
        val account = HotAccount.fromMnemonic(mnemonicPhrase, "", DerivationPath.BIP44_M_44H_501H_0H)

        SolanaPreferenceManager.storePublicKey(account.publicKey)

        WalletUtils.storeMnemonicSecurely(context, mnemonicPhrase)
        SolanaPreferenceManager.isWalletInitialized = true

        return account
    }



    //fun recoverAccount(userProvidedMnemonic: List<String?>?): HotAccount? {
    //    val cleanedMnemonic = userProvidedMnemonic?.mapNotNull { it } ?: return null
    //    return HotAccount.fromMnemonic(cleanedMnemonic, "", DerivationPath.BIP44_M_44H_501H_0H)
    //}
}
