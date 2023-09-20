package com.limelight.solanaWallet

import com.solana.core.PublicKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.solana.api.getBalance
import com.solana.Solana
import com.solana.networking.HttpNetworkingRouter
import com.solana.networking.RPCEndpoint




object SolanaApi {
    private val endPoint = RPCEndpoint.devnetSolana
    private val network = HttpNetworkingRouter(endPoint)
    val solana = Solana(network)
    private val scope = CoroutineScope(Dispatchers.IO)

    interface BalanceCallback {
        fun onBalanceReceived(balanceInLamports: Long?)
    }

    @JvmStatic
    fun getBalance(publicKey: PublicKey, callback: BalanceCallback) {
        scope.launch {
            val result = solana.api.getBalance(publicKey)
            callback.onBalanceReceived(result.getOrNull())
        }
    }
}

