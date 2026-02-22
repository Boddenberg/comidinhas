package br.com.boddenb.comidinhas.di

import br.com.boddenb.comidinhas.data.cache.RecipeCacheManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CacheModule {

    @Provides
    @Singleton
    fun provideRecipeCacheManager(): RecipeCacheManager {
        val defaultMaxSize = 50
        val defaultTtl = 1000L * 60L * 10L // 10 minutes
        return RecipeCacheManager(defaultMaxSize, defaultTtl)
    }
}

