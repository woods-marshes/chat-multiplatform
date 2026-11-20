package com.github.woodsmarshes.web.components

import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import react.FC
import react.Props
import react.dom.html.ReactHTML.button
import react.dom.html.ReactHTML.div
import react.dom.html.ReactHTML.span
import web.cssom.ClassName
import kotlin.time.Instant

internal fun formatAdminTime(instant: Instant): String =
    try {
        val dt = instant.toLocalDateTime(TimeZone.currentSystemDefault())
        "${dt.year}-${dt.month.number.toString().padStart(2, '0')}-${dt.day.toString().padStart(2, '0')} " +
            "${dt.hour.toString().padStart(2, '0')}:${dt.minute.toString().padStart(2, '0')}"
    } catch (e: Exception) {
        "—"
    }

internal external interface AdminPaginationProps : Props {
    var page: Int
    var total: Long
    var limit: Int
    var onPage: (Int) -> Unit
}

internal val AdminPagination =
    FC<AdminPaginationProps> { props ->
        val totalPages = maxOf(1, ((props.total + props.limit - 1) / props.limit).toInt())

        div {
            className = ClassName("admin-pagination")
            button {
                className = ClassName("btn btn-sm")
                disabled = props.page <= 1
                onClick = { props.onPage(props.page - 1) }
                +"上一页"
            }
            span {
                className = ClassName("admin-pagination-info")
                +"第 ${props.page} / $totalPages 页 · 共 ${props.total} 条"
            }
            button {
                className = ClassName("btn btn-sm")
                disabled = props.page >= totalPages
                onClick = { props.onPage(props.page + 1) }
                +"下一页"
            }
        }
    }

internal external interface AdminStatCardProps : Props {
    var label: String
    var value: String
}

internal val AdminStatCard =
    FC<AdminStatCardProps> { props ->
        div {
            className = ClassName("admin-stat-card")
            div {
                className = ClassName("admin-stat-value")
                +props.value
            }
            div {
                className = ClassName("admin-stat-label")
                +props.label
            }
        }
    }
