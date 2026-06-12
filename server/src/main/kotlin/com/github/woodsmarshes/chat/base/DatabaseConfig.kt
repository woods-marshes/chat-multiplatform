package com.github.woodsmarshes.chat.base

import kotlinx.serialization.Serializable

@Serializable
data class DatabaseConfig(
    val url: String,
    val username: String,
    val password: String,
) {
    init {
        require(url.isNotBlank()) { "数据库 URL 不能为空" }
        require(username.isNotBlank()) { "数据库用户名不能为空" }
        require(password.isNotBlank()) { "数据库密码不能为空" }
    }
}
