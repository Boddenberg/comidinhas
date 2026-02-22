package br.com.boddenb.comidinhas.data.parser

import kotlinx.serialization.json.*

object OpenAiResponseParser {

    fun extractTextFromResponse(raw: String, json: Json): String? {
        val root = runCatching { json.parseToJsonElement(raw) }.getOrNull() ?: return null

        root.jsonObject["output_text"]?.jsonPrimitive?.takeIf { it.isString }?.content?.let {
            if (it.isNotBlank()) return it
        }

        val out = root.jsonObject["output"]
        if (out is JsonArray) {
            val parts = mutableListOf<String>()
            out.forEach { item ->
                val obj = item as? JsonObject ?: return@forEach
                val content = obj["content"] as? JsonArray ?: return@forEach
                content.forEach { c ->
                    val cobj = c as? JsonObject ?: return@forEach
                    cobj["text"]?.jsonPrimitive?.takeIf { it.isString }?.content?.let { t ->
                        if (t.isNotBlank()) parts += t
                    }
                    if (cobj["type"]?.jsonPrimitive?.content == "output_text") {
                        cobj["text"]?.jsonPrimitive?.takeIf { it.isString }?.content?.let { t ->
                            if (t.isNotBlank()) parts += t
                        }
                    }
                }
            }
            if (parts.isNotEmpty()) return parts.joinToString("\n")
        }
        return null
    }

    fun extractJsonFence(text: String): String? {
        val re = Regex("```(?:json)?\\s*([\\s\\S]*?)\\s*```", RegexOption.IGNORE_CASE)
        return re.find(text)?.groupValues?.getOrNull(1)?.trim()
    }

    fun extractJsonFromText(text: String): String {
        val fenced = extractJsonFence(text)
        return (fenced ?: text).trim()
    }
}

