package br.com.boddenb.comidinhas.data.remote

import br.com.boddenb.comidinhas.domain.model.RecipeSearchRequest
import br.com.boddenb.comidinhas.domain.model.RecipeSearchResponse
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

interface RecipesApi {

    @POST("recipes/search")
    suspend fun search(@Body body: RecipeSearchRequest): RecipeSearchResponse

    companion object {

        fun create(
            baseUrl: String,
            debug: Boolean = true,            // liga/desliga logs de rede
            client: OkHttpClient? = null      // permite injetar um cliente custom, se quiser
        ): RecipesApi {
            val normalizedBaseUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"

            val json = Json {
                ignoreUnknownKeys = true
                // se quiser: explicitNulls = false, prettyPrint = false
            }

            val logging = HttpLoggingInterceptor().apply {
                level = if (debug) HttpLoggingInterceptor.Level.BODY
                else HttpLoggingInterceptor.Level.NONE
            }

            val ok = (client ?: OkHttpClient.Builder()
                .addInterceptor(logging)
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .writeTimeout(20, TimeUnit.SECONDS)
                .build())

            val retrofit = Retrofit.Builder()
                .baseUrl(normalizedBaseUrl) // ex.: "http://10.0.2.2:8080/" ou "https://seu-backend.com/"
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .client(ok)
                .build()

            return retrofit.create(RecipesApi::class.java)
        }
    }
}
