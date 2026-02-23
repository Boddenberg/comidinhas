package br.com.boddenb.comidinhas.ui.screen.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.boddenb.comidinhas.data.logger.AppLogger
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
    val selectedMode: SearchMode? = null,
    val isGeneric: Boolean = false,
    val featuredRecipeId: String? = null
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

        when (mode) {
            SearchMode.PREPARAR -> {
                _uiState.update { it.copy(selectedMode = mode, showModeSelection = false) }
                launchRecipeSearch(query)
            }
            SearchMode.DELIVERY -> {
                // Delivery ainda não implementado — não persiste modo
                _uiState.update { it.copy(showModeSelection = false) }
            }
            SearchMode.OUT -> {
                // Não persiste o modo OUT: na próxima busca o modal deve aparecer novamente
                _uiState.update { it.copy(showModeSelection = false) }
            }
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

                // Deduplicate by imageUrl before any save — keep first occurrence (lower index = higher priority)
                val preFilterDiscarded = mutableListOf<AppLogger.DiscardedRecipe>()
                val seenImageUrls = mutableSetOf<String>()
                val deduplicatedCandidates = response.results.filter { item ->
                    val url = item.imageUrl
                    if (url.isNullOrBlank()) {
                        true // no image yet — let save validate
                    } else if (url in seenImageUrls) {
                        preFilterDiscarded.add(AppLogger.DiscardedRecipe(item.name, "Imagem duplicada (mesma URL já usada por outra receita)"))
                        AppLogger.w(AppLogger.BUSCA, "🗑️ [PRÉ-FILTRO] \"${item.name}\" descartada — imagem duplicada: $url")
                        false
                    } else {
                        seenImageUrls.add(url)
                        true
                    }
                }

                // Run saves first — keep isLoading=true so no flash
                val accepted = mutableListOf<RecipeItem>()
                val saveDiscarded = mutableListOf<AppLogger.DiscardedRecipe>()

                deduplicatedCandidates.forEach { recipeItem ->
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
                        ).onSuccess { supabaseUrl ->
                            Log.d("HomeViewModel", "✅ Receita salva: ${recipe.name}")
                            accepted.add(recipeItem.copy(imageUrl = supabaseUrl))
                        }.onFailure { error ->
                            val reason = error.message ?: "Falha desconhecida no save"
                            Log.w("HomeViewModel", "⚠️ Descartando \"${recipe.name}\": $reason")
                            saveDiscarded.add(AppLogger.DiscardedRecipe(recipeItem.name, reason))
                        }
                    } catch (e: Exception) {
                        val reason = "Exceção inesperada: ${e.message}"
                        Log.w("HomeViewModel", "⚠️ Erro inesperado ao salvar \"${recipeItem.name}\": ${reason}")
                        saveDiscarded.add(AppLogger.DiscardedRecipe(recipeItem.name, reason))
                    }
                }

                val allDiscarded = preFilterDiscarded + saveDiscarded

                if (allDiscarded.isNotEmpty()) {
                    AppLogger.w(AppLogger.BUSCA, "╔══════════════════════════════════════════════╗")
                    AppLogger.w(AppLogger.BUSCA, "║  🗑️ RECEITAS DESCARTADAS: ${allDiscarded.size}")
                    allDiscarded.forEachIndexed { i, d ->
                        AppLogger.w(AppLogger.BUSCA, "║  [D${i+1}] \"${d.name}\"")
                        AppLogger.w(AppLogger.BUSCA, "║       Motivo: ${d.reason}")
                    }
                    AppLogger.w(AppLogger.BUSCA, "╚══════════════════════════════════════════════╝")
                }

                // Recalculate featuredRecipeId from accepted list — original ID may have been discarded
                val acceptedIds = accepted.map { it.id }.toSet()
                val recalculatedFeaturedId = when {
                    response.isGeneric || response.featuredRecipeId == null -> null
                    response.featuredRecipeId in acceptedIds -> response.featuredRecipeId
                    else -> {
                        // Featured was discarded — pick first accepted that matches the query
                        val q = query.lowercase().trim()
                        accepted.firstOrNull { it.name.lowercase().contains(q) }?.id
                            ?: accepted.firstOrNull()?.id
                    }
                }

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        displayQuery = displayQuery,
                        recipes = accepted,
                        isGeneric = response.isGeneric,
                        featuredRecipeId = recalculatedFeaturedId
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = "Erro ao buscar receitas: ${e.message}") }
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
