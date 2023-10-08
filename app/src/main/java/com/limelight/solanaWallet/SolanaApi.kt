package com.limelight.solanaWallet

import android.os.Build
import androidx.annotation.RequiresApi
import com.solana.core.PublicKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.solana.api.getBalance
import com.solana.Solana
import com.solana.api.Api
import kotlinx.serialization.*
import kotlinx.serialization.json.Json
import java.util.*
import com.solana.networking.HttpNetworkingRouter
import com.solana.networking.RPCEndpoint
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import com.solana.api.getMultipleAccounts
import com.solana.networking.serialization.serializers.solana.PublicKeyAs32ByteSerializer
import com.solana.api.getRecentBlockhash
import kotlinx.coroutines.launch
import com.solana.api.getRecentBlockhash
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume


object SolanaApi {
    private val endPoint = RPCEndpoint.devnetSolana
    private val network = HttpNetworkingRouter(endPoint)
    val solana = Solana(network)
    val scope = CoroutineScope(Dispatchers.IO)

    suspend fun getRecentBlockHashFromApi(): Result<String> {
        return suspendCancellableCoroutine { continuation ->
            solana.api.getRecentBlockhash { result ->
                continuation.resume(result)
            }
        }
    }

    @Serializable
    data class StartRentalInstructionArgs(
        val rentalTerminationTime: ULong
    )

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
        @Serializable(with = PublicKeyAs32ByteSerializer::class)
        val authority: PublicKey,
        @Serializable(with = PublicKeyAs32ByteSerializer::class)
        val client: PublicKey,
        @Serializable(with = PublicKeyAs32ByteSerializer::class)
        val rental: PublicKey?,

        val ipAddress: String,  // Changed to String
        val cpuName: String,    // Changed to String
        val gpuName: String,    // Changed to String

        val totalRamMb: UInt,  // Type remains the same
        val solPerHour: ULong, // Type remains the same

        val affairState: AffairState,  // Changed to enum type

        val affairTerminationTime: ULong,  // Type remains the same
        val activeRentalStartTime: ULong, // Type remains the same
        val dueRentAmount: ULong  // Type remains the same
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