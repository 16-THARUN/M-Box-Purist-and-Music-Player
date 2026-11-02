package com.mediaplayer.data.network

import com.mediaplayer.data.network.lyrics.ITunesApiService
import com.mediaplayer.data.network.lyrics.LrcLibApiService
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {
    private const val LRCLIB_URL = "https://lrclib.net/"
    private const val ITUNES_URL = "https://itunes.apple.com/"

    private val okHttpClient: OkHttpClient by lazy {
        val headerInterceptor = Interceptor { chain ->
            val originalRequest = chain.request()
            val requestWithUserAgent = originalRequest.newBuilder()
                .header("User-Agent", "MBoxPlayer/1.0 (contact@mediaplayer.com)")
                .header("Lrclib-Client", "MBoxPlayer/1.0")
                .build()
            chain.proceed(requestWithUserAgent)
        }

        OkHttpClient.Builder()
            .addInterceptor(headerInterceptor)
            .build()
    }

    val lrcLibApiService: LrcLibApiService by lazy {
        Retrofit.Builder()
            .baseUrl(LRCLIB_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(LrcLibApiService::class.java)
    }

    val iTunesApiService: ITunesApiService by lazy {
        Retrofit.Builder()
            .baseUrl(ITUNES_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ITunesApiService::class.java)
    }
}
