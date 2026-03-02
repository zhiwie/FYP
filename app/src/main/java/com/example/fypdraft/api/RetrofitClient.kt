package com.example.fypdraft.api

import com.example.fypdraft.data.api.ApiConfig
import com.example.fypdraft.data.api.ChatGPTApiService
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {

    // ── Shared OkHttp client ──────────────────────────────────────────────────
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    // ── Existing local backend (unchanged) ────────────────────────────────────
    private const val BASE_URL = "http://192.168.68.139:5000/"

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val apiService: ApiService = retrofit.create(ApiService::class.java)

    // ── OpenAI ChatGPT client (new) ───────────────────────────────────────────
    val chatGPTApiService: ChatGPTApiService by lazy {
        Retrofit.Builder()
            .baseUrl(ApiConfig.OPENAI_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ChatGPTApiService::class.java)
    }
}