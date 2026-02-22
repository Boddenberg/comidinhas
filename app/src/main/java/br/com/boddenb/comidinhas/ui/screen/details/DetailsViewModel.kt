package br.com.boddenb.comidinhas.ui.screen.details

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.boddenb.comidinhas.data.remote.ChatService
import br.com.boddenb.comidinhas.domain.model.ChatMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DetailsViewModel : ViewModel() {

    private val chatService = ChatService()

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages = _messages.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    fun sendMessage(text: String) {
        if (text.isBlank()) return

        viewModelScope.launch {
            // Adiciona a mensagem do usuário imediatamente
            val userMessage = ChatMessage(role = "user", content = text)
            _messages.value = _messages.value + userMessage
            _isLoading.value = true

            // Envia o histórico completo para o servidor
            val response = chatService.sendMessage(_messages.value)
            
            // Adiciona a resposta do servidor ao histórico
            _messages.value = _messages.value + response
            _isLoading.value = false
        }
    }
}
