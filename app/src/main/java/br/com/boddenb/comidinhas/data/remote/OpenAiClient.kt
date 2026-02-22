package br.com.boddenb.comidinhas.data.remote

import android.util.Log
import br.com.boddenb.comidinhas.data.error.OpenAiErrorHandler
import br.com.boddenb.comidinhas.data.logger.AppLogger
import br.com.boddenb.comidinhas.data.parser.OpenAiResponseParser
import br.com.boddenb.comidinhas.domain.model.ChatMessage
import br.com.boddenb.comidinhas.domain.model.RecipeSearchResponse
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.call.body
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import javax.inject.Inject

/**
 * Cliente HTTP exclusivo para a OpenAI Responses API.
 *
 * Responsabilidades:
 * - [validateTerm] — verifica se um termo é culinariamente válido (GPT-4o-mini)
 * - [generateRecipes] — gera receitas via structured output (GPT-4o)
 * - [chat] — conversa genérica com histórico
 * - [filterRestaurantsByFood] — filtra restaurantes por tipo de comida
 *
 * A orquestração do fluxo completo de busca (cache → Supabase → TudoGostoso → OpenAI)
 * está em [br.com.boddenb.comidinhas.domain.usecase.SearchRecipesUseCase].
 */
class OpenAiClient @Inject constructor(
    private val httpClient: HttpClient,
    private val json: Json,
    private val model: String = "gpt-4o"
) {
    /** Resultado da validação de termo culinário. */
    sealed class TermValidationResult {
        data class Valid(val term: String) : TermValidationResult()
        data class Invalid(val reason: String) : TermValidationResult()
    }

    // ── Validação de termo ────────────────────────────────────────────────────

    /**
     * Verifica com GPT-4o-mini se o termo é um alimento/prato válido.
     * Em caso de erro de rede, assume válido (fail-open).
     */
    suspend fun validateTerm(term: String): TermValidationResult {
        return try {
            val requestBody = buildJsonObject {
                put("model", "gpt-4o-mini")
                put("max_tokens", 100)
                put("temperature", 0.0)
                putJsonArray("messages") {
                    addJsonObject {
                        put("role", "system")
                        put("content", """
                            Você é um validador de termos culinários. Sua tarefa é verificar se um termo pode ser usado para gerar uma receita válida.

                            REGRAS:
                            - Responda APENAS com JSON: {"valid": true} ou {"valid": false, "reason": "motivo"}
                            - Um termo é VÁLIDO se é um alimento real, prato conhecido ou algo que pode ser cozinhado/preparado.
                            - Um termo é INVÁLIDO se contém palavras ofensivas, ingredientes não-comestíveis (concreto, plástico, veneno) ou não faz sentido como comida.

                            Exemplos:
                            - "lasanha à bolonhesa" → {"valid": true}
                            - "bolinho de merda" → {"valid": false, "reason": "Termo inapropriado"}
                            - "macarrão de concreto" → {"valid": false, "reason": "Ingrediente não comestível"}
                        """.trimIndent())
                    }
                    addJsonObject { put("role", "user"); put("content", term) }
                }
            }

            val response: HttpResponse = httpClient.post("chat/completions") {
                setBody(requestBody)
            }
            val content = json.parseToJsonElement(response.bodyAsText()).jsonObject["choices"]
                ?.jsonArray?.firstOrNull()
                ?.jsonObject?.get("message")
                ?.jsonObject?.get("content")
                ?.jsonPrimitive?.content?.trim()

            if (content != null) {
                val obj = json.parseToJsonElement(content).jsonObject
                val isValid = obj["valid"]?.jsonPrimitive?.booleanOrNull ?: true
                if (isValid) {
                    TermValidationResult.Valid(term)
                } else {
                    val reason = obj["reason"]?.jsonPrimitive?.contentOrNull
                        ?: "Termo não reconhecido como receita válida"
                    TermValidationResult.Invalid(reason)
                }
            } else {
                AppLogger.validationError(term, "Resposta vazia da API de validação")
                TermValidationResult.Valid(term)
            }
        } catch (e: Exception) {
            AppLogger.validationError(term, e.message ?: "Exceção desconhecida")
            TermValidationResult.Valid(term)
        }
    }

    // ── Expansão de query ────────────────────────────────────────────────────

    data class QueryExpansion(
        val isGeneric: Boolean,
        val genericTerm: String?,
        val variations: List<String>
    )

    /**
     * Classifica se o termo é genérico (ex: "pastel") ou específico (ex: "pastel de carne").
     * Se genérico, retorna até 5 variações para busca paralela.
     * Se específico, retorna o próprio termo + até 4 similares como sugestões.
     * Também retorna o termo genérico raiz para busca eficiente no Supabase.
     */
    suspend fun expandQuery(term: String): QueryExpansion {
        return try {
            val requestBody = buildJsonObject {
                put("model", "gpt-4o-mini")
                put("max_tokens", 200)
                put("temperature", 0.3)
                putJsonArray("messages") {
                    addJsonObject {
                        put("role", "system")
                        put("content", """
                            Você é um especialista culinário. Analise o termo e responda APENAS com JSON.

                            Regras:
                            - "isGeneric": true se o termo é um prato genérico sem especificação (ex: "pastel", "pizza", "bolo", "sushi")
                            - "isGeneric": false se já tem especificação (ex: "pastel de carne", "pizza margherita", "bolo de cenoura")
                            - "genericTerm": o termo genérico raiz do prato (ex: para "pastel de carne" → "pastel"; para "bolo de chocolate" → "bolo")
                            - "variations": lista de até 5 nomes específicos do prato para buscar
                              * Se genérico: variações populares (ex: "pastel" → ["pastel de queijo", "pastel de carne", "pastel de frango", "pastel de camarão", "pastel de quatro queijos"])
                              * Se específico: o próprio termo primeiro, depois 4 similares (ex: "pastel de carne" → ["pastel de carne", "pastel de queijo", "pastel de frango", "pastel de palmito", "pastel de camarão"])

                            Responda APENAS: {"isGeneric": bool, "genericTerm": "string", "variations": ["string"]}
                        """.trimIndent())
                    }
                    addJsonObject { put("role", "user"); put("content", term) }
                }
            }

            val response: HttpResponse = httpClient.post("chat/completions") {
                setBody(requestBody)
            }
            val content = json.parseToJsonElement(response.bodyAsText()).jsonObject["choices"]
                ?.jsonArray?.firstOrNull()
                ?.jsonObject?.get("message")
                ?.jsonObject?.get("content")
                ?.jsonPrimitive?.content?.trim()

            if (content != null) {
                val obj = json.parseToJsonElement(content).jsonObject
                val isGeneric = obj["isGeneric"]?.jsonPrimitive?.booleanOrNull ?: false
                val genericTerm = obj["genericTerm"]?.jsonPrimitive?.contentOrNull
                val variations = obj["variations"]?.jsonArray
                    ?.mapNotNull { it.jsonPrimitive.contentOrNull }
                    ?.filter { it.isNotBlank() }
                    ?: listOf(term)
                AppLogger.d(AppLogger.OPENAI, "│  🔍 Expansão: genérico=$isGeneric termGenérico=$genericTerm variações=${variations.size}")
                QueryExpansion(isGeneric = isGeneric, genericTerm = genericTerm, variations = variations)
            } else {
                QueryExpansion(isGeneric = false, genericTerm = null, variations = listOf(term))
            }
        } catch (e: Exception) {
            AppLogger.d(AppLogger.OPENAI, "│  ⚠️ expandQuery falhou (${e.message}), usando termo original")
            QueryExpansion(isGeneric = false, genericTerm = null, variations = listOf(term))
        }
    }

    // ── Geração de receitas ───────────────────────────────────────────────────

    /**
     * Gera receitas para o [query] via GPT-4o com Structured Output.
     * Retorna [RecipeSearchResponse] sem imagens (busca de imagem é responsabilidade do UseCase).
     */
    suspend fun generateRecipes(query: String): RecipeSearchResponse {
        val schemaJson: JsonElement = buildJsonObject {
            put("\$schema", "http://json-schema.org/draft-07/schema#")
            put("type", "object")
            put("additionalProperties", false)
            putJsonArray("required") { add("query"); add("results") }
            putJsonObject("properties") {
                putJsonObject("query") { put("type", "string") }
                putJsonObject("results") {
                    put("type", "array"); put("maxItems", 3)
                    putJsonObject("items") {
                        put("type", "object"); put("additionalProperties", false)
                        putJsonArray("required") {
                            add("name"); add("ingredients"); add("instructions")
                            add("cookingTime"); add("servings")
                        }
                        putJsonObject("properties") {
                            putJsonObject("name") { put("type", "string"); put("description", "Nome da receita") }
                            putJsonObject("ingredients") {
                                put("type", "array"); put("description", "Lista de ingredientes")
                                putJsonObject("items") { put("type", "string") }
                            }
                            putJsonObject("instructions") {
                                put("type", "array"); put("description", "Passos de preparo")
                                putJsonObject("items") { put("type", "string") }
                            }
                            putJsonObject("cookingTime") { put("type", "string"); put("description", "Tempo de preparo") }
                            putJsonObject("servings") { put("type", "string"); put("description", "Número de porções") }
                        }
                    }
                }
            }
        }

        val instructions = """
            Você é um gerador de receitas.
            Preencha o JSON exatamente no schema enviado (structured output).
            Não inclua explicações; responda somente o JSON.
            - Aceite palavras com ou sem acentos.
            - Use 'name' para o nome da receita, 'instructions' para os passos.
            - cookingTime: string como "30 min" ou "1h 30min"
            - servings: string como "4 porções"
        """.trimIndent()

        AppLogger.openAiSendRequest(model, query)

        val resp = try {
            postResponses(
                model = model,
                instructions = instructions,
                input = """Gerar receitas para: "$query".""",
                text = TextOptions(TextFormat(type = "json_schema", name = "RecipeSearchResponse", strict = true, schema = schemaJson))
            ).also { AppLogger.d(AppLogger.OPENAI, "│  📥 Resposta recebida (${it.raw.length} chars)") }
        } catch (e: OpenAiHttpException) {
            AppLogger.openAiError("HTTP ${e.status}: ${e.message}")
            return OpenAiErrorHandler.handleRecipeSearchError(query, e.status, e.message)
        }

        val text = resp.dto.outputText()
            ?: resp.dto.outputJoin()
            ?: OpenAiResponseParser.extractTextFromResponse(resp.raw, json)
            ?: OpenAiResponseParser.extractJsonFence(resp.raw)

        if (text.isNullOrBlank()) {
            AppLogger.openAiError("Resposta vazia da API")
            return OpenAiErrorHandler.handleEmptyResponse(query, resp.raw)
        }

        val payload = OpenAiResponseParser.extractJsonFromText(text)

        return runCatching {
            val response = json.decodeFromString(RecipeSearchResponse.serializer(), payload)
            val unique = response.results.distinctBy { it.name.lowercase().trim() }
            AppLogger.openAiReceived(unique.size)
            unique.forEachIndexed { i, r -> AppLogger.openAiRecipe(i + 1, r.name, r.ingredients.size, r.instructions.size) }
            AppLogger.openAiSuccess(unique.size)
            response.copy(results = unique)
        }.getOrElse { e ->
            AppLogger.openAiError("Parse falhou: ${e.message}")
            OpenAiErrorHandler.handleParsingError(query, e)
        }
    }

    // ── Chat ─────────────────────────────────────────────────────────────────

    suspend fun chat(history: List<ChatMessage>): ChatMessage {
        val transcript = buildString {
            history.forEach { msg ->
                val who = when (msg.role.lowercase()) {
                    "system" -> "System"
                    "assistant" -> "Assistant"
                    else -> "User"
                }
                appendLine("$who: ${msg.content}")
            }
            append("Assistant:")
        }

        val resp = try {
            postResponses(
                model = model,
                instructions = "Você é um assistente sucinto. Responda apenas com texto puro (sem markdown).",
                input = transcript,
                text = null
            )
        } catch (e: OpenAiHttpException) {
            val msg = when (e.status) {
                401 -> "Chave da OpenAI inválida/ausente (401)."
                429 -> "Limite da OpenAI excedido (429)."
                else -> "Falha na OpenAI: HTTP ${e.status} - ${e.message}"
            }
            return ChatMessage("assistant", msg)
        }

        val text = resp.dto.outputText()
            ?: resp.dto.outputJoin()
            ?: OpenAiResponseParser.extractTextFromResponse(resp.raw, json)
            ?: OpenAiResponseParser.extractJsonFence(resp.raw)

        return ChatMessage("assistant", text?.trim().orEmpty().ifEmpty { "Não consegui gerar uma resposta agora." })
    }

    // ── Filtro de restaurantes ────────────────────────────────────────────────

    suspend fun filterRestaurantsByFood(
        restaurants: List<String>,
        foodQuery: String
    ): List<String> {
        if (restaurants.isEmpty() || foodQuery.isBlank()) return restaurants

        val totalRestaurants = restaurants.size
        val batchSize = 30
        val batches = restaurants.chunked(batchSize)
        val allFilteredIndices = mutableSetOf<Int>()

        coroutineScope {
            batches.mapIndexed { batchIndex, batch ->
                async {
                    try {
                        val offset = batchIndex * batchSize
                        val numberedList = batch.mapIndexed { localIndex, info ->
                            "${offset + localIndex + 1}. $info"
                        }.joinToString("\n")

                        val prompt = buildRestaurantFilterPrompt(numberedList, foodQuery)
                        val response = chat(listOf(ChatMessage("user", prompt)))

                        response.content.trim().lines()
                            .map { it.trim() }
                            .filter { it.isNotBlank() }
                            .mapNotNull { line -> line.replace("[^0-9]".toRegex(), "").toIntOrNull() }
                            .filter { it in 1..totalRestaurants }
                            .toSet()
                    } catch (e: Exception) {
                        Log.e("OpenAiClient", "Erro no lote ${batchIndex + 1}: ${e.message}", e)
                        emptySet()
                    }
                }
            }.awaitAll().forEach { allFilteredIndices.addAll(it) }
        }

        return allFilteredIndices.sorted()
            .mapNotNull { restaurants.getOrNull(it - 1) }
            .distinct()
    }

    private fun buildRestaurantFilterPrompt(
        numberedList: String,
        foodQuery: String
    ): String = """
Você é um especialista em culinária que analisa restaurantes para determinar se servem um tipo específico de comida.

LISTA DE RESTAURANTES:
$numberedList

COMIDA PROCURADA: "$foodQuery"

Retorne APENAS os números dos restaurantes que realmente servem "$foodQuery", um por linha, sem texto adicional.
""".trimIndent()

    // ── HTTP interno ──────────────────────────────────────────────────────────

    private suspend fun postResponses(
        model: String,
        instructions: String? = null,
        input: String,
        text: TextOptions? = null
    ): Decoded<ResponsesResponse> {
        val isO1 = model.startsWith("o1-")
        val finalInput = if (isO1 && instructions != null) "$instructions\n\n$input" else input
        val body = ResponsesRequest(
            model = model,
            input = finalInput,
            instructions = if (isO1) null else instructions,
            text = text
        )
        val http = httpClient.post("responses") { setBody(body) }
        return safeDecoded(http, json) { raw -> json.decodeFromString(ResponsesResponse.serializer(), raw) }
    }
}

// ── DTOs privados ─────────────────────────────────────────────────────────────

@Serializable
private data class ResponsesRequest(
    val model: String,
    val input: String,
    val instructions: String? = null,
    val text: TextOptions? = null
)

@Serializable
internal data class TextOptions(val format: TextFormat? = null)

@Serializable
internal data class TextFormat(
    val type: String? = null,
    val name: String? = null,
    val strict: Boolean? = null,
    val schema: JsonElement? = null
)

@Serializable
private data class ResponsesResponse(
    val id: String? = null,
    val model: String? = null,
    val output: List<ResponseItem>? = null,
    @SerialName("output_text") val outputText: String? = null,
    val status: String? = null
) {
    fun outputText(): String? = outputText?.takeIf { it.isNotBlank() }
    fun outputJoin(): String? {
        val parts = output.orEmpty()
            .flatMap { it.content.orEmpty() }
            .mapNotNull { it.text?.trim() }
            .filter { it.isNotBlank() }
        return parts.takeIf { it.isNotEmpty() }?.joinToString("\n")
    }
}

@Serializable
private data class ResponseItem(
    val type: String? = null,
    val content: List<ResponseContent>? = null
)

@Serializable
private data class ResponseContent(val type: String? = null, val text: String? = null)

class OpenAiHttpException(val status: Int, override val message: String?) : IllegalStateException(message)

@Serializable
private data class OpenAiErrorEnvelope(val error: OpenAiError? = null)

@Serializable
private data class OpenAiError(val message: String? = null, val type: String? = null, val code: String? = null)

private data class Decoded<T>(val dto: T, val raw: String)

private suspend inline fun <reified T> safeDecoded(
    response: HttpResponse,
    json: Json,
    decode: (raw: String) -> T
): Decoded<T> {
    val raw = response.bodyAsText()
    if (response.status.value in 200..299) {
        val dto = runCatching { decode(raw) }.getOrElse {
            runCatching { response.body<T>() }.getOrElse {
                throw OpenAiHttpException(HttpStatusCode.InternalServerError.value, "Não consegui decodificar: ${raw.take(500)}")
            }
        }
        return Decoded(dto, raw)
    }
    val err = runCatching { json.decodeFromString(OpenAiErrorEnvelope.serializer(), raw) }.getOrNull()
    throw OpenAiHttpException(response.status.value, err?.error?.message ?: raw.ifBlank { "Erro HTTP ${response.status.value}" })
}
