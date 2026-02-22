package br.com.boddenb.comidinhas.domain.usecase

import android.util.Log
import br.com.boddenb.comidinhas.data.remote.OpenAiClient
import br.com.boddenb.comidinhas.domain.mapper.RestaurantToAiInfoMapper
import br.com.boddenb.comidinhas.domain.model.Restaurant
import javax.inject.Inject

class FilterRestaurantsByFoodUseCase @Inject constructor(
    private val openAiClient: OpenAiClient,
    private val mapper: RestaurantToAiInfoMapper
) {
    companion object {
        private const val TAG = "FilterRestaurantsUC"
    }

    suspend operator fun invoke(
        restaurants: List<Restaurant>,
        foodQuery: String
    ): List<Restaurant> {
        if (foodQuery.isBlank()) {
            Log.d(TAG, "Query vazia, retornando todos os ${restaurants.size} restaurantes")
            return restaurants
        }

        return try {
            Log.d(TAG, "Iniciando filtro por '$foodQuery' em ${restaurants.size} restaurantes")

            val restaurantInfos = restaurants.map { mapper.map(it) }

            Log.d(TAG, "Enviando para IA:")
            restaurantInfos.take(3).forEachIndexed { index, info ->
                Log.d(TAG, "  ${index + 1}. ${info.take(100)}...")
            }

            val filteredInfos = openAiClient.filterRestaurantsByFood(restaurantInfos, foodQuery)

            val filtered = matchFilteredToRestaurants(restaurants, restaurantInfos, filteredInfos)

            Log.d(TAG, "IA filtrou: ${filtered.size} de ${restaurants.size} restaurantes")
            Log.d(TAG, "Restaurantes mantidos:")
            filtered.forEachIndexed { index, r ->
                Log.d(TAG, "  ${index + 1}. ${r.name}")
            }

            sortByRating(filtered)
        } catch (e: Exception) {
            Log.e(TAG, "ERRO ao chamar IA: ${e.message}")
            Log.e(TAG, "   Retornando TODOS os ${restaurants.size} restaurantes sem filtro")
            e.printStackTrace()
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

