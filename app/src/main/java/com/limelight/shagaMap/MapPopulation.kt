package com.limelight.shagaMap

import android.content.Context
import android.util.Log
import com.limelight.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.regex.Pattern
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
        val totalRamMb: UInt,
        val solPerHour: ULong,
        val affairState: String,
        val affairStartTime: ULong,
        val affairTerminationTime: ULong
    )

    // Function to build marker properties, ip & latency are calculated in MapUtils.kt
    suspend fun buildMarkerProperties(context: Context, affair: DecodedAffairsData): Result<MarkerProperties> {
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


        suspend fun buildMarkerProperties(context: Context, affair: DecodedAffairsData): Result<MarkerProperties> {
            // Use already decoded ipAddress
            val ipAddressString = affair.ipAddress

            val coordinatesResult = NetworkUtils.ipToCoordinates(context, ipAddressString)
            val latencyResult = NetworkUtils.pingIpAddress(ipAddressString)
            Log.d("buildMarkerProperties", "Coordinates Result: $coordinatesResult, Latency Result: $latencyResult")

            if (coordinatesResult.isSuccess && latencyResult.isSuccess) {
                // Use already decoded cpuName and gpuName
                val cpuNameString = affair.cpuName
                val gpuNameString = affair.gpuName

                // Convert PublicKey to its string representation, if the class provides such a method.
                val authorityString = affair.authority.toString()  // Replace `toString()` with the actual method if available

                // Initialize the string representation of affairState
                var affairStateString = "UNKNOWN"  // Default to "UNKNOWN"

// Convert the UInt value to its corresponding string representation
                when (affair.affairState.toUInt()) {
                    0u -> affairStateString = "UNAVAILABLE"
                    1u -> affairStateString = "AVAILABLE"
                    else -> {}  // Leave it as "UNKNOWN"
                }


                // Now build MarkerProperties
                val markerProperties = MarkerProperties(
                    ipAddress = ipAddressString,
                    coordinates = coordinatesResult.getOrThrow(),
                    latency = latencyResult.getOrThrow(),
                    gpuName = gpuNameString,
                    cpuName = cpuNameString,
                    solanaLenderPublicKey = authorityString,
                    totalRamMb = affair.totalRamMb,
                    solPerHour = affair.solPerHour,
                    affairState = affairStateString,
                    affairStartTime = affair.activeRentalStartTime,
                    affairTerminationTime = affair.affairTerminationTime
                )

                Log.d("buildMarkerProperties", "Created MarkerProperties: $markerProperties") // Log the created MarkerProperties object

                return Result.success(markerProperties)
            } else {
                val failureReasons = mutableListOf<String>()
                coordinatesResult.exceptionOrNull()?.let { failureReasons.add("coordinates: ${it.message}") }
                latencyResult.exceptionOrNull()?.let { failureReasons.add("latency: ${it.message}") }

                return Result.failure(Exception("Failed to build marker properties due to: ${failureReasons.joinToString(", ")}"))
            }
        }



    }
}
