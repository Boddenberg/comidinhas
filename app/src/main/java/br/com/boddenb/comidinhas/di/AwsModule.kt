package br.com.boddenb.comidinhas.di

import br.com.boddenb.comidinhas.data.repository.RecipeRepository
import br.com.boddenb.comidinhas.data.repository.supabase.RecipeSupabaseRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.SupabaseClient
import javax.inject.Singleton

/**
 * Provê dependências de banco de dados (Supabase).
 * Renomeado de AwsModule após migração para Supabase.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideRecipeRepository(supabaseClient: SupabaseClient): RecipeRepository {
        return RecipeSupabaseRepository(supabaseClient)
    }
}
