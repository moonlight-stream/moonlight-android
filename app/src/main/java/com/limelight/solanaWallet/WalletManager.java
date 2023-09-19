// SolanaWallet.java
package com.limelight.solanaWallet;

import android.content.Context;

public interface SolanaWallet {
    void setup(Context context, WalletManager.BalanceUpdateCallback callback);
    void fetchAndDisplayBalance(String publicKeyString);
}


public class WalletManager implements SolanaWallet {

    private static class Holder {
        private static final WalletManager INSTANCE = new WalletManager();
    }

    private Context context;
    private BalanceUpdateCallback callback;

    private WalletManager() {
    }

    public static WalletManager getInstance() {
        return Holder.INSTANCE;
    }

    public interface BalanceUpdateCallback {
        void onBalanceUpdate(Double balance);
    }

    @Override
    public void setup(Context context, BalanceUpdateCallback callback) {
        this.context = context;
        this.callback = callback;
    }

    public BalanceUpdateCallback getCallback() {
        return callback;
    }

    @Override
    public void fetchAndDisplayBalance(String publicKeyString) {
        if (publicKeyString == null || publicKeyString.isEmpty()) {
            return;
        }
        SolanaApi.getBalance(publicKeyString, callback);
    }
}
