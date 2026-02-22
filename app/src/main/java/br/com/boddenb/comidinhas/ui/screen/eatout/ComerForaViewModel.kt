package br.com.boddenb.comidinhas.ui.screen.eatout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.boddenb.comidinhas.data.logger.AppLogger
import br.com.boddenb.comidinhas.domain.config.AppConstants
import br.com.boddenb.comidinhas.domain.model.LatLng
import br.com.boddenb.comidinhas.domain.model.OrderBy
import br.com.boddenb.comidinhas.domain.model.Restaurant
import br.com.boddenb.comidinhas.domain.usecase.GetNearbyRestaurantsUseCase
import br.com.boddenb.comidinhas.domain.usecase.GetCurrentLocationUseCase
import br.com.boddenb.comidinhas.domain.usecase.FilterRestaurantsByFoodUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ComerForaUiState {
    data object Loading : ComerForaUiState
    data class Success(
        val items: List<Restaurant>,
        val userLocation: LatLng,
        val isMapMode: Boolean,
        val radiusMeters: Int,
        val orderBy: OrderBy,
        val foodQuery: String = ""
    ) : ComerForaUiState
    data class Error(val message: String) : ComerForaUiState
    data class Empty(val userLocation: LatLng, val radiusMeters: Int, val orderBy: OrderBy) : ComerForaUiState
}

sealed interface ComerForaEvent {
    data object ToggleMap : ComerForaEvent
    data object Refresh : ComerForaEvent
    data class ChangeRadius(val meters: Int) : ComerForaEvent
    data class ChangeOrderBy(val orderBy: OrderBy) : ComerForaEvent
    data class SearchFood(val query: String) : ComerForaEvent
}

@HiltViewModel
class ComerForaViewModel @Inject constructor(
    private val getCurrentLocationUseCase: GetCurrentLocationUseCase,
    private val getNearbyRestaurantsUseCase: GetNearbyRestaurantsUseCase,
    private val filterRestaurantsByFoodUseCase: FilterRestaurantsByFoodUseCase
) : ViewModel() {

    companion object {
        private const val TAG = "ComerForaVM"
    }

    private val _state = MutableStateFlow<ComerForaUiState>(ComerForaUiState.Loading)
    val state: StateFlow<ComerForaUiState> = _state.asStateFlow()

    private var currentLocation: LatLng? = null
    private var isMapMode: Boolean = false
    private var radiusMeters: Int = AppConstants.DEFAULT_RADIUS_METERS
    private var orderBy: OrderBy = OrderBy.RATING
    private var foodQuery: String = ""

    private var cachedRestaurants: List<Restaurant> = emptyList()

    private var lastSearchLocation: LatLng? = null
    private var lastSearchRadius: Int = 0
    private var lastSearchOrderBy: OrderBy? = null

    init {
        refresh()
    }

    fun onEvent(event: ComerForaEvent) {
        when (event) {
            ComerForaEvent.ToggleMap -> {
                isMapMode = !isMapMode
                val s = _state.value
                if (s is ComerForaUiState.Success) _state.value = s.copy(isMapMode = isMapMode)
            }
            ComerForaEvent.Refresh -> {
                refresh()
            }
            is ComerForaEvent.ChangeRadius -> {
                radiusMeters = event.meters
                refresh()
            }
            is ComerForaEvent.ChangeOrderBy -> {
                orderBy = event.orderBy
                refresh()
            }
            is ComerForaEvent.SearchFood -> {
                foodQuery = event.query
                refresh()
            }
        }
    }

    private fun refresh() {
        viewModelScope.launch {
            _state.value = ComerForaUiState.Loading
            try {
                currentLocation = getCurrentLocationUseCase()

                val allRestaurants = fetchRestaurants()
                cachedRestaurants = allRestaurants

                val filteredRestaurants = applyFoodFilter(allRestaurants)

                updateUiState(filteredRestaurants)
            } catch (e: Exception) {
                AppLogger.e(TAG, "Error in refresh(): ${e.message}", e)
                _state.value = ComerForaUiState.Error(e.message ?: "Falha ao obter restaurantes")
            }
        }
    }

    private suspend fun fetchRestaurants(): List<Restaurant> {
        val needsNewSearch = lastSearchLocation != currentLocation ||
                            lastSearchRadius != radiusMeters ||
                            lastSearchOrderBy != orderBy ||
                            cachedRestaurants.isEmpty()

        return if (needsNewSearch) {
            AppLogger.d(TAG, "🔄 Buscando novos restaurantes (raio=${radiusMeters}m)")
            val fetched = getNearbyRestaurantsUseCase(
                center = currentLocation!!,
                radiusMeters = radiusMeters,
                orderBy = orderBy,
                includedTypes = listOf("restaurant")
            )
            lastSearchLocation = currentLocation
            lastSearchRadius = radiusMeters
            lastSearchOrderBy = orderBy
            AppLogger.d(TAG, "✅ ${fetched.size} restaurantes encontrados")
            fetched
        } else {
            AppLogger.d(TAG, "📦 Usando cache: ${cachedRestaurants.size} restaurantes")
            cachedRestaurants
        }
    }

    private suspend fun applyFoodFilter(restaurants: List<Restaurant>): List<Restaurant> {
        return filterRestaurantsByFoodUseCase(restaurants, foodQuery)
    }

    private fun updateUiState(restaurants: List<Restaurant>) {
        _state.value = if (restaurants.isEmpty()) {
            ComerForaUiState.Empty(currentLocation!!, radiusMeters, orderBy)
        } else {
            ComerForaUiState.Success(
                items = restaurants,
                userLocation = currentLocation!!,
                isMapMode = isMapMode,
                radiusMeters = radiusMeters,
                orderBy = orderBy,
                foodQuery = foodQuery
            )
        }
    }


    fun findById(id: String): Restaurant? {
        return cachedRestaurants.firstOrNull { it.id == id }
    }
}
