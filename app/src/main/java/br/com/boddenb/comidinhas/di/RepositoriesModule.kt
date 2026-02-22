package br.com.boddenb.comidinhas.di

import br.com.boddenb.comidinhas.data.repository.LocationRepositoryImpl
import br.com.boddenb.comidinhas.data.repository.RestaurantRepositoryImpl
import br.com.boddenb.comidinhas.domain.repository.LocationRepository
import br.com.boddenb.comidinhas.domain.repository.RestaurantRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoriesModule {
    @Binds
    @Singleton
    abstract fun bindLocationRepository(impl: LocationRepositoryImpl): LocationRepository

    @Binds
    @Singleton
    abstract fun bindRestaurantRepository(impl: RestaurantRepositoryImpl): RestaurantRepository
}

