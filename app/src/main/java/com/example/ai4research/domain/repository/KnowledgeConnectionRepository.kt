package com.example.ai4research.domain.repository

import com.example.ai4research.domain.model.ItemConnection
import kotlinx.coroutines.flow.Flow

interface KnowledgeConnectionRepository {
    fun observeConnectionsForItem(itemId: String): Flow<List<ItemConnection>>

    suspend fun getConnectionsForItem(itemId: String): List<ItemConnection>

    suspend fun rebuildAutoConnectionsForItem(itemId: String): Result<Unit>

    suspend fun replaceInsightConnections(insightId: String, targetItemIds: List<String>): Result<Unit>

    suspend fun deleteConnectionsForItem(itemId: String): Result<Unit>
}
