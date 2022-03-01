package com.lahsuak.apps.locationtracker.di

import android.content.Context
import com.google.android.gms.location.FusedLocationProviderClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.ServiceScoped
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton


@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Singleton
    @Provides
    fun provideFusedLocationProviderClient2(
        @ApplicationContext app: Context
    ) = FusedLocationProviderClient(app)

////    @Singleton
////    @Provides
////    fun provideSharedPreferences(@ApplicationContext app: Context) =
////        app.getSharedPreferences(SHARED_PREFERENCES_NAME, MODE_PRIVATE)
////
////    @Singleton
////    @Provides
////    fun provideName(sharedPref: SharedPreferences) = sharedPref.getString(KEY_NAME, "") ?: ""
////
////    @Singleton
////    @Provides
////    fun provideWeight(sharedPref: SharedPreferences) = sharedPref.getFloat(KEY_WEIGHT, 80f)
//
//    @Singleton
//    @Provides
//    fun provideFirstTimeToggle(sharedPref: SharedPreferences) =
//        sharedPref.getBoolean(KEY_FIRST_TIME_TOGGLE, true)

}








