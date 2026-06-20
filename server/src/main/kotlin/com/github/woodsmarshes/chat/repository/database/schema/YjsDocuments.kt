package com.github.woodsmarshes.chat.repository.database.schema

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.timestamp

object YjsDocuments : Table("yjs_documents") {
    val articleId = reference("article_id", Articles.id, onDelete = ReferenceOption.CASCADE).uniqueIndex()
    val state = blob("state")
    val updatedAt = timestamp("updated_at")

    override val primaryKey = PrimaryKey(articleId)
}