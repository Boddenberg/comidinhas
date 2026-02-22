package br.com.boddenb.comidinhas.data.remote

import br.com.boddenb.comidinhas.domain.model.ChatMessage
import br.com.boddenb.comidinhas.domain.model.ChatRequest
import br.com.boddenb.comidinhas.domain.model.ChatResponse
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.android.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

class ChatService {

    private val client = HttpClient(Android) {
        install(ContentNegotiation) {
            json(Json { 
                prettyPrint = true
                isLenient = true
                ignoreUnknownKeys = true 
            })
        }
    }

    suspend fun sendMessage(history: List<ChatMessage>): ChatMessage {
        try {
            val response: ChatResponse = client.post("http://10.0.2.2:8080/chat") {
                contentType(ContentType.Application.Json)
                setBody(ChatRequest(history))
            }.body()
            return response.response
        } catch (e: Exception) {
            // Em caso de erro (servidor offline, etc.), retorna uma mensagem de erro.
            return ChatMessage("error", "Não foi possível conectar ao servidor: ${e.message}")
        }
    }
}
