package br.com.boddenb.comidinhas.data.repository

import android.Manifest
import android.annotation.SuppressLint
import android.location.Location
import android.util.Log
import androidx.annotation.RequiresPermission
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import br.com.boddenb.comidinhas.domain.repository.LocationRepository
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class LocationRepositoryImpl @Inject constructor(
    private val fused: FusedLocationProviderClient
) : LocationRepository {
    companion object { private const val TAG = "LocationRepo" }

    @SuppressLint("MissingPermission")
    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    override suspend fun getCurrentLocation(timeoutMs: Long): Location = suspendCancellableCoroutine { cont ->
        val cts = CancellationTokenSource()
        cont.invokeOnCancellation { cts.cancel() }
        Log.d(TAG, "getCurrentLocation() start (no-timeout) priority=HIGH_ACCURACY")
        val t0 = System.currentTimeMillis()
        fused.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
            .addOnSuccessListener { loc ->
                if (loc != null) {
                    Log.d(TAG, "getCurrentLocation() OK (HIGH) in ${System.currentTimeMillis() - t0} ms acc=${loc.accuracy}m provider=${loc.provider}")
                    cont.resume(loc)
                } else {
                    Log.w(TAG, "getCurrentLocation() returned null (HIGH); trying lastLocation ...")
                    fused.lastLocation
                        .addOnSuccessListener { last ->
                            if (last != null) {
                                Log.d(TAG, "lastLocation OK age=? acc=${last.accuracy}m provider=${last.provider}")
                                cont.resume(last)
                            } else {
                                Log.w(TAG, "lastLocation is null; trying LOW_POWER ...")
                                fused.getCurrentLocation(Priority.PRIORITY_LOW_POWER, cts.token)
                                    .addOnSuccessListener { low ->
                                        if (low != null) {
                                            Log.d(TAG, "getCurrentLocation() OK (LOW) in ${System.currentTimeMillis() - t0} ms acc=${low.accuracy}m provider=${low.provider}")
                                            cont.resume(low)
                                        } else {
                                            cont.resumeWithException(IllegalStateException("Location is null (HIGH/last/LOW)"))
                                        }
                                    }
                                    .addOnFailureListener { e ->
                                        Log.e(TAG, "getCurrentLocation() LOW_POWER failed: ${e.message}", e)
                                        cont.resumeWithException(e)
                                    }
                            }
                        }
                        .addOnFailureListener { e ->
                            Log.e(TAG, "lastLocation failed: ${e.message}", e)
                            // Tentar LOW_POWER mesmo após falha de lastLocation
                            fused.getCurrentLocation(Priority.PRIORITY_LOW_POWER, cts.token)
                                .addOnSuccessListener { low ->
                                    if (low != null) cont.resume(low)
                                    else cont.resumeWithException(IllegalStateException("Location is null after lastLocation failure"))
                                }
                                .addOnFailureListener { e2 -> cont.resumeWithException(e2) }
                        }
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "getCurrentLocation() HIGH_ACCURACY failed: ${e.message}", e)

                fused.lastLocation
                    .addOnSuccessListener { last ->
                        if (last != null) cont.resume(last)
                        else cont.resumeWithException(e)
                    }
                    .addOnFailureListener { e2 -> cont.resumeWithException(e2) }
            }
    }
}
