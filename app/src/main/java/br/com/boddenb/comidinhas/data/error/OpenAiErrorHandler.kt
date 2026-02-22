package br.com.boddenb.comidinhas.data.error

import android.util.Log
import br.com.boddenb.comidinhas.domain.model.RecipeItem
import br.com.boddenb.comidinhas.domain.model.RecipeSearchResponse

object OpenAiErrorHandler {

    private const val TAG = "OpenAiErrorHandler"

    fun handleRecipeSearchError(
        query: String,
        status: Int,
        message: String?
    ): RecipeSearchResponse {
        Log.e(TAG, "❌ Erro HTTP da OpenAI: $status - $message")

        return when (status) {
            401 -> RecipeSearchResponse(
                query = query,
                results = listOf(
                    RecipeItem(
                        name = "Chave da OpenAI inválida",
                        ingredients = listOf("Verifique OPENAI_API_KEY em local.properties"),
                        instructions = listOf("Detalhe: ${message ?: "401 Unauthorized"}")
                    )
                )
            )
            429 -> RecipeSearchResponse(
                query = query,
                results = listOf(
                    RecipeItem(
                        name = "Limite da OpenAI excedido (429)",
                        ingredients = listOf("Ative o billing ou adicione créditos."),
                        instructions = listOf("Use o RecipesApiMock na HomeScreen.")
                    )
                )
            )
            else -> RecipeSearchResponse(
                query = query,
                results = listOf(
                    RecipeItem(
                        name = "Erro ao gerar receitas",
                        ingredients = listOf("Houve um problema ao chamar a API da OpenAI."),
                        instructions = listOf("HTTP $status: ${message ?: "Erro desconhecido"}")
                    )
                )
            )
        }
    }

    fun handleParsingError(query: String, error: Throwable, payload: String): RecipeSearchResponse {
        Log.e(TAG, "Falha ao decodificar JSON: ${error.message}")

        return RecipeSearchResponse(
            query = query,
            results = listOf(
                RecipeItem(
                    name = "Falha ao decodificar o JSON de receitas",
                    ingredients = listOf("JSON recebido nao bate com o schema esperado."),
                    instructions = listOf("Erro de parsing: ${error.message}", "Conteudo: ${payload.take(400)}")
                )
            )
        )
    }

    fun handleEmptyResponse(query: String, raw: String): RecipeSearchResponse {
        Log.e(TAG, "Nao consegui extrair texto da resposta")

        return RecipeSearchResponse(
            query = query,
            results = listOf(
                RecipeItem(
                    name = "Não consegui extrair a resposta da OpenAI",
                    ingredients = listOf("O corpo veio sem output_text nem content.text."),
                    instructions = listOf("Amostra bruta: ${raw.take(400)}")
                )
            )
        )
    }
}

