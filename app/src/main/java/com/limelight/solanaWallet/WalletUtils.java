package com.limelight.solanaWallet;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.limelight.utils.Loggatore;
import com.solana.core.DerivationPath;
import com.solana.core.HotAccount;

import java.util.List;
import java.lang.reflect.Type;

public class WalletUtils {

    static SharedPreferences getSharedPreferences(Context context) {
        return context.getSharedPreferences("com.limelight.solanaWallet", Context.MODE_PRIVATE);
    }

    public static void storeMnemonicSecurely(Context context, List<String> mnemonic) {
        String accountJson = new Gson().toJson(mnemonic);
        SolanaPreferenceManager.INSTANCE.initialize(context);
        SolanaPreferenceManager.INSTANCE.setIsWalletInitialized(true);
        SolanaPreferenceManager.INSTANCE.setEncryptedMnemonic(EncryptionHelper.encrypt(accountJson));

        // Log the encrypted mnemonic for debugging
        Loggatore.d("WalletDebug", "Encrypted mnemonic stored: " + EncryptionHelper.encrypt(accountJson));
    }



    public static List<String> getStoredMnemonic(Context context) throws IllegalStateException {
        String encryptedMnemonic = SolanaPreferenceManager.INSTANCE.getEncryptedMnemonic();

        // Log the encrypted mnemonic for debugging
        Loggatore.d("WalletDebug", "Retrieved encrypted mnemonic: " + encryptedMnemonic);

        if (encryptedMnemonic == null || encryptedMnemonic.isEmpty()) {
            throw new IllegalStateException("No mnemonic stored");
        }

        String mnemonicJson = EncryptionHelper.decrypt(encryptedMnemonic);
        Type type = new TypeToken<List<String>>() {}.getType();
        List<String> mnemonic = new Gson().fromJson(mnemonicJson, type);

        return mnemonic;
    }


    public static HotAccount getAccount(Context context) throws IllegalStateException {
        SharedPreferences sharedPreferences = getSharedPreferences(context);
        String encryptedMnemonicJson = SolanaPreferenceManager.INSTANCE.getEncryptedMnemonic();

        if (encryptedMnemonicJson == null || encryptedMnemonicJson.isEmpty()) {
            throw new IllegalStateException("No account data available");
        }

        String decryptedMnemonicJson = EncryptionHelper.decrypt(encryptedMnemonicJson);
        List<String> mnemonic = new Gson().fromJson(decryptedMnemonicJson, new TypeToken<List<String>>() {
        }.getType());
        return getAccountFromMnemonic(mnemonic);
    }

    public static HotAccount getAccountFromMnemonic(List<String> mnemonic) {
        return SolanaPreferenceManager.INSTANCE.getHotAccountFromMnemonic(mnemonic, "", DerivationPath.BIP44_M_44H_501H_0H_OH.INSTANCE);
    }

    // TODO: Add wallet seed phrase recovery, this is not the focus for now
    //public static HotAccount recoverAccount(List<String> userProvidedMnemonic) {
        // Using the userProvidedMnemonic parameter instead of an undefined mnemonic variable
        //return PreferenceManager.INSTANCE.getHotAccountFromMnemonic(mnemonic, "", DerivationPath.BIP44_M_44H_501H_0H_OH.INSTANCE);    }
    //}
}
