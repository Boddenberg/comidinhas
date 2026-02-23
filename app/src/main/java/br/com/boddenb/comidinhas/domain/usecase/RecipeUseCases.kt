package br.com.boddenb.comidinhas.domain.usecase

import android.graphics.Bitmap
import br.com.boddenb.comidinhas.data.model.recipe.RecipeEntity
import br.com.boddenb.comidinhas.domain.repository.RecipeRepository
import br.com.boddenb.comidinhas.domain.model.Recipe
import java.util.UUID
import javax.inject.Inject

class SaveRecipeUseCase @Inject constructor(
    private val recipeRepository: RecipeRepository
) {
    /** Salva uma receita com upload de imagem a partir de URL externa.
     *  Retorna a URL do Supabase Storage em caso de sucesso, falha caso a imagem seja inválida. */
    suspend operator fun invoke(
        recipe: Recipe,
        originalImageUrl: String?,
        searchQuery: String
    ): Result<String> = runCatching {
        val recipeId = recipe.id.ifEmpty { UUID.randomUUID().toString() }
        val uploadedUrl = if (!originalImageUrl.isNullOrEmpty()) {
            recipeRepository.uploadRecipeImageFromUrl(originalImageUrl, recipeId).getOrNull()
        } else null

        if (uploadedUrl == null) {
            throw Exception("Imagem indisponível para \"${recipe.name}\" — receita descartada")
        }

        recipeRepository.saveRecipe(buildEntity(recipe, recipeId, uploadedUrl, searchQuery)).getOrThrow()
        uploadedUrl
    }

    /** Salva uma receita com bitmap de imagem local. */
    suspend fun invoke(
        recipe: Recipe,
        imageBitmap: Bitmap?,
        searchQuery: String
    ): Result<Unit> = runCatching {
        val recipeId = recipe.id.ifEmpty { UUID.randomUUID().toString() }
        val uploadedUrl = if (imageBitmap != null) {
            recipeRepository.uploadRecipeImage(imageBitmap, recipeId).getOrNull()
        } else null
        recipeRepository.saveRecipe(buildEntity(recipe, recipeId, uploadedUrl, searchQuery)).getOrThrow()
    }

    private fun buildEntity(
        recipe: Recipe,
        recipeId: String,
        imageUrl: String?,
        searchQuery: String
    ) = RecipeEntity(
        id = recipeId,
        name = recipe.name,
        ingredients = recipe.ingredients,
        instructions = recipe.instructions,
        imageUrl = imageUrl,
        servings = recipe.servings,
        searchQuery = searchQuery.lowercase()
    )
}

class GetRecipesUseCase @Inject constructor(
    private val recipeRepository: RecipeRepository
) {
    suspend operator fun invoke(): Result<List<Recipe>> =
        recipeRepository.getAllRecipes().map { it.map { e -> e.toDomain() } }
}

class DeleteRecipeUseCase @Inject constructor(
    private val recipeRepository: RecipeRepository
) {
    suspend operator fun invoke(recipeId: String): Result<Unit> {
        recipeRepository.deleteRecipeImage(recipeId)
        return recipeRepository.deleteRecipe(recipeId)
    }
}
