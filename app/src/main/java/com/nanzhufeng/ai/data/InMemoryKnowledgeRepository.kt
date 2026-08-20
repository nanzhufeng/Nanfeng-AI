package com.nanzhufeng.ai.data

import com.nanzhufeng.ai.domain.KnowledgeItem
import com.nanzhufeng.ai.domain.KnowledgeItemId
import com.nanzhufeng.ai.domain.KnowledgeRepository

class InMemoryKnowledgeRepository : KnowledgeRepository {
    private val items = linkedMapOf<KnowledgeItemId, KnowledgeItem>()

    override fun save(item: KnowledgeItem): KnowledgeItem = item.also { items[it.id] = it }

    override fun findById(id: KnowledgeItemId): KnowledgeItem? = items[id]

    override fun listAll(): List<KnowledgeItem> = items.values.toList()
}
