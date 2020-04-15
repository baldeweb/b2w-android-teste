package com.b2w.routeme.viewmodel

import android.app.Application
import android.graphics.Color
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.PolylineOptions
import com.google.maps.DirectionsApiRequest
import com.google.maps.model.*

class MapsViewModel(application: Application) : AndroidViewModel(application) {
    var addressName = MutableLiveData<String>()

    fun setAddressName(address: String) {
        addressName.value = address
    }

    fun setupLocation(
        req: DirectionsApiRequest,
        mMap: GoogleMap
    ) {
        val pathRoute: MutableList<LatLng> = ArrayList()

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
                                            pathRoute.add(LatLng(coord1.lat, coord1.lng))
                                        }
                                    }
                                } else {
                                    val points: EncodedPolyline = step.polyline
                                    val coords = points.decodePath()
                                    for (coord in coords) {
                                        pathRoute.add(LatLng(coord.lat, coord.lng))
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

        if (pathRoute.size > 0) {
            val opts = PolylineOptions().addAll(pathRoute).color(Color.GRAY).width(5f)
            mMap.addPolyline(opts)
        }
    }

}