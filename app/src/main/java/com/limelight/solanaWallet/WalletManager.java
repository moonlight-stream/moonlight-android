package com.limelight.solanaWallet;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.widget.TextView;
import com.limelight.R;
import com.solana.Solana;
import com.solana.core.PublicKey;

public class WalletManager implements BalanceCallback {

    private Solana solana;

    private static class Holder {
        private static final WalletManager INSTANCE = new WalletManager();
    }

    private Context context;
    private BalanceUpdateCallback balanceUpdateCallback;
    private SharedPreferences sharedPreferences;
    private static TextView balanceTextView;
    private static TextView walletPublicKeyTextView;
    private Double currentBalance;

    private WalletManager() {
    }

    public static WalletManager getInstance() {
        return Holder.INSTANCE;
    }

    public interface BalanceUpdateCallback {
        void onBalanceUpdate(Double balance);
    }

    public void setup(Context context, BalanceUpdateCallback callback, TextView balanceTextView, TextView walletPublicKeyTextView) {
        this.context = context;
        this.balanceUpdateCallback = callback;
        this.sharedPreferences = context.getSharedPreferences(WalletInitializer.PREFS_NAME, Context.MODE_PRIVATE);
        this.balanceTextView = balanceTextView;
        this.walletPublicKeyTextView = walletPublicKeyTextView;
    }

    public void setContextIfNeeded(Context newContext) {
        if (this.context == null) {
            this.context = newContext;
        }
    }

    public void updateBalanceTextView(final String balanceText) {
        ((Activity) context).runOnUiThread(() -> {
            balanceTextView.setText(balanceText);
        });
    }

    public static void initializeUIWithPlaceholderBalance() {
        balanceTextView.setText("Loading balance...");
        walletPublicKeyTextView.setText("Loading public key...");
    }

    public void updateUIWithBalance() {
        Double currentBalance = getCurrentBalance();
        PublicKey rawPublicKey = SolanaPreferenceManager.getStoredPublicKey();
        String publicKey = rawPublicKey.toString();

        ((Activity) context).runOnUiThread(() -> {
            if (publicKey != null && !publicKey.isEmpty()) {
                walletPublicKeyTextView.setText(publicKey);
            } else {
                walletPublicKeyTextView.setText(R.string.pubkey_fetch_error);
            }

            if (currentBalance != null && currentBalance > 0) {
                String balanceString = context.getString(R.string.balance_format, currentBalance.floatValue());
                balanceTextView.setText(balanceString);
            } else {
                String noBalanceMessage = context.getString(R.string.no_balance_message);
                balanceTextView.setText(noBalanceMessage);
            }
        });
    }


    private Double getCurrentBalance() {
        return (double) sharedPreferences.getFloat("user_balance", 0);
    }

    @Override
    public void onBalanceReceived(Double balance) {
        if (balance != null) {
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putFloat("user_balance", balance.floatValue());
            editor.apply();
        }

        if (balanceUpdateCallback != null) {
            balanceUpdateCallback.onBalanceUpdate(balance);
        }
    }

    public void fetchAndDisplayBalance(PublicKey publicKey) {
        if (publicKey != null) {
            SolanaApi.getBalance(publicKey, balanceInLamports -> {
                if (balanceInLamports != null) {
                    Double balanceInSol = balanceInLamports.doubleValue() / 1_000_000_000;
                    handleBalanceReceived(balanceInSol);
                } else {
                    handleBalanceError(new Exception("Failed to fetch balance"));
                }
                updateUIWithBalance();  // Update the UI with the latest balance and public key
            });
        } else {
            handlePublicKeyNotFound();
            updateUIWithBalance();  // Update the UI to reflect that the public key was not found
        }
    }




    private void handleBalanceReceived(Double balanceInSol) {
        if (balanceInSol != null) {
            String balanceString = String.format(context.getString(R.string.balance_format), balanceInSol);

            // Store the new balance in shared preferences before updating the UI
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putFloat("user_balance", balanceInSol.floatValue());
            editor.apply();

            updateBalanceTextView(balanceString);
            WalletManager.getInstance().setCurrentBalance(balanceInSol);
        } else {
            updateBalanceTextView(context.getString(R.string.balance_fetch_error));
        }
    }

    private void handleBalanceError(Exception e) {
        // Here you can log the error or perform different actions based on the error type
        updateBalanceTextView(context.getString(R.string.balance_fetch_error));
    }

    private void handlePublicKeyNotFound() {
        // Handle the case where the public key is not found in the preferences
        updateBalanceTextView(context.getString(R.string.public_key_not_found_error)); // You would need to define public_key_not_found_error in your strings resource file
    }

    private void setCurrentBalance(Double balance) {
        this.currentBalance = balance;
    }
}

