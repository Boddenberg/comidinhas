package br.com.boddenb.comidinhas.ui.screen.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.boddenb.comidinhas.domain.model.RecipeItem
import br.com.boddenb.comidinhas.domain.model.SearchMode
import br.com.boddenb.comidinhas.domain.usecase.SaveRecipeUseCase
import br.com.boddenb.comidinhas.domain.usecase.SearchRecipesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import br.com.boddenb.comidinhas.domain.model.Recipe

data class HomeUiState(
    val searchQuery: String = "",
    val displayQuery: String = "",
    val recipes: List<RecipeItem> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val showModeSelection: Boolean = false,
    val selectedMode: SearchMode? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val searchRecipesUseCase: SearchRecipesUseCase,
    private val saveRecipeUseCase: SaveRecipeUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    fun onSearchQueryChange(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun requestSearch() {
        val state = _uiState.value
        if (state.searchQuery.isBlank()) return
        if (state.selectedMode != null) {
            performSearch(state.selectedMode)
        } else {
            _uiState.update { it.copy(showModeSelection = true) }
        }
    }


    fun hideModeSelection() {
        _uiState.update { it.copy(showModeSelection = false) }
    }

    fun performSearch(mode: SearchMode = SearchMode.PREPARAR) {
        val query = _uiState.value.searchQuery
        if (query.isBlank()) return

        _uiState.update { it.copy(selectedMode = mode, showModeSelection = false) }

        when (mode) {
            SearchMode.PREPARAR -> launchRecipeSearch(query)
            SearchMode.DELIVERY -> {
                _uiState.update { it.copy(selectedMode = null) }
            }
            SearchMode.OUT -> Unit
        }
    }

    private fun launchRecipeSearch(query: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val response = searchRecipesUseCase(query)

                if (response.errorMessage != null) {
                    _uiState.update { it.copy(isLoading = false, errorMessage = response.errorMessage, recipes = emptyList()) }
                    return@launch
                }

                val displayQuery = response.query.trim().replaceFirstChar { it.uppercase() }
                _uiState.update { it.copy(isLoading = false, displayQuery = displayQuery, recipes = response.results) }

                saveRecipesAutomatically(response.results, query)
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = "Erro ao buscar receitas: ${e.message}") }
            }
        }
    }

    private fun saveRecipesAutomatically(recipeItems: List<RecipeItem>, query: String) {
        viewModelScope.launch {
            recipeItems.forEach { recipeItem ->
                try {
                    val recipe = Recipe(
                        id = recipeItem.id,
                        name = recipeItem.name,
                        ingredients = recipeItem.ingredients,
                        instructions = recipeItem.instructions,
                        imageUrl = recipeItem.imageUrl ?: "",
                        cookingTime = recipeItem.cookingTime,
                        servings = recipeItem.servings
                    )
                    saveRecipeUseCase(
                        recipe = recipe,
                        originalImageUrl = recipeItem.imageUrl,
                        searchQuery = query.lowercase()
                    ).onSuccess {
                        Log.d("HomeViewModel", "✅ Receita salva: ${recipe.name}")
                    }.onFailure { error ->
                        Log.w("HomeViewModel", "⚠️ Erro ao salvar receita: ${error.message}")
                    }
                } catch (e: Exception) {
                    Log.w("HomeViewModel", "⚠️ Erro inesperado ao salvar: ${e.message}")
                }
            }
        }
    }

    fun clearSearchOnly() {
        _uiState.update { it.copy(searchQuery = "", displayQuery = "", recipes = emptyList(), errorMessage = null, isLoading = false, showModeSelection = false) }
    }

    fun clearSearch() {
        _uiState.update { HomeUiState() }
    }

    fun dismissError() {
        clearSearchOnly()
    }
}
