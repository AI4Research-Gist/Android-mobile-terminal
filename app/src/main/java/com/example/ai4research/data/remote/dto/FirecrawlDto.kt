package com.example.ai4research.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FirecrawlScrapeRequest(
    val url: String,
    val formats: List<String> = listOf("markdown", "html", "rawHtml", "links"),
    @SerialName("onlyMainContent")
    val onlyMainContent: Boolean = true,
    val timeout: Int = 30_000,
    val waitFor: Int? = null,
    val mobile: Boolean = false,
    val blockAds: Boolean = true,
    val proxy: String = "auto",
    @SerialName("removeBase64Images")
    val removeBase64Images: Boolean = true,
    @SerialName("skipTlsVerification")
    val skipTlsVerification: Boolean = false
)

@Serializable
data class FirecrawlScrapeResponse(
    val success: Boolean,
    val data: FirecrawlScrapeData? = null,
    val error: String? = null,
    val warning: String? = null
)

@Serializable
data class FirecrawlScrapeData(
    val markdown: String? = null,
    val summary: String? = null,
    val html: String? = null,
    val rawHtml: String? = null,
    val links: List<String> = emptyList(),
    val metadata: FirecrawlMetadata? = null
)

@Serializable
data class FirecrawlMetadata(
    val title: String? = null,
    val description: String? = null,
    val language: String? = null,
    val keywords: String? = null,
    val author: String? = null,
    @SerialName("sourceURL")
    val sourceUrl: String? = null,
    val url: String? = null,
    @SerialName("statusCode")
    val statusCode: Int? = null,
    @SerialName("contentType")
    val contentType: String? = null
)
