package br.com.boddenb.comidinhas.data.image

import android.util.Log
import br.com.boddenb.comidinhas.data.logger.AppLogger
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.*
import javax.inject.Inject

class DallEImageGenerator @Inject constructor(
    private val httpClient: HttpClient, // Para OpenAI/DALL-E
    private val json: Json
) {
    companion object {
        private const val TAG = "DallEImageGenerator"

        // Fonte de imagens: "BRAVE" | "UNSPLASH" | "DALLE"
        private const val IMAGE_SOURCE = "BRAVE"

        private const val BRAVE_IMAGE_SEARCH_URL = "https://api.search.brave.com/res/v1/images/search"
        private const val UNSPLASH_BASE_URL  = "https://api.unsplash.com/search/photos"
    }

    // HttpClient sem headers da OpenAI
    private val webClient: HttpClient by lazy {
        HttpClient(Android) {
            install(ContentNegotiation) { json(json) }
        }
    }

    suspend fun generateRecipeImage(recipeTitle: String): String {
        return when (IMAGE_SOURCE) {
            "DALLE"    -> generateWithDallE(recipeTitle)
            "UNSPLASH" -> searchOnUnsplash(recipeTitle)
            else       -> searchOnBrave(recipeTitle) // "BRAVE" é o padrão
        }
    }


    private suspend fun searchOnBrave(recipeTitle: String): String {
        AppLogger.imageStart(recipeTitle, "Brave")
        return try {
            val query = "$recipeTitle receita"

            val response: HttpResponse = webClient.get(BRAVE_IMAGE_SEARCH_URL) {
                header("Accept", "application/json")
                header("X-Subscription-Token", br.com.boddenb.comidinhas.BuildConfig.BRAVE_API_KEY)
                parameter("q", query)
                parameter("count", 5)
                parameter("safesearch", "strict")
            }

            val responseText = response.bodyAsText()

            val jsonResponse = json.parseToJsonElement(responseText).jsonObject

            val errorCode = jsonResponse["error"]?.jsonObject?.get("code")?.jsonPrimitive?.intOrNull
            if (errorCode != null) {
                val errorMsg = jsonResponse["error"]?.jsonObject?.get("detail")?.jsonPrimitive?.content
                AppLogger.imageError("Brave", "Erro $errorCode: $errorMsg", "Unsplash")
                return searchOnUnsplash(recipeTitle)
            }

            val results = jsonResponse["results"]?.jsonArray

            if (!results.isNullOrEmpty()) {
                val culinaryDomains = listOf(
                    "tudogostoso.com.br", "receitasnestle.com.br", "panelinha.com.br",
                    "cybercook.com.br", "receitasdeprimo.com.br", "receiteria.com.br",
                    "comidaereceita.com.br", "guiamais.com.br"
                )

                val culinaryResult = results.firstOrNull { result ->
                    val source = result.jsonObject["source"]?.jsonPrimitive?.contentOrNull ?: ""
                    val pageFetched = result.jsonObject["page_fetched"]?.jsonPrimitive?.contentOrNull ?: ""
                    culinaryDomains.any { source.contains(it) || pageFetched.contains(it) }
                }

                val bestResult = (culinaryResult ?: results.firstOrNull())?.jsonObject
                val imageUrl = bestResult
                    ?.get("properties")?.jsonObject?.get("url")?.jsonPrimitive?.contentOrNull
                    ?: bestResult?.get("thumbnail")?.jsonObject?.get("src")?.jsonPrimitive?.contentOrNull

                if (!imageUrl.isNullOrBlank()) {
                    val detail = if (culinaryResult != null) "site culinário" else "resultado geral"
                    AppLogger.imageFound("Brave", imageUrl, detail)
                    return imageUrl
                }
            }

            AppLogger.imageFallback("Brave", "Unsplash", "Nenhuma imagem nos resultados")
            searchOnUnsplash(recipeTitle)

        } catch (e: Exception) {
            AppLogger.imageError("Brave", e.message ?: "Exceção", "Unsplash")
            searchOnUnsplash(recipeTitle)
        }
    }

    // ─────────────────────────────────────────────
    // 2. UNSPLASH  (fallback gratuito)
    // ─────────────────────────────────────────────

    private suspend fun searchOnUnsplash(recipeTitle: String): String {
        AppLogger.imageStart(recipeTitle, "Unsplash")

        return try {
            val englishTerm = translateToEnglish(recipeTitle)
            val searchQuery = "$englishTerm food dish plate"
            AppLogger.d(AppLogger.IMAGEM, "│   [Unsplash] Query: \"$searchQuery\"")

            val response: HttpResponse = webClient.get(UNSPLASH_BASE_URL) {
                header("Authorization", "Client-ID ${br.com.boddenb.comidinhas.BuildConfig.UNSPLASH_CLIENT_ID}")
                parameter("query", searchQuery)
                parameter("per_page", 10)
                parameter("orientation", "squarish")
            }

            val responseText = response.bodyAsText()
            Log.d(TAG, "📷 [Unsplash] Resposta: ${responseText.take(300)}")

            val jsonResponse = json.parseToJsonElement(responseText).jsonObject
            val results = jsonResponse["results"]?.jsonArray

            if (!results.isNullOrEmpty()) {
                val foodResults = results.filter { result ->
                    val tags = result.jsonObject["tags"]?.jsonArray?.mapNotNull {
                        it.jsonObject["title"]?.jsonPrimitive?.contentOrNull?.lowercase()
                    } ?: emptyList()
                    val description = result.jsonObject["description"]?.jsonPrimitive?.contentOrNull?.lowercase() ?: ""
                    val altDescription = result.jsonObject["alt_description"]?.jsonPrimitive?.contentOrNull?.lowercase() ?: ""
                    val foodKeywords = listOf("food", "dish", "meal", "plate", "cook", "recipe", "eat", "delicious", "cuisine")
                    foodKeywords.any { keyword ->
                        tags.any { it.contains(keyword) } ||
                        description.contains(keyword) ||
                        altDescription.contains(keyword)
                    }
                }
                val finalResults = foodResults.ifEmpty { results }
                val randomIndex = (0 until minOf(finalResults.size, 5)).random()
                val imageUrl = finalResults[randomIndex]
                    .jsonObject["urls"]
                    ?.jsonObject?.get("regular")
                    ?.jsonPrimitive?.content

                if (imageUrl != null) {
                    AppLogger.imageFound("Unsplash", imageUrl, if (foodResults.isNotEmpty()) "com filtro food" else "sem filtro")
                    return imageUrl
                }
            }

            AppLogger.imageNotFound("Unsplash")
            generatePlaceholderUrl(recipeTitle)

        } catch (e: Exception) {
            AppLogger.imageError("Unsplash", e.message ?: "Exceção", "placeholder")
            generatePlaceholderUrl(recipeTitle)
        }
    }

    // ─────────────────────────────────────────────
    // 3. DALL-E 3  (pago, qualidade máxima)
    // ─────────────────────────────────────────────

    private suspend fun generateWithDallE(recipeTitle: String): String {
        AppLogger.imageStart(recipeTitle, "DALL-E")
        return try {
            val prompt = """
                Uma foto profissional e apetitosa de $recipeTitle em um prato elegante,
                bem iluminada, foco na comida, estilo de fotografia gastronômica,
                fundo desfocado, alta qualidade
            """.trimIndent()
            val requestBody = buildJsonObject {
                put("model", "dall-e-3")
                put("prompt", prompt)
                put("n", 1)
                put("size", "1024x1024")
                put("quality", "standard")
                put("style", "natural")
            }
            val response: HttpResponse = httpClient.post("images/generations") {
                setBody(requestBody)
            }
            val responseText = response.bodyAsText()
            val jsonResponse = json.parseToJsonElement(responseText).jsonObject
            val imageUrl = jsonResponse["data"]
                ?.jsonArray?.firstOrNull()
                ?.jsonObject?.get("url")
                ?.jsonPrimitive?.content

            if (imageUrl != null) {
                AppLogger.imageFound("DALL-E", imageUrl)
                imageUrl
            } else {
                AppLogger.imageFallback("DALL-E", "Unsplash", "URL ausente na resposta")
                searchOnUnsplash(recipeTitle)
            }
        } catch (e: Exception) {
            AppLogger.imageError("DALL-E", e.message ?: "Exceção", "Unsplash")
            searchOnUnsplash(recipeTitle)
        }
    }

    // ─────────────────────────────────────────────
    // HELPERS
    // ─────────────────────────────────────────────

    private suspend fun translateToEnglish(recipeTitle: String): String {
        return try {
            val requestBody = buildJsonObject {
                put("model", "gpt-4o-mini")
                put("max_tokens", 50)
                put("temperature", 0.0)
                putJsonArray("messages") {
                    addJsonObject {
                        put("role", "system")
                        put("content", """
                            Translate the food/recipe name to English.
                            Return ONLY the English translation, nothing else.
                            Keep it short (1-4 words).
                            Examples:
                            - "lasanha à bolonhesa" → "bolognese lasagna"
                            - "bolo de chocolate" → "chocolate cake"
                            - "frango grelhado" → "grilled chicken"
                            - "pão de queijo" → "cheese bread"
                        """.trimIndent())
                    }
                    addJsonObject {
                        put("role", "user")
                        put("content", recipeTitle)
                    }
                }
            }
            val response: HttpResponse = httpClient.post("chat/completions") {
                setBody(requestBody)
            }
            val responseText = response.bodyAsText()
            val jsonResponse = json.parseToJsonElement(responseText).jsonObject
            val translatedTerm = jsonResponse["choices"]
                ?.jsonArray?.firstOrNull()
                ?.jsonObject?.get("message")
                ?.jsonObject?.get("content")
                ?.jsonPrimitive?.content
                ?.trim()
                ?.lowercase()

            if (!translatedTerm.isNullOrBlank()) {
                Log.d(TAG, "🌐 Traduzido: '$recipeTitle' → '$translatedTerm'")
                translatedTerm
            } else {
                recipeTitle
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Erro ao traduzir: ${e.message}")
            translateStatically(recipeTitle)
        }
    }

    private fun translateStatically(recipeTitle: String): String {
        val translations = mapOf(
            "lasanha" to "lasagna", "bolonhesa" to "bolognese",
            "bolo" to "cake", "chocolate" to "chocolate",
            "frango" to "chicken", "grelhado" to "grilled",
            "assado" to "roasted", "frito" to "fried",
            "arroz" to "rice", "feijão" to "beans",
            "carne" to "beef", "peixe" to "fish",
            "salada" to "salad", "sopa" to "soup",
            "macarrão" to "pasta", "pizza" to "pizza",
            "pão" to "bread", "queijo" to "cheese",
            "ovo" to "egg", "batata" to "potato",
            "torta" to "pie", "pudim" to "pudding",
            "brigadeiro" to "chocolate truffle",
            "feijoada" to "black bean stew"
        )
        var result = recipeTitle.lowercase()
        translations.forEach { (pt, en) -> result = result.replace(pt, en) }
        return result
    }

    private fun generatePlaceholderUrl(recipeTitle: String): String {
        val seed = recipeTitle.hashCode().toString()
        return "https://picsum.photos/seed/$seed/800/600"
    }
}
