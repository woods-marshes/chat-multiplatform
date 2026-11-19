package com.github.woodsmarshes.web.pages.admin

import com.github.woodsmarshes.chat.core.contract.dto.admin.AdminLogListResponse
import com.github.woodsmarshes.web.components.AdminShell
import com.github.woodsmarshes.web.components.formatAdminTime
import com.github.woodsmarshes.web.storage.AdminRepository
import com.github.woodsmarshes.web.storage.describeAdminError
import react.FC
import react.Key
import react.Props
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

private fun logLevelClass(level: String): String = when (level.uppercase()) {
    "ERROR" -> "admin-badge admin-badge-banned"
    "WARN" -> "admin-badge admin-badge-guest"
    else -> "admin-badge admin-badge-normal"
}

val AdminLogsPage = FC<Props> {
    AdminShell {
        var level by useState("")
        var reload by useState(0)
        var result by useState<AdminLogListResponse?>(null)
        var loading by useState(true)
        var error by useState<String?>(null)

        useEffect(level, reload) {
            loading = true
            error = null
            try {
                result = AdminRepository.listLogs(level = level.ifBlank { null }, limit = 100)
            } catch (e: Exception) {
                error = describeAdminError(e)
                result = null
            } finally {
                loading = false
            }
        }

        div {
            className = ClassName("toolbar")
            h2 { +"日志监控" }
            div { className = ClassName("toolbar-spacer") }
            select {
                className = ClassName("admin-filter-select")
                value = level
                onChange = { event -> level = event.target.value.unsafeCast<String>() }
                option { value = ""; +"全部级别" }
                option { value = "WARN"; +"WARN" }
                option { value = "ERROR"; +"ERROR" }
            }
            button {
                className = ClassName("btn btn-sm")
                onClick = { reload++ }
                +"刷新"
            }
        }
        AdminLogContent.invoke {
            logResult = result
            isLoading = loading
            errorMessage = error
        }
    }
}

val AdminAuditLogsPage = FC<Props> {
    AdminShell {
        var reload by useState(0)
        var result by useState<AdminLogListResponse?>(null)
        var loading by useState(true)
        var error by useState<String?>(null)

        useEffect(reload) {
            loading = true
            error = null
            try {
                result = AdminRepository.listAuditLogs(limit = 100)
            } catch (e: Exception) {
                error = describeAdminError(e)
                result = null
            } finally {
                loading = false
            }
        }

        div {
            className = ClassName("toolbar")
            h2 { +"审计日志" }
            div { className = ClassName("toolbar-spacer") }
            button {
                className = ClassName("btn btn-sm")
                onClick = { reload++ }
                +"刷新"
            }
        }
        AdminLogContent.invoke {
            logResult = result
            isLoading = loading
            errorMessage = error
        }
    }
}

private external interface AdminLogContentProps : Props {
    var logResult: AdminLogListResponse?
    var isLoading: Boolean
    var errorMessage: String?
}

private val AdminLogContent = FC<AdminLogContentProps> { props ->
    when {
        props.isLoading -> div {
            className = ClassName("loading-container")
            div { className = ClassName("loading-spinner") }
        }
        props.errorMessage != null -> div {
            className = ClassName("error-message")
            p { +(props.errorMessage ?: "") }
        }
        props.logResult != null -> {
            val items = props.logResult!!.items
            if (items.isEmpty()) {
                div {
                    className = ClassName("empty-state")
                    p { +"没有日志" }
                }
            } else {
                div {
                    className = ClassName("admin-table-wrap")
                    table {
                        className = ClassName("admin-table")
                        thead {
                            tr {
                                th { +"时间" }
                                th { +"级别" }
                                th { +"Logger" }
                                th { +"消息" }
                                th { +"详情" }
                            }
                        }
                        tbody {
                            items.forEachIndexed { index, item ->
                                tr {
                                    key = Key(index.toString())
                                    td { +formatAdminTime(item.timestamp) }
                                    td {
                                        span {
                                            className = ClassName(logLevelClass(item.level))
                                            +item.level
                                        }
                                    }
                                    td { +item.logger }
                                    td { +item.message }
                                    td {
                                        className = ClassName("admin-cell-secondary")
                                        +(item.detail ?: "—")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
