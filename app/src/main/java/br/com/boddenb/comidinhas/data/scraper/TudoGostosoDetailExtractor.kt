package br.com.boddenb.comidinhas.data.scraper

import br.com.boddenb.comidinhas.data.logger.AppLogger
import br.com.boddenb.comidinhas.data.util.fixEncoding
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Extrai detalhes de uma receita a partir de uma pagina do TudoGostoso.
 *
 * Estrategia:
 * 1. Tenta JSON-LD com schema.org/Recipe (mais confiavel)
 * 2. Fallback: parsing do HTML
 */
class TudoGostosoDetailExtractor(
    private val httpClient: HttpClient,
    private val json: Json
) : RecipeDetailExtractor {

    companion object {
        private const val TAG = "TGDetailExtractor"
    }

    override suspend fun extract(url: String): RecipeDetails? = withContext(Dispatchers.IO) {
        AppLogger.tgExtracting(url)
        try {
            val html = httpClient.get(url) {
                header("User-Agent", "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36")
                header("Accept-Language", "pt-BR,pt;q=0.9")
            }.bodyAsText()

            val doc: org.jsoup.nodes.Document = org.jsoup.Jsoup.parse(html)
            doc.setBaseUri(url)

            val fromJsonLd = tryJsonLd(doc, url)
            if (fromJsonLd != null) {
                AppLogger.tgExtractedJsonLd(fromJsonLd.title)
                return@withContext fromJsonLd
            }

            AppLogger.d(AppLogger.TUDO_GOSTOSO, "│  ⚠️ JSON-LD não encontrado → tentando HTML fallback")
            val fromHtml = tryHtmlFallback(doc, url)
            if (fromHtml != null) AppLogger.tgExtractedHtmlFallback(fromHtml.title)
            else AppLogger.tgError("Nenhuma estratégia funcionou para: $url")
            fromHtml
        } catch (e: Exception) {
            AppLogger.tgError("Exceção ao extrair $url: ${e.message}")
            null
        }
    }

    private fun tryJsonLd(doc: org.jsoup.nodes.Document, sourceUrl: String): RecipeDetails? {
        val scripts: org.jsoup.select.Elements = doc.select("script[type=application/ld+json]")
        val scriptList: List<org.jsoup.nodes.Element> = scripts.toList()
        for (script in scriptList) {
            try {
                val element = json.parseToJsonElement(script.data().trim())
                val recipeObj: JsonObject? = findRecipeJsonLd(element)
                recipeObj?.let { return parseJsonLdRecipe(it, sourceUrl) }
            } catch (e: Exception) {
                AppLogger.d(AppLogger.TUDO_GOSTOSO, "│  ⚠️ Falha ao parsear bloco JSON-LD: ${e.message}")
            }
        }
        return null
    }

    private fun findRecipeJsonLd(element: JsonElement): JsonObject? {
        if (element is JsonObject) {
            if (element["@type"]?.jsonPrimitive?.contentOrNull == "Recipe") return element
            return null
        }
        if (element is JsonArray) {
            val list: List<JsonElement> = element.toList()
            for (item in list) {
                if (item is JsonObject && item["@type"]?.jsonPrimitive?.contentOrNull == "Recipe") return item
            }
        }
        return null
    }

    private fun parseJsonLdRecipe(obj: JsonObject, sourceUrl: String): RecipeDetails {
        val title = obj["name"]?.jsonPrimitive?.contentOrNull ?: ""

        val imageUrl = when (val img = obj["image"]) {
            is JsonPrimitive -> img.contentOrNull
            is JsonObject    -> img["url"]?.jsonPrimitive?.contentOrNull
            is JsonArray     -> img.firstOrNull()?.let {
                if (it is JsonPrimitive) it.contentOrNull
                else it.jsonObject["url"]?.jsonPrimitive?.contentOrNull
            }
            else -> null
        }

        val ingredients = obj["recipeIngredient"]?.jsonArray
            ?.mapNotNull { it.jsonPrimitive.contentOrNull?.trim() }
            ?.filter { it.isNotBlank() }
            ?.let { fixEncoding(it) } ?: emptyList()

        val steps = when (val inst = obj["recipeInstructions"]) {
            is JsonArray -> inst.mapNotNull { step ->
                when {
                    step is JsonPrimitive -> step.contentOrNull
                    step is JsonObject    -> step["text"]?.jsonPrimitive?.contentOrNull
                    else -> null
                }?.trim()
            }.filter { it.isNotBlank() }
            is JsonPrimitive -> listOf(inst.content).filter { it.isNotBlank() }
            else -> emptyList()
        }.let { fixEncoding(it) }

        val timeRaw = obj["totalTime"]?.jsonPrimitive?.contentOrNull
            ?: obj["cookTime"]?.jsonPrimitive?.contentOrNull
            ?: obj["prepTime"]?.jsonPrimitive?.contentOrNull

        val yieldText = when (val y = obj["recipeYield"]) {
            is JsonPrimitive -> y.contentOrNull
            is JsonArray     -> y.firstOrNull()?.jsonPrimitive?.contentOrNull
            else -> null
        }

        val aggRating = obj["aggregateRating"]?.jsonObject
        val rating       = aggRating?.get("ratingValue")?.jsonPrimitive?.doubleOrNull
        val reviewsCount = aggRating?.get("reviewCount")?.jsonPrimitive?.intOrNull
            ?: aggRating?.get("ratingCount")?.jsonPrimitive?.intOrNull

        return RecipeDetails(
            title = fixEncoding(title), sourceUrl = sourceUrl, imageUrl = imageUrl,
            ingredients = ingredients, steps = steps,
            timeText = parseIsoDuration(timeRaw), yieldText = yieldText,
            difficultyText = null, rating = rating, reviewsCount = reviewsCount
        )
    }

    // ── HTML fallback ────────────────────────────────────────────────────────

    private fun tryHtmlFallback(doc: org.jsoup.nodes.Document, sourceUrl: String): RecipeDetails? {
        val title = (doc.select("h1[class*=recipe], h1[class*=receita], h1[itemprop=name], h1") as org.jsoup.select.Elements)
            .toList().firstOrNull()?.text()?.trim()?.ifBlank { null } ?: return null

        val imageUrl = (doc.select(
            "img[class*=recipe], img[class*=receita], img[itemprop=image], " +
            ".recipe-image img, [class*=hero] img, article img"
        ) as org.jsoup.select.Elements).toList().firstOrNull()?.attr("abs:src")?.ifBlank { null }

        val ingredients = (doc.select(
            "[itemprop=recipeIngredient], [class*=ingredient], [class*=ingrediente], " +
            "ul[class*=ingredient] li, ul[class*=ingrediente] li"
        ) as org.jsoup.select.Elements).toList().map { it.text().trim() }.filter { it.isNotBlank() }

        val steps = (doc.select(
            "[itemprop=recipeInstructions], [class*=instruction], [class*=preparo], " +
            "[class*=direction], ol[class*=step] li, ol[class*=preparo] li"
        ) as org.jsoup.select.Elements).toList().map { it.text().trim() }.filter { it.isNotBlank() }

        if (ingredients.isEmpty() && steps.isEmpty()) {
            AppLogger.d(AppLogger.TUDO_GOSTOSO, "│  ⚠️ HTML fallback: nenhum ingrediente ou passo encontrado")
            return null
        }

        val timeText = (doc.select("[class*=time], [class*=tempo], [itemprop=totalTime], [itemprop=cookTime]") as org.jsoup.select.Elements)
            .toList().firstOrNull()?.text()?.trim()
        val yieldText = (doc.select("[itemprop=recipeYield], [class*=servings], [class*=porcao], [class*=rendimento]") as org.jsoup.select.Elements)
            .toList().firstOrNull()?.text()?.trim()
        val rating = CandidateRanker.parseRating(
            (doc.select("[itemprop=ratingValue], [class*=rating-value], [class*=nota]") as org.jsoup.select.Elements).toList().firstOrNull()?.text()
        )
        val reviewsCount = CandidateRanker.parseReviewCount(
            (doc.select("[itemprop=reviewCount], [itemprop=ratingCount], [class*=review-count]") as org.jsoup.select.Elements).toList().firstOrNull()?.text()
        )

        return RecipeDetails(
            title = title, sourceUrl = sourceUrl, imageUrl = imageUrl,
            ingredients = ingredients, steps = steps,
            timeText = timeText, yieldText = yieldText,
            difficultyText = null, rating = rating, reviewsCount = reviewsCount
        )
    }

    // ── ISO 8601 duration parser ─────────────────────────────────────────────

    private fun parseIsoDuration(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        if (!raw.startsWith("PT", ignoreCase = true)) return raw
        return try {
            val upper = raw.uppercase().removePrefix("PT")
            val hours   = Regex("(\\d+)H").find(upper)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            val minutes = Regex("(\\d+)M").find(upper)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            when {
                hours > 0 && minutes > 0 -> "${hours}h ${minutes}min"
                hours > 0   -> "${hours}h"
                minutes > 0 -> "${minutes} min"
                else        -> raw
            }
        } catch (_: Exception) { raw }
    }
}

