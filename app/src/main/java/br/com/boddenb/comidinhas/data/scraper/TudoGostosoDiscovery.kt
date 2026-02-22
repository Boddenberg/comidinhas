package br.com.boddenb.comidinhas.data.scraper

import android.util.Log
import br.com.boddenb.comidinhas.data.logger.AppLogger
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URLEncoder

/**
 * Modo A - Usa a busca interna do TudoGostoso.
 * URL padrao: https://www.tudogostoso.com.br/busca/?q=lasanha+bolonhesa
 */
class TudoGostosoSearchDiscovery(private val httpClient: HttpClient) : RecipeLinkDiscovery {

    companion object {
        private const val TAG = "TGSearch-ModoA"
        private const val BASE_SEARCH_URL = "https://www.tudogostoso.com.br/busca/?q="
        private const val TUDOGOSTOSO_HOST = "www.tudogostoso.com.br"
        private const val RECIPE_PATH_PREFIX = "/receita/"
    }

    override suspend fun discover(query: String): List<RecipeCandidate> = withContext(Dispatchers.IO) {
        try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "$BASE_SEARCH_URL$encoded"
            AppLogger.tgModeA(url)
            val html = httpClient.get(url) {
                header("User-Agent", "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36")
                header("Accept-Language", "pt-BR,pt;q=0.9")
            }.bodyAsText()
            val result = parseSearchResults(html)
            AppLogger.tgModeAResult(result.size)
            result
        } catch (e: Exception) {
            AppLogger.tgError("[Modo A] ${e.message ?: "Exceção"}")
            emptyList()
        }
    }

    private fun parseSearchResults(html: String): List<RecipeCandidate> {
        return try {
            val doc = org.jsoup.Jsoup.parse(html)
            val candidates = mutableListOf<RecipeCandidate>()
            val links = doc.select("a[href*=/receita/]").toList()

            for (link in links) {
                val href = link.attr("href")
                if (!href.contains(RECIPE_PATH_PREFIX)) continue
                val fullUrl = if (href.startsWith("http")) href else "https://$TUDOGOSTOSO_HOST$href"
                if (candidates.any { it.url == fullUrl }) continue

                val title = link.select("h2, h3, [class*=title], [class*=name]").toList()
                    .firstOrNull()?.text()?.trim()?.ifBlank { null }
                    ?: link.text().trim().ifBlank { null }
                    ?: continue

                val ratingText = link.select("[class*=rating], [class*=nota], [class*=stars]").toList().firstOrNull()?.text()
                val reviewText = link.select("[class*=review], [class*=avalia]").toList().firstOrNull()?.text()
                val timeText   = link.select("[class*=time], [class*=tempo]").toList().firstOrNull()?.text()

                candidates.add(RecipeCandidate(
                    title = title, url = fullUrl,
                    rating = CandidateRanker.parseRating(ratingText),
                    reviewsCount = CandidateRanker.parseReviewCount(reviewText),
                    timeText = timeText
                ))
                if (candidates.size >= 10) break
            }
            candidates
        } catch (e: Exception) {
            AppLogger.tgError("[Modo A] Parse: ${e.message ?: "Exceção"}")
            emptyList()
        }
    }
}

/**
 * Modo B - Scraping do Google para encontrar links do TudoGostoso.
 * Busca "site:tudogostoso.com.br {query}" no Google e extrai os links.
 * AVISO: pode ser bloqueado por rate limiting. Use apenas como fallback.
 */
class GoogleSearchDiscovery(private val httpClient: HttpClient) : RecipeLinkDiscovery {

    companion object {
        private const val TAG = "TGSearch-ModoB"
        private const val GOOGLE_SEARCH_URL = "https://www.google.com/search"
        private const val TUDOGOSTOSO_DOMAIN = "tudogostoso.com.br"
        private const val RECIPE_PATH_PREFIX = "/receita/"
    }

    override suspend fun discover(query: String): List<RecipeCandidate> = withContext(Dispatchers.IO) {
        Log.d(TAG, "[Modo B] Buscando no Google para: '$query'")
        try {
            val searchQuery = "site:$TUDOGOSTOSO_DOMAIN $query"
            val encoded = URLEncoder.encode(searchQuery, "UTF-8")
            val url = "$GOOGLE_SEARCH_URL?q=$encoded&num=10&hl=pt-BR"
            AppLogger.tgModeB(url)
            val html = httpClient.get(url) {
                header("User-Agent", "Mozilla/5.0 (Linux; Android 10; Pixel 4) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                header("Accept", "text/html,application/xhtml+xml")
                header("Accept-Language", "pt-BR,pt;q=0.9,en;q=0.8")
                header("Referer", "https://www.google.com/")
            }.bodyAsText()
            val result = parseGoogleResults(html)
            AppLogger.tgModeBResult(result.size)
            result
        } catch (e: Exception) {
            AppLogger.tgError("[Modo B] ${e.message ?: "Exceção"}")
            emptyList()
        }
    }

    private fun parseGoogleResults(html: String): List<RecipeCandidate> {
        return try {
            val doc = org.jsoup.Jsoup.parse(html)
            val candidates = mutableListOf<RecipeCandidate>()
            val allLinks = doc.select("a[href]").toList()

            for (link in allLinks) {
                val href = link.attr("href")
                val actualUrl: String = when {
                    href.contains(TUDOGOSTOSO_DOMAIN) && href.contains(RECIPE_PATH_PREFIX) -> href
                    href.startsWith("/url?q=") -> {
                        val inner = href.removePrefix("/url?q=").substringBefore("&")
                        if (inner.contains(TUDOGOSTOSO_DOMAIN) && inner.contains(RECIPE_PATH_PREFIX)) inner else continue
                    }
                    else -> continue
                }
                if (candidates.any { it.url == actualUrl }) continue

                val title = link.select("h3").toList().firstOrNull()?.text()?.trim()?.ifBlank { null }
                    ?: link.closest("div")?.select("h3")?.toList()?.firstOrNull()?.text()?.trim()?.ifBlank { null }
                    ?: extractTitleFromUrl(actualUrl)

                candidates.add(RecipeCandidate(title = title, url = actualUrl, rating = null, reviewsCount = null, timeText = null))
                if (candidates.size >= 10) break
            }
            candidates
        } catch (e: Exception) {
            AppLogger.tgError("[Modo B] Parse: ${e.message ?: "Exceção"}")
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

class CompositeRecipeLinkDiscovery(
    private val modeA: TudoGostosoSearchDiscovery,
    private val modeB: GoogleSearchDiscovery
) : RecipeLinkDiscovery {

    override suspend fun discover(query: String): List<RecipeCandidate> {
        val resultA = modeA.discover(query)
        if (resultA.isNotEmpty()) return resultA
        AppLogger.d(AppLogger.TUDO_GOSTOSO, "│  ⚠️ Modo A vazio → tentando Modo B (Google)")
        return modeB.discover(query)
    }
}
