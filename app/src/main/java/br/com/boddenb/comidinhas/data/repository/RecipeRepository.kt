package br.com.boddenb.comidinhas.data.repository

import android.graphics.Bitmap
import br.com.boddenb.comidinhas.data.model.recipe.RecipeEntity

/**
 * Contrato genérico para persistência e recuperação de receitas.
 * Permite trocar implementação (Supabase, AWS, etc.) sem impactar camadas superiores.
 */
interface RecipeRepository {
    suspend fun saveRecipe(recipe: RecipeEntity): Result<Unit>
    suspend fun getAllRecipes(): Result<List<RecipeEntity>>
    suspend fun getRecipesByQuery(query: String): Result<List<RecipeEntity>>
    suspend fun deleteRecipe(recipeId: String): Result<Unit>
    suspend fun uploadRecipeImage(bitmap: Bitmap, recipeId: String): Result<String>
    suspend fun uploadRecipeImageFromUrl(imageUrl: String, recipeId: String): Result<String>
    suspend fun imageExists(recipeId: String): Boolean
    suspend fun recipeExists(recipeId: String): Boolean
    suspend fun deleteRecipeImage(recipeId: String): Result<Unit>
}
