package com.github.woodsmarshes.web.pages.admin

import com.github.woodsmarshes.chat.core.contract.dto.admin.AdminConversationDetailResponse
import com.github.woodsmarshes.chat.core.contract.dto.admin.AdminMessageListResponse
import com.github.woodsmarshes.chat.core.model.ConversationType
import com.github.woodsmarshes.chat.core.model.JoinGroupContent
import com.github.woodsmarshes.chat.core.model.MediaContent
import com.github.woodsmarshes.chat.core.model.Message
import com.github.woodsmarshes.chat.core.model.MessageCategory
import com.github.woodsmarshes.chat.core.model.MessageContent
import com.github.woodsmarshes.chat.core.model.System
import com.github.woodsmarshes.chat.core.model.TextContent
import com.github.woodsmarshes.web.Router
import com.github.woodsmarshes.web.components.AdminShell
import com.github.woodsmarshes.web.components.formatAdminTime
import com.github.woodsmarshes.web.koinInject
import com.github.woodsmarshes.web.storage.AdminRepository
import com.github.woodsmarshes.web.storage.describeAdminError
import kotlinx.browser.window
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.promise
import react.FC
import react.Key
import react.Props
import react.dom.html.ReactHTML.a
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.h2
import react.dom.html.ReactHTML.h3
import react.dom.html.ReactHTML.p
import react.dom.html.ReactHTML.span
import react.dom.html.ReactHTML.table
import react.dom.html.ReactHTML.tbody
import react.dom.html.ReactHTML.td
import react.dom.html.ReactHTML.th
import react.dom.html.ReactHTML.thead
import react.dom.html.ReactHTML.tr
import react.useEffect
import react.useState
import web.cssom.ClassName
import kotlin.uuid.Uuid

private fun parseAdminConversationId(): Uuid? =
    Router.currentPath().substringAfterLast('/').let { raw -> runCatching { Uuid.parse(raw) }.getOrNull() }

private fun messagePreview(message: Message): String =
    when (val c = message.content) {
        is TextContent -> c.text.ifBlank { "[空文本]" }
        is MediaContent -> "[${c.fileName}]"
        is JoinGroupContent -> "${c.userName} 加入群组"
        is System -> "[系统消息]"
    }

private fun senderLabel(message: Message): String = message.sender?.let { it.displayName ?: it.username } ?: "系统"

val AdminConversationDetailPage =
    FC<Props> {
        AdminShell {
            val conversationId = parseAdminConversationId()
            var detail by useState<AdminConversationDetailResponse?>(null)
            var messages by useState<List<Message>>(emptyList())
            var hasMore by useState(false)
            var loading by useState(true)
            var loadingMore by useState(false)
            var error by useState<String?>(null)
            var actionError by useState<String?>(null)
            var notice by useState<String?>(null)
            var busy by useState(false)

            val loadDetail: suspend (clearActionState: Boolean) -> Unit = { clearActionState ->
                if (conversationId == null) {
                    loading = false
                    error = "无效的会话 ID"
                } else {
                    loading = true
                    error = null
                    if (clearActionState) {
                        detail = null
                        messages = emptyList()
                        hasMore = false
                        actionError = null
                        notice = null
                    }
                    try {
                        detail = AdminRepository.getConversation(conversationId)
                        val page = AdminRepository.listConversationMessages(conversationId, beforeId = null, limit = 50)
                        messages = page.items
                        hasMore = page.hasMore
                    } catch (e: Exception) {
                        error = describeAdminError(e)
                        detail = null
                    } finally {
                        loading = false
                    }
                }
            }

            useEffect(conversationId) { loadDetail(true) }

            fun runAction(
                confirmText: String,
                block: suspend () -> String?,
            ) {
                if (busy || conversationId == null) return
                if (!window.confirm(confirmText)) return
                busy = true
                actionError = null
                notice = null
                koinInject<CoroutineScope>().promise {
                    try {
                        notice = block()
                        loadDetail(false)
                    } catch (e: Exception) {
                        actionError = describeAdminError(e)
                    } finally {
                        busy = false
                    }
                }
            }

            fun loadOlder() {
                val cid = conversationId ?: return
                val before = messages.lastOrNull()?.id ?: return
                if (loadingMore) return
                loadingMore = true
                koinInject<CoroutineScope>().promise {
                    try {
                        val page = AdminRepository.listConversationMessages(cid, beforeId = before, limit = 50)
                        messages = messages + page.items
                        hasMore = page.hasMore
                    } catch (e: Exception) {
                        actionError = describeAdminError(e)
                    } finally {
                        loadingMore = false
                    }
                }
            }

            div {
                className = ClassName("toolbar")
                h2 { +"会话详情" }
                div { className = ClassName("toolbar-spacer") }
                a {
                    href = "#/admin/conversations"
                    className = ClassName("btn btn-sm")
                    onClick = { event ->
                        event.preventDefault()
                        Router.navigate("/admin/conversations")
                    }
                    +"返回列表"
                }
            }

            when {
                conversationId == null -> {
                    div {
                        className = ClassName("error-message")
                        p { +"无效的会话 ID" }
                    }
                }

                loading -> {
                    div {
                        className = ClassName("loading-container")
                        div { className = ClassName("loading-spinner") }
                    }
                }

                error != null -> {
                    div {
                        className = ClassName("error-message")
                        p { +(error ?: "") }
                    }
                }

                detail != null -> {
                    val d = detail!!
                    val c = d.conversation
                    val title =
                        d.groupName?.takeIf { it.isNotBlank() }
                            ?: if (c.type == ConversationType.GROUP) "群聊 ${c.id}" else "私聊 ${c.id}"

                    div {
                        className = ClassName("admin-detail-grid")
                        div {
                            className = ClassName("admin-card")
                            h3 { +title }
                            div {
                                className = ClassName("admin-field")
                                span {
                                    className = ClassName("admin-field-label")
                                    +"类型"
                                }
                                span {
                                    +(
                                        if (c.type ==
                                            ConversationType.GROUP
                                        ) {
                                            "群聊"
                                        } else {
                                            "私聊"
                                        }
                                    )
                                }
                            }
                            div {
                                className = ClassName("admin-field")
                                span {
                                    className = ClassName("admin-field-label")
                                    +"ID"
                                }
                                span { +c.id.toString() }
                            }
                            div {
                                className = ClassName("admin-field")
                                span {
                                    className = ClassName("admin-field-label")
                                    +"创建时间"
                                }
                                span { +formatAdminTime(c.createdAt) }
                            }
                            div {
                                className = ClassName("admin-field")
                                span {
                                    className = ClassName("admin-field-label")
                                    +"更新时间"
                                }
                                span { +formatAdminTime(c.updatedAt) }
                            }
                            div {
                                className = ClassName("admin-field")
                                span {
                                    className = ClassName("admin-field-label")
                                    +"状态"
                                }
                                span {
                                    +(
                                        if (c.deletedAt !=
                                            null
                                        ) {
                                            "已删除"
                                        } else {
                                            "正常"
                                        }
                                    )
                                }
                            }
                        }
                        div {
                            className = ClassName("admin-card")
                            h3 { +"统计" }
                            div {
                                className = ClassName("admin-field")
                                span {
                                    className = ClassName("admin-field-label")
                                    +"参与者"
                                }
                                span { +d.participantCount.toString() }
                            }
                            div {
                                className = ClassName("admin-field")
                                span {
                                    className = ClassName("admin-field-label")
                                    +"消息数"
                                }
                                span { +d.messageCount.toString() }
                            }
                        }
                    }

                    if (actionError != null || notice != null) {
                        div {
                            className =
                                ClassName(if (actionError != null) "admin-alert admin-alert-error" else "admin-alert admin-alert-success")
                            p { +(actionError ?: notice ?: "") }
                        }
                    }

                    if (c.deletedAt == null) {
                        div {
                            className = ClassName("admin-card")
                            h3 { +"危险操作" }
                            button {
                                className = ClassName("btn btn-sm btn-danger")
                                disabled = busy
                                onClick = {
                                    runAction("确认删除该会话？将对所有成员生效并同步客户端。") {
                                        AdminRepository.deleteConversation(conversationId)
                                        "已删除会话"
                                    }
                                }
                                +"删除会话"
                            }
                        }
                    }

                    div {
                        className = ClassName("admin-card")
                        h3 { +"参与者" }
                        if (d.participants.isEmpty()) {
                            p {
                                className = ClassName("admin-cell-secondary")
                                +"没有参与者"
                            }
                        } else {
                            div {
                                className = ClassName("admin-table-wrap")
                                table {
                                    className = ClassName("admin-table")
                                    thead {
                                        tr {
                                            th { +"用户" }
                                            th { +"角色" }
                                            th { +"加入时间" }
                                        }
                                    }
                                    tbody {
                                        d.participants.forEach { p ->
                                            tr {
                                                key = Key(p.user.id.toString())
                                                td {
                                                    div {
                                                        className = ClassName("admin-cell-primary")
                                                        +(p.user.displayName ?: p.user.username)
                                                    }
                                                    div {
                                                        className = ClassName("admin-cell-secondary")
                                                        +"@${p.user.username}"
                                                    }
                                                }
                                                td { +p.role.name }
                                                td { +formatAdminTime(p.joinedAt) }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    div {
                        className = ClassName("admin-card")
                        h3 { +"消息（最新在前）" }
                        if (hasMore) {
                            button {
                                className = ClassName("btn btn-sm")
                                disabled = loadingMore
                                onClick = { loadOlder() }
                                +if (loadingMore) "加载中..." else "加载更早"
                            }
                        }
                        if (messages.isEmpty()) {
                            p {
                                className = ClassName("admin-cell-secondary")
                                +"没有消息"
                            }
                        } else {
                            messages.forEach { m ->
                                div {
                                    key = Key(m.id.toString())
                                    className = ClassName("admin-message" + if (m.revokedAt != null) " admin-message-revoked" else "")
                                    div {
                                        className = ClassName("admin-message-head")
                                        span {
                                            className = ClassName("admin-message-sender")
                                            +senderLabel(m)
                                        }
                                        span {
                                            className = ClassName("admin-message-time")
                                            +formatAdminTime(m.createdAt)
                                        }
                                        if (m.revokedAt != null) {
                                            span {
                                                className = ClassName("admin-badge admin-badge-banned")
                                                +"已撤回"
                                            }
                                        }
                                        if (m.category == MessageCategory.NORMAL && m.revokedAt == null) {
                                            button {
                                                className = ClassName("btn btn-sm btn-danger")
                                                disabled = busy
                                                onClick = {
                                                    runAction("确认撤回该消息？内容将被永久清空并同步客户端。") {
                                                        AdminRepository.revokeMessage(m.id)
                                                        "已撤回消息"
                                                    }
                                                }
                                                +"撤回"
                                            }
                                        }
                                    }
                                    div {
                                        className = ClassName("admin-message-body")
                                        +(if (m.revokedAt != null) "[已撤回]" else messagePreview(m))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
