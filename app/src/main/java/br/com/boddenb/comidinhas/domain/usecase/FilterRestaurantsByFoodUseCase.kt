package br.com.boddenb.comidinhas.domain.usecase

import br.com.boddenb.comidinhas.data.logger.AppLogger
import br.com.boddenb.comidinhas.data.remote.OpenAiClient
import br.com.boddenb.comidinhas.domain.mapper.RestaurantToAiInfoMapper
import br.com.boddenb.comidinhas.domain.model.Restaurant
import javax.inject.Inject

class FilterRestaurantsByFoodUseCase @Inject constructor(
    private val openAiClient: OpenAiClient,
    private val mapper: RestaurantToAiInfoMapper
) {
    private val TAG = "FilterRestaurantsUC"

    suspend operator fun invoke(
        restaurants: List<Restaurant>,
        foodQuery: String
    ): List<Restaurant> {
        if (foodQuery.isBlank()) {
            AppLogger.d(TAG, "Query vazia, retornando todos os ${restaurants.size} restaurantes")
            return restaurants
        }

        return try {
            AppLogger.d(TAG, "Iniciando filtro por '$foodQuery' em ${restaurants.size} restaurantes")

            val restaurantInfos = restaurants.map { mapper.map(it) }

            AppLogger.d(TAG, "Enviando para IA (primeiros 3):")
            restaurantInfos.take(3).forEachIndexed { index, info ->
                AppLogger.d(TAG, "  ${index + 1}. ${info.take(100)}...")
            }

            val filteredInfos = openAiClient.filterRestaurantsByFood(restaurantInfos, foodQuery)

            val filtered = matchFilteredToRestaurants(restaurants, restaurantInfos, filteredInfos)

            AppLogger.d(TAG, "IA filtrou: ${filtered.size} de ${restaurants.size} restaurantes")
            filtered.forEachIndexed { index, r -> AppLogger.d(TAG, "  ${index + 1}. ${r.name}") }

            sortByRating(filtered)
        } catch (e: Exception) {
            AppLogger.e(TAG, "ERRO ao chamar IA: ${e.message}", e)
            AppLogger.e(TAG, "Retornando TODOS os ${restaurants.size} restaurantes sem filtro")
            restaurants
        }
    }

    private fun matchFilteredToRestaurants(
        restaurants: List<Restaurant>,
        restaurantInfos: List<String>,
        filteredInfos: List<String>
    ): List<Restaurant> {
        val filteredNamesSet = filteredInfos.toSet()
        val infoToRestaurant = restaurants.mapIndexed { index, restaurant ->
            restaurantInfos[index] to restaurant
        }.toMap()

        return filteredNamesSet.mapNotNull { info ->
            infoToRestaurant[info]
        }.distinctBy { it.id }
    }

    private fun sortByRating(restaurants: List<Restaurant>): List<Restaurant> {
        return restaurants.sortedWith(
            compareByDescending<Restaurant> { it.rating ?: 0.0 }
                .thenByDescending { it.ratingsCount ?: 0 }
        )
    }
}
