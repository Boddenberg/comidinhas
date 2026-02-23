package br.com.boddenb.comidinhas.data.model.recipe

import br.com.boddenb.comidinhas.domain.model.Recipe
import kotlinx.serialization.Serializable

@Serializable
data class RecipeEntity(
    val id: String,
    val name: String,
    val ingredients: List<String>,
    val instructions: List<String>,
    val imageUrl: String? = null,
    val servings: String? = null,
    val searchQuery: String? = null,
    val cookingTime: String? = null,
    val createdAt: Long? = null,
    val source: String? = null
) {
    fun toDomain(): Recipe = Recipe(
        id = id,
        name = name,
        ingredients = ingredients,
        instructions = instructions,
        imageUrl = imageUrl ?: "",
        cookingTime = cookingTime,
        servings = servings
    )
}
