package com.zl.qiewash

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Looper
import androidx.core.content.ContextCompat

/** One-shot coarse location lookup; the app only needs a plausible coordinate for signing. */
object Locate {
    fun allowed(ctx: Context): Boolean =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    fun lastKnown(ctx: Context): DoubleArray? {
        val lm = ctx.getSystemService(LocationManager::class.java) ?: return null
        for (provider in listOf(
            LocationManager.NETWORK_PROVIDER,
            LocationManager.GPS_PROVIDER,
            LocationManager.PASSIVE_PROVIDER,
        )) {
            val loc = runCatching { lm.getLastKnownLocation(provider) }.getOrNull()
            if (loc != null) return doubleArrayOf(loc.latitude, loc.longitude)
        }
        return null
    }

    fun request(ctx: Context, onResult: (Double, Double) -> Unit) {
        val lm = ctx.getSystemService(LocationManager::class.java) ?: return
        val done = java.util.concurrent.atomic.AtomicBoolean(false)
        val listener = object : android.location.LocationListener {
            override fun onLocationChanged(location: android.location.Location) {
                if (done.compareAndSet(false, true)) {
                    lm.removeUpdates(this)
                    onResult(location.latitude, location.longitude)
                }
            }

            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
        }
        listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER).forEach { provider ->
            runCatching {
                lm.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper())
            }
        }
    }
}
