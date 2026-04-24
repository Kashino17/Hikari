package com.hikari.app.di

import com.hikari.app.data.api.HikariApi
import com.hikari.app.data.prefs.SettingsStore
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides @Singleton
    fun provideOkHttpClient(store: SettingsStore): OkHttpClient {
        val baseUrlInterceptor = Interceptor { chain ->
            val currentBase = runBlocking { store.backendUrl.first() }
            val base = currentBase.toHttpUrlOrNull() ?: return@Interceptor chain.proceed(chain.request())
            val orig = chain.request()
            val newUrl = orig.url.newBuilder()
                .scheme(base.scheme)
                .host(base.host)
                .port(base.port)
                .build()
            chain.proceed(orig.newBuilder().url(newUrl).build())
        }
        val logging = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }
        return OkHttpClient.Builder()
            .addInterceptor(baseUrlInterceptor)
            .addInterceptor(logging)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .callTimeout(0, TimeUnit.SECONDS)   // no overall timeout — streaming calls run until done
            .retryOnConnectionFailure(true)
            .build()
    }

    @Provides @Singleton
    fun provideJson(): Json = Json { ignoreUnknownKeys = true }

    @Provides @Singleton
    fun provideRetrofit(client: OkHttpClient, json: Json, store: SettingsStore): Retrofit {
        val initialBase = runBlocking { store.backendUrl.first() }
            .let { if (it.endsWith("/")) it else "$it/" }
        return Retrofit.Builder()
            .baseUrl(initialBase)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    @Provides @Singleton
    fun provideHikariApi(retrofit: Retrofit): HikariApi = retrofit.create(HikariApi::class.java)
}
