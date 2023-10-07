package com.limelight.shagaMap

 import android.Manifest
 import android.content.Intent
 import android.content.pm.PackageManager
 import android.graphics.BitmapFactory
 import android.os.Build
 import android.os.Bundle
import android.util.Log
 import android.view.LayoutInflater
 import android.view.View
 import android.view.ViewManager
 import android.widget.Button
 import android.widget.FrameLayout
 import android.widget.TextView
 import androidx.annotation.RequiresApi
 import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
 import com.google.android.material.floatingactionbutton.FloatingActionButton
 import com.limelight.R
import com.limelight.solanaWallet.SolanaApi
 import com.mapbox.geojson.Point
import com.mapbox.maps.MapView
import com.mapbox.maps.MapboxMap
import com.mapbox.maps.QueryFeaturesCallback
import com.mapbox.maps.RenderedQueryGeometry
import com.mapbox.maps.RenderedQueryOptions
import com.mapbox.maps.Style
 import com.mapbox.maps.ViewAnnotationOptions
 import com.mapbox.maps.plugin.annotation.annotations
 import com.mapbox.maps.plugin.annotation.generated.PointAnnotation
 import com.mapbox.maps.plugin.annotation.generated.PointAnnotationManager
 import com.mapbox.maps.plugin.annotation.generated.PointAnnotationOptions
 import com.mapbox.maps.plugin.annotation.generated.createPointAnnotationManager
 import com.mapbox.maps.plugin.gestures.OnMapClickListener
 import com.mapbox.maps.plugin.locationcomponent.location
 import com.mapbox.maps.viewannotation.ViewAnnotationManager
 import java.lang.Math.sqrt
 import java.text.SimpleDateFormat
 import java.util.Date
 import java.util.Locale
 import kotlin.math.pow
 import androidx.lifecycle.lifecycleScope
 import kotlinx.coroutines.Dispatchers
 import kotlinx.coroutines.launch
 import com.mapbox.maps.CameraOptions
 import com.mapbox.maps.EdgeInsets
 import org.bitcoinj.core.Base58
 import com.limelight.solanaWallet.SolanaApi.solana
 import com.limelight.solanaWallet.WalletActivity
 import com.solana.api.AccountInfo
 import com.solana.api.AccountInfoSerializer
 import com.solana.api.getAccountInfo
 import com.solana.api.getMultipleAccountsInfo
 import com.solana.core.PublicKey
 import com.solana.networking.serialization.serializers.base64.BorshAsBase64JsonArraySerializer
 import com.solana.networking.serialization.serializers.solana.AnchorAccountSerializer
 import kotlinx.coroutines.CoroutineScope
 import kotlinx.coroutines.withContext
 import org.bouncycastle.util.encoders.Base64

/* simplified flow:
Map initializes.
User clicks "Populate Map."
Solana-Fetch the list of Affairs asynchronously.
Deserialize the data.
Populate the map with markers.
Enable or activate the map click listener.
*/

class MapActivity : AppCompatActivity(), OnMapClickListener {
    private lateinit var mapView: MapView
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var mapboxMap: MapboxMap
    private lateinit var viewAnnotationManager: ViewAnnotationManager
    private lateinit var pointAnnotationManager: PointAnnotationManager
    private val annotationToPropertiesMap: MutableMap<PointAnnotation, MapPopulation.MarkerProperties> = mutableMapOf()
    private var userLocation: Point? = null

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_map)
        mapView = findViewById(R.id.mapView)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)


        mapView.getMapboxMap().loadStyleUri(Style.MAPBOX_STREETS) {

            mapboxMap = mapView.getMapboxMap() // this initializes the map
            pointAnnotationManager = mapView.annotations.createPointAnnotationManager()
            viewAnnotationManager = mapView.viewAnnotationManager
            // Add custom images for markers here
            it.addImage("Lenders", BitmapFactory.decodeResource(resources, R.drawable.gaming_pc))


            // Make the points clickable & show view annotation when clicked
            pointAnnotationManager.addClickListener { clickedAnnotation ->
                // Retrieve the MarkerProperties associated with this PointAnnotation
                val markerProperties = annotationToPropertiesMap[clickedAnnotation]
                // If MarkerProperties exist for this PointAnnotation, proceed
                if (markerProperties != null) {
                    // Add or show the ViewAnnotation using the clicked PointAnnotation and associated MarkerProperties
                    addViewAnnotationWithProperties(clickedAnnotation, markerProperties)
                }
                true // Return true to indicate that we've handled this click event

            }

            onMapReady()

            initializeLocation() // initialize location

            initializeLocationPuck() // this initializes the location
        }

        // Moved button initialization to its own function
        initializePopulateMapButton()

        initializeBackButton()

        // Initialize new Go To Wallet Activity Button
        val goToWalletActivityButton: Button = findViewById(R.id.goToWalletActivityButton)
        goToWalletActivityButton.setOnClickListener {
            val intent = Intent(this@MapActivity, WalletActivity::class.java)
            startActivity(intent)
        }

        initializeTestButton()
    }



    private fun addGamingPCMarkerWithProperties(marker: MapPopulation.MarkerProperties): PointAnnotation {
        val pointAnnotationOptions: PointAnnotationOptions = PointAnnotationOptions()
            .withPoint(Point.fromLngLat(marker.coordinates.longitude, marker.coordinates.latitude))
            .withIconImage("Lenders")
            .withIconSize(0.2)
        val createdAnnotation = pointAnnotationManager.create(pointAnnotationOptions)
        annotationToPropertiesMap[createdAnnotation] = marker
        return createdAnnotation
    }

    private fun addViewAnnotationWithProperties(pointAnnotation: PointAnnotation, markerProperties: MapPopulation.MarkerProperties) {
        if (::pointAnnotationManager.isInitialized) {

            val layoutInflater = LayoutInflater.from(this)
            val view = layoutInflater.inflate(R.layout.view_annotation_item_callout, null)
            // Step 1: Identify the Button within the Inflated View
            val startRentingButton: Button = view.findViewById(R.id.startRentingButton)

            // Step 2: Add OnClickListener
            startRentingButton.setOnClickListener {
                val intent = Intent(this@MapActivity, RentingActivity::class.java)
                intent.putExtra("solanaLenderPublicKey", markerProperties.solanaLenderPublicKey)
                intent.putExtra("latency", markerProperties.latency)
                Log.d("MapActivity", "Sending solanaLenderPublicKey: ${markerProperties.solanaLenderPublicKey}")
                startActivity(intent)
            }

            val layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
            view.layoutParams = layoutParams

            if (!populateViewAnnotationWithMarkerProperties(view, markerProperties)) {
                Log.e("ViewPopulationError", "Failed to populate the view annotation.")
                return
            }

            val options = ViewAnnotationOptions.Builder()
                .geometry(pointAnnotation.geometry)
                .build()

            try {
                viewAnnotationManager.addViewAnnotation(view, options)
            } catch (e: NullPointerException) {
                Log.e("ViewAnnotationError", "Caught NullPointerException: ${e.message}")
            }
        } else {
            Log.e("InitializationError", "pointAnnotationManager or viewAnnotationManager is not initialized.")
        }
    }


    private fun populateViewAnnotationWithMarkerProperties(view: View, markerProperties: MapPopulation.MarkerProperties): Boolean {
        return try {
            // Populate Latency
            val latencyTextView: TextView = view.findViewById(R.id.latency)
            latencyTextView.text = "Latency: ${markerProperties.latency} ms"
            // Populate GPU Model
            view.findViewById<TextView>(R.id.gpuName).text = "GPU Model: ${markerProperties.gpuName}"
            // Populate CPU Model
            view.findViewById<TextView>(R.id.cpuName).text = "CPU Model: ${markerProperties.cpuName}"
            // Populate Total RAM
            val totalRamGb = markerProperties.totalRamMb.toDouble() / 1024.0
            view.findViewById<TextView>(R.id.totalRamGb).text = "Total RAM: ${String.format("%.2f", totalRamGb)} GB"
            // Populate Cost per Hour
            view.findViewById<TextView>(R.id.usdcPerHour).text = "Cost per hour: ${markerProperties.solPerHour} SOL"
            // Populate Rent Available Until
            val affairTerminationTimeInMillis = markerProperties.affairTerminationTime.toLong() * 1000
            val affairTerminationDate = Date(affairTerminationTimeInMillis)
            val format = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
            view.findViewById<TextView>(R.id.affairTerminationTime).text = "Rent available until: ${format.format(affairTerminationDate)}"
            // Populate Lender's ID
            view.findViewById<TextView>(R.id.solanaLenderPublicKey).text = "Lender's ID: ${markerProperties.solanaLenderPublicKey}"
            // Add close button functionality
            val closeButton: Button = view.findViewById(R.id.closeButton)
            closeButton.setOnClickListener {
                (view.parent as? ViewManager)?.removeView(view)
            }

            true  // Return true if successful
        } catch (e: Exception) {
            // Handle exception
            Log.e("ViewPopulationError", "Error populating view annotation: ${e.message}")
            false  // Return false if an error occurs
        }
    }


    private fun initializeTestButton() {
        val testButton = findViewById<Button>(R.id.testButton)
        testButton.setOnClickListener {
            Log.d("DebugFlow", "Test Button Clicked")
            val targetAddress = "AsCMkK1iqA5jXGGp9TiapbBATNPjbZp1jfcWy7unkevo"
            val targetAddressPubkey = PublicKey(targetAddress)
            Log.d("DebugFlow", "Target Public Key: $targetAddressPubkey")

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    Log.d("DebugFlow", "Fetching AccountInfo")
                    val result: AccountInfo<SolanaApi.AffairsData?>? = try {
                        solana.api.getAccountInfo(
                            serializer = AccountInfoSerializer(BorshAsBase64JsonArraySerializer(AnchorAccountSerializer("AffairsData", SolanaApi.AffairsData.serializer()))),
                            account = targetAddressPubkey
                        ).getOrThrow()
                    } catch (e: kotlinx.serialization.SerializationException) {
                        Log.e("DebugFlow", "SerializationException: ${e.message}")
                        return@launch
                    } catch (e: Exception) {
                        Log.e("DebugFlow", "Unknown Exception: ${e.message}")
                        return@launch
                    }
                    if (result != null) {
                        Log.d("DebugFlow", "Fetched data successfully")

                        // Assume that 'data' is the AffairDummy object fetched
                        val data: SolanaApi.AffairsData? = result.data

                        if (data != null) {
                            Log.d("DebugFlow", "Authority: ${data.authority}")
                            Log.d("DebugFlow", "Client: ${data.client}")
                            Log.d("DebugFlow", "Rental: ${data.rental}")
                            Log.d("DebugFlow", "Total RAM MB: ${data.totalRamMb}")
                            Log.d("DebugFlow", "Sol Per Hour: ${data.solPerHour}")

                            // Converting byte arrays to ASCII strings for logging
                            val ipAddressString = String(data.ipAddress).trimEnd('\u0000')  // Trimming null characters at the end
                            val cpuNameString = String(data.cpuName).trimEnd('\u0000')      // Trimming null characters at the end
                            val gpuNameString = String(data.gpuName).trimEnd('\u0000')      // Trimming null characters at the end


                            // For demonstration purposes, you can replace these with the actual ByteArray from your data
                            val demoIpAddress = byteArrayOf(49, 48, 57, 46, 49, 49, 56, 46, 49, 52, 54, 46, 49, 55, 49)
                            val demoCpuName = byteArrayOf(73, 110, 116, 101, 108, 40, 82, 41, 32, 67, 111, 114, 101, 40, 84, 77, 41, 32, 105, 55, 45, 56, 55, 53, 48, 72, 32, 67, 80, 85, 32, 64, 32, 50, 46, 50, 48, 71, 72, 122, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)

                            // Convert byte arrays to ASCII strings
                            val ZipAddressString = String(demoIpAddress).trimEnd('\u0000')
                            val ZcpuNameString = String(demoCpuName).trimEnd('\u0000')

                            Log.d("DebugFlow", "IP Address: $ZipAddressString")  // Should log "109.118.146.171"
                            Log.d("DebugFlow", "CPU Name: $ZcpuNameString")      // Should log "Intel(R) Core(TM) i7-8750H CPU @ 2.20GHz"


                            Log.d("DebugFlow", "IP Address: $ipAddressString")
                            Log.d("DebugFlow", "CPU Name: $cpuNameString")
                            Log.d("DebugFlow", "GPU Name: $gpuNameString")
                        } else {
                            Log.e("DebugFlow", "Fetched data is null")
                        }
                    }
                } catch (e: OutOfMemoryError) {
                    Log.e("DebugFlow", "Ran out of memory: ${e.message}")
                } catch (e: Exception) {
                    Log.e("DebugFlow", "An unknown error occurred: ${e.message}")
                }
            }
        }
    }




/*
        private fun initializeTestButton() {
            val testButton: Button = findViewById(R.id.testButton)
            testButton.setOnClickListener {
                val testPayloads = listOf(
                    SolanaApi.AffairsData(
                        authority = PublicKey("5FrmAaNgQyFfF1YqPTJvMnAKdRTCi8QeDTJs2t9yZZsP"), // Replace with actual PublicKey object initialization
                        client = PublicKey("5FrmAaNgQyFfF1YqPTJvMnAKdRTCi8QeDTJs2t9yZZsP"), // Replace with actual PublicKey object initialization
                        rental = PublicKey("5FrmAaNgQyFfF1YqPTJvMnAKdRTCi8QeDTJs2t9yZZsP"), // Replace with actual PublicKey object initialization
                        // Convert IP address string "109.118.146.171" to byte array
                        ipAddress = "109.118.146.171".toByteArray(Charsets.US_ASCII),
                        // Convert CPU name string "Intel i7" to byte array
                        cpuName = "Intel i7".toByteArray(Charsets.US_ASCII),
                        // Convert GPU name string "NVIDIA GTX 1060" to byte array
                        gpuName = "NVIDIA GTX 1060".toByteArray(Charsets.US_ASCII),
                        // Use UInt for totalRamMb
                        totalRamMb = 4096u,
                        // Use ULong for solPerHour
                        solPerHour = 10uL,
                        // Use UInt for affairState
                        affairState = 1u,
                        // Use ULong for affairTerminationTime, activeRentalStartTime, and dueRentAmount
                        affairTerminationTime = 1696381504uL,
                        activeRentalStartTime = 0uL, // Replace with actual value
                        dueRentAmount = 0uL // Replace with actual value
                    )
                )

                // Add this block to populate the AffairsDataHolder.affairsMap
                testPayloads.forEach { affairData ->
                        affairData.let { nonNullData ->
                            nonNullData.authority.let { authority: PublicKey ->  // Explicitly specify the type

                                val authorityBytes = authority.toByteArray()  // Convert PublicKey to byte array
                                if (authorityBytes.isNotEmpty()) {  // Check for null or empty
                                    val authorityKey = Base58.encode(authorityBytes)  // Use Base58 encoding

                                    // Initialize empty strings as default
                                    var ipAddressString = ""
                                    var cpuNameString = ""
                                    var gpuNameString = ""

                                    // Use the ipAddressBase64 property for Base64 encoding and decoding
                                    if (nonNullData.ipAddress.isNotEmpty()) {
                                        try {
                                            ipAddressString = nonNullData.ipAddressString
                                        } catch (e: Exception) {
                                            Log.e("DebugFlow", "Error in decoding ipAddress: ${e.message}")
                                        }
                                    }

                                    // Use the cpuNameBase64 property for Base64 encoding and decoding
                                    if (nonNullData.cpuName.isNotEmpty()) {
                                        try {
                                            cpuNameString = nonNullData.cpuNameString
                                        } catch (e: Exception) {
                                            Log.e("DebugFlow", "Error in decoding cpuName: ${e.message}")
                                        }
                                    }

                                    // Use the gpuNameBase64 property for Base64 encoding and decoding
                                    if (nonNullData.gpuName.isNotEmpty()) {
                                        try {
                                            gpuNameString = nonNullData.gpuNameString
                                        } catch (e: Exception) {
                                            Log.e("DebugFlow", "Error in decoding gpuName: ${e.message}")
                                        }
                                    }
                                    // Create a new DecodedAffairsData object with decoded and other values
                                    val decodedData = DecodedAffairsData(
                                        authority = nonNullData.authority,
                                        client = nonNullData.client,
                                        rental = nonNullData.rental,
                                        ipAddress = ipAddressString,
                                        cpuName = cpuNameString,
                                        gpuName = gpuNameString,
                                        totalRamMb = nonNullData.totalRamMb,
                                        solPerHour = nonNullData.solPerHour,
                                        affairState = nonNullData.affairState,
                                        affairTerminationTime = nonNullData.affairTerminationTime,
                                        activeRentalStartTime = nonNullData.activeRentalStartTime,
                                        dueRentAmount = nonNullData.dueRentAmount
                                    )
                                    // Store the decoded data in the HashMap
                                    AffairsDataHolder.affairsMap[authorityKey] = decodedData
                                }
                            }
                        }
                    }

                Log.d("MapActivity", "affairsMap: ${AffairsDataHolder.affairsMap.keys.joinToString(", ")}")
                // Log to debug
                Log.d("MapActivity", "Synthetic affairsMap: ${AffairsDataHolder.affairsMap.keys.joinToString(", ")}")
                // Initialize MapPopulation if it's not already
                val mapPopulation = MapPopulation()
                // Create a list to collect valid MarkerProperties
                val validMarkerProperties = mutableListOf<MapPopulation.MarkerProperties>()

                lifecycleScope.launch(Dispatchers.IO) {
                    for (testPayload in testPayloads) {
                        val conversionResult =
                            mapPopulation.buildMarkerProperties(this@MapActivity, testPayload)
                        if (conversionResult.isFailure) {
                            // Log and handle the failure
                            Log.e("Test", "Conversion failed for payload: $testPayload")
                            continue
                        }

                        val markerProperty = conversionResult.getOrThrow()
                        validMarkerProperties.add(markerProperty)  // Add to the list
                        // Switch back to the Main thread to update UI
                        withContext(Dispatchers.Main) {
                            // Add the marker to the map
                            addGamingPCMarkerWithProperties(markerProperty)
                        }
                    }
                    // Now adjust the camera, but on the Main thread
                    withContext(Dispatchers.Main) {
                        // Assuming userLocation has been initialized and is non-null
                        userLocation?.let {
                            adjustCameraToShowAllPoints(validMarkerProperties, it, mapView)
                        } ?: run {
                            Log.e("adjustCamera", "User location is null. Cannot adjust camera.")
                        }
                    }
                }
            }
        }

 */



    private fun initializeLocation() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                1
            )
        } else {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                Log.d("Location", "Success Listener Triggered")
                if (location != null) {
                    val lat = location.latitude
                    val lon = location.longitude
                    userLocation = Point.fromLngLat(lon, lat)
                    mapView.getMapboxMap().setCamera(
                        com.mapbox.maps.CameraOptions.Builder()
                            .center(Point.fromLngLat(lon, lat))
                            .zoom(12.0)
                            .build()
                    )
                }
            }.addOnFailureListener { e ->
                Log.d("Location", "Failed to get location: $e")
            }
        }
    }


    private fun initializeLocationPuck() {
        mapView.location.updateSettings {
            enabled = true
            pulsingEnabled = true
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                initializeLocation()
            } else {
                // Handle permission denial
                Log.d("Permission", "Location permission denied")
            }
        }
    }

    // Function to fetch AffairsList
    @RequiresApi(Build.VERSION_CODES.O)
    private fun initializePopulateMapButton() {
        val button = findViewById<FloatingActionButton>(R.id.populateMapButton)

        button.setOnClickListener {
            Log.d("DebugFlow", "Populate Map Button Clicked")
            val affairsListAddress = getString(R.string.affairs_list_address)

            // Create a PublicKey object from the string address
            val affairsListAddressPubkey = PublicKey(affairsListAddress)

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    Log.d("DebugFlow", "Fetching the AffairsListData")
                    Log.d("DebugFlow", "Public Key: $affairsListAddressPubkey")

                    val result: AccountInfo<SolanaApi.AffairsListData?>? = try {
                        solana.api.getAccountInfo(
                            serializer = AccountInfoSerializer(BorshAsBase64JsonArraySerializer(AnchorAccountSerializer("AffairsListData", SolanaApi.AffairsListData.serializer()))),
                            account = affairsListAddressPubkey
                        ).getOrThrow()
                    } catch (e: kotlinx.serialization.SerializationException) {
                        Log.e("DebugFlow", "Serialization Exception: ${e.message}")
                        null
                    } catch (e: java.io.IOException) {
                        Log.e("DebugFlow", "IO Exception: ${e.message}")
                        null
                    } catch (e: Exception) {
                        Log.e("DebugFlow", "Generic Exception: ${e.message}")
                        null
                    }

                    // Log the result for debugging
                    if (result != null) {
                        Log.d("DebugFlow", "Result: $result")
                    } else {
                        Log.e("DebugFlow", "Result is null")
                    }

                    // Check if data is null or empty
                    val affairsListData: AccountInfo<SolanaApi.AffairsListData?>? = result
                    if (affairsListData?.data == null) {
                        Log.e("DebugFlow", "Affairs list data is null.")
                        return@launch
                    }

                    // Unwrap the data field from AccountInfo to get SolanaApi.AffairsListData
                    val actualAffairsListData: SolanaApi.AffairsListData = affairsListData.data!!

                    Log.d("DebugFlow", "Size of serialized AffairsListData: ${actualAffairsListData.toString().length}")

                    Log.d("DebugFlow", "Extracting the list of public keys")

                    // Extract the list of public keys
                    val publicKeys: List<PublicKey> = actualAffairsListData.activeAffairs

                    Log.d("DebugFlow", "Size of the list of public keys: ${publicKeys.size}")

                    publicKeys.forEach {
                        Log.d("DebugFlow", "Public key: $it")
                    }

                    // Heap size logging
                    Log.d("DebugFlow", "Available heap size: ${Runtime.getRuntime().freeMemory()}")
                    Log.d("DebugFlow", "Max heap size: ${Runtime.getRuntime().maxMemory()}")

                    // Fetch multiple accounts' data
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            Log.d("DebugFlow", "Fetching multiple accounts")
                            // Fetch multiple accounts
                            val multipleAccountsResult = solana.api.getMultipleAccountsInfo(
                                serializer = SolanaApi.AffairsData.serializer(),
                                //serializer = AccountInfoSerializer(BorshAsBase64JsonArraySerializer(AnchorAccountSerializer("AffairsData", SolanaApi.AffairsData.serializer()))),
                                accounts = publicKeys
                            )

                            // Check for success
                            if (multipleAccountsResult.isSuccess) {
                                val multipleAccountsData = multipleAccountsResult.getOrNull()

                                // If the data is null, log an error and return
                                if (multipleAccountsData == null) {
                                    Log.e("DebugFlow", "Multiple accounts data is null.")
                                    return@launch
                                }

                                Log.d("DebugFlow", "Filtering and extracting data")

                                // Filter out nulls and extract data
                                val affairsDataList: List<SolanaApi.AffairsData> =
                                    multipleAccountsData.filterNotNull().mapNotNull { it.data }

                                // If the list is empty, log an error and return
                                if (affairsDataList.isEmpty()) {
                                    Log.e("DebugFlow", "No valid AffairsData found.")
                                    return@launch
                                }

                                Log.d("DebugFlow", "Saving the fetched AffairsData to local HashMap")

                                // Data transformation & local temporary storage
                                affairsDataList.forEach { affairData ->
                                    affairData.let { nonNullData ->
                                        nonNullData.authority.let { authority: PublicKey ->  // Explicitly specify the type

                                            val authorityBytes = authority.toByteArray()  // Convert PublicKey to byte array
                                            if (authorityBytes.isNotEmpty()) {  // Check for null or empty
                                                val authorityKey = Base58.encode(authorityBytes)  // Use Base58 encoding

                                                // Initialize empty strings as default
                                                var ipAddressString = ""
                                                var cpuNameString = ""
                                                var gpuNameString = ""

                                                // Use the ipAddressBase64 property for Base64 encoding and decoding
                                                if (nonNullData.ipAddress.isNotEmpty()) {
                                                    try {
                                                        ipAddressString = nonNullData.ipAddressString
                                                    } catch (e: Exception) {
                                                        Log.e("DebugFlow", "Error in decoding ipAddress: ${e.message}")
                                                    }
                                                }

                                                // Use the cpuNameBase64 property for Base64 encoding and decoding
                                                if (nonNullData.cpuName.isNotEmpty()) {
                                                    try {
                                                        cpuNameString = nonNullData.cpuNameString
                                                    } catch (e: Exception) {
                                                        Log.e("DebugFlow", "Error in decoding cpuName: ${e.message}")
                                                    }
                                                }

                                                // Use the gpuNameBase64 property for Base64 encoding and decoding
                                                if (nonNullData.gpuName.isNotEmpty()) {
                                                    try {
                                                        gpuNameString = nonNullData.gpuNameString
                                                    } catch (e: Exception) {
                                                        Log.e("DebugFlow", "Error in decoding gpuName: ${e.message}")
                                                    }
                                                }
                                                // Create a new DecodedAffairsData object with decoded and other values
                                                val decodedData = DecodedAffairsData(
                                                    authority = nonNullData.authority,
                                                    client = nonNullData.client,
                                                    rental = nonNullData.rental,
                                                    ipAddress = ipAddressString,
                                                    cpuName = cpuNameString,
                                                    gpuName = gpuNameString,
                                                    totalRamMb = nonNullData.totalRamMb,
                                                    solPerHour = nonNullData.solPerHour,
                                                    affairState = nonNullData.affairState,
                                                    affairTerminationTime = nonNullData.affairTerminationTime,
                                                    activeRentalStartTime = nonNullData.activeRentalStartTime,
                                                    dueRentAmount = nonNullData.dueRentAmount
                                                )
                                                // Store the decoded data in the HashMap
                                                AffairsDataHolder.affairsMap[authorityKey] = decodedData
                                            }
                                        }
                                    }
                                }

                                Log.d("DebugFlow", "AffairsMap: ${AffairsDataHolder.affairsMap.keys.joinToString(", ")}")
                                // Initialize MapPopulation
                                val mapPopulation = MapPopulation()
                                // Start another coroutine to work on UI
                                lifecycleScope.launch(Dispatchers.IO) {
                                    Log.d("DebugFlow", "Starting UI coroutine")
                                    // Convert the DecodedAffairsData to MarkerProperties
                                    val validMarkerProperties = AffairsDataHolder.affairsMap.values.mapNotNull { decodedData ->
                                        // Since we're inside a coroutine, you can call suspending functions
                                        runCatching {
                                            mapPopulation.buildMarkerProperties(this@MapActivity, decodedData)
                                        }.getOrNull()  // Here we use getOrNull to handle exceptions and null gracefully
                                    }

                                    Log.d("DebugFlow", "Adding markers to map")

                                    validMarkerProperties.forEach { result ->
                                        result?.let { markerProperty ->  // Unwrap the Result object
                                            if (markerProperty.isSuccess) {  // Check if it's a successful Result
                                                addGamingPCMarkerWithProperties(markerProperty.getOrThrow())  // Extract the value and pass it
                                            } else {
                                                Log.e("DebugFlow", "MarkerProperty construction failed: ${markerProperty.exceptionOrNull()?.message}")
                                            }
                                        }
                                    }

                                    // Adjust the camera to fit all markers and user's location
                                    userLocation?.let { nonNullUserLocation ->
                                        // Extract only the successful MarkerProperties into a new list
                                        val successfulMarkerProperties = validMarkerProperties.mapNotNull { result ->
                                            result.takeIf { it.isSuccess }?.getOrThrow()
                                        }

                                        if (successfulMarkerProperties.isNotEmpty()) {
                                            Log.d("DebugFlow", "Adjusting camera")
                                            adjustCameraToShowAllPoints(
                                                successfulMarkerProperties,
                                                nonNullUserLocation,
                                                mapView
                                            )
                                        } else {
                                            Log.e("DebugFlow", "No valid marker properties found")
                                        }
                                    } ?: run {
                                        Log.e("DebugFlow", "User location is null")
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("DebugFlow", "Nested Coroutine Error: ${e.message}")
                        }
                    }
                } catch (e: Exception) {
                    Log.e("DebugFlow", "Main Coroutine Error: ${e.message}")
                    e.printStackTrace()
                }
            }
        }
    }



    private fun initializeBackButton() {
        val backButton: Button = findViewById(R.id.backButton)
        backButton.setOnClickListener {
            finish()
        }
    }

    fun handleMapClick(
        mapboxMap: MapboxMap,
        point: Point,
        callback: QueryFeaturesCallback
    ) {
        // Convert clicked point to pixel coordinate
        val pixel = mapboxMap.pixelForCoordinate(point)
        // Create the geometry object for querying rendered features
        val geometry = RenderedQueryGeometry(pixel)
        // Create default query options
        val options = RenderedQueryOptions(emptyList(),null)
        // Execute the query
        mapboxMap.queryRenderedFeatures(geometry, options, callback)
    }




    private fun onMapReady() {
        val mapLoadingOverview: TextView = findViewById(R.id.mapLoadingOverview)
        mapLoadingOverview.visibility = View.GONE
        Log.d("MapActivity", "Map is ready.")
    }



    override fun onStart() {
        super.onStart()
        mapView.onStart()
        Log.d("MapActivity", "onStart called.")
    }



    override fun onStop() {
        super.onStop()
        mapView.onStop()
        Log.d("MapActivity", "onStop called.")
    }



    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()
        Log.d("MapActivity", "onLowMemory called.")
    }



    override fun onDestroy() {
        super.onDestroy()
        mapView.onDestroy()
        Log.d("MapActivity", "onDestroy called.")
    }


    private fun findNearbyPointAnnotation(clickedPoint: Point, threshold: Double = 0.01): PointAnnotation? {
        for ((annotation, _) in annotationToPropertiesMap) {
            val annotationPoint = annotation.geometry
            val distance = sqrt(
                (annotationPoint.latitude() - clickedPoint.latitude()).pow(2.0) +
                        (annotationPoint.longitude() - clickedPoint.longitude()).pow(2.0)
            )
            if (distance < threshold) {
                return annotation
            }
        }
        return null
    }

    override fun onMapClick(point: Point): Boolean {
        val nearbyAnnotation = findNearbyPointAnnotation(point)
        nearbyAnnotation?.let { annotation ->
            val markerProperties = annotationToPropertiesMap[annotation]
            markerProperties?.let {
                addViewAnnotationWithProperties(annotation, it)
            }
        }
        return true
    }

    fun adjustCameraToShowAllPoints(markerPropertiesList: List<MapPopulation.MarkerProperties>?, userLocation: Point?, mapView: MapView) {
        if (markerPropertiesList == null || userLocation == null || markerPropertiesList.isEmpty()) {
            Log.e("adjustCamera", "Invalid input parameters")
            return
        }

        val points: MutableList<Point> = ArrayList()
        // Convert marker coordinates to Point and add to the list
        for (marker in markerPropertiesList) {
            val point = Point.fromLngLat(marker.coordinates.longitude, marker.coordinates.latitude)
            points.add(point)
        }
        // Add the user's location to the list
        points.add(userLocation)
        // Calculate camera options to fit these points
        var cameraOptions = mapView.getMapboxMap().cameraForCoordinates(points, EdgeInsets(0.0, 0.0, 0.0, 0.0), null, null)
        // Modify the zoom level to zoom out by 20%
        val currentZoom = cameraOptions.zoom ?: 0.0
        val adjustedZoom = currentZoom * 0.8  // Reduce zoom level by 20%
        // Build a new CameraOptions object with the adjusted zoom level
        cameraOptions = CameraOptions.Builder()
            .center(cameraOptions.center)
            .zoom(adjustedZoom)
            .build()
        // Set the calculated camera options to move the camera
        mapView.getMapboxMap().setCamera(cameraOptions)
    }

}