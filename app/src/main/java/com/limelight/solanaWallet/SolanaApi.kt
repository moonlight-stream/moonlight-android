package com.limelight.solanaWallet

import org.json.JSONObject

import com.solana.Solana
import com.solana.api.Api
import com.solana.networking.HttpNetworkingRouter
import com.solana.networking.RPCEndpoint
import com.solana.networking.Network

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

import java.net.HttpURLConnection
import java.net.URL


data class BalanceResponse(val result: BalanceResult)
data class BalanceResult(val value: Long)


object SolanaApi {
    private const val RPC_URL = "https://api.devnet.solana.com"

    private val customEndpoint = RPCEndpoint.custom(
        URL(RPC_URL),
        URL(RPC_URL),
        Network.devnet // Adjust this as needed
    )

    private val endPoint = RPCEndpoint.devnetSolana
    private val network = HttpNetworkingRouter(endPoint)
    val solana = Solana(network)


    private val router = HttpNetworkingRouter(customEndpoint)
    private val api = Api(router)

    val dispatcher: CoroutineDispatcher = Dispatchers.IO


    interface BalanceCallback {
        fun onBalanceReceived(balance: Double?)
    }


    suspend fun getBalance(publicKey: String, callback: BalanceCallback) {
        val url = URL("$RPC_URL/v2/getBalance/$publicKey")
        var response = ""

        with(url.openConnection() as HttpURLConnection) {
            requestMethod = "GET"

            inputStream.bufferedReader().use {
                it.lines().forEach { line ->
                    response += line
                }
            }
        }

        val jsonObject = JSONObject(response)
        val result = jsonObject.getDouble("result") // ensure "result" is the correct key
        callback.onBalanceReceived(result)
    }
}


