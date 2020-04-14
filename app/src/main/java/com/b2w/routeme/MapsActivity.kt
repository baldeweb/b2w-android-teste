package com.b2w.routeme

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolylineOptions
import com.google.maps.DirectionsApi
import com.google.maps.DirectionsApiRequest
import com.google.maps.GeoApiContext
import com.google.maps.model.*

class MapsActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val REQUEST_PERMISSION_LOCATION = 101
    private var mLocation: Location? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_maps)
        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }

    override fun onMapReady(googleMap: GoogleMap?) {
        mMap = googleMap!!

        getCurrentPosition()
    }

    private fun setupDrawRoute(latLng: Location) {
        val currentLocation = LatLng(latLng.latitude, latLng.longitude)
        mMap.addMarker(MarkerOptions().position(currentLocation))

        val targetLocation = LatLng(-23.5415905, -46.8000877)
        mMap.addMarker(MarkerOptions().position(targetLocation))

        val path: MutableList<LatLng> = ArrayList()

        val context = GeoApiContext.Builder()
            .apiKey(getString(R.string.google_maps_key))
            .build()

        val req: DirectionsApiRequest = DirectionsApi
            .getDirections(
                context,
                "${latLng.latitude},${latLng.longitude}",
                "-23.5415905,-46.8000877"
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

}