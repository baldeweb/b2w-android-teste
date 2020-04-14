package com.b2w.routeme

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.common.api.Status
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolylineOptions
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.widget.Autocomplete
import com.google.android.libraries.places.widget.AutocompleteActivity
import com.google.android.libraries.places.widget.AutocompleteSupportFragment
import com.google.android.libraries.places.widget.listener.PlaceSelectionListener
import com.google.android.libraries.places.widget.model.AutocompleteActivityMode
import com.google.maps.DirectionsApi
import com.google.maps.DirectionsApiRequest
import com.google.maps.GeoApiContext
import com.google.maps.model.*
import java.util.*
import kotlin.collections.ArrayList


class MapsActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val REQUEST_PERMISSION_LOCATION = 101
    private val AUTOCOMPLETE_REQUEST_CODE = 1
    private var mLocation: Location? = null
    private var latitudeFromAutocomplete : Double = 0.0
    private var longitudeFromAutocomplete : Double = 0.0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_maps)
        initialSetupMap()
    }

    private fun initialSetupMap() {
        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        if (!Places.isInitialized())
            Places.initialize(applicationContext, getString(R.string.google_maps_key), Locale.getDefault());
    }

    private fun setupAutoCompletePlaces() {
        val fields= listOf(Place.Field.ID,Place.Field.NAME,Place.Field.LAT_LNG)
        val intent = Autocomplete.IntentBuilder(AutocompleteActivityMode.OVERLAY, fields).build(this)
        startActivityForResult(intent, AUTOCOMPLETE_REQUEST_CODE)
    }

    override fun onMapReady(googleMap: GoogleMap?) {
        mMap = googleMap!!

        getCurrentPosition()
    }

    private fun setupDrawRoute(latLng: Location) {
        val currentLocation = LatLng(latLng.latitude, latLng.longitude)
        mMap.addMarker(MarkerOptions().position(currentLocation))

        val targetLocation = LatLng(latitudeFromAutocomplete, longitudeFromAutocomplete)
        mMap.addMarker(MarkerOptions().position(targetLocation))

        val path: MutableList<LatLng> = ArrayList()

        val context = GeoApiContext.Builder()
            .apiKey(getString(R.string.google_maps_key))
            .build()

        val req: DirectionsApiRequest = DirectionsApi
            .getDirections(
                context,
                "${latLng.latitude},${latLng.longitude}",
                "$latitudeFromAutocomplete,$longitudeFromAutocomplete"
            )

        try {
            val res: DirectionsResult = req.await()
            if (res.routes != null && res.routes.isNotEmpty()) {
                val route: DirectionsRoute = res.routes.get(0)
                if (route.legs != null) {
                    for (i in route.legs.indices) {
                        val leg: DirectionsLeg = route.legs.get(i)
                        if (leg.steps != null) {
                            for (j in leg.steps.indices) {
                                val step: DirectionsStep = leg.steps.get(j)
                                if (step.steps != null && step.steps.isNotEmpty()) {
                                    for (k in step.steps.indices) {
                                        val step1: DirectionsStep = step.steps.get(k)
                                        val points1: EncodedPolyline = step1.polyline
                                        val coords1 = points1.decodePath()
                                        for (coord1 in coords1) {
                                            path.add(LatLng(coord1.lat, coord1.lng))
                                        }
                                    }
                                } else {
                                    val points: EncodedPolyline = step.polyline
                                    val coords = points.decodePath()
                                    for (coord in coords) {
                                        path.add(LatLng(coord.lat, coord.lng))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (ex: java.lang.Exception) {
            Log.e("LOG", ex.localizedMessage ?: "")
        }

        if (path.size > 0) {
            val opts = PolylineOptions().addAll(path).color(Color.BLUE).width(5f)
            mMap.addPolyline(opts)
        }

        mMap.uiSettings.isZoomControlsEnabled = true
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLocation, 16F))
    }

    private fun getCurrentPosition() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(
                    this,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            ) {
                fusedLocationClient.lastLocation
                    .addOnSuccessListener { location: Location? ->
                        mLocation = location!!
                        setupDrawRoute(location)
                    }
            } else {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.READ_CONTACTS),
                    REQUEST_PERMISSION_LOCATION
                )
            }
        } else {
            fusedLocationClient.lastLocation
                .addOnSuccessListener { location: Location? ->
                    mLocation = location!!
                    setupDrawRoute(location)
                }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == AUTOCOMPLETE_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK) {
                val place = data?.let { Autocomplete.getPlaceFromIntent(it) };

                latitudeFromAutocomplete = place?.latLng?.latitude ?: 0.0
                longitudeFromAutocomplete = place?.latLng?.longitude ?: 0.0

                mMap.clear()
                getCurrentPosition()
            } else if (resultCode == AutocompleteActivity.RESULT_ERROR) {
                val status = data?.let { Autocomplete.getStatusFromIntent(it) }
                Log.d("LOG", status?.statusMessage ?: "");
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_PERMISSION_LOCATION -> {
                // If request is cancelled, the result arrays are empty.
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {

                } else {
                }
            }
        }
    }

    fun onSearchLocation(v: View) {
        setupAutoCompletePlaces()
    }

}