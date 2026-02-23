package br.com.boddenb.comidinhas.domain.usecase

import br.com.boddenb.comidinhas.data.logger.AppLogger
import br.com.boddenb.comidinhas.data.model.recipe.RecipeEntity
import br.com.boddenb.comidinhas.domain.repository.RecipeRepository
import br.com.boddenb.comidinhas.data.scraper.CandidateRanker
import br.com.boddenb.comidinhas.data.scraper.RecipeDetails
import br.com.boddenb.comidinhas.data.scraper.RecipeLinkDiscovery
import br.com.boddenb.comidinhas.data.scraper.TudoGostosoResult
import br.com.boddenb.comidinhas.domain.model.RecipeItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject

class SearchAndFetchTudoGostosoUseCase @Inject constructor(
    private val discovery: RecipeLinkDiscovery,
    private val extractor: br.com.boddenb.comidinhas.data.scraper.RecipeDetailExtractor,
    private val recipeRepository: RecipeRepository
) {

    suspend fun execute(query: String): TudoGostosoResult {
        val normalized = query.lowercase().trim()

        val candidates = discovery.discover(normalized)
        if (candidates.isEmpty()) {
            AppLogger.tgNotFound(normalized)
            return TudoGostosoResult.NotFound(normalized)
        }

        val ranked = CandidateRanker.rank(candidates, topN = 3)
        AppLogger.tgCandidates(ranked.map { "'${it.candidate.title}' | ${it.reason}" })

        val top = ranked.first()
        AppLogger.tgExtracting(top.candidate.url)
        var details = extractor.extract(top.candidate.url)

        if (details == null) {
            AppLogger.d(AppLogger.TUDO_GOSTOSO, "│  ⚠️ Top 1 falhou na extração, tentando top 2...")
            details = ranked.getOrNull(1)?.let { extractor.extract(it.candidate.url) }
        }

        if (details != null && details.imageUrl.isNullOrBlank()) {
            AppLogger.d(AppLogger.TUDO_GOSTOSO, "│  ⚠️ Top 1 sem imagem, tentando candidato seguinte...")
            var idx = 1
            while (idx < ranked.size) {
                val candidate = ranked.getOrNull(idx) ?: break
                val alt = extractor.extract(candidate.candidate.url)
                if (alt != null && !alt.imageUrl.isNullOrBlank() && isImageAccessible(alt.imageUrl)) {
                    AppLogger.d(AppLogger.TUDO_GOSTOSO, "│  ✅ Imagem encontrada no candidato ${idx + 1}: ${alt.imageUrl}")
                    details = alt
                    break
                }
                idx++
            }
            if (details?.imageUrl.isNullOrBlank()) {
                AppLogger.d(AppLogger.TUDO_GOSTOSO, "│  ⚠️ Nenhum candidato com imagem encontrado")
            }
        }

        if (details != null && !details.imageUrl.isNullOrBlank() && !isImageAccessible(details.imageUrl)) {
            AppLogger.d(AppLogger.TUDO_GOSTOSO, "│  ⚠️ Imagem inacessível (${details.imageUrl}) — descartando receita \"${details.title}\"")
            details = details.copy(imageUrl = null)
        }

        if (details == null || details.imageUrl.isNullOrBlank()) {
            AppLogger.tgNotFound(normalized)
            return TudoGostosoResult.NotFound(normalized)
        }


        AppLogger.tgSuccess(details.title, details.imageUrl, details.ingredients.size, details.steps.size)
        saveToRepository(details, normalized)

        return TudoGostosoResult.Success(details = details, topCandidates = ranked)
    }

    /**
     * Busca um único termo e extrai TODOS os candidatos ranqueados (não só o top 1).
     * Usado no fallback por termo raiz genérico para maximizar resultados.
     */
    suspend fun executeAllCandidates(query: String): List<RecipeDetails> = coroutineScope {
        val normalized = query.lowercase().trim()
        val candidates = discovery.discover(normalized)
        if (candidates.isEmpty()) return@coroutineScope emptyList()

        val ranked = CandidateRanker.rank(candidates, topN = 5)
        AppLogger.d(AppLogger.TUDO_GOSTOSO, "│  🔁 Extraindo ${ranked.size} candidato(s) para \"$normalized\"")

        val deferred = ranked.map { rc ->
            async { runCatching { extractor.extract(rc.candidate.url) }.getOrNull() }
        }

        deferred.awaitAll()
            .filterNotNull()
            .filter { !it.imageUrl.isNullOrBlank() && isImageAccessible(it.imageUrl) }
            .also { list ->
                if (list.size < deferred.size) {
                    AppLogger.d(AppLogger.TUDO_GOSTOSO, "│  ⚠️ executeAllCandidates: ${deferred.size - list.size} receita(s) sem imagem ignoradas")
                }
                list.forEach { d ->
                    saveToRepository(d, normalized)
                }
            }
            .distinctBy { it.title.lowercase().trim() }
    }

    /**
     * Busca múltiplas variações em paralelo e retorna todas as que tiveram sucesso,
     * deduplicando por nome normalizado.
     */
    suspend fun executeMultiple(variations: List<String>): List<RecipeDetails> = coroutineScope {
        AppLogger.d(AppLogger.TUDO_GOSTOSO, "┌─ TG MULTI: ${variations.size} variações → ${variations.joinToString(", ")}")

        val deferred = variations.map { variation ->
            async {
                runCatching { execute(variation) }.getOrNull()
            }
        }

        val results = deferred.awaitAll()
            .filterIsInstance<TudoGostosoResult.Success>()
            .map { it.details }
            .filter { !it.imageUrl.isNullOrBlank() && isImageAccessible(it.imageUrl) }
            .distinctBy { it.title.lowercase().trim() }

        AppLogger.d(AppLogger.TUDO_GOSTOSO, "└─ TG MULTI: ${results.size} receitas encontradas de ${variations.size} variações")
        results
    }

    fun toRecipeItem(details: RecipeDetails): RecipeItem {
        return RecipeItem(
            id = UUID.randomUUID().toString(),
            name = details.title,
            ingredients = details.ingredients,
            instructions = details.steps,
            imageUrl = details.imageUrl,
            cookingTime = details.timeText,
            servings = details.yieldText
        )
    }

    private suspend fun isImageAccessible(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        return withContext(Dispatchers.IO) {
            try {
                val conn = (java.net.URL(url).openConnection() as java.net.HttpURLConnection).apply {
                    requestMethod = "HEAD"
                    connectTimeout = 6_000
                    readTimeout = 6_000
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36")
                    instanceFollowRedirects = true
                }
                val status = conn.responseCode
                val contentType = conn.contentType?.lowercase() ?: ""
                conn.disconnect()
                val ok = status in 200..299 && contentType.startsWith("image/")
                if (!ok) {
                    AppLogger.d(AppLogger.TUDO_GOSTOSO, "│  🚫 Imagem bloqueada: HTTP $status / $contentType → $url")
                }
                ok
            } catch (e: Exception) {
                AppLogger.d(AppLogger.TUDO_GOSTOSO, "│  🚫 Imagem inacessível (${e.message}): $url")
                false
            }
        }
    }

    private suspend fun saveToRepository(details: RecipeDetails, searchQuery: String) {
        try {
            val entity = RecipeEntity(
                id = UUID.randomUUID().toString(),
                name = details.title,
                ingredients = details.ingredients,
                instructions = details.steps,
                imageUrl = details.imageUrl,
                servings = details.yieldText,
                searchQuery = searchQuery,
                source = "TudoGostoso",
                createdAt = System.currentTimeMillis()
            )
            val result = recipeRepository.saveRecipe(entity)
            if (result.isSuccess) {
                AppLogger.supabaseSaveRecipe(details.title, entity.id)
            } else {
                AppLogger.supabaseError("saveRecipe(TG:${details.title})", result.exceptionOrNull()?.message ?: "Erro desconhecido")
            }
        } catch (e: Exception) {
            AppLogger.supabaseError("saveToRepository(TG)", e.message ?: "Exceção")
        }
    }
}





