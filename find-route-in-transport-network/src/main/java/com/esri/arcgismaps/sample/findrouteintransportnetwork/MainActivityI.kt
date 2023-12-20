/* Copyright 2023 Esri
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.esri.arcgismaps.sample.findrouteintransportnetwork

import android.view.View
import android.widget.AdapterView
import com.arcgismaps.location.LocationDisplayAutoPanMode
import com.arcgismaps.mapping.BasemapStyle
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.RadioGroup
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.lifecycleScope
import com.arcgismaps.ApiKey
import com.arcgismaps.ArcGISEnvironment
import com.arcgismaps.Color
import com.arcgismaps.geometry.Envelope
import com.arcgismaps.geometry.Geometry
import com.arcgismaps.geometry.GeometryEngine
import com.arcgismaps.geometry.Point
import com.arcgismaps.geometry.SpatialReference
import com.arcgismaps.mapping.ArcGISMap
import com.arcgismaps.mapping.symbology.CompositeSymbol
import com.arcgismaps.mapping.symbology.HorizontalAlignment
import com.arcgismaps.mapping.symbology.PictureMarkerSymbol
import com.arcgismaps.mapping.symbology.SimpleLineSymbol
import com.arcgismaps.mapping.symbology.SimpleLineSymbolStyle
import com.arcgismaps.mapping.symbology.TextSymbol
import com.arcgismaps.mapping.symbology.VerticalAlignment
import com.arcgismaps.mapping.view.Graphic
import com.arcgismaps.mapping.view.GraphicsOverlay
import com.arcgismaps.mapping.view.ScreenCoordinate
import com.arcgismaps.tasks.networkanalysis.RouteParameters
import com.arcgismaps.tasks.networkanalysis.RouteTask
import com.arcgismaps.tasks.networkanalysis.Stop
import com.esri.arcgismaps.sample.findrouteintransportnetwork.Connect.RetrofitInstance
import com.esri.arcgismaps.sample.findrouteintransportnetwork.Models.Location
import com.esri.arcgismaps.sample.findrouteintransportnetwork.Service.ApiService
import com.esri.arcgismaps.sample.findrouteintransportnetwork.databinding.ActivityMapBinding
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.File
import kotlin.math.roundToInt



class MainActivityI : AppCompatActivity() {
    private val apiService = RetrofitInstance.retrofit.create(ApiService::class.java)
    private val points : ArrayList<Point> = ArrayList();
    private val activityMainBinding: ActivityMapBinding by lazy {
        DataBindingUtil.setContentView(this, R.layout.activity_map)
    }

    private val provisionPath: String by lazy {
        getExternalFilesDir(null)?.path.toString() + File.separator + getString(R.string.app_name)
    }

    private val mapView by lazy {
        activityMainBinding.mapView
    }

    private val toggleButtons by lazy {
        activityMainBinding.toggleButtons
    }

    private val clearButton by lazy {
        activityMainBinding.clearButton
    }

    private val distanceTimeTextView by lazy {
        activityMainBinding.distanceTimeTextView
    }

    private val stopsOverlay: GraphicsOverlay by lazy { GraphicsOverlay() }
    private val routeOverlay: GraphicsOverlay by lazy { GraphicsOverlay() }

    private val envelope = Envelope(
        Point(-1.3045e7, 3.87e6, 0.0, SpatialReference.webMercator()),
        Point(-1.3025e7, 3.84e6, 0.0, SpatialReference.webMercator())
    )

    // create a route task to calculate routes
    private var routeTask: RouteTask? = null

    private var routeParameters: RouteParameters? = null

    private fun requestPermissions() {
        // coarse location permission
        val permissionCheckCoarseLocation =
            ContextCompat.checkSelfPermission(this@MainActivityI,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) ==
                    PackageManager.PERMISSION_GRANTED
        // fine location permission
        val permissionCheckFineLocation =
            ContextCompat.checkSelfPermission(this@MainActivityI,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) ==
                    PackageManager.PERMISSION_GRANTED

        // if permissions are not already granted, request permission from the user
        if (!(permissionCheckCoarseLocation && permissionCheckFineLocation)) {
            ActivityCompat.requestPermissions(
                this@MainActivityI,
                arrayOf(
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ),
                2
            )
        } else {
            // permission already granted, so start the location display
            lifecycleScope.launch {
                mapView.locationDisplay.dataSource.start().onSuccess {
                    activityMainBinding.spinner.setSelection(1, true)
                }
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            lifecycleScope.launch {
                mapView.locationDisplay.dataSource.start().onSuccess {
                    activityMainBinding.spinner.setSelection(1, true)
                }
            }
        } else {
            Snackbar.make(
                mapView,
                "Location permissions required to run this sample!",
                Snackbar.LENGTH_LONG
            ).show()
            // update UI to reflect that the location display did not actually start
            activityMainBinding.spinner.setSelection(0, true)
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // authentication with an API key or named user is
        // required to access basemaps and other location services
        ArcGISEnvironment.apiKey = ApiKey.create(BuildConfig.API_KEY)
        // some parts of the API require an Android Context to properly interact with Android system
        // features, such as LocationProvider and application resources
        ArcGISEnvironment.applicationContext = applicationContext
        lifecycle.addObserver(mapView)
        mapView.map = ArcGISMap(BasemapStyle.ArcGISNavigationNight)
        val locationDisplay = mapView.locationDisplay
        lifecycleScope.launch {
            // listen to changes in the status of the location data source
            locationDisplay.dataSource.start()
                .onSuccess {
                    // permission already granted, so start the location display
                    Log.d("LOCATION","SUCCESS")
                    activityMainBinding.spinner.setSelection(1, true)
                    routeParameters = routeTask?.createDefaultParameters()?.getOrThrow()
                }.onFailure {
                    requestPermissions()
                    Log.d("LOCATION",it.message.toString())
                    // check permissions to see if failure may be due to lack of permissions
                }
        }
        activityMainBinding.spinner.apply {
            adapter = SpinnerAdapter(this@MainActivityI, R.id.locationTextView, panModeSpinnerElements)
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?,
                    view: View,
                    position: Int,
                    id: Long
                ) { when (panModeSpinnerElements[position].text) {
                    "Stop" -> lifecycleScope.launch { locationDisplay.dataSource.stop() }
                    "On" -> lifecycleScope.launch { locationDisplay.dataSource.start() }
                    "Re-center" -> { locationDisplay.setAutoPanMode(LocationDisplayAutoPanMode.Recenter) }
                    "Navigation" -> { locationDisplay.setAutoPanMode(LocationDisplayAutoPanMode.Navigation) }
                    "Compass" -> { locationDisplay.setAutoPanMode(LocationDisplayAutoPanMode.CompassNavigation) } }
                }override fun onNothingSelected(parent: AdapterView<*>?) {} } }

        // add the graphics overlays to the map view
        mapView.graphicsOverlays.addAll(listOf(routeOverlay, stopsOverlay))

        // create a route task using the geodatabase file
        val geodatabaseFile = File(provisionPath + getString(R.string.geodatabase_path))
        routeTask = RouteTask(geodatabaseFile.path, "Streets_ND")



        toggleButtons.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                when (checkedId) {
                    R.id.fastestButton -> {
                        // calculate fastest route
                        routeParameters?.travelMode =
                            routeTask?.getRouteTaskInfo()?.travelModes?.get(0)

                        // update route based on selection
                        updateRoute()
                    }
                    R.id.shortestButton -> {
                        // calculate shortest route
                        routeParameters?.travelMode =
                            routeTask?.getRouteTaskInfo()?.travelModes?.get(1)

                        // update route based on selection
                        updateRoute()
                    }
                }
            }
        }

        // make a clear button to reset the stops and routes
        clearButton.setOnClickListener {
            stopsOverlay.graphics.clear()
            routeOverlay.graphics.clear()
            clearButton.isEnabled = false
            distanceTimeTextView.text = getString(R.string.tap_on_map_to_create_a_transport_network)
        }

        setUpMapView()
    }


    private fun setUpMapView() {
        with(lifecycleScope) {
            // set the viewpoint of the MapView
            launch {
                mapView.setViewpointGeometry(envelope)
            }

            // add graphic at the tapped coordinate
            launch {
                mapView.onSingleTapConfirmed.collect { tapEvent ->
                    val screenCoordinate = tapEvent.screenCoordinate
                    addOrSelectGraphic(screenCoordinate)
                    clearButton.isEnabled = true
                }
            }
        }
    }

    /**
     * Updates the calculated route using the
     * stops on the map by calling routeTask.solveRoute().
     * Creates a graphic to display the route.
     * */
    private fun updateRoute() = lifecycleScope.launch {
        // get a list of stops from the graphics currently on the graphics overlay.
        val stops = stopsOverlay.graphics.map {
            Stop(it.geometry as Point)
        }

        // do not calculate a route if there is only one stop
        if (stops.size <= 1) return@launch

        routeParameters?.setStops(stops)

        // solve the route
        val results = routeParameters?.let { routeTask?.solveRoute(it) }
        if (results != null) {
            results.onFailure {
                showError("No route solution. ${it.message}")
                routeOverlay.graphics.clear()
            }.onSuccess { routeResult ->
                // get the first solved route result
                val route = routeResult.routes[0]

                // create graphic for route
                val graphic = Graphic(
                    route.routeGeometry, SimpleLineSymbol(
                        SimpleLineSymbolStyle.Solid,
                        Color.green, 3F
                    )
                )
                routeOverlay.graphics.clear()
                routeOverlay.graphics.add(graphic)

                // set distance-time text
                val travelTime = route.travelTime.roundToInt()
                val travelDistance = "%.2f".format(
                    route.totalLength * 0.000621371192 // convert meters to miles and round 2 decimals
                )
                distanceTimeTextView.text = String.format("$travelTime min ($travelDistance mi)")
            }
        }
    }

    /**
     * Selects a graphic if there is one at the
     * provided [screenCoordinate] or, if there is
     * none, creates a new graphic.
     * */
    private suspend fun addOrSelectGraphic(screenCoordinate: ScreenCoordinate) {
        val result =
            mapView.identifyGraphicsOverlay(stopsOverlay, screenCoordinate, 10.0, false)

        result.onFailure {
            showError(it.message.toString())
        }.onSuccess { identifyGraphicsOverlayResult ->
            val graphics = identifyGraphicsOverlayResult.graphics

            if (stopsOverlay.selectedGraphics.isNotEmpty()) {
                stopsOverlay.unselectGraphics(stopsOverlay.selectedGraphics)
            }

            if (graphics.isNotEmpty()) {
                Log.d("A", "addOrSelectGraphic: ")
                val firstGraphic = graphics[0]
                firstGraphic.isSelected = true
                showLatLongPopup(firstGraphic.geometry as Point)  // Display lat-long information
            } else {// there is no graphic at this location

                val locationPoint = mapView.screenToLocation(screenCoordinate)
                Log.d("POINT X", locationPoint?.x.toString());
                Log.d("POINT Y", locationPoint?.y.toString());
                // check if tapped location is within the envelope
                if (GeometryEngine.within(locationPoint as Geometry, envelope)) {
                    if(stopsOverlay.graphics.size == 0) {
                        Log.d("B", "addOrSelectGraphic: ")
                        createStopSymbol(stopsOverlay.graphics.size + 1, locationPoint, "Start")
                        points.add(locationPoint);
                    } else if(stopsOverlay.graphics.size == 1){
                        Log.d("C", "addOrSelectGraphic: ")
                        getStations(points[0],locationPoint);
                        // createStopSymbol(stopsOverlay.graphics.size + 1, Point(stations[1].lan, stations[1].lat, locationPoint.spatialReference))
                    }
                }// make a new graphic at the tapped location
                else
                    showError("Tapped location is outside the transport network")
            }
        }
    }
    /**
     * Creates a composite symbol to represent a numbered stop.
     * The [stopNumber] is the ordinal number of this stop and the
     * symbol will be placed at the [locationPoint].
     */
    private fun createStopSymbol(stopNumber: Int, locationPoint: Point?, tag:String) {
        // create a orange pin PictureMarkerSymbol
        val pinSymbol = PictureMarkerSymbol.createWithImage(
            ContextCompat.getDrawable(
                this,
                R.drawable.pin_symbol
            ) as BitmapDrawable
        ).apply {
            // set the scale of the symbol
            width = 24f
            height = 24f
            // set in pin "drop" to be offset to the point on map
            offsetY = 10f
        }
        var color : Color
        if(tag == "Station") {
            color = Color.green
        } else {
            color = Color.black
        }
        // create black stop number TextSymbol
        val stopNumberSymbol = TextSymbol(
            stopNumber.toString(),
            color,
            12f,
            HorizontalAlignment.Center,
            VerticalAlignment.Bottom
        ).apply {
            offsetY = 4f
        }

        // create a composite symbol and add the picture marker symbol and text symbol
        val compositeSymbol = CompositeSymbol()
        compositeSymbol.symbols.addAll(listOf(pinSymbol, stopNumberSymbol))

        // create a graphic to add to the overlay and update the route
        val graphic = Graphic(locationPoint, compositeSymbol)
        stopsOverlay.graphics.add(graphic)

        updateRoute()
    }

    private fun showError(message: String) {
        Log.e(localClassName, message)
        Snackbar.make(mapView, message, Snackbar.LENGTH_SHORT).show()
    }

    private fun getStations(from: Point, to: Point) {
        Log.d("CALL", "GETPOST")
        val call: Call<List<Location>> = apiService.getPost(from.x * 1E-5, from.y * 1E-5, to.x * 1E-5, to.y * 1E-5, "bus");
        call.enqueue(object : Callback<List<Location>> {
            override fun onResponse(call: Call<List<Location>>, response: Response<List<Location>>) {
                if (response.isSuccessful) {
                    var i = 2
                    response.body()?.let { locations ->
                        locations.forEach { location ->
                            Log.d("STATIONS", (location.lan * 1E5).toString())
                            Log.d("STATIONS", (location.lat * 1E5).toString())

                            val stationTitle = location.title
                            showLatLongPopup(Point(location.lan * 1E5, location.lat * 1E5, to.spatialReference), true, location.title)
                            // Assuming the title is available in the response
                            createStopSymbol(i, Point(location.lan * 1E5, location.lat * 1E5, to.spatialReference), "Station")
                            i = i + 1
                        }
                        createStopSymbol(4, to, "End")
                    }
                } else {
                    runOnUiThread {
                        showError("Failed to get station information")
                    }
                }
            }

            override fun onFailure(call: Call<List<Location>>, t: Throwable) {
                // Handle network failures
                // For example, you can show an error message in the TextView
                runOnUiThread {
                    Log.d("ERROR", "Error: ${t.message}")
                    showError("Failed to get station information")
                }
            }
        })
    }

    val panModeSpinnerElements = arrayListOf(
        ItemData("Stop", R.drawable.locationdisplaydisabled),
        ItemData("On", R.drawable.locationdisplayon),
        ItemData("Re-center", R.drawable.locationdisplayrecenter),
        ItemData("Navigation", R.drawable.locationdisplaynavigation),
        ItemData("Compass", R.drawable.locationdisplayheading)
    )


    private suspend fun getStationTitle(latitude: Double, longitude: Double): String {
        val titleResponse = apiService.getStationTitle(latitude, longitude)
        return if (titleResponse.isSuccessful) {
            titleResponse.body() ?: "Unknown Station"
        } else {
            "Unknown Station"
        }
    }



    private fun showLatLongPopup(locationPoint: Point, isStation: Boolean = false, stationName: String? = null) {
        val latitude = locationPoint.y / 1E5
        val longitude = locationPoint.x / 1E6

        val stationInfo = if (isStation && !stationName.isNullOrBlank()) "\nStation: $stationName" else ""
        val popupContent = "Latitude: $latitude\nLongitude: $longitude$stationInfo"

        val builder = AlertDialog.Builder(this)
        builder.setTitle("Location Information")
            .setMessage(popupContent)
            .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
        builder.create().show()
    }







}











