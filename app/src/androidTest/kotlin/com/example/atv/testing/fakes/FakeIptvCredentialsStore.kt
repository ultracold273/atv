package com.example.atv.testing.fakes

import com.example.atv.domain.model.IptvCredentials
import com.example.atv.domain.repository.IptvCredentialsStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeIptvCredentialsStore : IptvCredentialsStore {
    private val credentials = MutableStateFlow<IptvCredentials?>(null)

    override fun observe(): Flow<IptvCredentials?> = credentials

    override suspend fun read(): IptvCredentials? = credentials.value

    override suspend fun save(creds: IptvCredentials) {
        credentials.value = creds
    }

    override suspend fun clear() {
        credentials.value = null
    }
}
