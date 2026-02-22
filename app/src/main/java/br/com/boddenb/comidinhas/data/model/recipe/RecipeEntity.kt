package br.com.boddenb.comidinhas.data.model.recipe

import br.com.boddenb.comidinhas.domain.model.Recipe
import kotlinx.serialization.Serializable

/**
 * Entidade de dados para receitas armazenadas no Supabase.
 *
 * DTO (Data Transfer Object) entre a aplicação e o banco de dados.
 * Não deve conter lógica de negócio — apenas estrutura de dados e mapeamento básico.
 */
@Serializable
data class RecipeEntity(
    val id: String,
    val name: String,
    val ingredients: List<String>,
    val instructions: List<String>,
    val imageUrl: String? = null,
    val cookingTime: String? = null,
    val servings: String? = null,
    val searchQuery: String? = null,
    val createdAt: Long? = null,
    val source: String? = null
) {
    /**
     * Converte esta entidade para o modelo de domínio.
     * imageUrl vazio string indica ausência de imagem no domínio.
     */
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
