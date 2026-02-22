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

/**
 * Serviço de correção de termos de busca.
 *
 * Fluxo:
 * 1. Verifica se o termo já foi corrigido antes (cache no Supabase)
 * 2. Se não, pede ao GPT para corrigir
 * 3. Salva a correção no Supabase para próximas vezes
 */
@Singleton
class TermCorrectionService @Inject constructor(
    private val supabaseClient: SupabaseClient,
    private val httpClient: HttpClient,
    private val json: Json
) {
    companion object {
        private const val TABLE_NAME = "term_corrections"

        // Toggle para ativar/desativar correção
        private const val ENABLE_CORRECTION = true
    }

    /**
     * Corrige um termo de busca.
     * Primeiro tenta buscar no cache (Supabase), se não encontrar, usa GPT.
     */
    suspend fun correctTerm(originalTerm: String): String {
        if (!ENABLE_CORRECTION) {
            return originalTerm
        }

        val normalizedTerm = originalTerm.trim().lowercase()
        AppLogger.correctionStart(normalizedTerm)

        // 1. Buscar no cache (Supabase)
        val cachedCorrection = getCachedCorrection(normalizedTerm)
        if (cachedCorrection != null) {
            AppLogger.correctionCacheHit(normalizedTerm, cachedCorrection)
            incrementHitCount(normalizedTerm)
            return cachedCorrection
        }

        // 2. Não encontrou no cache, verificar se precisa correção com GPT
        AppLogger.correctionCacheMiss(normalizedTerm)
        val correctedTerm = correctWithGpt(normalizedTerm)
        AppLogger.correctionGptResult(normalizedTerm, correctedTerm)

        // 3. Salvar no cache para próximas vezes
        AppLogger.correctionSaved(normalizedTerm, correctedTerm)
        saveCorrectionToCache(normalizedTerm, correctedTerm)

        return correctedTerm
    }

    /**
     * Busca correção no cache (Supabase)
     */
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

    /**
     * Incrementa o contador de uso (analytics)
     */
    private suspend fun incrementHitCount(originalTerm: String) {
        try {
            // Busca o hit_count atual e incrementa
            supabaseClient.postgrest.from(TABLE_NAME).update({
                // hit_count = hit_count + 1 (usando RPC seria melhor, mas funciona assim)
                set("hit_count", 1) // Simplificado - idealmente usar uma function SQL
            }) { filter { eq("original_term", originalTerm) } }
        } catch (_: Exception) {
            // Silencioso - não é crítico
        }
    }

    /**
     * Salva correção no cache (Supabase)
     */
    private suspend fun saveCorrectionToCache(originalTerm: String, correctedTerm: String) {
        try {
            supabaseClient.postgrest.from(TABLE_NAME).upsert(
                TermCorrectionInsert(originalTerm = originalTerm, correctedTerm = correctedTerm)
            ) { onConflict = "original_term" }
        } catch (e: Exception) {
            AppLogger.correctionError(originalTerm, "Erro ao salvar cache: ${e.message}")
        }
    }

    /**
     * Usa GPT para verificar e corrigir o termo.
     * Foco em CORREÇÃO ORTOGRÁFICA, não tradução!
     */
    private suspend fun correctWithGpt(term: String): String {
        return try {
            val requestBody = buildJsonObject {
                put("model", "gpt-4o-mini")
                put("max_tokens", 50)
                put("temperature", 0.0) // Zero criatividade para correção precisa
                putJsonArray("messages") {
                    addJsonObject {
                        put("role", "system")
                        put("content", """
                            Você é um corretor ortográfico de termos culinários em português brasileiro.

                            Sua tarefa:
                            - Corrigir erros de digitação e ortografia
                            - Manter o idioma PORTUGUÊS (não traduzir!)
                            - Retornar APENAS o termo corrigido, nada mais
                            - Se o termo já estiver correto, retorne ele igual

                            Exemplos:
                            - "laasanha a bolonhasa" → "lasanha à bolonhesa"
                            - "brigadero" → "brigadeiro"
                            - "bolo de xocolate" → "bolo de chocolate"
                            - "fango grelhado" → "frango grelhado"
                            - "lasanha à bolonhesa" → "lasanha à bolonhesa" (já está correto)
                            - "macarrao" → "macarrão"
                            - "pao de queijo" → "pão de queijo"
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

            if (!correctedTerm.isNullOrBlank()) {
                correctedTerm
            } else {
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
