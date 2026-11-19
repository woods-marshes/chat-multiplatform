package com.github.woodsmarshes.web.pages.admin

import com.github.woodsmarshes.chat.core.contract.dto.admin.AdminConversationListResponse
import com.github.woodsmarshes.chat.core.model.ConversationType
import com.github.woodsmarshes.web.Router
import com.github.woodsmarshes.web.components.AdminPagination
import com.github.woodsmarshes.web.components.AdminShell
import com.github.woodsmarshes.web.components.formatAdminTime
import com.github.woodsmarshes.web.storage.AdminRepository
import com.github.woodsmarshes.web.storage.describeAdminError
import react.FC
import react.Key
import react.Props
import react.dom.html.ReactHTML.a
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.h2
import react.dom.html.ReactHTML.option
import react.dom.html.ReactHTML.p
import react.dom.html.ReactHTML.select
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

private data class AdminConversationQuery(
    val type: ConversationType? = null,
    val page: Int = 1,
)

private fun String.toConversationTypeOrNull(): ConversationType? = when (this) {
    "GROUP" -> ConversationType.GROUP
    "PRIVATE" -> ConversationType.PRIVATE
    else -> null
}

private fun conversationTitle(item: com.github.woodsmarshes.chat.core.contract.dto.admin.AdminConversationListItem): String =
    item.groupName?.takeIf { it.isNotBlank() }
        ?: if (item.conversation.type == ConversationType.GROUP) "群聊 ${item.conversation.id}" else "私聊 ${item.conversation.id}"

val AdminConversationsPage = FC<Props> {
    AdminShell {
        var typeSel by useState("")
        var query by useState(AdminConversationQuery())
        var result by useState<AdminConversationListResponse?>(null)
        var loading by useState(true)
        var error by useState<String?>(null)

        useEffect(query) {
            loading = true
            error = null
            try {
                result = AdminRepository.listConversations(type = query.type, page = query.page)
            } catch (e: Exception) {
                error = describeAdminError(e)
                result = null
            } finally {
                loading = false
            }
        }

        div {
            className = ClassName("toolbar")
            h2 { +"会话管理" }
            div { className = ClassName("toolbar-spacer") }
            select {
                className = ClassName("admin-filter-select")
                value = typeSel
                onChange = { event -> typeSel = event.target.value.unsafeCast<String>() }
                option { value = ""; +"全部类型" }
                option { value = "GROUP"; +"群聊" }
                option { value = "PRIVATE"; +"私聊" }
            }
            button {
                className = ClassName("btn btn-primary btn-sm")
                onClick = { query = AdminConversationQuery(type = typeSel.toConversationTypeOrNull(), page = 1) }
                +"查询"
            }
        }

        when {
            loading -> div {
                className = ClassName("loading-container")
                div { className = ClassName("loading-spinner") }
            }
            error != null -> div {
                className = ClassName("error-message")
                p { +(error ?: "") }
            }
            result != null -> {
                val items = result!!.items
                if (items.isEmpty()) {
                    div {
                        className = ClassName("empty-state")
                        p { +"没有会话" }
                    }
                } else {
                    div {
                        className = ClassName("admin-table-wrap")
                        table {
                            className = ClassName("admin-table")
                            thead {
                                tr {
                                    th { +"会话" }
                                    th { +"类型" }
                                    th { +"参与者" }
                                    th { +"消息数" }
                                    th { +"更新时间" }
                                    th { +"操作" }
                                }
                            }
                            tbody {
                                items.forEach { item ->
                                    val c = item.conversation
                                    tr {
                                        key = Key(c.id.toString())
                                        td {
                                            div { className = ClassName("admin-cell-primary"); +conversationTitle(item) }
                                            div { className = ClassName("admin-cell-secondary"); +c.id.toString() }
                                        }
                                        td {
                                            span {
                                                className = ClassName(
                                                    if (c.type == ConversationType.GROUP) "admin-badge admin-badge-group" else "admin-badge admin-badge-private"
                                                )
                                                +(if (c.type == ConversationType.GROUP) "群聊" else "私聊")
                                            }
                                        }
                                        td { +item.participantCount.toString() }
                                        td { +item.messageCount.toString() }
                                        td {
                                            +formatAdminTime(c.updatedAt)
                                            if (c.deletedAt != null) {
                                                span { className = ClassName("admin-badge admin-badge-banned"); +" 已删除" }
                                            }
                                        }
                                        td {
                                            a {
                                                href = "#/admin/conversations/${c.id}"
                                                className = ClassName("admin-link")
                                                onClick = { event ->
                                                    event.preventDefault()
                                                    Router.navigate("/admin/conversations/${c.id}")
                                                }
                                                +"详情"
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    AdminPagination.invoke {
                        page = query.page
                        total = result!!.total
                        limit = result!!.limit
                        onPage = { p -> query = query.copy(page = p) }
                    }
                }
            }
        }
    }
}
