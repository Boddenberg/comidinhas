package br.com.boddenb.comidinhas

import br.com.boddenb.comidinhas.routing.configureRouting
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.*
import kotlinx.serialization.json.Json

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0") { module() }.start(wait = true)
}

fun Application.module() {
    install(CORS) {
        anyHost() // para dev. (em prod: restrinja)
        allowHeader(HttpHeaders.ContentType)
    }
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true; prettyPrint = true })
    }
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            cause.printStackTrace()
            call.respond(HttpStatusCode.InternalServerError, "Erro interno no servidor: ${cause.localizedMessage}")
        }
    }

    configureRouting()
}
