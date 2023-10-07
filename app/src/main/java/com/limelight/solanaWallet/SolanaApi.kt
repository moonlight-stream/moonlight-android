package com.limelight.solanaWallet

import android.os.Build
import androidx.annotation.RequiresApi
import com.solana.core.PublicKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.solana.api.getBalance
import com.solana.Solana
import kotlinx.serialization.*
import kotlinx.serialization.json.Json
import java.util.*
import com.solana.networking.HttpNetworkingRouter
import com.solana.networking.RPCEndpoint
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import com.solana.api.getMultipleAccounts
import com.solana.networking.serialization.serializers.solana.PublicKeyAs32ByteSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder


object SolanaApi {
    private val endPoint = RPCEndpoint.devnetSolana
    private val network = HttpNetworkingRouter(endPoint)
    val solana = Solana(network)
    val scope = CoroutineScope(Dispatchers.IO)


    @Serializable
    data class AffairsListData(
        val activeAffairs: List<@Serializable(with = PublicKeyAs32ByteSerializer::class) PublicKey>
    )

    object AffairStateUIntSerializer : KSerializer<UInt> {
        override val descriptor: SerialDescriptor =
            PrimitiveSerialDescriptor("AffairState", PrimitiveKind.STRING)

        override fun serialize(encoder: Encoder, value: UInt) {
            val state = when (value.toUInt()) {
                0u -> "UNAVAILABLE"
                1u -> "AVAILABLE"
                else -> "UNKNOWN"
            }
            encoder.encodeString(state)
        }

        override fun deserialize(decoder: Decoder): UInt {
            val state = decoder.decodeString()
            return when (state) {
                "UNAVAILABLE" -> 0u
                "AVAILABLE" -> 1u
                else -> throw SerializationException("Unknown AffairState value: $state")
            }
        }
    }

    @Serializable
    data class AffairDummy(
        @Serializable(with = PublicKeyAs32ByteSerializer::class)
        val authority: PublicKey,
        @Serializable(with = PublicKeyAs32ByteSerializer::class)
        val client: PublicKey,
        @Serializable(with = PublicKeyAs32ByteSerializer::class)
        val rental: PublicKey?,

        val ipAddress: ByteArray = ByteArray(15),
        val cpuName: ByteArray = ByteArray(64),
        val gpuName: ByteArray = ByteArray(64),

        val totalRamMb: UInt,
        val solPerHour: ULong
    )

    @Serializable
    data class AffairsData(
        @Serializable(with = PublicKeyAs32ByteSerializer::class)
        val authority: PublicKey,
        @Serializable(with = PublicKeyAs32ByteSerializer::class)
        val client: PublicKey,
        @Serializable(with = PublicKeyAs32ByteSerializer::class)
        val rental: PublicKey?,

        val ipAddress: ByteArray = ByteArray(15),
        val cpuName: ByteArray = ByteArray(64),
        val gpuName: ByteArray = ByteArray(64),

        val totalRamMb: UInt,
        val solPerHour: ULong,
       // @Serializable(with = AffairStateUIntSerializer::class)
        val affairState: UInt,
        val affairTerminationTime: ULong,
        val activeRentalStartTime: ULong,
        val dueRentAmount: ULong
    ) {
        var ipAddressString: String
            get() = ipAddress.toString(Charsets.UTF_8)
            // Trimming null characters at the end
            set(value) {
                val bytes = value.toByteArray()
                if (bytes.size != 15) throw IllegalArgumentException("ipAddress must contain exactly 15 elements")
                System.arraycopy(bytes, 0, ipAddress, 0, bytes.size)
            }

        var cpuNameString: String
            get() = cpuName.toString(Charsets.UTF_8)
            set(value) {
                val bytes = value.toByteArray()
                if (bytes.size > 64) throw IllegalArgumentException("cpuName cannot contain more than 64 elements")
                System.arraycopy(bytes, 0, cpuName, 0, bytes.size)
            }
        var gpuNameString: String
            get() = gpuName.toString(Charsets.UTF_8)
            set(value) {
                val bytes = value.toByteArray()
                if (bytes.size > 64) throw IllegalArgumentException("gpuName cannot contain more than 64 elements")
                System.arraycopy(bytes, 0, gpuName, 0, bytes.size)
            }

        init {
            if (ipAddress.size != 15) {
                throw IllegalArgumentException("ipAddress must contain exactly 15 elements")
            }
            if (cpuName.size > 64) {
                throw IllegalArgumentException("cpuName cannot contain more than 64 elements")
            }
            if (gpuName.size > 64) {
                throw IllegalArgumentException("gpuName cannot contain more than 64 elements")
            }
        }
    }






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