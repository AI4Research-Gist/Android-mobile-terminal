package com.example.ai4research.data.mapper

import com.example.ai4research.data.local.entity.ProjectContextDocumentEntity
import com.example.ai4research.domain.model.ProjectContextDocument
import java.util.Date
import java.util.UUID

object ProjectContextDocumentMapper {
    fun entityToDomain(entity: ProjectContextDocumentEntity): ProjectContextDocument {
        return ProjectContextDocument(
            projectId = entity.projectId,
            title = entity.title,
            markdownPath = entity.markdownPath,
            summary = entity.summary,
            keywords = entity.keywords
                .split(Regex("[,，\\n]+"))
                .map(String::trim)
                .filter(String::isNotBlank),
            updatedAt = Date(entity.updatedAt)
        )
    }

    fun createEntity(
        ownerUserId: String,
        projectId: String,
        title: String,
        markdownPath: String,
        summary: String,
        keywords: List<String>,
        updatedAt: Long
    ): ProjectContextDocumentEntity {
        return ProjectContextDocumentEntity(
            id = UUID.nameUUIDFromBytes("$ownerUserId#$projectId".toByteArray()).toString(),
            ownerUserId = ownerUserId,
            projectId = projectId,
            title = title,
            markdownPath = markdownPath,
            summary = summary,
            keywords = keywords.joinToString(","),
            updatedAt = updatedAt
        )
    }
}
