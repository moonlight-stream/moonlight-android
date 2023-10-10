package com.limelight.solanaWallet

import android.content.Context
import com.limelight.utils.Loggatore
import com.solana.vendor.bip39.Mnemonic
import com.solana.vendor.bip39.WordCount
import com.solana.vendor.bip32.wallet.DerivableType
import com.solana.vendor.bip32.wallet.SolanaBip44
import org.bitcoinj.crypto.MnemonicCode


object WalletInitializer {

    const val PREFS_NAME = "com.limelight.solanaWallet"

    fun initializeWallet(context: Context): ShagaHotAccount? {
        Loggatore.d("WalletDebug", "initializeWallet entered")
        SolanaPreferenceManager.initialize(context)

        // Try to fetch the stored HotAccount
        val storedAccount = SolanaPreferenceManager.getStoredHotAccount()

        return if (storedAccount != null) {
            Loggatore.d("WalletDebug", "Wallet already initialized.")
            SolanaPreferenceManager.isWalletInitialized = true
            storedAccount
        } else {
            Loggatore.d("WalletDebug", "Wallet not initialized. Setting up...")
            SolanaPreferenceManager.isWalletInitialized = false
            createNewWalletAccount(context)
        }
    }

    fun createNewWalletAccount(context: Context): ShagaHotAccount? {
        if (SolanaPreferenceManager.isWalletInitialized) {
            Loggatore.d("WalletDebug", "Wallet already exists. Aborting creation of a new account.")
            return SolanaPreferenceManager.getStoredHotAccount() // Retrieve the stored HotAccount
        }

        val mnemonicPhrase = Mnemonic(WordCount.COUNT_24).phrase
        val account = ShagaHotAccount.fromMnemonic(mnemonicPhrase, "", DerivationPath.BIP44_M_44H_501H_0H_OH)

        // Store PublicKey and HotAccount
        SolanaPreferenceManager.storePublicKey(account.publicKey)
        SolanaPreferenceManager.storeHotAccount(account)

        WalletUtils.storeMnemonicSecurely(context, mnemonicPhrase)
        SolanaPreferenceManager.isWalletInitialized = true

        // Generate and store the private key (secret key) using the mnemonic
        val seed = MnemonicCode.toSeed(mnemonicPhrase, "")
        val solanaBip44 = SolanaBip44()
        val privateKey = solanaBip44.getPrivateKeyFromSeed(seed, DerivableType.BIP44)

        // Store the private key securely
        SolanaPreferenceManager.storePrivateKey(privateKey, context)

        return account
    }
}
