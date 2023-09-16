package com.limelight.utils

import com.solana.networking.NetworkingRouter
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import java.net.HttpURLConnection
import java.net.URL

data class ApiError(override val message: String?) : Exception(message)

class Api(
    val router: NetworkingRouter,
    val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    suspend fun getBalance(publicKey: String): String {
        val url = URL("${SolanaUtil.RPC_URL}/v2/getBalance/$publicKey")
        var response = ""

        with(url.openConnection() as HttpURLConnection) {
            requestMethod = "GET"

            // Reading the response
            inputStream.bufferedReader().use {
                it.lines().forEach { line ->
                    response += line
                }
            }
        }
        // Return the whole response as a String, you'll update this later to return just the balance
        return response
    }

}
