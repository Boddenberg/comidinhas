package br.com.boddenb.comidinhas.di

import br.com.boddenb.comidinhas.BuildConfig
import br.com.boddenb.comidinhas.data.cache.RecipeCacheManager
import br.com.boddenb.comidinhas.data.correction.TermCorrectionService
import br.com.boddenb.comidinhas.data.image.DallEImageGenerator
import br.com.boddenb.comidinhas.data.remote.OpenAiClient
import br.com.boddenb.comidinhas.data.repository.RecipeRepository
import br.com.boddenb.comidinhas.domain.usecase.SearchAndFetchTudoGostosoUseCase
import br.com.boddenb.comidinhas.domain.usecase.SearchRecipesUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.SupabaseClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class OpenAiHttpClient

@Module
@InstallIn(SingletonComponent::class)
object OpenAiModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        prettyPrint = false
        coerceInputValues = true
    }

    @Provides
    @Singleton
    @OpenAiHttpClient
    fun provideOpenAiHttpClient(json: Json): HttpClient {
        val apiKey = try { BuildConfig.OPENAI_API_KEY } catch (_: Exception) { "" }
        return HttpClient(Android) {
            install(ContentNegotiation) { json(json) }
            install(Logging) { level = LogLevel.INFO }
            install(HttpTimeout) {
                requestTimeoutMillis = 60_000
                connectTimeoutMillis = 30_000
                socketTimeoutMillis  = 60_000
            }
            defaultRequest {
                url("https://api.openai.com/v1/")
                headers.append(HttpHeaders.Authorization, "Bearer $apiKey")
                contentType(ContentType.Application.Json)
            }
        }
    }

    @Provides
    @Singleton
    fun provideDallEImageGenerator(
        @OpenAiHttpClient httpClient: HttpClient,
        json: Json
    ): DallEImageGenerator = DallEImageGenerator(httpClient, json)

    @Provides
    @Singleton
    fun provideTermCorrectionService(
        supabaseClient: SupabaseClient,
        @OpenAiHttpClient httpClient: HttpClient,
        json: Json
    ): TermCorrectionService = TermCorrectionService(supabaseClient, httpClient, json)

    @Provides
    @Singleton
    fun provideOpenAiClient(
        @OpenAiHttpClient httpClient: HttpClient,
        json: Json
    ): OpenAiClient = OpenAiClient(httpClient = httpClient, json = json, model = "gpt-4o")

    @Provides
    @Singleton
    fun provideSearchRecipesUseCase(
        openAiClient: OpenAiClient,
        recipeRepository: RecipeRepository,
        cacheManager: RecipeCacheManager,
        termCorrectionService: TermCorrectionService,
        imageGenerator: DallEImageGenerator,
        tudoGostosoUseCase: SearchAndFetchTudoGostosoUseCase
    ): SearchRecipesUseCase = SearchRecipesUseCase(
        openAiClient = openAiClient,
        recipeRepository = recipeRepository,
        cacheManager = cacheManager,
        termCorrectionService = termCorrectionService,
        imageGenerator = imageGenerator,
        tudoGostosoUseCase = tudoGostosoUseCase
    )
}
