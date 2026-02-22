package br.com.boddenb.comidinhas.data.error

import android.util.Log
import br.com.boddenb.comidinhas.domain.model.RecipeSearchResponse

/**
 * Converte erros da OpenAI em [RecipeSearchResponse] com errorMessage preenchido.
 * A UI decide como exibir o erro — nenhum item fake é criado aqui.
 */
object OpenAiErrorHandler {

    private const val TAG = "OpenAiErrorHandler"

    fun handleRecipeSearchError(query: String, status: Int, message: String?): RecipeSearchResponse {
        Log.e(TAG, "Erro HTTP da OpenAI: $status - $message")
        val errorMsg = when (status) {
            401 -> "Chave da OpenAI inválida. Verifique OPENAI_API_KEY em local.properties."
            429 -> "Limite de uso da OpenAI excedido. Tente novamente mais tarde."
            else -> "Erro ao buscar receitas (HTTP $status). Tente novamente."
        }
        return RecipeSearchResponse(query = query, results = emptyList(), errorMessage = errorMsg)
    }

    fun handleParsingError(query: String, error: Throwable): RecipeSearchResponse {
        Log.e(TAG, "Falha ao decodificar resposta da OpenAI: ${error.message}")
        return RecipeSearchResponse(
            query = query,
            results = emptyList(),
            errorMessage = "Não foi possível processar a resposta. Tente novamente."
        )
    }

    fun handleEmptyResponse(query: String, raw: String): RecipeSearchResponse {
        Log.e(TAG, "Resposta vazia da OpenAI. Amostra: ${raw.take(200)}")
        return RecipeSearchResponse(
            query = query,
            results = emptyList(),
            errorMessage = "Resposta vazia da OpenAI. Tente novamente."
        )
    }
}
