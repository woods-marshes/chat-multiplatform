package com.github.woodsmarshes.web.pages.admin

import com.github.woodsmarshes.chat.core.contract.dto.admin.AdminUserListResponse
import com.github.woodsmarshes.chat.core.model.UserRole
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
import react.dom.html.ReactHTML.input
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

private data class AdminUserQuery(
    val keyword: String = "",
    val role: UserRole? = null,
    val banned: Boolean? = null,
    val page: Int = 1,
)

private fun String.toUserRoleOrNull(): UserRole? =
    when (this) {
        "ADMIN" -> UserRole.ADMIN
        "MEMBER" -> UserRole.MEMBER
        "GUEST" -> UserRole.GUEST
        "BOT" -> UserRole.BOT
        else -> null
    }

private fun String.toBannedOrNull(): Boolean? =
    when (this) {
        "true" -> true
        "false" -> false
        else -> null
    }

private fun roleBadgeClass(role: UserRole): String =
    when (role) {
        UserRole.ADMIN -> "admin-badge admin-badge-admin"
        UserRole.BOT -> "admin-badge admin-badge-bot"
        UserRole.GUEST -> "admin-badge admin-badge-guest"
        UserRole.MEMBER -> "admin-badge admin-badge-member"
    }

val AdminUsersPage =
    FC<Props> {
        AdminShell {
            var keyword by useState("")
            var roleSel by useState("")
            var bannedSel by useState("")
            var query by useState(AdminUserQuery())
            var result by useState<AdminUserListResponse?>(null)
            var loading by useState(true)
            var error by useState<String?>(null)

            useEffect(query) {
                loading = true
                error = null
                try {
                    result =
                        AdminRepository.listUsers(
                            keyword = query.keyword.ifBlank { null },
                            role = query.role,
                            banned = query.banned,
                            page = query.page,
                        )
                } catch (e: Exception) {
                    error = describeAdminError(e)
                    result = null
                } finally {
                    loading = false
                }
            }

            div {
                className = ClassName("toolbar")
                h2 { +"用户管理" }
            }

            div {
                className = ClassName("admin-filter-bar")
                input {
                    className = ClassName("auth-input admin-filter-input")
                    placeholder = "关键词（用户名/邮箱）"
                    value = keyword
                    onChange = { event -> keyword = event.target.value.unsafeCast<String>() }
                }
                select {
                    className = ClassName("admin-filter-select")
                    value = roleSel
                    onChange = { event -> roleSel = event.target.value.unsafeCast<String>() }
                    option {
                        value = ""
                        +"全部角色"
                    }
                    option {
                        value = "ADMIN"
                        +"管理员"
                    }
                    option {
                        value = "MEMBER"
                        +"成员"
                    }
                    option {
                        value = "GUEST"
                        +"访客"
                    }
                    option {
                        value = "BOT"
                        +"机器人"
                    }
                }
                select {
                    className = ClassName("admin-filter-select")
                    value = bannedSel
                    onChange = { event -> bannedSel = event.target.value.unsafeCast<String>() }
                    option {
                        value = ""
                        +"全部状态"
                    }
                    option {
                        value = "false"
                        +"正常"
                    }
                    option {
                        value = "true"
                        +"已封禁"
                    }
                }
                button {
                    className = ClassName("btn btn-primary btn-sm")
                    onClick = {
                        query =
                            AdminUserQuery(
                                keyword = keyword.trim(),
                                role = roleSel.toUserRoleOrNull(),
                                banned = bannedSel.toBannedOrNull(),
                                page = 1,
                            )
                    }
                    +"查询"
                }
            }

            when {
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

                result != null -> {
                    val items = result!!.items
                    if (items.isEmpty()) {
                        div {
                            className = ClassName("empty-state")
                            p { +"没有匹配的用户" }
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
                                        th { +"状态" }
                                        th { +"活跃会话" }
                                        th { +"注册时间" }
                                        th { +"操作" }
                                    }
                                }
                                tbody {
                                    items.forEach { item ->
                                        val u = item.user
                                        tr {
                                            key = Key(u.id.toString())
                                            td {
                                                div {
                                                    className = ClassName("admin-cell-primary")
                                                    +(u.displayName ?: u.username)
                                                }
                                                div {
                                                    className = ClassName("admin-cell-secondary")
                                                    +"@${u.username}"
                                                }
                                                if (!u.email.isNullOrBlank()) {
                                                    div {
                                                        className = ClassName("admin-cell-secondary")
                                                        +u.email
                                                    }
                                                }
                                            }
                                            td {
                                                span {
                                                    className = ClassName(roleBadgeClass(u.role))
                                                    +u.role.name
                                                }
                                            }
                                            td {
                                                if (u.bannedAt != null) {
                                                    span {
                                                        className = ClassName("admin-badge admin-badge-banned")
                                                        +"已封禁"
                                                    }
                                                } else {
                                                    span {
                                                        className = ClassName("admin-badge admin-badge-normal")
                                                        +"正常"
                                                    }
                                                }
                                            }
                                            td { +item.activeSessionCount.toString() }
                                            td { +formatAdminTime(u.createdAt) }
                                            td {
                                                a {
                                                    href = "#/admin/users/${u.id}"
                                                    className = ClassName("admin-link")
                                                    onClick = { event ->
                                                        event.preventDefault()
                                                        Router.navigate("/admin/users/${u.id}")
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
