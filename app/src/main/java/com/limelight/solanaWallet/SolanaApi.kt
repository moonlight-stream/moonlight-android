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



object SolanaApi {
    private val endPoint = RPCEndpoint.devnetSolana
    private val network = HttpNetworkingRouter(endPoint)
    val solana = Solana(network)
    val scope = CoroutineScope(Dispatchers.IO)

    @Serializable
    data class AffairsListData(val pubKeys: List<String>)

    @Serializable
    data class AffairsData(
        val authority: String, // publicKey in Solana
        val client: String?, // publicKey in Solana
        val rental: String?, // option<publicKey> in Solana
        val ipAddress: String, // Array<u8, 15> in Solana
        val cpuName: String, // Array<u8, 64> in Solana
        val gpuName: String, // Array<u8, 64> in Solana
        val totalRamMb: Int, // u32 in Solana
        val usdcPerHour: Int, // u32 in Solana
        val affairState: String,
        val affairTerminationTime: Long, // u64 in Solana
        val activeRentalStartTime: Long?, // u64 in Solana
        val dueRentAmount: Long? // u64 in Solana
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


    // Used to get Affairs list from source: strings.xml <string name="affairs_list_address">
    // Source: https://github.com/metaplex-foundation/SolanaKT/blob/master/solana/src/main/java/com/solana/api/getAccountInfo.kt
    interface AccountInfoCallback<T> {
        fun onAccountInfoReceived(accountInfo: T?)
    }

    inline fun <reified T> getAccountInfo(
        accountAddress: String,
        serializer: KSerializer<T>,
        crossinline onComplete: (Result<T>) -> Unit
    ) {
        val publicKey = PublicKey(accountAddress)
        scope.launch {
            val requestResult: Result<T> = solana.api.getAccountInfo(
                serializer,
                publicKey,
                Commitment.MAX,
                Encoding.base64,
                null,
                null
            )
            onComplete(requestResult)
        }
    }


    // used to get Affairs Payload to use them in MapActivity & MapPopulation
    interface MultipleAccountsCallback<T> {
        fun onMultipleAccountsReceived(accountInfos: List<AccountInfo<T>?>?)
    }

    // source: https://github.com/metaplex-foundation/SolanaKT/blob/master/solana/src/main/java/com/solana/api/getMultipleAccounts.kt
    inline fun <reified T> getMultipleAccounts(
        accountAddresses: List<String>,
        serializer: KSerializer<T>,
        crossinline onComplete: (Result<List<AccountInfo<T>?>>) -> Unit
    ) {
        val publicKeys = accountAddresses.map { PublicKey(it) }
        scope.launch {
            solana.api.getMultipleAccounts(
                serializer,
                publicKeys
            ) { result: Result<List<AccountInfo<T>?>> ->
                onComplete(result)
            }
        }
    }
}