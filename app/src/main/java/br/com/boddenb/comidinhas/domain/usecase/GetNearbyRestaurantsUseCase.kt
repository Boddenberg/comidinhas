package br.com.boddenb.comidinhas.domain.usecase

import br.com.boddenb.comidinhas.domain.model.*
import br.com.boddenb.comidinhas.domain.repository.RestaurantRepository
import javax.inject.Inject

class GetNearbyRestaurantsUseCase @Inject constructor(
    private val repo: RestaurantRepository
) {
    suspend operator fun invoke(
        center: LatLng,
        radiusMeters: Int,
        orderBy: OrderBy,
        includedTypes: List<String> = listOf("restaurant")
    ): List<Restaurant> =
        repo.searchNearby(center, radiusMeters, orderBy, includedTypes)
}
