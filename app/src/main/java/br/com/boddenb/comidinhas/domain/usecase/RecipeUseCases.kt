package br.com.boddenb.comidinhas.domain.usecase

import android.graphics.Bitmap
import br.com.boddenb.comidinhas.data.model.recipe.RecipeEntity
import br.com.boddenb.comidinhas.data.repository.RecipeRepository
import br.com.boddenb.comidinhas.domain.model.Recipe
import java.util.UUID
import javax.inject.Inject

class SaveRecipeUseCase @Inject constructor(
    private val recipeRepository: RecipeRepository
) {

    /**
     * Salva uma receita completa (dados + imagem)
     * @param recipe Receita do domain
     * @param originalImageUrl URL original da imagem (será copiada para S3)
     * @param searchQuery Query de busca original
     */
    suspend operator fun invoke(
        recipe: Recipe,
        originalImageUrl: String?,
        searchQuery: String
    ): Result<Unit> {
        try {
            val recipeId = recipe.id.ifEmpty { UUID.randomUUID().toString() }

            // Se tem imagem, faz upload para S3
            val uploadedUrl = if (!originalImageUrl.isNullOrEmpty()) {
                recipeRepository.uploadRecipeImageFromUrl(originalImageUrl, recipeId).getOrNull()
            } else null

            // Salva metadados no DynamoDB
            val entity = RecipeEntity(
                id = recipeId,
                name = recipe.name,
                ingredients = recipe.ingredients,
                instructions = recipe.instructions,
                imageUrl = uploadedUrl ?: originalImageUrl,
                cookingTime = recipe.cookingTime,
                servings = recipe.servings,
                searchQuery = searchQuery.lowercase()
            )

            return recipeRepository.saveRecipe(entity)
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    /**
     * Salva receita com bitmap (imagem local)
     */
    suspend fun invoke(
        recipe: Recipe,
        imageBitmap: Bitmap?,
        searchQuery: String
    ): Result<Unit> {
        try {
            val recipeId = recipe.id.ifEmpty { UUID.randomUUID().toString() }

            val uploadedUrl = if (imageBitmap != null) {
                recipeRepository.uploadRecipeImage(imageBitmap, recipeId).getOrNull()
            } else null

            val entity = RecipeEntity(
                id = recipeId,
                name = recipe.name,
                ingredients = recipe.ingredients,
                instructions = recipe.instructions,
                imageUrl = uploadedUrl,
                cookingTime = recipe.cookingTime,
                servings = recipe.servings,
                searchQuery = searchQuery.lowercase()
            )

            return recipeRepository.saveRecipe(entity)
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }
}

class GetRecipesUseCase @Inject constructor(
    private val recipeRepository: RecipeRepository
) {

    suspend operator fun invoke(): Result<List<Recipe>> {
        return recipeRepository.getAllRecipes().map { it.map { e -> e.toDomain() } }
    }

    suspend fun getByQuery(query: String): Result<List<Recipe>> {
        return recipeRepository.getRecipesByQuery(query).map { it.map { e -> e.toDomain() } }
    }
}

class DeleteRecipeUseCase @Inject constructor(
    private val recipeRepository: RecipeRepository
) {

    /**
     * Deleta uma receita usando apenas o recipeId (chave primária simples)
     */
    suspend operator fun invoke(recipeId: String): Result<Unit> {
        // Deleta imagem do S3
        recipeRepository.deleteRecipeImage(recipeId)

        // Deleta do DynamoDB usando apenas id
        return recipeRepository.deleteRecipe(recipeId)
    }
}
