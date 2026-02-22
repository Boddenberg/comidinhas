package br.com.boddenb.comidinhas.di

import br.com.boddenb.comidinhas.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SupabaseModule {

    @Provides
    @Singleton
    fun provideSupabaseClient(): SupabaseClient {
        val url = BuildConfig.SUPABASE_URL
        val anonKey = BuildConfig.SUPABASE_ANON_KEY
        require(url.isNotBlank()) { "Missing SUPABASE_URL (configure in local.properties)" }
        require(anonKey.isNotBlank()) { "Missing SUPABASE_ANON_KEY (configure in local.properties)" }
        return createSupabaseClient(supabaseUrl = url, supabaseKey = anonKey) {
            install(Postgrest)
            install(Storage)
        }
    }
}
