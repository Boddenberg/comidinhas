package br.com.boddenb.comidinhas.data.cache

import br.com.boddenb.comidinhas.domain.model.RecipeSearchResponse
import br.com.boddenb.comidinhas.data.util.TextNormalizer
import java.util.LinkedHashMap

// Construction is provided by a Hilt module to avoid Hilt/Kapt issues
// with @Inject constructors that have default parameter values.

class RecipeCacheManager constructor(
    private val maxSize: Int,
    private val ttlMillis: Long
) {
    private data class CacheEntry(
        val response: RecipeSearchResponse,
        val timestamp: Long
    )

    private val cache: LinkedHashMap<String, CacheEntry> = object : LinkedHashMap<String, CacheEntry>(maxSize, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CacheEntry>?): Boolean {
            return this.size > maxSize
        }
    }

    fun get(query: String): RecipeSearchResponse? {
        val key = normalizeQuery(query)
        synchronized(cache) {
            val entry = cache[key] ?: return null
            val age = System.currentTimeMillis() - entry.timestamp

            return if (age <= ttlMillis) {
                entry.response
            } else {
                cache.remove(key)
                null
            }
        }
    }

    fun put(query: String, response: RecipeSearchResponse) {
        val key = normalizeQuery(query)
        synchronized(cache) {
            cache[key] = CacheEntry(response, System.currentTimeMillis())
        }
    }

    @Suppress("unused")
    fun clear() {
        synchronized(cache) {
            cache.clear()
        }
    }

    private fun normalizeQuery(query: String): String = TextNormalizer.normalize(query)
}
