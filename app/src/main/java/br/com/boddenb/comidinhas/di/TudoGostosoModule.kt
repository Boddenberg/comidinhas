package br.com.boddenb.comidinhas.di

import br.com.boddenb.comidinhas.data.repository.RecipeRepository
import br.com.boddenb.comidinhas.data.scraper.CompositeRecipeLinkDiscovery
import br.com.boddenb.comidinhas.data.scraper.GoogleSearchDiscovery
import br.com.boddenb.comidinhas.data.scraper.RecipeDetailExtractor
import br.com.boddenb.comidinhas.data.scraper.RecipeLinkDiscovery
import br.com.boddenb.comidinhas.data.scraper.TudoGostosoDetailExtractor
import br.com.boddenb.comidinhas.data.scraper.TudoGostosoSearchDiscovery
import br.com.boddenb.comidinhas.domain.usecase.SearchAndFetchTudoGostosoUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import kotlinx.serialization.json.Json
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * Qualificador para o HttpClient de scraping (sem headers da OpenAI).
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ScrapingHttpClient

@Module
@InstallIn(SingletonComponent::class)
object TudoGostosoModule {

    /**
     * HttpClient sem autenticacao - usado para scraping de sites externos.
     */
    @Provides
    @Singleton
    @ScrapingHttpClient
    fun provideScrapingHttpClient(): HttpClient {
        return HttpClient(Android) {
            install(Logging) { level = LogLevel.INFO }
            install(HttpTimeout) {
                requestTimeoutMillis = 20_000
                connectTimeoutMillis = 10_000
                socketTimeoutMillis = 20_000
            }
            followRedirects = true
        }
    }

    @Provides
    @Singleton
    fun provideTudoGostosoSearchDiscovery(
        @ScrapingHttpClient httpClient: HttpClient
    ): TudoGostosoSearchDiscovery {
        return TudoGostosoSearchDiscovery(httpClient)
    }

    @Provides
    @Singleton
    fun provideGoogleSearchDiscovery(
        @ScrapingHttpClient httpClient: HttpClient
    ): GoogleSearchDiscovery {
        return GoogleSearchDiscovery(httpClient)
    }

    @Provides
    @Singleton
    fun provideRecipeLinkDiscovery(
        modeA: TudoGostosoSearchDiscovery,
        modeB: GoogleSearchDiscovery
    ): RecipeLinkDiscovery {
        return CompositeRecipeLinkDiscovery(modeA, modeB)
    }

    @Provides
    @Singleton
    fun provideRecipeDetailExtractor(
        @ScrapingHttpClient httpClient: HttpClient,
        json: Json
    ): RecipeDetailExtractor {
        return TudoGostosoDetailExtractor(httpClient, json)
    }

    @Provides
    @Singleton
    fun provideSearchAndFetchTudoGostosoUseCase(
        discovery: RecipeLinkDiscovery,
        extractor: RecipeDetailExtractor,
        recipeRepository: RecipeRepository
    ): SearchAndFetchTudoGostosoUseCase {
        return SearchAndFetchTudoGostosoUseCase(discovery, extractor, recipeRepository)
    }
}

