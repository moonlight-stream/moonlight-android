package com.limelight.solanaWallet

import com.solana.core.PublicKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.solana.api.getBalance
import com.solana.Solana
import com.solana.api.AccountInfo
import com.solana.api.getAccountInfo
import com.solana.networking.Commitment
import com.solana.networking.Encoding
import com.solana.networking.HttpNetworkingRouter
import com.solana.networking.RPCEndpoint
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import com.solana.api.getMultipleAccounts
import com.solana.networking.serialization.serializers.solana.PublicKeyAs32ByteSerializer
import kotlinx.serialization.SerialName


object SolanaApi {
    private val endPoint = RPCEndpoint.devnetSolana
    private val network = HttpNetworkingRouter(endPoint)
    val solana = Solana(network)
    val scope = CoroutineScope(Dispatchers.IO)


    @Serializable
    data class AffairsListData(
        val activeAffairs: List<String>  // These are the public keys
    )

    @Serializable
    data class AffairsData(
        val accountDiscriminator: String,  // Base64 encoded
        val authority: String,
        val client: String,
        val rental: String?,
        val ipAddress: String,
        val cpuName: String,
        val gpuName: String,
        val totalRamMb: Int,
        val solPerHour: Long,
        val affairState: String,
        val affairTerminationTime: Long,
        val activeRentalStartTime: Long,
        val dueRentAmount: Long
    )




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