package br.com.boddenb.comidinhas.data.correction

import br.com.boddenb.comidinhas.data.logger.AppLogger
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TermCorrectionService @Inject constructor(
    private val supabaseClient: SupabaseClient,
    private val httpClient: HttpClient,
    private val json: Json
) {
    companion object {
        private const val TABLE_NAME = "term_corrections"
        private const val ENABLE_CORRECTION = true
    }

    suspend fun correctTerm(originalTerm: String): String {
        if (!ENABLE_CORRECTION) return originalTerm

        val normalizedTerm = originalTerm.trim().lowercase()
        AppLogger.correctionStart(normalizedTerm)

        val cachedCorrection = getCachedCorrection(normalizedTerm)
        if (cachedCorrection != null) {
            AppLogger.correctionCacheHit(normalizedTerm, cachedCorrection)
            incrementHitCount(normalizedTerm)
            return cachedCorrection
        }

        AppLogger.correctionCacheMiss(normalizedTerm)
        val correctedTerm = correctWithGpt(normalizedTerm)
        AppLogger.correctionGptResult(normalizedTerm, correctedTerm)
        AppLogger.correctionSaved(normalizedTerm, correctedTerm)
        saveCorrectionToCache(normalizedTerm, correctedTerm)

        return correctedTerm
    }

    private suspend fun getCachedCorrection(originalTerm: String): String? {
        return try {
            val result = supabaseClient.postgrest
                .from(TABLE_NAME)
                .select(columns = Columns.list("corrected_term")) {
                    filter { eq("original_term", originalTerm) }
                    limit(1)
                }
                .decodeList<TermCorrectionResponse>()
            result.firstOrNull()?.correctedTerm
        } catch (e: Exception) {
            AppLogger.correctionError(originalTerm, "Erro ao buscar cache: ${e.message}")
            null
        }
    }

    private suspend fun incrementHitCount(originalTerm: String) {
        try {
            supabaseClient.postgrest.rpc(
                "increment_term_hit_count",
                buildJsonObject { put("term", originalTerm) }
            )
        } catch (_: Exception) { }
    }

    private suspend fun saveCorrectionToCache(originalTerm: String, correctedTerm: String) {
        try {
            supabaseClient.postgrest.from(TABLE_NAME).upsert(
                TermCorrectionInsert(originalTerm = originalTerm, correctedTerm = correctedTerm)
            ) { onConflict = "original_term" }
        } catch (e: Exception) {
            AppLogger.correctionError(originalTerm, "Erro ao salvar cache: ${e.message}")
        }
    }

    private suspend fun correctWithGpt(term: String): String {
        return try {
            val requestBody = buildJsonObject {
                put("model", "gpt-4o-mini")
                put("max_tokens", 50)
                put("temperature", 0.0)
                putJsonArray("messages") {
                    addJsonObject {
                        put("role", "system")
                        put("content", """
                            Você é um corretor ortográfico de termos culinários em português brasileiro.
                            Corrija erros de digitação e ortografia.
                            Mantenha o idioma PORTUGUÊS (não traduzir!).
                            Retorne APENAS o termo corrigido, nada mais.
                            Se o termo já estiver correto, retorne ele igual.
                        """.trimIndent())
                    }
                    addJsonObject {
                        put("role", "user")
                        put("content", term)
                    }
                }
            }

            val response: HttpResponse = httpClient.post("chat/completions") {
                setBody(requestBody)
            }

            val responseText = response.bodyAsText()
            val jsonResponse = json.parseToJsonElement(responseText).jsonObject
            val correctedTerm = jsonResponse["choices"]
                ?.jsonArray?.firstOrNull()
                ?.jsonObject?.get("message")
                ?.jsonObject?.get("content")
                ?.jsonPrimitive?.content?.trim()?.lowercase()

            if (!correctedTerm.isNullOrBlank()) correctedTerm
            else {
                AppLogger.correctionError(term, "Resposta vazia do GPT")
                term
            }
        } catch (e: Exception) {
            AppLogger.correctionError(term, e.message ?: "Exceção")
            term
        }
    }
}

@Serializable
data class TermCorrectionResponse(
    @SerialName("corrected_term") val correctedTerm: String
)

@Serializable
data class TermCorrectionInsert(
    @SerialName("original_term") val originalTerm: String,
    @SerialName("corrected_term") val correctedTerm: String
)
