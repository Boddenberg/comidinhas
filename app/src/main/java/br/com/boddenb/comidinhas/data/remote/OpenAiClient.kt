package br.com.boddenb.comidinhas.data.remote

import android.util.Log
import br.com.boddenb.comidinhas.data.cache.RecipeCacheManager
import br.com.boddenb.comidinhas.data.correction.TermCorrectionService
import br.com.boddenb.comidinhas.data.error.OpenAiErrorHandler
import br.com.boddenb.comidinhas.data.image.DallEImageGenerator
import br.com.boddenb.comidinhas.data.logger.AppLogger
import br.com.boddenb.comidinhas.data.model.recipe.RecipeEntity
import br.com.boddenb.comidinhas.data.parser.OpenAiResponseParser
import br.com.boddenb.comidinhas.data.repository.RecipeRepository
import br.com.boddenb.comidinhas.domain.model.ChatMessage
import br.com.boddenb.comidinhas.domain.model.RecipeItem
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
import br.com.boddenb.comidinhas.data.scraper.TudoGostosoResult
import br.com.boddenb.comidinhas.data.util.fixEncoding
import br.com.boddenb.comidinhas.domain.usecase.SearchAndFetchTudoGostosoUseCase
import javax.inject.Inject

class OpenAiClient @Inject constructor(
    private val httpClient: HttpClient,
    private val json: Json,
    private val imageGenerator: DallEImageGenerator,
    private val cacheManager: RecipeCacheManager,
    private val recipeRepository: RecipeRepository? = null,
    private val termCorrectionService: TermCorrectionService? = null,
    private val model: String = "gpt-4o",
    private val tudoGostosoUseCase: SearchAndFetchTudoGostosoUseCase? = null
) {
    private val TAG = "OpenAiClient"

    // true = busca no TudoGostoso primeiro; false = apenas OpenAI
    private val TUDO_GOSTOSO_ENABLED = true

    /**
     * Resultado da validação de termo
     */
    sealed class TermValidationResult {
        data class Valid(val term: String) : TermValidationResult()
        data class Invalid(val reason: String) : TermValidationResult()
    }

    suspend fun searchRecipes(query: String): RecipeSearchResponse {
        AppLogger.searchStart(query)

        val normalizedQuery = query.lowercase().trim()

        // 1. Cache em memória
        cacheManager.get(normalizedQuery)?.let { cached ->
            AppLogger.cacheHit(normalizedQuery)
            AppLogger.searchEnd(normalizedQuery, "Cache em memória", cached.results.size)
            return cached
        }
        AppLogger.cacheMiss(normalizedQuery)

        // 2. BUSCA NO REPOSITÓRIO (Supabase) + correção de termos
        var searchQuery = normalizedQuery
        var foundInRecipes = false
        var recipes: List<RecipeEntity> = emptyList()

        recipeRepository?.let { repo ->
            AppLogger.supabaseSearchStart(normalizedQuery)
            val directResult = repo.getRecipesByQuery(normalizedQuery)

            directResult.getOrNull()?.let { found ->
                if (found.isNotEmpty()) {
                    AppLogger.supabaseSearchFound(normalizedQuery, found.size)
                    recipes = found
                    foundInRecipes = true
                } else {
                    AppLogger.supabaseSearchEmpty(normalizedQuery)
                }
            }

            // Se não encontrou, tenta correção de termo
            if (!foundInRecipes && termCorrectionService != null) {
                AppLogger.correctionStart(normalizedQuery)
                val correctedQuery = termCorrectionService.correctTerm(normalizedQuery)

                if (correctedQuery != normalizedQuery) {
                    searchQuery = correctedQuery
                    AppLogger.supabaseSearchStart(correctedQuery)
                    val correctedResult = repo.getRecipesByQuery(correctedQuery)
                    correctedResult.getOrNull()?.let { found ->
                        if (found.isNotEmpty()) {
                            AppLogger.supabaseSearchFound(correctedQuery, found.size)
                            recipes = found
                            foundInRecipes = true
                        } else {
                            AppLogger.supabaseSearchEmpty(correctedQuery)
                        }
                    }
                }
            }
        }

        // 3. Encontrou no Supabase — retorna
        if (foundInRecipes && recipes.isNotEmpty()) {
            val uniqueRecipes = recipes
                .distinctBy { it.id }
                .distinctBy { it.name.lowercase().trim() }

            AppLogger.d(AppLogger.SUPABASE, "🔄 ${uniqueRecipes.size} receita(s) únicas (de ${recipes.size} recuperadas)")

            val recipeItems = uniqueRecipes.map { entity ->
                var finalImageUrl = entity.imageUrl
                if (finalImageUrl.isNullOrEmpty() || !isValidImageUrl(finalImageUrl)) {
                    AppLogger.imageStart(entity.name, "fallback")
                    finalImageUrl = imageGenerator.generateRecipeImage(entity.name)
                    AppLogger.imageFound("fallback", finalImageUrl ?: "null")
                } else {
                    AppLogger.d(AppLogger.SUPABASE, "   ✅ ${entity.name} → imagem já disponível")
                }
                RecipeItem(
                    id = entity.id, name = fixEncoding(entity.name),
                    ingredients = fixEncoding(entity.ingredients), instructions = fixEncoding(entity.instructions),
                    imageUrl = finalImageUrl, cookingTime = entity.cookingTime, servings = entity.servings
                )
            }
            val response = RecipeSearchResponse(query = searchQuery, results = recipeItems)
            cacheManager.put(searchQuery, response)
            AppLogger.cacheSave(searchQuery)
            AppLogger.searchEnd(searchQuery, "Supabase", recipeItems.size)
            return response
        }

        // 4. TudoGostoso
        val tgUseCase = if (TUDO_GOSTOSO_ENABLED) tudoGostosoUseCase else null
        if (tgUseCase != null) {
            AppLogger.tgStart(searchQuery)
            val tgResult = runCatching { tgUseCase.execute(searchQuery) }.getOrNull()

            when (tgResult) {
                is TudoGostosoResult.Success -> {
                    val details = tgResult.details
                    AppLogger.tgSuccess(details.title, details.imageUrl, details.ingredients.size, details.steps.size)
                    if (tgResult.topCandidates.isNotEmpty()) {
                        AppLogger.tgCandidates(tgResult.topCandidates.map { "'${it.candidate.title}' (${it.reason})" })
                    }

                    val item = tgUseCase.toRecipeItem(details)
                    val itemWithImage = if (item.imageUrl.isNullOrEmpty()) {
                        AppLogger.imageStart(item.name, "fallback TudoGostoso")
                        val url = imageGenerator.generateRecipeImage(item.name)
                        AppLogger.imageFound("fallback", url ?: "null")
                        item.copy(imageUrl = url)
                    } else item

                    val response = RecipeSearchResponse(query = searchQuery, results = listOf(itemWithImage))
                    cacheManager.put(searchQuery, response)
                    AppLogger.cacheSave(searchQuery)
                    AppLogger.searchEnd(searchQuery, "TudoGostoso", 1)
                    return response
                }
                is TudoGostosoResult.NotFound -> AppLogger.tgNotFound(searchQuery)
                is TudoGostosoResult.Error    -> AppLogger.tgError(tgResult.message)
                null                          -> AppLogger.tgError("Exceção inesperada")
            }
        }

        // 5. Validação do termo
        AppLogger.validationStart(searchQuery)
        val validationResult = validateTermForGeneration(searchQuery)

        if (validationResult is TermValidationResult.Invalid) {
            AppLogger.validationInvalid(searchQuery, validationResult.reason)
            AppLogger.searchError(query, validationResult.reason)
            return RecipeSearchResponse(
                query = searchQuery, results = emptyList(),
                errorMessage = "Não foi possível encontrar receitas para '$query'. ${validationResult.reason}"
            )
        }
        AppLogger.validationValid(searchQuery)

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
                            add("name")
                            add("ingredients")
                            add("instructions")
                            add("cookingTime")
                            add("servings")
                        }
                        putJsonObject("properties") {
                            putJsonObject("name") {
                                put("type", "string")
                                put("description", "Nome da receita")
                            }
                            putJsonObject("ingredients") {
                                put("type", "array")
                                put("description", "Lista de ingredientes")
                                putJsonObject("items") { put("type", "string") }
                            }
                            putJsonObject("instructions") {
                                put("type", "array")
                                put("description", "Passos de preparo")
                                putJsonObject("items") { put("type", "string") }
                            }
                            putJsonObject("cookingTime") {
                                put("type", "string")
                                put("description", "Tempo de preparo (ex: '30 min', '1h 30min')")
                            }
                            putJsonObject("servings") {
                                put("type", "string")
                                put("description", "Número de porções (ex: '4 porções', '6-8 pessoas')")
                            }
                        }
                    }
                }
            }
        }

        val instructions = """
            Você é um gerador de receitas.
            Preencha o JSON exatamente no schema enviado (structured output).
            Não inclua explicações; responda somente o JSON.
            IMPORTANTE:
            - Aceite palavras com ou sem acentos (ex: "lasanha" = "lasanha", "acai" = "açaí", "feijoada" = "feijoada").
            - Seja flexível com variações de escrita do português.
            - Use 'name' para o nome da receita (não 'title')
            - Use 'instructions' para os passos (não 'steps')
            - cookingTime deve ser uma string como "30 min" ou "1h 30min"
            - servings deve ser uma string como "4 porções" ou "6-8 pessoas"
        """.trimIndent()

        val user = """Gerar receitas para: "$searchQuery"."""

        val textOptions = TextOptions(
            format = TextFormat(
                type = "json_schema",
                name = "RecipeSearchResponse",
                strict = true,
                schema = schemaJson
            )
        )

        // 6. Gerar com OpenAI
        AppLogger.openAiSendRequest(model, searchQuery)

        val resp = try {
            postResponses(
                model = model,
                instructions = instructions,
                input = user,
                text = textOptions
            ).also {
                AppLogger.d(AppLogger.OPENAI, "│  📥 Resposta recebida (${it.raw.length} chars)")
            }
        } catch (e: OpenAiHttpException) {
            AppLogger.openAiError("HTTP ${e.status}: ${e.message}")
            return OpenAiErrorHandler.handleRecipeSearchError(searchQuery, e.status, e.message)
        }

        val text = resp.dto.outputText()
            ?: resp.dto.outputJoin()
            ?: OpenAiResponseParser.extractTextFromResponse(resp.raw, json)
            ?: OpenAiResponseParser.extractJsonFence(resp.raw)

        if (text.isNullOrBlank()) {
            AppLogger.openAiError("Resposta vazia da API")
            return OpenAiErrorHandler.handleEmptyResponse(searchQuery, resp.raw)
        }

        val payload = OpenAiResponseParser.extractJsonFromText(text)

        return runCatching {
            val response = json.decodeFromString(RecipeSearchResponse.serializer(), payload)
            val uniqueResults = response.results.distinctBy { it.name.lowercase().trim() }
            AppLogger.openAiReceived(uniqueResults.size)

            val resultsWithImages = uniqueResults.mapIndexed { index, recipe ->
                AppLogger.openAiRecipe(index + 1, recipe.name, recipe.ingredients.size, recipe.instructions.size)
                AppLogger.imageStart(recipe.name, "OpenAI-pipeline")
                val imageUrl = imageGenerator.generateRecipeImage(recipe.name)
                AppLogger.imageFound("pipeline", imageUrl ?: "null")
                recipe.copy(imageUrl = imageUrl)
            }
            val finalResponse = response.copy(results = resultsWithImages)
            cacheManager.put(searchQuery, finalResponse)
            AppLogger.cacheSave(searchQuery)
            AppLogger.openAiSuccess(resultsWithImages.size)
            AppLogger.searchEnd(searchQuery, "OpenAI", resultsWithImages.size)
            finalResponse
        }.getOrElse { e ->
            AppLogger.openAiError("Parse falhou: ${e.message}")
            OpenAiErrorHandler.handleParsingError(searchQuery, e, payload)
        }
    }

    private fun isValidImageUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        if (!url.startsWith("http://") && !url.startsWith("https://")) return false
        if (url.contains("oaidalleapiprodscus.blob.core.windows.net")) return false

        val validDomains = listOf(
            "supabase.co",
            "unsplash.com",
            "images.unsplash.com",
            "picsum.photos"
        )

        return validDomains.any { url.contains(it) }
    }


    private suspend fun validateTermForGeneration(term: String): TermValidationResult {
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
                            - Um termo é VÁLIDO se:
                              * É um alimento real ou prato conhecido
                              * É algo que pode ser cozinhado/preparado
                              * Não é ofensivo, vulgar ou inapropriado
                              * Não contém ingredientes impossíveis/fictícios

                            - Um termo é INVÁLIDO se:
                              * Contém palavras ofensivas ou vulgares
                              * Menciona ingredientes não-comestíveis (concreto, plástico, veneno, etc.)
                              * É claramente uma piada ou trollagem
                              * Não faz sentido como comida

                            Exemplos:
                            - "lasanha à bolonhesa" → {"valid": true}
                            - "bolo de chocolate" → {"valid": true}
                            - "bolinho de merda" → {"valid": false, "reason": "Termo inapropriado"}
                            - "macarrão de concreto" → {"valid": false, "reason": "Ingrediente não comestível"}
                            - "pizza de unicórnio" → {"valid": true} (criativo, mas ok)
                            - "sopa de pregos" → {"valid": false, "reason": "Ingrediente não comestível"}
                        """.trimIndent())
                    }
                    addJsonObject { put("role", "user"); put("content", term) }
                }
            }

            val response: HttpResponse = httpClient.post("chat/completions") {
                setBody(requestBody)
            }

            val responseText = response.bodyAsText()
            val jsonResponse = json.parseToJsonElement(responseText).jsonObject
            val content = jsonResponse["choices"]
                ?.jsonArray?.firstOrNull()
                ?.jsonObject?.get("message")
                ?.jsonObject?.get("content")
                ?.jsonPrimitive?.content?.trim()

            if (content != null) {
                val validationJson = json.parseToJsonElement(content).jsonObject
                val isValid = validationJson["valid"]?.jsonPrimitive?.booleanOrNull ?: true
                if (isValid) {
                    AppLogger.validationValid(term)
                    TermValidationResult.Valid(term)
                } else {
                    val reason = validationJson["reason"]?.jsonPrimitive?.contentOrNull
                        ?: "Termo não reconhecido como receita válida"
                    AppLogger.validationInvalid(term, reason)
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


    suspend fun chat(history: List<ChatMessage>): ChatMessage {
        val instructions = """
            Você é um assistente sucinto.
            Responda apenas com texto puro (sem markdown).
        """.trimIndent()

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
                instructions = instructions,
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

    private suspend fun postResponses(
        model: String,
        instructions: String? = null,
        input: String,
        text: TextOptions? = null
    ): Decoded<ResponsesResponse> {
        val isO1Model = model.startsWith("o1-")

        val finalInput = if (isO1Model && instructions != null) "$instructions\n\n$input" else input
        val finalInstructions = if (isO1Model) null else instructions

        val body = ResponsesRequest(
            model = model,
            input = finalInput,
            instructions = finalInstructions,
            text = text
        )
        val http = httpClient.post("responses") { setBody(body) }
        return safeDecoded(http, json) { raw ->
            json.decodeFromString(ResponsesResponse.serializer(), raw)
        }
    }

    suspend fun filterRestaurantsByFood(
        restaurants: List<String>,
        foodQuery: String
    ): List<String> {
        if (restaurants.isEmpty() || foodQuery.isBlank()) {
            Log.d(TAG, "filterRestaurantsByFood: lista vazia ou query em branco, retornando todos")
            return restaurants
        }

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
                            val globalIndex = offset + localIndex + 1
                            "$globalIndex. $info"
                        }.joinToString("\n")

                        val prompt = """
Você é um especialista em culinária que analisa restaurantes para determinar se eles servem um tipo específico de comida.

FORMATO DOS DADOS:
número. Nome | Endereço | Rating | Preço | Tipos: [tipos de estabelecimento] | Descrição: [texto] | Reviews: [comentários]

LISTA DE RESTAURANTES:
$numberedList

COMIDA PROCURADA: "$foodQuery"

🎯 TAREFA: Retorne APENAS os restaurantes que REALMENTE servem "$foodQuery"

📊 COMO ANALISAR (em ordem de prioridade):

1️⃣ TIPOS DE ESTABELECIMENTO (campo "Tipos:"):
   - Se tem tipo específico da comida (ex: "japanese restaurant" para sushi) → INCLUIR
   - Se tem tipo incompatível (ex: "japanese restaurant" para churrasco) → EXCLUIR
   - Se não tem tipo específico → seguir para próxima análise

2️⃣ NOME DO RESTAURANTE:
   - Se menciona a comida procurada → INCLUIR
   - Se menciona culinária relacionada → INCLUIR
   - Se menciona culinária incompatível → EXCLUIR

3️⃣ REVIEWS (campo "Reviews:"):
   - Clientes mencionam a comida procurada? → INCLUIR
   - Clientes mencionam APENAS pratos incompatíveis? → EXCLUIR
   - Reviews são genéricos ou vazios? → seguir para próxima análise

4️⃣ DESCRIÇÃO EDITORIAL:
   - Menciona a comida ou tipo de culinária relacionado? → INCLUIR
   - Menciona EXCLUSIVIDADE em outro tipo de comida? → EXCLUIR

5️⃣ CONTEXTO E LÓGICA:
   - Restaurantes "gerais", "variados", "brasileiros" → geralmente têm muitas opções → INCLUIR
   - Restaurantes altamente especializados em UMA culinária → só incluir se for compatível

🚫 INCOMPATIBILIDADES ÓBVIAS:

Churrascaria ❌ sushi, pizza, comida japonesa, comida vegana
Pizzaria ❌ sushi, feijoada, churrasco
Japonês ❌ feijoada, pizza, churrasco, comida brasileira
Sorveteria ❌ refeições quentes, carnes, massas
Vegano/Vegetariano ❌ carnes, peixes, frutos do mar

✅ COMPATIBILIDADES COMUNS:

PIZZA → Italianos, pizzarias, bares, restaurantes gerais
SUSHI → Japonês, asiático, oriental, fusion
CHURRASCO → Churrascarias, brasileiros, bares, parrillas
FEIJOADA → Brasileiros, bares, botecos, comida caseira
LASANHA → Italianos, restaurantes gerais, cantinas
HAMBÚRGUER → Hamburguerias, bares, restaurantes americanos, gerais
SORVETE → Sorveterias, gelaterias, cafeterias, restaurantes gerais

⚠️ REGRA DE OURO:
Em caso de DÚVIDA entre incluir ou excluir → analise os REVIEWS primeiro!
Se reviews mencionam a comida → INCLUIR
Se reviews NÃO mencionam e tipo é incompatível → EXCLUIR

📤 RESPOSTA: Retorne APENAS os números (um por linha), sem texto adicional.

Exemplo:
1
5
8
""".trimIndent()

                        val response = chat(listOf(ChatMessage("user", prompt)))
                        val responseText = response.content.trim()

                        val indices = responseText.lines()
                            .map { it.trim() }
                            .filter { it.isNotBlank() }
                            .mapNotNull { line ->
                                line.replace("[^0-9]".toRegex(), "").toIntOrNull()
                            }
                            .filter { it in 1..totalRestaurants }
                            .toSet()

                        indices
                    } catch (e: Exception) {
                        Log.e(TAG, "   ❌ Erro no lote ${batchIndex + 1}: ${e.message}", e)
                        emptySet<Int>()
                    }
                }
            }.awaitAll().forEach { batchIndices ->
                allFilteredIndices.addAll(batchIndices)
            }
        }

        val filtered = allFilteredIndices.sorted().mapNotNull { globalIndex ->
            val zeroBasedIndex = globalIndex - 1
            restaurants.getOrNull(zeroBasedIndex)
        }.distinct()

        return filtered
    }
}

@Serializable
private data class ResponsesRequest(
    val model: String,
    val input: String,
    val instructions: String? = null,
    val text: TextOptions? = null
)

@Serializable
private data class TextOptions(
    val format: TextFormat? = null
)

@Serializable
private data class TextFormat(
    val type: String? = null,
    val name: String? = null,
    val strict: Boolean? = null,
    val schema: JsonElement? = null
)

@Serializable
private data class ResponsesResponse(
    val id: String? = null,
    val model: String? = null,
    val created: Long? = null,
    val output: List<ResponseItem>? = null,
    @SerialName("output_text")
    val outputText: String? = null,
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
    val id: String? = null,
    val type: String? = null,
    val role: String? = null,
    val content: List<ResponseContent>? = null
)

@Serializable
private data class ResponseContent(
    val type: String? = null,
    val text: String? = null
)

class OpenAiHttpException(
    val status: Int,
    override val message: String?
) : IllegalStateException(message)

@Serializable
private data class OpenAiErrorEnvelope(val error: OpenAiError? = null)

@Serializable
private data class OpenAiError(
    val message: String? = null,
    val type: String? = null,
    val code: String? = null
)

private data class Decoded<T>(val dto: T, val raw: String)

private suspend inline fun <reified T> safeDecoded(
    response: HttpResponse,
    json: Json,
    decode: (raw: String) -> T
): Decoded<T> {
    val raw = response.bodyAsText()
    val statusCode = response.status
    if (statusCode.value in 200..299) {
        val dto = runCatching { decode(raw) }.getOrElse {
            runCatching { response.body<T>() }.getOrElse {
                throw OpenAiHttpException(
                    HttpStatusCode.InternalServerError.value,
                    "Não consegui decodificar a resposta, body: ${raw.take(500)}"
                )
            }
        }
        return Decoded(dto, raw)
    }
    val err = runCatching { json.decodeFromString(OpenAiErrorEnvelope.serializer(), raw) }.getOrNull()
    val msg = err?.error?.message ?: raw.ifBlank { "Erro HTTP ${statusCode.value}" }
    throw OpenAiHttpException(statusCode.value, msg)
}
