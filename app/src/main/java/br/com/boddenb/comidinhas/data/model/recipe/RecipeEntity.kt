package br.com.boddenb.comidinhas.data.model.recipe

import br.com.boddenb.comidinhas.domain.model.Recipe
import kotlinx.serialization.Serializable

/**
 * Entidade de dados para receitas armazenadas no DynamoDB.
 *
 * Representa o mapeamento direto de uma receita persistida no AWS DynamoDB,
 * incluindo metadados técnicos necessários para auditoria e rastreabilidade.
 *
 * @property id Identificador único (UUID) da receita
 * @property name Nome da receita (ex: "Lasanha Bolonhesa")
 * @property ingredients Lista ordenada de ingredientes necessários
 * @property instructions Lista ordenada de passos de preparo
 * @property imageUrl URL completa da imagem armazenada no S3 (nullable)
 * @property cookingTime Tempo estimado de preparo formatado (ex: "45 min")
 * @property servings Número de porções formatado (ex: "4 porções")
 * @property searchQuery Query original que gerou esta receita (para analytics)
 * @property createdAt Timestamp Unix (milliseconds) de criação
 * @property source Sistema de origem da receita (padrão: "OpenAI")
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
    companion object {
        private const val SOURCE_OPENAI = "OpenAI"
        private const val EMPTY_IMAGE_URL = ""
    }

    /**
     * Converte esta entidade de dados para o modelo de domínio.
     *
     * Remove campos técnicos (createdAt, source, searchQuery) e
     * garante que imageUrl nunca seja null no domínio.
     *
     * @return [Recipe] modelo de domínio puro
     */
    fun toDomain(): Recipe = Recipe(
        id = id,
        name = name,
        ingredients = ingredients,
        instructions = instructions,
        imageUrl = imageUrl ?: EMPTY_IMAGE_URL,
        cookingTime = cookingTime,
        servings = servings
    )
}
