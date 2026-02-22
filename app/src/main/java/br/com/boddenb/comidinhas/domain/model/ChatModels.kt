package br.com.boddenb.comidinhas.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class ChatMessage(val role: String, val content: String)

@Serializable
data class ChatRequest(val history: List<ChatMessage>)

@Serializable
data class ChatResponse(val response: ChatMessage)
