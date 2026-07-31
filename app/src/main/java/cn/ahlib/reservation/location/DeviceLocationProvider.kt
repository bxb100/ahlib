package cn.ahlib.reservation.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

class DeviceLocationProvider(context: Context) {
    private val locationManager =
        context.applicationContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val appContext = context.applicationContext

    suspend fun getCurrentLocation(timeoutMillis: Long = 12_000L): Location? {
        if (!hasLocationPermission()) {
            return null
        }

        return withTimeoutOrNull(timeoutMillis) {
            requestLocation()
        }
    }

    private fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_FINE_LOCATION,
        )
        val coarse = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )
        return fine == PackageManager.PERMISSION_GRANTED ||
            coarse == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    private suspend fun requestLocation(): Location? = suspendCancellableCoroutine { continuation ->
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter { provider ->
                runCatching { locationManager.isProviderEnabled(provider) }.getOrDefault(false)
            }

        val recentLocation = providers
            .mapNotNull { provider ->
                runCatching {
                    locationManager.getLastKnownLocation(provider)
                }.getOrNull()
            }
            .filter { location -> location.elapsedRealtimeAgeMillis <= MAX_LAST_LOCATION_AGE }
            .minByOrNull(Location::getAccuracy)

        if (recentLocation != null) {
            continuation.resume(recentLocation)
            return@suspendCancellableCoroutine
        }

        if (providers.isEmpty()) {
            continuation.resume(null)
            return@suspendCancellableCoroutine
        }

        val cancellationSignals = providers.map { CancellationSignal() }
        continuation.invokeOnCancellation {
            cancellationSignals.forEach(CancellationSignal::cancel)
        }
        var pendingProviderCount = providers.size
        providers.forEachIndexed { index, provider ->
            if (!continuation.isActive) {
                return@forEachIndexed
            }
            runCatching {
                locationManager.getCurrentLocation(
                    provider,
                    cancellationSignals[index],
                    ContextCompat.getMainExecutor(appContext),
                ) { location ->
                    if (!continuation.isActive) {
                        return@getCurrentLocation
                    }
                    if (location != null) {
                        cancellationSignals.forEach(CancellationSignal::cancel)
                        continuation.resume(location)
                    } else {
                        pendingProviderCount -= 1
                        if (pendingProviderCount == 0) {
                            continuation.resume(null)
                        }
                    }
                }
            }.onFailure {
                pendingProviderCount -= 1
                if (pendingProviderCount == 0 && continuation.isActive) {
                    continuation.resume(null)
                }
            }
        }
    }

    private companion object {
        const val MAX_LAST_LOCATION_AGE = 120_000L
    }
}
