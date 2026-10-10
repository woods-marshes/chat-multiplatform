package com.github.woodsmarshes.chat.core.database.dao

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.github.woodsmarshes.chat.core.database.di.createDatabase
import com.github.woodsmarshes.chat.core.model.ConversationRole
import com.github.woodsmarshes.chat.core.model.ConversationType
import com.github.woodsmarshes.chat.core.model.MessageCategory
import com.github.woodsmarshes.chat.core.model.MessageRenderType
import com.github.woodsmarshes.chat.core.model.ParticipantSettings
import com.github.woodsmarshes.chat.core.model.TextContent
import com.github.woodsmarshes.chat.core.model.UserRole
import io.github.woodsmarshes.chat.db.ConversationEntity
import io.github.woodsmarshes.chat.db.MessageEntity
import io.github.woodsmarshes.chat.db.ParticipantEntity
import io.github.woodsmarshes.chat.db.UserEntity
import kotlinx.coroutines.runBlocking
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * 会话列表排序契约：按会话内最后一条消息的时间倒序；无消息的会话退回创建时间。
 *
 * 排序绝不能依赖 ConversationEntity.updated_at —— sync 会把它写成与消息无关的
 * 时间（私聊是对方资料更新时间、群聊是群资料更新时间），冷启动同步后会把
 * 老会话错误地顶到最上面。
 */
class ConversationListOrderingTest {
    private val me = Uuid.parse("018f0000-0000-7000-8000-000000000001")
    private val sender = Uuid.parse("018f0000-0000-7000-8000-000000000002")
    private val convOld = Uuid.parse("018f0000-0000-7000-8000-000000000010")
    private val convNew = Uuid.parse("018f0000-0000-7000-8000-000000000011")
    private val convEmpty = Uuid.parse("018f0000-0000-7000-8000-000000000012")
    private val msgOld = Uuid.parse("018f0000-0000-7000-8000-000000000020")
    private val msgNew = Uuid.parse("018f0000-0000-7000-8000-000000000021")

    private val t0 = Instant.fromEpochMilliseconds(1_700_000_000_000)

    private fun at(msAfterT0: Long) = Instant.fromEpochMilliseconds(1_700_000_000_000L + msAfterT0)

    @Test
    fun orderedByLastMessageTimeWithCreatedTimeFallback() = runBlocking {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, Properties().apply { put("foreign_keys", "true") })
        val db = createDatabase { schema -> schema.create(driver).await(); driver }
        try {
            db.usersQueries.upsertUser(UserEntity(me, "me", null, null, null, null, t0, t0, null, UserRole.MEMBER))
            db.usersQueries.upsertUser(UserEntity(sender, "wood", null, null, null, null, t0, t0, null, UserRole.MEMBER))

            // 老会话：创建最早、消息最旧；updated_at 被刷成最新的垃圾值
            // （模拟冷启动 sync 写入的"对方资料更新时间"）。
            db.conversationsQueries.upsertConversation(
                ConversationEntity(convOld, ConversationType.GROUP, msgOld, null, t0, at(10_000), null)
            )
            // 新会话：创建晚于老会话，但持有最新的消息。
            db.conversationsQueries.upsertConversation(
                ConversationEntity(convNew, ConversationType.PRIVATE, msgNew, null, at(1_000), at(3_000), null)
            )
            // 无消息会话：创建时间晚于老会话，从未收到过消息。
            db.conversationsQueries.upsertConversation(
                ConversationEntity(convEmpty, ConversationType.GROUP, null, null, at(500), at(500), null)
            )

            listOf(convOld, convNew, convEmpty).forEach { conversationId ->
                db.participantsQueries.insertParticipant(
                    ParticipantEntity(conversationId, me, ConversationRole.MEMBER, null, t0, null, ParticipantSettings())
                )
            }

            db.messagesQueries.insertMessage(
                MessageEntity(
                    id = msgOld, conversation_id = convOld, user_id = sender,
                    category = MessageCategory.NORMAL, render_type = MessageRenderType.TEXT,
                    content = TextContent("old"), reply_to_message_id = null,
                    created_at = at(2_000), revoked_at = null, local_send_status = null,
                )
            )
            db.messagesQueries.insertMessage(
                MessageEntity(
                    id = msgNew, conversation_id = convNew, user_id = sender,
                    category = MessageCategory.NORMAL, render_type = MessageRenderType.TEXT,
                    content = TextContent("new"), reply_to_message_id = null,
                    created_at = at(3_000), revoked_at = null, local_send_status = null,
                )
            )

            val order = db.conversationsQueries.getConversationListView(me)
                .executeAsList()
                .map { it.conversation_id }

            // 旧排序（C.updated_at DESC）会把 updated_at 被刷成最新值的 convOld 排到最前。
            assertEquals(listOf(convNew, convOld, convEmpty), order)
        } finally {
            driver.close()
        }
    }
}
