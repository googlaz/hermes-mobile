package com.hermes.app.di

import android.content.Context
import com.hermes.app.data.local.SecurePreferences
import com.hermes.app.data.remote.TailscaleManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object TailscaleModule {

    @Provides
    @Singleton
    fun provideTailscaleManager(
        @ApplicationContext context: Context,
        securePreferences: SecurePreferences
    ): TailscaleManager {
        return TailscaleManager(context, securePreferences)
    }
}
