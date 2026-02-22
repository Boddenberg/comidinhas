package br.com.boddenb.comidinhas.ui.screen.home

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.boddenb.comidinhas.data.remote.OpenAiClient
import br.com.boddenb.comidinhas.domain.model.Recipe
import br.com.boddenb.comidinhas.domain.model.RecipeItem
import br.com.boddenb.comidinhas.domain.usecase.SaveRecipeUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SearchMode {
    PREPARAR,
    DELIVERY,
    OUT
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val openAiClient: OpenAiClient,
    private val saveRecipeUseCase: SaveRecipeUseCase
) : ViewModel() {

    var searchQuery by mutableStateOf("")
        private set

    /** Termo exibido na UI após a busca — pode ser diferente do digitado se houve correção. */
    var displayQuery by mutableStateOf("")
        private set

    var recipes by mutableStateOf<List<RecipeItem>>(emptyList())
        private set

    var isLoading by mutableStateOf(false)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    var showModeSelection by mutableStateOf(false)
        private set

    var selectedMode by mutableStateOf<SearchMode?>(null)
        private set

    fun onSearchQueryChange(query: String) {
        searchQuery = query
    }

    fun requestSearch() {
        if (searchQuery.isBlank()) return
        if (selectedMode != null) {
            performSearch(selectedMode!!)
        } else {
            showModeSelection = true
        }
    }

    fun openModeSelection() {
        showModeSelection = true
    }

    fun performSearch(mode: SearchMode = SearchMode.PREPARAR) {
        if (searchQuery.isBlank()) return

        selectedMode = mode
        showModeSelection = false

        when (mode) {
            SearchMode.PREPARAR -> {
                viewModelScope.launch {
                    isLoading = true
                    errorMessage = null
                    try {
                        val response = openAiClient.searchRecipes(searchQuery)

                        // Verifica se há mensagem de erro (termo inválido)
                        if (response.errorMessage != null) {
                            errorMessage = response.errorMessage
                            recipes = emptyList()
                            return@launch
                        }

                        // Atualiza displayQuery com o termo corrigido retornado pela API
                        displayQuery = response.query
                            .trim()
                            .replaceFirstChar { it.uppercase() }

                        recipes = response.results

                        saveRecipesAutomatically(response.results)

                    } catch (e: Exception) {
                        errorMessage = "Erro ao buscar receitas: ${e.message}"
                    } finally {
                        isLoading = false
                    }
                }
            }
            SearchMode.DELIVERY -> {
                showModeSelection = false
                selectedMode = null
            }
            SearchMode.OUT -> {
                showModeSelection = false
            }
        }
    }

    private fun saveRecipesAutomatically(recipeItems: List<RecipeItem>) {
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
                        searchQuery = searchQuery.lowercase()
                    ).onSuccess {
                        Log.d("HomeViewModel", "✅ Receita salva automaticamente: ${recipe.name}")
                    }.onFailure { error ->
                        Log.w("HomeViewModel", "⚠️ Erro ao salvar receita (silencioso): ${error.message}")
                    }
                } catch (e: Exception) {
                    Log.w("HomeViewModel", "⚠️ Erro ao processar receita: ${e.message}")
                }
            }
        }
    }

    fun hideModeSelection() {
        showModeSelection = false
    }

    fun clearSearchOnly() {
        searchQuery = ""
        displayQuery = ""
        recipes = emptyList()
        errorMessage = null
        isLoading = false
        showModeSelection = false
    }

    fun clearSearch() {
        searchQuery = ""
        displayQuery = ""
        recipes = emptyList()
        errorMessage = null
        isLoading = false
        selectedMode = null
        showModeSelection = false
    }

    fun dismissError() {
        clearSearchOnly()
    }
}

