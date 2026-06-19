package com.example.atv.di

import com.example.atv.data.local.secure.IptvCredentialsStoreImpl
import com.example.atv.domain.repository.IptvCredentialsStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SecureStorageModule {
    @Binds
    @Singleton
    abstract fun bindIptvCredentialsStore(impl: IptvCredentialsStoreImpl): IptvCredentialsStore
}
