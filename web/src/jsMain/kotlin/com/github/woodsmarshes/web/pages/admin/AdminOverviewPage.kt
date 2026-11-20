package com.github.woodsmarshes.web.pages.admin

import com.github.woodsmarshes.chat.core.contract.dto.admin.AdminStatsResponse
import com.github.woodsmarshes.web.components.AdminShell
import com.github.woodsmarshes.web.components.AdminStatCard
import com.github.woodsmarshes.web.storage.AdminRepository
import com.github.woodsmarshes.web.storage.describeAdminError
import react.FC
import react.Props
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.h2
import react.dom.html.ReactHTML.p
import react.useEffect
import react.useState
import web.cssom.ClassName

val AdminOverviewPage =
    FC<Props> {
        AdminShell {
            var stats by useState<AdminStatsResponse?>(null)
            var loading by useState(true)
            var error by useState<String?>(null)

            useEffect(Unit) {
                try {
                    stats = AdminRepository.getStats()
                } catch (e: Exception) {
                    error = describeAdminError(e)
                } finally {
                    loading = false
                }
            }

            div {
                className = ClassName("toolbar")
                h2 { +"管理概览" }
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

                stats != null -> {
                    val s = stats!!
                    div {
                        className = ClassName("admin-stat-grid")
                        AdminStatCard.invoke {
                            label = "用户总数"
                            value = s.userCount.toString()
                        }
                        AdminStatCard.invoke {
                            label = "管理员"
                            value = s.adminCount.toString()
                        }
                        AdminStatCard.invoke {
                            label = "已封禁"
                            value = s.bannedUserCount.toString()
                        }
                        AdminStatCard.invoke {
                            label = "会话总数"
                            value = s.conversationCount.toString()
                        }
                        AdminStatCard.invoke {
                            label = "群聊 / 私聊"
                            value = "${s.groupConversationCount} / ${s.privateConversationCount}"
                        }
                        AdminStatCard.invoke {
                            label = "消息总数"
                            value = s.messageCount.toString()
                        }
                        AdminStatCard.invoke {
                            label = "活跃会话"
                            value = s.activeSessionCount.toString()
                        }
                    }
                }
            }
        }
    }
