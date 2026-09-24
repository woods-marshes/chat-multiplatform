package com.github.woodsmarshes.chat.core.network.serialization

import kotlinx.serialization.json.Json

val ProjectJson = Json {
    classDiscriminator = "type"
    allowStructuredMapKeys = true
    prettyPrint = false
    isLenient = true
    useAlternativeNames = false
    ignoreUnknownKeys = true
    serializersModule = ProjectSerializersModule
}