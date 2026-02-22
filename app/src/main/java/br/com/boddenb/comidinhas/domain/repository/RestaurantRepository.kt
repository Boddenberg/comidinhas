package br.com.boddenb.comidinhas.domain.repository

import br.com.boddenb.comidinhas.domain.model.*

interface RestaurantRepository {
    suspend fun searchNearby(
        center: LatLng,
        radiusMeters: Int,
        orderBy: OrderBy,
        includedTypes: List<String> = listOf("restaurant")
    ): List<Restaurant>
}

