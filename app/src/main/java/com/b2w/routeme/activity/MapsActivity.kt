package com.b2w.routeme.activity

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.location.Location
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import com.b2w.routeme.R
import com.b2w.routeme.viewmodel.MapsViewModel
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
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

class MapsActivity : AppCompatActivity(), OnMapReadyCallback {

    private val viewModel: MapsViewModel by lazy { MapsViewModel(application) }

    private lateinit var mMap: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private val REQUEST_PERMISSION_LOCATION = 101
    private val AUTOCOMPLETE_REQUEST_CODE = 1
    private var mLocation: Location? = null
    private var latitudeFromAutocomplete: Double = 0.0
    private var longitudeFromAutocomplete: Double = 0.0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_maps)
    }

    override fun onStart() {
        super.onStart()

        initialSetupMap()
        initObservers()
        getCurrentPositionAndroidQ()
    }

    private fun getCurrentPositionAndroidQ() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult?) {
                (locationResult ?: return).locations.forEach {
                    setupDrawRoute(it)
                }
            }
        }
    }

    private fun initObservers() {
        viewModel.addressName.observe(this, Observer<String> {
            tvwSearchLocation.text = it
        })
    }

    private fun initialSetupMap() {
        (supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment).getMapAsync(this)

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
        mMap.addMarker(
            MarkerOptions().position(currentLocation).icon(
                bitmapDescriptorFromVector(
                    R.drawable.ic_pin_current_location
                )
            )
        )

        val targetLocation = LatLng(latitudeFromAutocomplete, longitudeFromAutocomplete)
        mMap.addMarker(
            MarkerOptions().position(targetLocation).icon(
                bitmapDescriptorFromVector(
                    R.drawable.ic_pin_target_destination
                )
            )
        )

        val context = GeoApiContext.Builder()
            .apiKey(getString(R.string.google_maps_key))
            .build()

        val req: DirectionsApiRequest = DirectionsApi
            .getDirections(
                context,
                "${latLng.latitude},${latLng.longitude}",
                "$latitudeFromAutocomplete,$longitudeFromAutocomplete"
            )

        viewModel.setupLocation(req, mMap)

        mMap.uiSettings.isZoomControlsEnabled = true
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLocation, 16F))
    }

    private fun getCurrentPosition() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        if ((ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED) || (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED)
        ) {
            if ((ActivityCompat.shouldShowRequestPermissionRationale(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                )) || (ActivityCompat.shouldShowRequestPermissionRationale(
                    this,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ))
            ) {
                alertPermission()
            } else {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ),
                    REQUEST_PERMISSION_LOCATION
                )
            }
        } else {
            fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                mLocation = location
                location?.let { setupDrawRoute(it) }
            }
        }
    }

    private fun alertPermission() {
        val alertDialog = AlertDialog.Builder(this)
        alertDialog.setTitle(getString(R.string.maps_alert_title_permission))
        alertDialog.setMessage(getString(R.string.maps_alert_message_permission))
        alertDialog.setPositiveButton(getString(R.string.maps_alert_positive_button_message_permission)) { _, _ ->
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                REQUEST_PERMISSION_LOCATION
            )
        }
        alertDialog.setNegativeButton(getString(R.string.maps_alert_negative_button_message_permission)) { _, _ -> }
        alertDialog.create()
        alertDialog.show()
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
                return
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
            REQUEST_PERMISSION_LOCATION -> getCurrentPosition()
        }
    }

    fun onSearchLocation(v: View) {
        setupAutoCompletePlaces()
    }
}