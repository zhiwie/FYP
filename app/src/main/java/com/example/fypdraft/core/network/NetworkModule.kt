package com.example.fypdraft.core.network

import com.example.fypdraft.core.config.AppConfig
import com.example.fypdraft.data.api.ChatGPTApiService
import com.example.fypdraft.data.api.SpotifyApiService
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Centralised networking. One shared OkHttpClient, lazy Retrofit instances.
 */
object NetworkModule {

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            })
            .connectTimeout(AppConfig.CONNECT_TIMEOUT_SECS, TimeUnit.SECONDS)
            .readTimeout(AppConfig.READ_TIMEOUT_SECS, TimeUnit.SECONDS)
            .writeTimeout(AppConfig.WRITE_TIMEOUT_SECS, TimeUnit.SECONDS)
            .build()
    }

    private fun retrofit(baseUrl: String): Retrofit =
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

    val chatGPTApi: ChatGPTApiService by lazy {
        retrofit(AppConfig.OPENAI_BASE_URL).create(ChatGPTApiService::class.java)
    }

    val spotifyApi: SpotifyApiService by lazy {
        retrofit("https://api.spotify.com/v1/").create(SpotifyApiService::class.java)
    }
}