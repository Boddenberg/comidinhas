package br.com.boddenb.comidinhas.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class RecipeSearchRequest(val query: String)

@Serializable
data class RecipeSearchResponse(
    val query: String,
    val results: List<RecipeItem>,
    val errorMessage: String? = null  // Mensagem de erro para termos inválidos
)

@Serializable
data class RecipeItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val ingredients: List<String>,
    val instructions: List<String>,
    val imageUrl: String? = null,
    val cookingTime: String? = null,
    val servings: String? = null
)

/**
 * Modelo de domínio para receitas persistidas.
 *
 * Representa uma receita independente da camada de dados (AWS, cache, etc).
 * Usado para operações de persistência e recuperação.
 */
data class Recipe(
    val id: String,
    val name: String,
    val ingredients: List<String>,
    val instructions: List<String>,
    val imageUrl: String,
    val cookingTime: String?,
    val servings: String?
)

