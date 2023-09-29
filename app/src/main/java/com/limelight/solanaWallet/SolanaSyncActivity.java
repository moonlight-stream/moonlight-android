package com.limelight.solanaWallet;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import com.limelight.R;

public class SolanaSyncActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pc_view);
    }
}

/*
Step 4: Fetch Available Sessions
Use the RPC call getProgramAccounts to fetch the list of active sessions from the Sunshine server. The Program ID is essential here to filter out accounts owned by your Sunshine program.

javascript
Copy code
const connection = new solanaWeb3.Connection(
  solanaWeb3.clusterApiUrl("devnet") // or testnet or mainnet
);

const PROGRAM_ID = new solanaWeb3.PublicKey("YourShagaProgramIDHere");

const fetchedSessions = await connection.getProgramAccounts(PROGRAM_ID, {
  filters: [
    // Your filters here
  ]
});
Step 5: Filter and Display Sessions
Once you receive the list of available sessions, you can filter and display them based on your needs (e.g., by IP Address, CPU Name, etc.).

Step 6: Join a Session
To join a session, you will likely send a transaction to the Solana blockchain, interacting with your Sunshine program. This transaction will contain the necessary instructions and accounts to process the session joining logic.

Here's a high-level outline:

Define Program ID: Know the Program ID of your deployed smart contract.
Solana SDK: Integrate Solana SDK into the Moonlight client.
Initialize Connection: Connect to Solana's network and specify the Program ID.
Fetch Sessions: Use getProgramAccounts to fetch active sessions.
Display Sessions: Show the
 */