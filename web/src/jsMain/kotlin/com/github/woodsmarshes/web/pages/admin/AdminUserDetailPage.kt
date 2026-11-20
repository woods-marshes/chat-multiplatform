package com.github.woodsmarshes.web.pages.admin

import com.github.woodsmarshes.chat.core.contract.dto.admin.AdminUserDetailResponse
import com.github.woodsmarshes.chat.core.contract.dto.auth.AuthSessionListResponse
import com.github.woodsmarshes.chat.core.model.UserRole
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
import kotlin.uuid.Uuid

private fun parseAdminUserId(): Uuid? =
    Router.currentPath().substringAfterLast('/').let { raw ->
        runCatching { Uuid.parse(raw) }.getOrNull()
    }

val AdminUserDetailPage =
    FC<Props> {
        AdminShell {
            val userId = parseAdminUserId()
            var detail by useState<AdminUserDetailResponse?>(null)
            var sessions by useState<AuthSessionListResponse?>(null)
            var loading by useState(true)
            var error by useState<String?>(null)
            var busy by useState(false)
            var actionError by useState<String?>(null)
            var notice by useState<String?>(null)
            var tempPassword by useState<String?>(null)
            var roleSel by useState("")

            val load: suspend (clearActionState: Boolean) -> Unit = { clearActionState ->
                if (userId == null) {
                    loading = false
                    error = "无效的用户 ID"
                } else {
                    loading = true
                    error = null
                    if (clearActionState) {
                        tempPassword = null
                        notice = null
                        actionError = null
                        roleSel = ""
                    }
                    try {
                        detail = AdminRepository.getUser(userId)
                        sessions = runCatching { AdminRepository.listUserSessions(userId) }.getOrNull()
                    } catch (e: Exception) {
                        error = describeAdminError(e)
                        detail = null
                        sessions = null
                    } finally {
                        loading = false
                    }
                }
            }

            useEffect(userId) { load(true) }

            fun runAction(
                confirmText: String,
                block: suspend () -> String?,
            ) {
                if (busy || userId == null) return
                if (!window.confirm(confirmText)) return
                busy = true
                actionError = null
                notice = null
                tempPassword = null
                koinInject<CoroutineScope>().promise {
                    try {
                        notice = block()
                        load(false)
                    } catch (e: Exception) {
                        actionError = describeAdminError(e)
                    } finally {
                        busy = false
                    }
                }
            }

            div {
                className = ClassName("toolbar")
                h2 { +"用户详情" }
                div { className = ClassName("toolbar-spacer") }
                a {
                    href = "#/admin/users"
                    className = ClassName("btn btn-sm")
                    onClick = { event ->
                        event.preventDefault()
                        Router.navigate("/admin/users")
                    }
                    +"返回列表"
                }
            }

            if (actionError != null || notice != null) {
                div {
                    className = ClassName(if (actionError != null) "admin-alert admin-alert-error" else "admin-alert admin-alert-success")
                    p { +(actionError ?: notice ?: "") }
                }
            }

            if (tempPassword != null) {
                div {
                    className = ClassName("admin-temp-password")
                    div {
                        className = ClassName("admin-temp-password-label")
                        +"一次性临时密码（仅显示一次）"
                    }
                    div {
                        className = ClassName("admin-temp-password-value")
                        +(tempPassword ?: "")
                    }
                }
            }

            when {
                userId == null -> {
                    div {
                        className = ClassName("error-message")
                        p { +"无效的用户 ID" }
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
                    val u = d.user
                    val immutable = u.role == UserRole.ADMIN

                    div {
                        className = ClassName("admin-detail-grid")
                        div {
                            className = ClassName("admin-card")
                            h3 { +"资料" }
                            div {
                                className = ClassName("admin-field")
                                span {
                                    className = ClassName("admin-field-label")
                                    +"用户名"
                                }
                                span { +u.username }
                            }
                            div {
                                className = ClassName("admin-field")
                                span {
                                    className = ClassName("admin-field-label")
                                    +"昵称"
                                }
                                span {
                                    +(
                                        u.displayName
                                            ?: "—"
                                    )
                                }
                            }
                            div {
                                className = ClassName("admin-field")
                                span {
                                    className = ClassName("admin-field-label")
                                    +"邮箱"
                                }
                                span {
                                    +(
                                        u.email
                                            ?: "—"
                                    )
                                }
                            }
                            div {
                                className = ClassName("admin-field")
                                span {
                                    className = ClassName("admin-field-label")
                                    +"角色"
                                }
                                span { +u.role.name }
                            }
                            div {
                                className = ClassName("admin-field")
                                span {
                                    className = ClassName("admin-field-label")
                                    +"状态"
                                }
                                span {
                                    +(
                                        if (u.bannedAt !=
                                            null
                                        ) {
                                            "已封禁"
                                        } else {
                                            "正常"
                                        }
                                    )
                                }
                            }
                            div {
                                className = ClassName("admin-field")
                                span {
                                    className = ClassName("admin-field-label")
                                    +"注册时间"
                                }
                                span { +formatAdminTime(u.createdAt) }
                            }
                            div {
                                className = ClassName("admin-field")
                                span {
                                    className = ClassName("admin-field-label")
                                    +"最近活跃"
                                }
                                span {
                                    +(
                                        d.lastActiveAt?.let(::formatAdminTime)
                                            ?: "—"
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
                                    +"活跃会话"
                                }
                                span { +d.activeSessionCount.toString() }
                            }
                            div {
                                className = ClassName("admin-field")
                                span {
                                    className = ClassName("admin-field-label")
                                    +"会话数"
                                }
                                span { +d.conversationCount.toString() }
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

                    if (!immutable) {
                        div {
                            className = ClassName("admin-card")
                            h3 { +"操作" }
                            div { className = ClassName("admin-actions") }
                            div {
                                className = ClassName("admin-action-row")
                                button {
                                    className = ClassName("btn btn-sm" + if (u.bannedAt != null) "" else " btn-danger")
                                    disabled = busy
                                    onClick = {
                                        val ban = u.bannedAt == null
                                        runAction(if (ban) "确认封禁该用户？将立即吊销其会话。" else "确认解除封禁？") {
                                            AdminRepository.setBanned(userId, ban)
                                            "已${if (ban) "封禁" else "解除封禁"}用户"
                                        }
                                    }
                                    +if (u.bannedAt != null) "解除封禁" else "封禁用户"
                                }
                                button {
                                    className = ClassName("btn btn-sm btn-danger")
                                    disabled = busy
                                    onClick = {
                                        runAction("确认重置该用户密码？将吊销其全部会话并生成一次性临时密码。") {
                                            tempPassword = AdminRepository.resetPassword(userId).temporaryPassword
                                            "已重置密码"
                                        }
                                    }
                                    +"重置密码"
                                }
                                button {
                                    className = ClassName("btn btn-sm btn-danger")
                                    disabled = busy
                                    onClick = {
                                        runAction("确认踢出该用户全部会话？") {
                                            AdminRepository.revokeAllUserSessions(userId)
                                            "已撤销全部会话"
                                        }
                                    }
                                    +"踢出全部会话"
                                }
                            }
                            div {
                                className = ClassName("admin-action-row")
                                select {
                                    className = ClassName("admin-filter-select")
                                    value = roleSel
                                    onChange = { event -> roleSel = event.target.value.unsafeCast<String>() }
                                    option {
                                        value = ""
                                        +"选择新角色"
                                    }
                                    UserRole.entries.filter { it != UserRole.ADMIN && it != u.role }.forEach { role ->
                                        option {
                                            value = role.name
                                            +role.name
                                        }
                                    }
                                }
                                button {
                                    className = ClassName("btn btn-sm")
                                    disabled = busy || roleSel.isBlank()
                                    onClick = {
                                        val newRole = runCatching { UserRole.valueOf(roleSel) }.getOrNull()
                                        if (newRole != null) {
                                            runAction("确认将用户角色改为 $newRole？") {
                                                AdminRepository.changeRole(userId, newRole)
                                                roleSel = ""
                                                "已更新角色"
                                            }
                                        }
                                    }
                                    +"修改角色"
                                }
                            }
                        }
                    } else {
                        div {
                            className = ClassName("admin-card")
                            p { +"管理员账号不可通过后台执行封禁、改角色、重置密码或踢会话。" }
                        }
                    }

                    div {
                        className = ClassName("admin-card")
                        h3 { +"登录会话" }
                        val items = sessions?.items.orEmpty()
                        if (items.isEmpty()) {
                            p {
                                className = ClassName("admin-cell-secondary")
                                +"没有活跃会话"
                            }
                        } else {
                            div {
                                className = ClassName("admin-table-wrap")
                                table {
                                    className = ClassName("admin-table")
                                    thead {
                                        tr {
                                            th { +"设备" }
                                            th { +"IP" }
                                            th { +"创建时间" }
                                            th { +"最近活跃" }
                                            th { +"过期时间" }
                                        }
                                    }
                                    tbody {
                                        items.forEach { s ->
                                            tr {
                                                key = Key(s.id.toString())
                                                td { +(s.deviceName ?: s.userAgent ?: "未知设备") }
                                                td { +(s.ipAddress ?: "—") }
                                                td { +formatAdminTime(s.createdAt) }
                                                td { +(s.lastUsedAt?.let(::formatAdminTime) ?: "—") }
                                                td { +formatAdminTime(s.expiresAt) }
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
    }
