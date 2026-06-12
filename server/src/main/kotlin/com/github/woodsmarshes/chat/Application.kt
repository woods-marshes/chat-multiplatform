package com.github.woodsmarshes.chat

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.server.application.*
import io.ktor.server.plugins.cors.routing.CORS

fun main(args: Array<String>) =
    io.ktor.server.netty.EngineMain.main(args)


fun Application.module() {
    configureFrameworks()
    configureSerialization()
    install(CORS) {
        val isDev = this@module.environment.config.propertyOrNull("ktor.development")?.getString() == "true"
        if (isDev) {
            anyHost()
        } else {
            allowHost("localhost")
        }
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        allowMethod(HttpMethod.Options)
    }
    configureSockets()
    configureSecurity()
    configureHTTP()
    configureRouting()
}
