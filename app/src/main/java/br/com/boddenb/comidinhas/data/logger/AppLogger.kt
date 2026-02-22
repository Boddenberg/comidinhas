package br.com.boddenb.comidinhas.data.logger

import android.util.Log

/**
 * Logger estruturado do Comidinhas.
 *
 * Todos os logs usam o prefixo "COMIDINHAS" para filtrar facilmente no Logcat:
 *   Logcat filter: tag:COMIDINHAS
 *
 * Hierarquia de tags:
 *   COMIDINHAS/BUSCA      → fluxo principal de busca (OpenAiClient)
 *   COMIDINHAS/CACHE      → cache em memória
 *   COMIDINHAS/SUPABASE   → banco de dados Supabase
 *   COMIDINHAS/CORREÇÃO   → correção de termos (TermCorrectionService)
 *   COMIDINHAS/TUDOGOSTOSO→ scraping TudoGostoso
 *   COMIDINHAS/OPENAI     → geração de receitas via GPT
 *   COMIDINHAS/IMAGEM     → busca de imagens (Brave / Unsplash / DALL-E)
 *   COMIDINHAS/VALIDAÇÃO  → validação de termos
 *
 * Formato visual:
 *   ┌─── início de bloco
 *   │    conteúdo
 *   └─── fim de bloco
 *   ✅   sucesso
 *   ❌   erro
 *   ⚠️   aviso
 *   📥   dado recebido / encontrado
 *   📤   dado enviado / enviando
 *   🔄   processando / convertendo
 *   💾   salvando
 *   🗑️   descartado / nenhum resultado
 */
object AppLogger {

    // ── Tags ────────────────────────────────────────────────────────────────
    const val BUSCA       = "COMIDINHAS/BUSCA"
    const val CACHE       = "COMIDINHAS/CACHE"
    const val SUPABASE    = "COMIDINHAS/SUPABASE"
    const val CORRECAO    = "COMIDINHAS/CORREÇÃO"
    const val TUDO_GOSTOSO = "COMIDINHAS/TUDOGOSTOSO"
    const val OPENAI      = "COMIDINHAS/OPENAI"
    const val IMAGEM      = "COMIDINHAS/IMAGEM"
    const val VALIDACAO   = "COMIDINHAS/VALIDAÇÃO"

    // ── Helpers de nível ────────────────────────────────────────────────────
    fun d(tag: String, msg: String) = safeLog { Log.d(tag, msg) }
    fun i(tag: String, msg: String) = safeLog { Log.i(tag, msg) }
    fun w(tag: String, msg: String) = safeLog { Log.w(tag, msg) }
    fun e(tag: String, msg: String, throwable: Throwable? = null) = safeLog {
        if (throwable != null) Log.e(tag, msg, throwable) else Log.e(tag, msg)
    }

    // ── Separadores visuais ─────────────────────────────────────────────────
    fun section(tag: String, title: String) =
        d(tag, "┌────────────────────────────────────────")
            .also { d(tag, "│  $title") }
            .also { d(tag, "└────────────────────────────────────────") }

    fun header(tag: String, title: String) =
        d(tag, "▶ $title")

    fun result(tag: String, title: String) =
        d(tag, "◀ $title")

    // ── Bloco de busca principal ─────────────────────────────────────────────
    fun searchStart(query: String) {
        d(BUSCA, "")
        d(BUSCA, "╔══════════════════════════════════════════════╗")
        d(BUSCA, "║  NOVA BUSCA: \"$query\"")
        d(BUSCA, "╚══════════════════════════════════════════════╝")
    }

    fun searchEnd(query: String, source: String, count: Int) {
        d(BUSCA, "╔══════════════════════════════════════════════╗")
        d(BUSCA, "║  RESULTADO FINAL: \"$query\"")
        d(BUSCA, "║  Fonte : $source")
        d(BUSCA, "║  Qtd   : $count receita(s)")
        d(BUSCA, "╚══════════════════════════════════════════════╝")
        d(BUSCA, "")
    }

    fun searchError(query: String, reason: String) {
        e(BUSCA, "╔══════════════════════════════════════════════╗")
        e(BUSCA, "║  ❌ BUSCA SEM RESULTADO: \"$query\"")
        e(BUSCA, "║  Motivo: $reason")
        e(BUSCA, "╚══════════════════════════════════════════════╝")
        d(BUSCA, "")
    }

    // ── Cache ────────────────────────────────────────────────────────────────
    fun cacheHit(query: String) =
        i(CACHE, "📥 HIT  → \"$query\" encontrado no cache em memória")

    fun cacheMiss(query: String) =
        d(CACHE, "🗑️ MISS → \"$query\" não está no cache")

    fun cacheSave(query: String) =
        d(CACHE, "💾 SALVO → \"$query\" gravado no cache em memória")

    // ── Supabase ─────────────────────────────────────────────────────────────
    fun supabaseSearchStart(query: String) =
        d(SUPABASE, "📤 Buscando receitas para \"$query\"...")

    fun supabaseSearchFound(query: String, count: Int) =
        i(SUPABASE, "📥 $count receita(s) encontrada(s) para \"$query\"")

    fun supabaseSearchEmpty(query: String) =
        d(SUPABASE, "🗑️ Nenhuma receita para \"$query\"")

    fun supabaseSaveRecipe(name: String, id: String) =
        d(SUPABASE, "💾 Receita salva: \"$name\" (id: $id)")

    fun supabaseUploadImageStart(filename: String) =
        d(SUPABASE, "📤 Upload de imagem: $filename")

    fun supabaseUploadImageOk(filename: String, publicUrl: String) =
        i(SUPABASE, "✅ Upload concluído: $filename → $publicUrl")

    fun supabaseUploadImageFail(filename: String, reason: String) =
        w(SUPABASE, "⚠️ Falha no upload: $filename | $reason")

    fun supabaseError(operation: String, reason: String) =
        e(SUPABASE, "❌ Erro em \"$operation\": $reason")

    // ── Correção de termos ────────────────────────────────────────────────────
    fun correctionStart(term: String) =
        d(CORRECAO, "📤 Verificando termo: \"$term\"")

    fun correctionCacheHit(original: String, corrected: String) =
        i(CORRECAO, "📥 Cache HIT: \"$original\" → \"$corrected\"")

    fun correctionCacheMiss(term: String) =
        d(CORRECAO, "🗑️ Cache MISS: \"$term\" → consultando GPT")

    fun correctionGptResult(original: String, corrected: String) =
        if (original == corrected)
            i(CORRECAO, "✅ Termo já está correto: \"$original\"")
        else
            i(CORRECAO, "✏️ Corrigido: \"$original\" → \"$corrected\"")

    fun correctionSaved(original: String, corrected: String) =
        d(CORRECAO, "💾 Correção salva no Supabase: \"$original\" → \"$corrected\"")

    fun correctionError(term: String, reason: String) =
        w(CORRECAO, "⚠️ Erro na correção de \"$term\": $reason (usando original)")

    // ── TudoGostoso ───────────────────────────────────────────────────────────
    fun tgStart(query: String) {
        d(TUDO_GOSTOSO, "┌──────────────────────────────────────────")
        d(TUDO_GOSTOSO, "│  🍽️ Scraping TudoGostoso para: \"$query\"")
    }

    fun tgModeA(url: String) =
        d(TUDO_GOSTOSO, "│  [Modo A] GET $url")

    fun tgModeAResult(count: Int) =
        d(TUDO_GOSTOSO, "│  [Modo A] $count candidato(s) encontrado(s)")

    fun tgModeB(url: String) =
        d(TUDO_GOSTOSO, "│  [Modo B] Google scraping: $url")

    fun tgModeBResult(count: Int) =
        d(TUDO_GOSTOSO, "│  [Modo B] $count candidato(s) encontrado(s)")

    fun tgCandidates(candidates: List<String>) {
        d(TUDO_GOSTOSO, "│  Candidatos ranqueados:")
        candidates.forEachIndexed { i, c -> d(TUDO_GOSTOSO, "│    ${i+1}. $c") }
    }

    fun tgExtracting(url: String) =
        d(TUDO_GOSTOSO, "│  🔎 Extraindo detalhes de: $url")

    fun tgExtractedJsonLd(title: String) =
        i(TUDO_GOSTOSO, "│  ✅ JSON-LD encontrado: \"$title\"")

    fun tgExtractedHtmlFallback(title: String) =
        i(TUDO_GOSTOSO, "│  ✅ HTML fallback: \"$title\"")

    fun tgSuccess(title: String, imageUrl: String?, ingredients: Int, steps: Int) {
        i(TUDO_GOSTOSO, "│  ✅ RECEITA OBTIDA:")
        i(TUDO_GOSTOSO, "│     Título     : $title")
        i(TUDO_GOSTOSO, "│     Ingredientes: $ingredients")
        i(TUDO_GOSTOSO, "│     Passos     : $steps")
        i(TUDO_GOSTOSO, "│     Imagem     : ${imageUrl ?: "nenhuma (buscará separado)"}")
        d(TUDO_GOSTOSO, "└──────────────────────────────────────────")
    }

    fun tgNotFound(query: String) {
        w(TUDO_GOSTOSO, "│  ⚠️ Nenhum resultado para \"$query\"")
        d(TUDO_GOSTOSO, "└──────────────────────────────────────────")
    }

    fun tgError(reason: String) {
        e(TUDO_GOSTOSO, "│  ❌ Erro: $reason")
        d(TUDO_GOSTOSO, "└──────────────────────────────────────────")
    }

    // ── OpenAI ────────────────────────────────────────────────────────────────
    fun openAiSendRequest(model: String, query: String) {
        d(OPENAI, "┌──────────────────────────────────────────")
        d(OPENAI, "│  📤 Request GPT")
        d(OPENAI, "│  Modelo: $model")
        d(OPENAI, "│  Query : \"$query\"")
    }

    fun openAiReceived(count: Int) =
        i(OPENAI, "│  📥 $count receita(s) gerada(s) pela IA")

    fun openAiRecipe(index: Int, name: String, ingredients: Int, steps: Int) {
        d(OPENAI, "│  [$index] $name")
        d(OPENAI, "│       Ingredientes: $ingredients | Passos: $steps")
    }

    fun openAiSuccess(count: Int) {
        i(OPENAI, "│  ✅ $count receita(s) prontas")
        d(OPENAI, "└──────────────────────────────────────────")
    }

    fun openAiError(reason: String) {
        e(OPENAI, "│  ❌ Erro: $reason")
        d(OPENAI, "└──────────────────────────────────────────")
    }

    // ── Validação de termos ────────────────────────────────────────────────────
    fun validationStart(term: String) =
        d(VALIDACAO, "📤 Validando: \"$term\"")

    fun validationValid(term: String) =
        i(VALIDACAO, "✅ Válido: \"$term\"")

    fun validationInvalid(term: String, reason: String) =
        w(VALIDACAO, "🚫 Inválido: \"$term\" → $reason")

    fun validationError(term: String, reason: String) =
        w(VALIDACAO, "⚠️ Erro ao validar \"$term\": $reason (permissão concedida)")

    // ── Imagem ────────────────────────────────────────────────────────────────
    fun imageStart(recipe: String, source: String) =
        d(IMAGEM, "┌── 🔍 Buscando imagem [$source] para: \"$recipe\"")

    fun imageFound(source: String, url: String, detail: String = "") =
        i(IMAGEM, "└── ✅ [$source] ${if (detail.isNotBlank()) "($detail) " else ""}→ $url")

    fun imageFallback(from: String, to: String, reason: String) =
        w(IMAGEM, "│   ⚠️ [$from] $reason → fallback para [$to]")

    fun imageNotFound(source: String) =
        w(IMAGEM, "└── ⚠️ [$source] Nenhuma imagem → placeholder")

    fun imageError(source: String, reason: String, fallback: String) =
        w(IMAGEM, "└── ❌ [$source] $reason → fallback para [$fallback]")

    // ── Util ─────────────────────────────────────────────────────────────────
    private inline fun safeLog(block: () -> Unit) {
        try { block() } catch (_: Throwable) { /* Ambiente sem Android (testes/server) */ }
    }
}

