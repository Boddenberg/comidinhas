package br.com.boddenb.comidinhas.routing

import br.com.boddenb.comidinhas.data.remote.OpenAiClient
import br.com.boddenb.comidinhas.domain.model.ChatRequest
import br.com.boddenb.comidinhas.domain.model.ChatResponse
import br.com.boddenb.comidinhas.domain.model.RecipeSearchRequest
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileInputStream
import java.util.Properties

fun getProperty(key: String, file: File): String? {
    if (!file.exists()) return null
    val props = Properties()
    FileInputStream(file).use { props.load(it) }
    return props.getProperty(key)
}

fun Application.configureRouting() {
    val propertiesFile = File(System.getProperty("user.dir"), "local.properties")

    val openAiKey = getProperty("OPENAI_API_KEY", propertiesFile)
        ?: System.getenv("OPENAI_API_KEY")
        ?: error("Defina OPENAI_API_KEY em local.properties ou como variável de ambiente")

    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        prettyPrint = false
    }

    val httpClient = HttpClient {
        install(ContentNegotiation) { json(json) }
        install(Logging) { level = LogLevel.INFO }
        defaultRequest {
            url("https://api.openai.com/v1/")
            headers.append(HttpHeaders.Authorization, "Bearer $openAiKey")
            contentType(ContentType.Application.Json)
        }
    }

    val openAi = OpenAiClient(httpClient = httpClient, json = json)

    routing {
        post("/recipes/search") {
            val req = call.receive<RecipeSearchRequest>()
            val result = openAi.generateRecipes(req.query)
            call.respond(result)
        }
        post("/chat") {
            val req = call.receive<ChatRequest>()
            val result = openAi.chat(req.history)
            call.respond(ChatResponse(result))
        }
        get("/") { call.respondText("OK") }
    }
}
