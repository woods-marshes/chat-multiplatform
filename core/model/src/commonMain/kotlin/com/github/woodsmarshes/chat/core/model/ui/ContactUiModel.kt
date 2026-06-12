package com.github.woodsmarshes.chat.core.model.ui

import kotlin.uuid.Uuid

data class ContactUiModel(
    val id: Uuid,
    val username: String,
    val displayName: String? = null,
    val avatarUrl: String? = null,
    val bio: String? = null,
    val isOnline: Boolean = false,
    val pinYinHead: String = "", // 拼音首字母，用于分组排序
)

data class ContactRequestUiModel(
    val id: Uuid,
    val userId: Uuid,
    val username: String,
    val displayName: String?,
    val avatarUrl: String?,
    val message: String?,
    val requestCount: Int = 1,
)
