package com.example.ai4research.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class NocoItemDto(
    @SerialName("Id")
    val id: Int? = null,
    @SerialName("ownerId")
    val ownerUserId: String? = null,
    @SerialName("title")
    val title: String? = null,
    @SerialName("type")
    val type: String? = null,
    @SerialName("summary")
    val summary: String? = null,
    @SerialName("content_md")
    val contentMd: String? = null,
    @SerialName("origin_url")
    val originUrl: String? = null,
    @SerialName("audio_url")
    val audioUrl: String? = null,
    @SerialName("status")
    val status: String? = "processing (解析中)",
    @SerialName("read_status")
    val readStatus: String? = "unread (未读)",
    @SerialName("tags")
    val tags: String? = null,
    @SerialName("projects_count")
    val projectsCount: Int? = null,
    @SerialName("project_id")
    val projectId: Int? = null,
    @SerialName("meta_json")
    val metaJson: JsonElement? = null,
    @SerialName("CreatedAt")
    val createdAt: String? = null,
    @SerialName("UpdatedAt")
    val updatedAt: String? = null
)

@Serializable
data class NocoListResponse<T>(
    @SerialName("list")
    val list: List<T>,
    @SerialName("pageInfo")
    val pageInfo: PageInfo? = null
)

@Serializable
data class PageInfo(
    @SerialName("totalRows")
    val totalRows: Int? = null,
    @SerialName("page")
    val page: Int? = null,
    @SerialName("pageSize")
    val pageSize: Int? = null,
    @SerialName("isFirstPage")
    val isFirstPage: Boolean? = null,
    @SerialName("isLastPage")
    val isLastPage: Boolean? = null
)

@Serializable
data class NocoProjectDto(
    @SerialName("Id")
    val id: Int? = null,
    @SerialName("ownerId")
    val ownerUserId: String? = null,
    @SerialName("Title")
    val title: String? = null,
    @SerialName("name")
    val name: String? = null,
    @SerialName("description")
    val description: String? = null,
    @SerialName("CreatedAt")
    val createdAt: String? = null
)
