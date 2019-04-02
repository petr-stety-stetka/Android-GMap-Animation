package cz.ukol.animace

import android.location.Location
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.SystemClock
import android.view.animation.LinearInterpolator
import android.widget.Button
import com.google.android.gms.maps.*

import com.google.android.gms.maps.model.*
import java.util.ArrayList
import kotlin.properties.Delegates.observable

class MapsActivity : AppCompatActivity(), OnMapReadyCallback {

    private val markers = ArrayList<Marker>()
    private lateinit var googleMap: GoogleMap
    private val mHandler = Handler()
    private var selectedMarker: Marker? = null
    private val animator = Animator()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_maps)

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        val start = findViewById<Button>(R.id.btn_play)

        animator.onRunningChanged = { oldValue, newValue ->
            if(newValue)
                start.text = "Zastavit animaci"
            else
                start.text = "Přehrát animaci"
        }

        start.setOnClickListener {
            if(!animator.running)
                animator.startAnimation(true)
            else
                animator.stopAnimation()
        }
    }

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    override fun onMapReady(googleMap: GoogleMap) {
        this.googleMap = googleMap

        this.googleMap.uiSettings.isZoomControlsEnabled = true
        this.googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(49.1923963,16.5485575), 12F))

        addMarkerToMap(LatLng(49.196464, 16.531420), "Kohoutovice Hájenka")
        addMarkerToMap(LatLng(49.195169, 16.536424), "Žebětínská")
        addMarkerToMap(LatLng(49.191779, 16.539019), "Výletní")
        addMarkerToMap(LatLng(49.190457, 16.543369), "Myslivní")
        addMarkerToMap(LatLng(49.191525, 16.545821), "Šárka")
        addMarkerToMap(LatLng(49.193527, 16.561815), "Antonína procházky")
        addMarkerToMap(LatLng(49.193481, 16.566803), "Anthropos")
        addMarkerToMap(LatLng(49.193506, 16.571351), "Pisárky")
        addMarkerToMap(LatLng(49.191853, 16.575786), "Lipová")
        addMarkerToMap(LatLng(49.189594, 16.586377), "Výstaviště hlavní vstup")
        addMarkerToMap(LatLng(49.190519, 16.593888), "Mendlovo náměstí")
    }

    private fun addMarkerToMap(latLng: LatLng, title: String) {
        val marker = googleMap.addMarker(MarkerOptions().position(latLng).title(title))
        markers.add(marker)
    }

    inner class Animator : Runnable {

        var running: Boolean by observable(false) { _, oldValue, newValue ->
            onRunningChanged?.invoke(oldValue, newValue)
        }
        var onRunningChanged: ((Boolean, Boolean) -> Unit)? = null

        private val ANIMATE_SPEEED = 2000
        private val ANIMATE_SPEEED_TURN = 1000
        private val BEARING_OFFSET = 20

        private val interpolator = LinearInterpolator()
        private var currentIndex = 0
        private var tilt = 90f
        private var start = SystemClock.uptimeMillis()
        private var endLatLng: LatLng? = null
        private var beginLatLng: LatLng? = null
        private var showPolyline = false
        private var trackingMarker: Marker? = null

        private var polyLine: Polyline? = null
        private val rectOptions = PolylineOptions()

        fun reset() {
            resetMarkers()
            start = SystemClock.uptimeMillis()
            currentIndex = 0
            endLatLng = getEndLatLng()
            beginLatLng = getBeginLatLng()

        }

        private fun resetMarkers() {
            for (marker in markers) {
                marker.setIcon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
            }
        }

        private fun stop() {
            trackingMarker!!.remove()
            mHandler.removeCallbacks(animator)
            running = false
        }

        private fun highLightMarker(index: Int) {
            highLightMarker(markers[index])
        }

        private fun highLightMarker(marker: Marker) {
            marker.setIcon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
            marker.showInfoWindow()
            selectedMarker = marker
        }

        private fun initialize(showPolyLine: Boolean) {
            reset()
            this.showPolyline = showPolyLine
            highLightMarker(0)
            if (showPolyLine) {
                polyLine = initializePolyLine()
            }
            // We first need to put the camera in the correct position for the first run (we need 2 markers for this).....
            val markerPos = markers.get(0).position
            val secondPos = markers.get(1).position
            setupCameraPositionForMovement(markerPos, secondPos)
        }

        private fun setupCameraPositionForMovement(markerPos: LatLng, secondPos: LatLng) {
            val bearing = bearingBetweenLatLngs(markerPos, secondPos)
            trackingMarker = googleMap.addMarker(
                MarkerOptions().position(markerPos).icon(BitmapDescriptorFactory.fromResource(R.drawable.marker_bus_icon)))//.visible(false))

            val cameraPosition = CameraPosition.Builder()
                .target(markerPos)
                .bearing(bearing + BEARING_OFFSET)
                .tilt(90f)
                .zoom(if (googleMap.cameraPosition.zoom >= 16) googleMap.cameraPosition.zoom else 16F)
                .build()

            googleMap.animateCamera(
                CameraUpdateFactory.newCameraPosition(cameraPosition),
                ANIMATE_SPEEED_TURN,
                object : GoogleMap.CancelableCallback {
                    override fun onFinish() {
                        animator.reset()
                        val handler = Handler()
                        handler.post(animator)
                    }

                    override fun onCancel() {}
                })
        }

        private fun convertLatLngToLocation(latLng: LatLng): Location {
            val loc = Location("someLoc")
            loc.latitude = latLng.latitude
            loc.longitude = latLng.longitude
            return loc
        }

        private fun bearingBetweenLatLngs(begin: LatLng, end: LatLng): Float {
            val beginL = convertLatLngToLocation(begin)
            val endL = convertLatLngToLocation(end)
            return beginL.bearingTo(endL)
        }

        private fun initializePolyLine(): Polyline {
            rectOptions.add(markers[0].position)
            return googleMap.addPolyline(rectOptions)
        }

        /**
         * Add the marker to the polyline.
         */
        private fun updatePolyLine(latLng: LatLng) {
            val points = polyLine!!.points
            points.add(latLng)
            polyLine!!.points = points
        }

        fun stopAnimation() {
            animator.stop()
            //trackingMarker!!.isVisible = false
        }

        fun startAnimation(showPolyLine: Boolean) {
            if (markers.size > 2) {
            //    trackingMarker!!.isVisible = true
                running = true
                animator.initialize(showPolyLine)
            }
        }

        override fun run() {
            val elapsed = SystemClock.uptimeMillis() - start
            val t = interpolator.getInterpolation(elapsed.toFloat() / ANIMATE_SPEEED).toDouble()
            val lat = t * endLatLng!!.latitude + (1 - t) * beginLatLng!!.latitude
            val lng = t * endLatLng!!.longitude + (1 - t) * beginLatLng!!.longitude
            val newPosition = LatLng(lat, lng)

            trackingMarker!!.position = newPosition
            if (showPolyline) {
                updatePolyLine(newPosition)
            }

            if (t < 1) {
                mHandler.postDelayed(this, 16)
            } else {
                println("Move to next marker.... current = " + currentIndex + " and size = " + markers.size)
                // imagine 5 elements -  0|1|2|3|4 currentindex must be smaller than 4
                if (currentIndex < markers.size - 2) {
                    currentIndex++
                    endLatLng = getEndLatLng()
                    beginLatLng = getBeginLatLng()
                    start = SystemClock.uptimeMillis()
                    val begin = getBeginLatLng()
                    val end = getEndLatLng()
                    val bearingL = bearingBetweenLatLngs(begin, end)
                    highLightMarker(currentIndex)
                    val cameraPosition = CameraPosition.Builder()
                        .target(end) // changed this...
                        .bearing(bearingL + BEARING_OFFSET)
                        .tilt(tilt)
                        .zoom(googleMap.cameraPosition.zoom)
                        .build()
                    googleMap.animateCamera(
                        CameraUpdateFactory.newCameraPosition(cameraPosition),
                        ANIMATE_SPEEED_TURN,
                        null
                    )
                    start = SystemClock.uptimeMillis()
                    mHandler.postDelayed(animator, 16)
                } else {
                    currentIndex++
                    highLightMarker(currentIndex)
                    stopAnimation()
                }
            }
        }

        private fun getEndLatLng(): LatLng {
            return markers.get(currentIndex + 1).position
        }

        private fun getBeginLatLng(): LatLng {
            return markers.get(currentIndex).position
        }
    }
}
