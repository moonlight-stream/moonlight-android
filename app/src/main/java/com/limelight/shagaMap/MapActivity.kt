package com.limelight.shagaMap

 import android.Manifest
 import android.content.Intent
 import android.content.pm.PackageManager
 import android.graphics.BitmapFactory
 import android.os.Bundle
import android.util.Log
 import android.view.LayoutInflater
 import android.view.View
 import android.view.ViewManager
 import android.widget.Button
 import android.widget.FrameLayout
 import android.widget.TextView
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
 import kotlinx.coroutines.withContext
 import com.mapbox.maps.CameraOptions
 import com.mapbox.maps.EdgeInsets
 import org.bitcoinj.core.Base58
 import android.util.Base64
 import com.limelight.solanaWallet.SolanaApi.solana
 import com.limelight.solanaWallet.WalletActivity
 import com.solana.api.AccountInfo
 import com.solana.api.AccountInfoSerializer
 import com.solana.api.getAccountInfo
 import com.solana.api.getMultipleAccountsInfo
 import com.solana.core.PublicKey
 import com.solana.models.buffer.AccountInfoData
 import com.solana.networking.serialization.serializers.base64.BorshAsBase64JsonArraySerializer
 import com.solana.networking.serialization.serializers.solana.AnchorAccountSerializer
 import com.solana.rxsolana.api.getAccountInfo
 import kotlinx.coroutines.CoroutineScope
 import kotlinx.serialization.KSerializer

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

            //initializeMapInteractions()

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

        //initializeTestButton()
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
            latencyTextView.text = "Latency: ${markerProperties.latency} ms"  // Assuming latency is in milliseconds
            // Populate GPU Model
            view.findViewById<TextView>(R.id.gpuName).text = "GPU Model: ${markerProperties.gpuName}"
            // Populate CPU Model
            view.findViewById<TextView>(R.id.cpuName).text = "CPU Model: ${markerProperties.cpuName}"
            // Populate Total RAM
            val totalRamGb = markerProperties.totalRamMb / 1024.0  // Assuming RAM is initially in MB
            view.findViewById<TextView>(R.id.totalRamGb).text = "Total RAM: ${String.format("%.2f", totalRamGb)} GB"
            // Populate Cost per Hour
            view.findViewById<TextView>(R.id.usdcPerHour).text = "Cost per hour: ${markerProperties.usdcPerHour} USDC"
            // Populate Rent Available Until
            // Convert Unix timestamp (seconds) to milliseconds
            val affairTerminationTimeInMillis = markerProperties.affairTerminationTime * 1000
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


    /*
    private fun initializeTestButton() {
        val testButton: Button = findViewById(R.id.testButton)
        testButton.setOnClickListener {
            val testPayloads = listOf(
                SolanaApi.AffairsData(
                    authority = "5FrmAaNgQyFfF1YqPTJvMnAKdRTCi8QeDTJs2t9yZZsP", // Assuming this is base64
                    client = null, // Assuming this is base64
                    rental = "5FrmAaNgQyFfF1YqPTJvMnAKdRTCi8QeDTJs2t9yZZsP", // Assuming this is base64
                    ipAddress = "MTA5LjExOC4xNDYuMTcx", // "109.118.146.171" in base64
                    cpuName = "SW50ZWwgaTc=", // "Intel i7" in base64
                    gpuName = "TlZJRElBIEdUWCAxMDYw", // "NVIDIA GTX 1060" in base64
                    totalRamMb = 4096,
                    usdcPerHour = 10,
                    affairState = "U29tZVN0YXRl", // "SomeState" in base64
                    affairTerminationTime = 1696381504L,
                    activeRentalStartTime = null,
                    dueRentAmount = null,
                ),
            )
            // Add this block to populate the AffairsDataHolder.affairsMap
            testPayloads.forEach { affairData ->
                val authorityKey = Base58.encode(String(Base64.decode(affairData.authority, android.util.Base64.DEFAULT)).toByteArray())
                AffairsDataHolder.affairsMap[authorityKey] = affairData
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
    private fun initializePopulateMapButton() {
        val button = findViewById<FloatingActionButton>(R.id.populateMapButton)

        button.setOnClickListener {
            Log.d("DebugFlow", "Populate Map Button Clicked")
            val affairsListAddress = getString(R.string.affairs_list_address)

            // Serializer for AffairsListData
            val affairsListDataSerializer = AccountInfoSerializer(
                AnchorAccountSerializer(
                    SolanaApi.AffairsListData.serializer()
                )
            )

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    Log.d("DebugFlow", "Fetching the AffairsListData")
                    Log.d("DebugFlow", "Serializer: $affairsListDataSerializer")
                    Log.d("DebugFlow", "Public Key: ${PublicKey(affairsListAddress)}")

                    // Fetch the AffairsListData
                    val result = solana.api.getAccountInfo(
                        serializer = affairsListDataSerializer,
                        account = PublicKey(affairsListAddress)
                    ).getOrThrow()

                    // Check if data is null or empty
                    val affairsListData: SolanaApi.AffairsListData? = result?.data
                    if (affairsListData == null) {
                        Log.e("DebugFlow", "Affairs list data is null.")
                        return@launch
                    }

                    Log.d("DebugFlow", "Extracting the list of public keys")

                    // Extract the list of public keys
                    val publicKeys: List<String> = affairsListData.activeAffairs
                    val publicKeyObjects = publicKeys.map { PublicKey(it) }

                    // Fetch multiple accounts' data
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            Log.d("DebugFlow", "Fetching multiple accounts")

                            // Fetch multiple accounts
                            val multipleAccountsResult = solana.api.getMultipleAccountsInfo(
                                serializer = AccountInfoSerializer(
                                    BorshAsBase64JsonArraySerializer(
                                        SolanaApi.AffairsData.serializer()
                                    )
                                ),
                                accounts = publicKeyObjects
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
                                val affairsDataList: List<AccountInfo<SolanaApi.AffairsData?>> =
                                    multipleAccountsData.filterNotNull().mapNotNull { it.data }

                                // If the list is empty, log an error and return
                                if (affairsDataList.isEmpty()) {
                                    Log.e("DebugFlow", "No valid AffairsData found.")
                                    return@launch
                                }

                                Log.d("DebugFlow", "Saving the fetched AffairsData to local HashMap")

                                // Save the fetched AffairsData to the local HashMap
                                affairsDataList.forEach { affairData ->
                                    affairData?.data?.let { nonNullData ->
                                        nonNullData.authority?.let { authority ->
                                            val authorityKey = Base58.encode(
                                                String(
                                                    Base64.decode(
                                                        authority,
                                                        android.util.Base64.DEFAULT
                                                    )
                                                ).toByteArray()
                                            )
                                            AffairsDataHolder.affairsMap[authorityKey] = nonNullData
                                        }
                                    }
                                }

                                Log.d("DebugFlow", "AffairsMap: ${AffairsDataHolder.affairsMap.keys.joinToString(", ")}")

                                // Initialize MapPopulation
                                val mapPopulation = MapPopulation()

                                // Start another coroutine to work on UI
                                lifecycleScope.launch(Dispatchers.IO) {
                                    Log.d("DebugFlow", "Starting UI coroutine")

                                    // Convert the raw AffairsData to MarkerProperties
                                    val validMarkerProperties = affairsDataList.mapNotNull {
                                        it.data?.let { data ->
                                            mapPopulation.buildMarkerProperties(this@MapActivity, data)
                                        }
                                    }.filter { it.isSuccess }
                                        .mapNotNull { it.getOrNull() } // Handle nulls gracefully

                                    Log.d("DebugFlow", "Adding markers to map")

                                    validMarkerProperties.forEach { markerProperty ->
                                        addGamingPCMarkerWithProperties(markerProperty)
                                    }

                                    // Adjust the camera to fit all markers and user's location
                                    userLocation?.let { nonNullUserLocation ->
                                        if (validMarkerProperties.isNotEmpty()) {
                                            Log.d("DebugFlow", "Adjusting camera")
                                            adjustCameraToShowAllPoints(
                                                validMarkerProperties,
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