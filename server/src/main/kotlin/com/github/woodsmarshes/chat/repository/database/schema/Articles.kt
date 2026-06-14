package com.github.woodsmarshes.chat.repository.database.schema

import com.github.woodsmarshes.chat.core.model.ArticleStats
import com.github.woodsmarshes.chat.core.model.ArticleStatus
import com.github.woodsmarshes.chat.core.network.serialization.ProjectJson
import com.github.woodsmarshes.chat.repository.database.UuidV7Table
import kotlinx.serialization.json.JsonElement
import org.jetbrains.exposed.v1.datetime.timestamp
import org.jetbrains.exposed.v1.json.jsonb
import kotlin.time.Clock

object Articles : UuidV7Table("articles") {
    val title = varchar("title", length = 512)
    val content = jsonb<JsonElement>("content", ProjectJson)
    val authorId = reference("author_id", Users)
    val status = enumerationByName("status", 32, ArticleStatus::class)
    val excerpt = text("excerpt").nullable()
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at").clientDefault { Clock.System.now() }
    val publishedAt = timestamp("published_at").nullable()

    val coverImage = varchar("cover_image", length = 1024).nullable()
    val deletedAt = timestamp("deleted_at").nullable()
    val slug = varchar("slug", length = 512).nullable()

    val stats = jsonb<ArticleStats>("stats", ProjectJson).default(ArticleStats())

    init {
        index(false, authorId)
        index(false, status, createdAt)
        uniqueIndex(slug)
    }
}
