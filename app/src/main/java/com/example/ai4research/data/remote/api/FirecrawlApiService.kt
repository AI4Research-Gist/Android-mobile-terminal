package com.example.ai4research.data.remote.api

import com.example.ai4research.BuildConfig
import com.example.ai4research.data.remote.dto.FirecrawlScrapeRequest
import com.example.ai4research.data.remote.dto.FirecrawlScrapeResponse
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface FirecrawlApiService {

    @POST("scrape")
    suspend fun scrape(
        @Header("Authorization") authorization: String?,
        @Body request: FirecrawlScrapeRequest
    ): FirecrawlScrapeResponse

    companion object {
        const val BASE_URL: String = BuildConfig.FIRECRAWL_BASE_URL
    }
}
