package br.com.boddenb.comidinhas.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import br.com.boddenb.comidinhas.domain.model.RecipeItem

class AppViewModel : ViewModel() {
    var selectedRecipe by mutableStateOf<RecipeItem?>(null)
        private set

    fun onRecipeSelected(recipe: RecipeItem) {
        selectedRecipe = recipe
    }
}
