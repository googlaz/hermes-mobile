package com.hermes.app.di

import com.hermes.app.data.local.SecurePreferences
import com.hermes.app.data.remote.AuthInterceptor
import com.hermes.app.data.remote.DynamicUrlInterceptor
import com.hermes.app.data.remote.HermesApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideLoggingInterceptor(): HttpLoggingInterceptor {
        return HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        authInterceptor: AuthInterceptor,
        dynamicUrlInterceptor: DynamicUrlInterceptor,
        loggingInterceptor: HttpLoggingInterceptor
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(dynamicUrlInterceptor) // На лету подставляет актуальный сохраненный IP (для ФТ-1.1, ФТ-6.1)
            .addInterceptor(authInterceptor)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(180, TimeUnit.SECONDS)   // Локальный inference может занять 30-120с
            .writeTimeout(60, TimeUnit.SECONDS)
            .callTimeout(200, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(
        okHttpClient: OkHttpClient,
        securePreferences: SecurePreferences
    ): Retrofit {
        // Дефолтный базовый URL (будет заменен динамически с помощью DynamicUrlInterceptor)
        val host = securePreferences.tailscaleHost ?: "127.0.0.1"
        val port = securePreferences.serverPort
        val baseUrl = "http://$host:$port/"

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun provideHermesApiService(
        retrofit: Retrofit
    ): HermesApiService {
        return retrofit.create(HermesApiService::class.java)
    }
}
