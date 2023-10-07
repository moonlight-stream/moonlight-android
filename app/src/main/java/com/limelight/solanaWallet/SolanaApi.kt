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
        val activeAffairs: List<@Serializable(with = PublicKeyAs32ByteSerializer::class) PublicKey>
    )

    @Serializable
    enum class AffairState {
        Unavailable,
        Available
    }
    @Serializable
    data class AffairsData(
        @Serializable(with = PublicKeyAs32ByteSerializer::class) val authority: PublicKey,
        @Serializable(with = PublicKeyAs32ByteSerializer::class) val client: PublicKey,
        @Serializable(with = PublicKeyAs32ByteSerializer::class) val rental: PublicKey?,
        val ipAddress: List<UByte>,
        val cpuName: List<UByte>,
        val gpuName: List<UByte>,
        val totalRamMb: UShort,
        val solPerHour: ULong,
        val affairState: AffairState,
        val affairTerminationTime: ULong,
        val activeRentalStartTime: ULong,
        val dueRentAmount: ULong
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