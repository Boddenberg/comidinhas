package br.com.boddenb.comidinhas.data.scraper

import br.com.boddenb.comidinhas.data.logger.AppLogger
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URLEncoder

class TudoGostosoSearchDiscovery(private val httpClient: HttpClient) : RecipeLinkDiscovery {

    companion object {
        private const val BASE_SEARCH_URL = "https://www.tudogostoso.com.br/busca/?q="
        private const val TUDOGOSTOSO_HOST = "www.tudogostoso.com.br"
        private const val RECIPE_PATH_PREFIX = "/receita/"
    }

    override suspend fun discover(query: String): List<RecipeCandidate> = withContext(Dispatchers.IO) {
        try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "$BASE_SEARCH_URL$encoded"
            AppLogger.tgModeA(url)
            AppLogger.d(AppLogger.TUDO_GOSTOSO, "│  📤 [TudoGostoso/GET] Modo A — busca interna")
            AppLogger.d(AppLogger.TUDO_GOSTOSO, "│      URL: $url")

            val httpResponse = httpClient.get(url) {
                header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                header("Accept-Language", "pt-BR,pt;q=0.9,en;q=0.8")
                header("Accept-Encoding", "identity")
                header("Cache-Control", "no-cache")
            }
            val html = httpResponse.bodyAsText()

            AppLogger.d(AppLogger.TUDO_GOSTOSO, "│  📥 [TudoGostoso/GET] Resposta recebida")
            AppLogger.d(AppLogger.TUDO_GOSTOSO, "│      HTTP   : ${httpResponse.status.value}")
            AppLogger.d(AppLogger.TUDO_GOSTOSO, "│      Tamanho: ${html.length} chars")
            AppLogger.d(AppLogger.TUDO_GOSTOSO, "│  [Modo A] HTML snippet: ${html.take(500).replace("\n", " ")}")

            val fromNextData = tryParseNextData(html)
            if (fromNextData.isNotEmpty()) {
                AppLogger.d(AppLogger.TUDO_GOSTOSO, "│  [Modo A] __NEXT_DATA__: ${fromNextData.size} candidato(s)")
                AppLogger.tgModeAResult(fromNextData.size)
                return@withContext fromNextData
            }

            val fromHtml = parseSearchResults(html)
            AppLogger.d(AppLogger.TUDO_GOSTOSO, "│  [Modo A] HTML links: ${fromHtml.size} candidato(s)")
            AppLogger.tgModeAResult(fromHtml.size)
            fromHtml
        } catch (e: Exception) {
            AppLogger.tgError("[Modo A] ${e.message ?: "Exceção"}")
            emptyList()
        }
    }

    private fun tryParseNextData(html: String): List<RecipeCandidate> {
        return try {
            val start = html.indexOf("__NEXT_DATA__")
            if (start == -1) return emptyList()
            val jsonStart = html.indexOf("{", start)
            if (jsonStart == -1) return emptyList()

            var depth = 0
            var jsonEnd = jsonStart
            for (i in jsonStart until html.length) {
                when (html[i]) {
                    '{' -> depth++
                    '}' -> { depth--; if (depth == 0) { jsonEnd = i; break } }
                }
            }
            if (depth != 0) return emptyList()

            val jsonStr = html.substring(jsonStart, jsonEnd + 1)
            val root = Json.parseToJsonElement(jsonStr).jsonObject

            val candidates = mutableListOf<RecipeCandidate>()
            findRecipesInJson(root, candidates)
            AppLogger.d(AppLogger.TUDO_GOSTOSO, "│  [NEXT_DATA] ${candidates.size} candidato(s) extraídos do JSON")
            candidates
        } catch (e: Exception) {
            AppLogger.d(AppLogger.TUDO_GOSTOSO, "│  [NEXT_DATA] Falhou: ${e.message}")
            emptyList()
        }
    }

    private fun findRecipesInJson(node: Any?, candidates: MutableList<RecipeCandidate>, depth: Int = 0) {
        if (depth > 15 || candidates.size >= 10) return
        when (node) {
            is JsonObject -> {
                val slug = node["slug"]?.jsonPrimitive?.content
                    ?: node["url"]?.jsonPrimitive?.content
                    ?: node["href"]?.jsonPrimitive?.content

                val title = node["title"]?.jsonPrimitive?.content
                    ?: node["name"]?.jsonPrimitive?.content

                if (!slug.isNullOrBlank() && slug.contains("/receita/") && !title.isNullOrBlank()) {
                    val fullUrl = if (slug.startsWith("http")) slug else "https://$TUDOGOSTOSO_HOST$slug"
                    if (candidates.none { it.url == fullUrl }) {
                        val ratingRaw = node["rating"]?.jsonPrimitive?.content
                            ?: node["averageRating"]?.jsonPrimitive?.content
                        val reviewsRaw = node["ratingsCount"]?.jsonPrimitive?.content
                            ?: node["reviewCount"]?.jsonPrimitive?.content
                        candidates.add(RecipeCandidate(
                            title = title,
                            url = fullUrl,
                            rating = CandidateRanker.parseRating(ratingRaw),
                            reviewsCount = CandidateRanker.parseReviewCount(reviewsRaw),
                            timeText = node["prepTime"]?.jsonPrimitive?.content
                                ?: node["totalTime"]?.jsonPrimitive?.content
                        ))
                    }
                } else {
                    node.values.forEach { findRecipesInJson(it, candidates, depth + 1) }
                }
            }
            is JsonArray -> node.forEach { findRecipesInJson(it, candidates, depth + 1) }
            else -> {}
        }
    }

    private fun parseSearchResults(html: String): List<RecipeCandidate> {
        return try {
            val doc = org.jsoup.Jsoup.parse(html)
            val candidates = mutableListOf<RecipeCandidate>()

            // Seletor 1: links diretos com /receita/
            val recipeLinks = doc.select("a[href*=/receita/]").toList()
            AppLogger.d(AppLogger.TUDO_GOSTOSO, "│  [HTML] ${recipeLinks.size} links com /receita/ encontrados")

            for (link in recipeLinks) {
                val href = link.attr("href")
                val fullUrl = if (href.startsWith("http")) href else "https://$TUDOGOSTOSO_HOST$href"
                if (candidates.any { it.url == fullUrl }) continue

                // Tenta buscar título no link e nos elementos pai próximos
                val title = link.select("h2, h3, h4, [class*=title], [class*=name], [class*=recipe]")
                    .firstOrNull()?.text()?.trim()?.ifBlank { null }
                    ?: link.parents().take(3).firstNotNullOfOrNull { parent ->
                        parent.select("h2, h3, h4").firstOrNull()?.text()?.trim()?.ifBlank { null }
                    }
                    ?: link.attr("aria-label").trim().ifBlank { null }
                    ?: link.attr("title").trim().ifBlank { null }
                    ?: link.text().trim().ifBlank { null }
                    ?: continue

                if (title.length < 4) continue  // filtra títulos muito curtos / inválidos

                val container = link.parents().take(5).firstOrNull { p ->
                    p.select("[class*=rating], [class*=nota], [class*=avalia], [class*=review]").isNotEmpty()
                }
                val ratingText = container?.select("[class*=rating], [class*=nota], [class*=stars]")
                    ?.firstOrNull()?.text()
                val reviewText = container?.select("[class*=avalia], [class*=review]")
                    ?.firstOrNull()?.text()
                val timeText = container?.select("[class*=time], [class*=tempo], [class*=preparo]")
                    ?.firstOrNull()?.text()

                candidates.add(RecipeCandidate(
                    title = title, url = fullUrl,
                    rating = CandidateRanker.parseRating(ratingText),
                    reviewsCount = CandidateRanker.parseReviewCount(reviewText),
                    timeText = timeText
                ))
                if (candidates.size >= 10) break
            }

            // Seletor 2: cards de receita (article, [class*=card], [class*=recipe])
            if (candidates.size < 3) {
                val cards = doc.select("article, [class*=recipe-card], [class*=card-recipe], [class*=RecipeCard]").toList()
                AppLogger.d(AppLogger.TUDO_GOSTOSO, "│  [HTML] ${cards.size} cards de receita encontrados")
                for (card in cards) {
                    val link = card.select("a[href]").firstOrNull() ?: continue
                    val href = link.attr("href")
                    if (!href.contains(RECIPE_PATH_PREFIX)) continue
                    val fullUrl = if (href.startsWith("http")) href else "https://$TUDOGOSTOSO_HOST$href"
                    if (candidates.any { it.url == fullUrl }) continue

                    val title = card.select("h2, h3, h4, [class*=title], [class*=name]")
                        .firstOrNull()?.text()?.trim()?.ifBlank { null }
                        ?: link.text().trim().ifBlank { null }
                        ?: continue
                    if (title.length < 4) continue

                    candidates.add(RecipeCandidate(
                        title = title, url = fullUrl,
                        rating = CandidateRanker.parseRating(
                            card.select("[class*=rating], [class*=nota]").firstOrNull()?.text()
                        ),
                        reviewsCount = CandidateRanker.parseReviewCount(
                            card.select("[class*=avalia], [class*=review]").firstOrNull()?.text()
                        ),
                        timeText = card.select("[class*=time], [class*=tempo]").firstOrNull()?.text()
                    ))
                    if (candidates.size >= 10) break
                }
            }

            candidates.distinctBy { it.url }
        } catch (e: Exception) {
            AppLogger.tgError("[Modo A] Parse HTML: ${e.message ?: "Exceção"}")
            emptyList()
        }
    }
}

class BraveWebSearchDiscovery(
    private val httpClient: HttpClient,
    private val json: Json
) : RecipeLinkDiscovery {

    companion object {
        private const val BRAVE_WEB_SEARCH_URL = "https://api.search.brave.com/res/v1/web/search"
        private const val TUDOGOSTOSO_DOMAIN = "tudogostoso.com.br"
        private const val RECIPE_PATH_PREFIX = "/receita/"
    }

    override suspend fun discover(query: String): List<RecipeCandidate> = withContext(Dispatchers.IO) {
        try {
            val searchQuery = "site:$TUDOGOSTOSO_DOMAIN $query receita"
            AppLogger.tgModeB("Brave Web API: \"$searchQuery\"")
            AppLogger.d(AppLogger.TUDO_GOSTOSO, "│  📤 [BraveAPI/GET] Requisição de busca")
            AppLogger.d(AppLogger.TUDO_GOSTOSO, "│      Endpoint: $BRAVE_WEB_SEARCH_URL")
            AppLogger.d(AppLogger.TUDO_GOSTOSO, "│      Query   : \"$searchQuery\"")

            val httpResponse = httpClient.get(BRAVE_WEB_SEARCH_URL) {
                header("Accept", "application/json")
                header("X-Subscription-Token", br.com.boddenb.comidinhas.BuildConfig.BRAVE_API_KEY)
                parameter("q", searchQuery)
                parameter("count", "10")
                parameter("country", "BR")
                parameter("search_lang", "pt")
                parameter("text_decorations", "false")
            }
            val responseText = httpResponse.bodyAsText()

            AppLogger.d(AppLogger.TUDO_GOSTOSO, "│  📥 [BraveAPI/GET] Resposta recebida")
            AppLogger.d(AppLogger.TUDO_GOSTOSO, "│      HTTP   : ${httpResponse.status.value}")
            AppLogger.d(AppLogger.TUDO_GOSTOSO, "│      Tamanho: ${responseText.length} chars")

            val root = json.parseToJsonElement(responseText).jsonObject
            val webResults = root["web"]?.jsonObject
                ?.get("results")?.jsonArray
                ?: run {
                    val errorMsg = root["message"]?.jsonPrimitive?.content
                        ?: root["error"]?.jsonObject?.get("detail")?.jsonPrimitive?.content
                        ?: "Sem campo 'web.results'"
                    AppLogger.tgError("[Modo B] Brave API: $errorMsg")
                    return@withContext emptyList()
                }

            AppLogger.d(AppLogger.TUDO_GOSTOSO, "│      Resultados brutos: ${webResults.size}")

            val candidates = mutableListOf<RecipeCandidate>()
            for (result in webResults) {
                val obj = result.jsonObject
                val url = obj["url"]?.jsonPrimitive?.content ?: continue
                if (!url.contains(TUDOGOSTOSO_DOMAIN) || !url.contains(RECIPE_PATH_PREFIX)) continue
                if (candidates.any { it.url == url }) continue

                val title = obj["title"]?.jsonPrimitive?.content?.trim()?.ifBlank { null }
                    ?: extractTitleFromUrl(url)

                val rating = obj["rating"]?.jsonPrimitive?.doubleOrNull
                val reviewCount = obj["review_count"]?.jsonPrimitive?.intOrNull

                candidates.add(RecipeCandidate(
                    title = title,
                    url = url,
                    rating = rating,
                    reviewsCount = reviewCount,
                    timeText = null
                ))
                if (candidates.size >= 10) break
            }

            AppLogger.tgModeBResult(candidates.size)
            if (candidates.isNotEmpty()) {
                candidates.forEachIndexed { i, c ->
                    AppLogger.d(AppLogger.TUDO_GOSTOSO, "│      [${i+1}] \"${c.title}\" → ${c.url}")
                }
            }
            candidates
        } catch (e: Exception) {
            AppLogger.tgError("[Modo B] Brave Web: ${e.message ?: "Exceção"}")
            emptyList()
        }
    }

    private fun extractTitleFromUrl(url: String): String =
        url.substringAfterLast(RECIPE_PATH_PREFIX)
            .removeSuffix(".html")
            .replace(Regex("^\\d+-"), "")
            .replace("-", " ")
            .trim()
            .ifBlank { url }
}

/**
 * Combina Modo A e Modo B, mesclando resultados para maximizar candidatos.
 * Usa Modo B como complemento quando Modo A retorna menos de 3 candidatos.
 */
class CompositeRecipeLinkDiscovery(
    private val modeA: TudoGostosoSearchDiscovery,
    private val modeB: BraveWebSearchDiscovery
) : RecipeLinkDiscovery {

    override suspend fun discover(query: String): List<RecipeCandidate> {
        val resultA = modeA.discover(query)

        if (resultA.size >= 3) return resultA

        AppLogger.d(AppLogger.TUDO_GOSTOSO, "│  Modo A retornou ${resultA.size} → complementando com Modo B")
        val resultB = modeB.discover(query)

        if (resultB.isEmpty()) return resultA

        val existingUrls = resultA.map { it.url }.toSet()
        val complementB = resultB.filter { it.url !in existingUrls }
        val merged = (resultA + complementB).take(10)
        AppLogger.d(AppLogger.TUDO_GOSTOSO, "│  Mesclado: ${resultA.size} (A) + ${complementB.size} (B novos) = ${merged.size}")
        return merged
    }
}
