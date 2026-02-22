package br.com.boddenb.comidinhas.data.repository

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.annotation.RequiresPermission
import br.com.boddenb.comidinhas.domain.model.*
import br.com.boddenb.comidinhas.domain.repository.RestaurantRepository
import com.google.android.libraries.places.api.model.CircularBounds
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FetchPhotoRequest
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.android.libraries.places.api.net.SearchNearbyRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class RestaurantRepositoryImpl @Inject constructor(
    private val places: PlacesClient,
    @ApplicationContext private val context: Context
) : RestaurantRepository {

    companion object { private const val TAG = "RestaurantRepo" }

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    override suspend fun searchNearby(
        center: LatLng,
        radiusMeters: Int,
        orderBy: OrderBy,
        includedTypes: List<String>
    ): List<Restaurant> {
        try {
            val fields = listOf(
                Place.Field.ID,
                Place.Field.DISPLAY_NAME,
                Place.Field.LOCATION,
                Place.Field.RATING,
                Place.Field.USER_RATING_COUNT,
                Place.Field.FORMATTED_ADDRESS,
                Place.Field.PRICE_LEVEL,
                Place.Field.PHOTO_METADATAS,
                Place.Field.WEBSITE_URI,
                Place.Field.OPENING_HOURS,
                Place.Field.REVIEWS,
                Place.Field.NATIONAL_PHONE_NUMBER,
                Place.Field.EDITORIAL_SUMMARY,
                Place.Field.TYPES
            )

            val rankPreference = when (orderBy) {
                OrderBy.DISTANCE -> SearchNearbyRequest.RankPreference.DISTANCE
                OrderBy.RATING -> SearchNearbyRequest.RankPreference.POPULARITY
            }

            val allPlaces = mutableSetOf<Place>()
            val offset = (radiusMeters * 0.25) / 111000.0

            val searchPoints = listOf(
                center,
                LatLng(center.latitude + offset, center.longitude),
                LatLng(center.latitude - offset, center.longitude),
                LatLng(center.latitude, center.longitude + offset),
                LatLng(center.latitude, center.longitude - offset),
                LatLng(center.latitude + offset, center.longitude + offset),
                LatLng(center.latitude + offset, center.longitude - offset),
                LatLng(center.latitude - offset, center.longitude + offset),
                LatLng(center.latitude - offset, center.longitude - offset)
            )

            searchPoints.forEachIndexed { index, searchCenter ->
                try {
                    val sectorCircle = CircularBounds.newInstance(
                        com.google.android.gms.maps.model.LatLng(searchCenter.latitude, searchCenter.longitude),
                        radiusMeters.toDouble()
                    )

                    val request = SearchNearbyRequest.builder(sectorCircle, fields)
                        .setIncludedTypes(includedTypes)
                        .setMaxResultCount(20)
                        .setRankPreference(rankPreference)
                        .build()

                    val response = places.searchNearby(request).awaitCancellable()
                    allPlaces.addAll(response.places)

                    if (index < searchPoints.size - 1) {
                        kotlinx.coroutines.delay(100)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "   Erro no setor ${index + 1}: ${e.message}")
                }
            }

            val placesList = allPlaces.toList()
            val mapped = coroutineScope {
                placesList.map { p ->
                    async {
                        val loc = p.location ?: return@async null

                        val photoUrl = p.photoMetadatas?.firstOrNull()?.let { photoMetadata ->
                            try {
                                val photoRequest = FetchPhotoRequest.builder(photoMetadata)
                                    .setMaxWidth(400)
                                    .setMaxHeight(400)
                                    .build()

                                val photoResponse = places.fetchPhoto(photoRequest).awaitCancellable()
                                val bitmap = photoResponse.bitmap

                                saveBitmapToCache(p.id ?: "unknown", bitmap)
                            } catch (e: Exception) {
                                null
                            }
                        }

                        Restaurant(
                            id = p.id ?: "",
                            name = p.displayName ?: "",
                            latLng = LatLng(loc.latitude, loc.longitude),
                            rating = p.rating,
                            ratingsCount = p.userRatingCount,
                            address = p.formattedAddress,
                            isOpenNow = null,
                            priceLevel = p.priceLevel,
                            photoUrl = photoUrl,
                            phoneNumber = p.nationalPhoneNumber,
                            website = p.websiteUri?.toString(),
                            openingHours = p.openingHours?.weekdayText,
                            reviews = p.reviews?.take(5)?.mapNotNull { review ->
                                try {
                                    Review(
                                        authorName = review.authorAttribution.name ?: "Anônimo",
                                        rating = review.rating,
                                        text = review.text ?: "",
                                        relativeTime = review.relativePublishTimeDescription ?: ""
                                    )
                                } catch (e: Exception) {
                                    null
                                }
                            },
                            editorialSummary = p.editorialSummary,
                            types = emptyList()
                        )
                    }
                }.awaitAll().filterNotNull()
            }

            return when (orderBy) {
                OrderBy.DISTANCE -> mapped.sortedBy { haversine(center, it.latLng) }
                OrderBy.RATING -> mapped.sortedWith(compareByDescending<Restaurant> { it.rating ?: 0.0 }.thenByDescending { it.ratingsCount ?: 0 })
            }
        } catch (t: Throwable) {
            throw t
        }
    }

    private fun saveBitmapToCache(id: String, bitmap: Bitmap): String? {
        return try {
            val cacheDir = File(context.cacheDir, "restaurant_photos")
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }

            val photoFile = File(cacheDir, "$id.jpg")
            FileOutputStream(photoFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            }

            photoFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save bitmap to cache: ${e.message}", e)
            null
        }
    }
}

private suspend fun <T> com.google.android.gms.tasks.Task<T>.awaitCancellable(): T = suspendCancellableCoroutine { cont ->
    this.addOnSuccessListener { cont.resume(it) }
        .addOnFailureListener { cont.resumeWithException(it) }
}

private fun haversine(a: LatLng, b: LatLng): Double {
    val R = 6371000.0
    val dLat = Math.toRadians(b.latitude - a.latitude)
    val dLon = Math.toRadians(b.longitude - a.longitude)
    val lat1 = Math.toRadians(a.latitude)
    val lat2 = Math.toRadians(b.latitude)
    val sinDLat = sin(dLat / 2)
    val sinDLon = sin(dLon / 2)
    val h = sinDLat * sinDLat + cos(lat1) * cos(lat2) * sinDLon * sinDLon
    val c = 2 * atan2(sqrt(h), sqrt(1 - h))
    return R * c
}
