package br.com.boddenb.comidinhas.domain.model

data class LatLng(val latitude: Double, val longitude: Double)

enum class OrderBy { DISTANCE, RATING }

data class Review(
    val authorName: String,
    val rating: Double,
    val text: String,
    val relativeTime: String
)

data class Restaurant(
    val id: String,
    val name: String,
    val latLng: LatLng,
    val rating: Double?,
    val ratingsCount: Int?,
    val address: String?,
    val isOpenNow: Boolean?,
    val priceLevel: Int?,
    val photoUrl: String?,
    val phoneNumber: String? = null,
    val website: String? = null,
    val openingHours: List<String>? = null,
    val reviews: List<Review>? = null,
    val editorialSummary: String? = null,
    val types: List<String>? = null
)

