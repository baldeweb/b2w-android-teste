package com.b2w.routeme

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import com.b2w.routeme.viewmodel.MapsViewModel
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.widget.Autocomplete
import com.google.android.libraries.places.widget.AutocompleteActivity
import com.google.android.libraries.places.widget.model.AutocompleteActivityMode
import com.google.maps.DirectionsApi
import com.google.maps.DirectionsApiRequest
import com.google.maps.GeoApiContext
import kotlinx.android.synthetic.main.activity_maps.*
import java.util.*
import kotlin.collections.ArrayList

class MapsActivity : AppCompatActivity(), OnMapReadyCallback {

    private val viewModel: MapsViewModel by lazy { MapsViewModel(application) }

    private lateinit var mMap: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val REQUEST_PERMISSION_LOCATION = 101
    private val AUTOCOMPLETE_REQUEST_CODE = 1
    private var mLocation: Location? = null
    private var latitudeFromAutocomplete: Double = 0.0
    private var longitudeFromAutocomplete: Double = 0.0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_maps)
        initialSetupMap()
        initObservers()
    }

    private fun initObservers() {
        viewModel.addressName.observe(this, Observer<String> {
            tvwSearchLocation.text = it
        })
    }

    private fun initialSetupMap() {
        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        if (!Places.isInitialized())
            Places.initialize(
                applicationContext,
                getString(R.string.google_maps_key),
                Locale.getDefault()
            );
    }

    private fun setupAutoCompletePlaces() {
        val fields = listOf(Place.Field.ID, Place.Field.NAME, Place.Field.LAT_LNG)
        val intent =
            Autocomplete.IntentBuilder(AutocompleteActivityMode.OVERLAY, fields).build(this)
        startActivityForResult(intent, AUTOCOMPLETE_REQUEST_CODE)
    }

    override fun onMapReady(googleMap: GoogleMap?) {
        mMap = googleMap!!

        getCurrentPosition()
    }

    private fun bitmapDescriptorFromVector(vectorResId: Int): BitmapDescriptor? {
        val vectorDrawable = ContextCompat.getDrawable(this, vectorResId)
        vectorDrawable!!.setBounds(
            0,
            0,
            vectorDrawable.intrinsicWidth,
            vectorDrawable.intrinsicHeight
        )
        val bitmap = Bitmap.createBitmap(
            vectorDrawable.intrinsicWidth,
            vectorDrawable.intrinsicHeight,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        vectorDrawable.draw(canvas)
        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }

    private fun setupDrawRoute(latLng: Location) {
        val currentLocation = LatLng(latLng.latitude, latLng.longitude)
        mMap.addMarker(MarkerOptions().position(currentLocation).icon(bitmapDescriptorFromVector(R.drawable.ic_pin_current_location)))

        val targetLocation = LatLng(latitudeFromAutocomplete, longitudeFromAutocomplete)
        mMap.addMarker(MarkerOptions().position(targetLocation).icon(bitmapDescriptorFromVector(R.drawable.ic_pin_target_destination)))

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

        viewModel.setupLocation(req, path, mMap)

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
                viewModel.setAddressName(place?.name ?: "")
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