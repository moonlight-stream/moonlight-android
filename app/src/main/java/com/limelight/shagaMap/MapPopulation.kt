package com.limelight.shagaMap

import android.content.Context
import android.util.Log
import android.view.View
import com.google.gson.JsonObject
import com.limelight.R
import com.limelight.shagaMap.MapPopulation.NetworkUtils.isValidIpAddress
import com.limelight.solanaWallet.SolanaApi
import com.mapbox.geojson.Feature
import com.mapbox.geojson.FeatureCollection
import com.mapbox.geojson.Point
import com.mapbox.maps.MapboxMap
import com.mapbox.maps.ViewAnnotationAnchor
import com.mapbox.maps.extension.style.layers.addLayer
import com.mapbox.maps.extension.style.layers.getLayer
import com.mapbox.maps.extension.style.StyleContract
import com.mapbox.maps.extension.style.sources.addSource
import com.mapbox.maps.extension.style.sources.generated.GeoJsonSource
import com.mapbox.maps.extension.style.sources.getSource
import com.mapbox.maps.extension.style.sources.getSourceAs
import com.mapbox.maps.viewannotation.ViewAnnotationManager
import com.mapbox.maps.viewannotation.viewAnnotationOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.bitcoinj.core.Base58
import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.math.BigInteger
import java.util.regex.Pattern
import kotlin.math.roundToInt
import kotlin.math.roundToLong

class MapPopulation {
    data class Coordinates(val latitude: Double, val longitude: Double)
    data class MarkerProperties( // just SolanaApi.AffairsData + Latency field & Coordinates
        val ipAddress: String,
        val coordinates: Coordinates,
        val latency: Long,
        val gpuName: String,
        val cpuName: String,
        val solanaLenderPublicKey: String,
        val totalRamMb: Int,
        val usdcPerHour: Int,
        val affairState: String,
        val affairStartTime: Long?,
        val affairTerminationTime: Long
    )

    // Function to build marker properties, ip & latency are calculated in MapUtils.kt
    suspend fun buildMarkerProperties(context: Context, affair: SolanaApi.AffairsData): Result<MarkerProperties> {
        return MarkerUtils.buildMarkerProperties(context, affair)
    }

    object NetworkUtils {
        // Create a shared OkHttpClient instance
        private val client = OkHttpClient()

        // Moved outside the function as a private const
        private const val IP_PATTERN = "^(25[0-5]|2[0-4][0-9]|[0-1]?[0-9][0-9]?)\\." +
                "(25[0-5]|2[0-4][0-9]|[0-1]?[0-9][0-9]?)\\." +
                "(25[0-5]|2[0-4][0-9]|[0-1]?[0-9][0-9]?)\\." +
                "(25[0-5]|2[0-4][0-9]|[0-1]?[0-9][0-9]?)$"

        fun isValidIpAddress(ipAddress: String): Boolean {
            val ipPattern = Pattern.compile(IP_PATTERN)
            return ipPattern.matcher(ipAddress).matches()
        }

        suspend fun ipToCoordinates(context: Context, ipAddress: String): Result<MapPopulation.Coordinates> = withContext(Dispatchers.IO) {

            try {
                // Step 1: Validate and Get string resources
                val rapidApiKey = context.getString(R.string.rapidapi_key)
                val rapidApiHost = context.getString(R.string.rapidapi_host)
                val apiUrl = context.getString(R.string.ip_to_coordinates_url)

                // Validate IP address (add more robust validation if needed)
                if (!isValidIpAddress(ipAddress)) {
                    throw IllegalArgumentException("IP address is invalid")
                }

                /// Step 2: Create request
                val mediaType = "application/x-www-form-urlencoded".toMediaTypeOrNull()
                val body = "ip=$ipAddress".toRequestBody(mediaType)

                val request = Request.Builder()
                    .url(apiUrl)
                    .post(body)
                    .addHeader("content-type", "application/x-www-form-urlencoded")
                    .addHeader("X-RapidAPI-Key", rapidApiKey)
                    .addHeader("X-RapidAPI-Host", rapidApiHost)
                    .build()

                // Step 3: Execute request and handle response
                val client = OkHttpClient()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.e("ipToCoordinates", "Unexpected response code: $response")
                        throw Exception("Unexpected response code")
                    }

                    val responseBody = response.body?.string() ?: throw Exception("Empty response body")
                    Log.d("ipToCoordinates", "Response Body: $responseBody")  // Log the raw response body

                    // Parsing JSON to extract latitude and longitude
                    try {
                        val json = JSONObject(responseBody)
                        val latitude = json.getDouble("latitude")
                        val longitude = json.getDouble("longitude")
                        Log.d("ipToCoordinates", "Parsed Latitude: $latitude, Longitude: $longitude")  // Log parsed values

                        return@withContext Result.success(Coordinates(latitude, longitude))
                    } catch (jsonException: JSONException) {
                        Log.e("ipToCoordinates", "JSON parsing failed", jsonException)
                        throw jsonException
                    }
                }
            } catch (e: Exception) {
                // Step 4: Error handling
                Log.e("ipToCoordinates", "Failed to fetch coordinates", e)
                return@withContext Result.failure(e)
            }
        }



        fun pingIpAddress(ipAddress: String): Result<Long> {
            try {
                // Step 1: Validate the IP address
                if (!isValidIpAddress(ipAddress)) {
                    throw IllegalArgumentException("IP address is invalid")
                }

                // Step 2: Execute the ping command
                val process = Runtime.getRuntime().exec("ping -c 1 $ipAddress")
                val buf = BufferedReader(InputStreamReader(process.inputStream))

                // Step 3: Read the output line by line to find the 'time=' substring
                var latency: Long? = null  // Made it nullable
                buf.forEachLine { line ->
                    val timePos = line.indexOf("time=")
                    if (timePos != -1) {
                        val timeStr = line.substring(timePos + 5)
                        val endPos = timeStr.indexOf(" ")

                        // Added check for endPos != -1
                        if (endPos != -1) {
                            try {
                                // Convert to Float first to handle cases like "17.6"
                                val latencyFloat = timeStr.substring(0, endPos).toFloat()

                                // Round to the nearest Long if your application logic requires it
                                val latencyLong = latencyFloat.roundToLong()

                                // Now use latencyLong in your application
                                latency = latencyLong

                            } catch (e: NumberFormatException) {
                                Log.e("pingIpAddress", "Failed to convert latency to Long", e)
                                throw e
                            }
                            return@forEachLine
                        }
                    }
                }

                // Step 4: Check if latency was extracted
                latency?.let {
                    Log.d("pingIpAddress", "Parsed Latency: $it")  // Log parsed latency
                    return Result.success(it)
                } ?: throw Exception("Failed to extract latency from ping output")

            } catch (e: Exception) {
                // Log the error and return failure
                Log.e("pingIpAddress", "Ping failed for IP: $ipAddress", e)
                return Result.failure(e)
            }
        }

    }


    object MarkerUtils {


        suspend fun buildMarkerProperties(context: Context, affair: SolanaApi.AffairsData): Result<MarkerProperties> {
            // Convert List<Byte> back to ByteArray for NetworkUtils functions
            val ipAddressString = String(android.util.Base64.decode(affair.ipAddress, android.util.Base64.DEFAULT))
            val coordinatesResult = NetworkUtils.ipToCoordinates(context, ipAddressString)
            val latencyResult = NetworkUtils.pingIpAddress(ipAddressString)

            Log.d("buildMarkerProperties", "Coordinates Result: $coordinatesResult, Latency Result: $latencyResult")  // Log the results


            return if (coordinatesResult.isSuccess && latencyResult.isSuccess) {
                // Convert List<Byte> back to String for MarkerProperties
                val cpuNameString = String(android.util.Base64.decode(affair.cpuName, android.util.Base64.DEFAULT))
                val gpuNameString = String(android.util.Base64.decode(affair.gpuName, android.util.Base64.DEFAULT))
                val authorityString = Base58.encode(String(android.util.Base64.decode(affair.authority, android.util.Base64.DEFAULT)).toByteArray())
                Log.d("buildMarkerProperties", "Encoded authorityKey: $authorityString")
                val affairStateString = String(android.util.Base64.decode(affair.affairState, android.util.Base64.DEFAULT))

                val markerProperties = MarkerProperties(
                    ipAddress = ipAddressString,
                    coordinates = coordinatesResult.getOrThrow(),
                    latency = latencyResult.getOrThrow(),
                    gpuName = gpuNameString,
                    cpuName = cpuNameString,
                    solanaLenderPublicKey = authorityString,
                    totalRamMb = affair.totalRamMb,
                    usdcPerHour = affair.solPerHour.toInt(),
                    affairState = affairStateString,
                    affairStartTime = affair.activeRentalStartTime,
                    affairTerminationTime = affair.affairTerminationTime
                )

                Log.d("buildMarkerProperties", "Created MarkerProperties: $markerProperties") // Log the created MarkerProperties object

                Result.success(markerProperties)

            } else {
                val failureReasons = mutableListOf<String>()
                coordinatesResult.exceptionOrNull()?.let { failureReasons.add("coordinates: ${it.message}") }
                latencyResult.exceptionOrNull()?.let { failureReasons.add("latency: ${it.message}") }

                Result.failure(Exception("Failed to build marker properties due to: ${failureReasons.joinToString(", ")}"))
            }
        }


    }
}
