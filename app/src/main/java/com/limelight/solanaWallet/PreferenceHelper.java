package com.limelight.solanaWallet;

import android.content.Context;
import android.content.SharedPreferences;

public class PreferenceHelper {

    private static final String PREFERENCES_FILE_NAME = "com.limelight.preferences";
    private static final String WALLET_CREATED_KEY = "wallet_created";

    private final SharedPreferences preferences;

    public PreferenceHelper(Context context) {
        this.preferences = context.getSharedPreferences(PREFERENCES_FILE_NAME, Context.MODE_PRIVATE);
    }

    public boolean isWalletCreated() {
        return preferences.getBoolean(WALLET_CREATED_KEY, false);
    }

    public void setWalletCreated(boolean walletCreated) {
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean(WALLET_CREATED_KEY, walletCreated);
        editor.apply();
    }
}
