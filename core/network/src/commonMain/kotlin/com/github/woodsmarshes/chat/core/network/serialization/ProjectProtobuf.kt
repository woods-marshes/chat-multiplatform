package com.github.woodsmarshes.chat.core.network.serialization

import kotlinx.serialization.protobuf.ProtoBuf

val ProjectProtobuf = ProtoBuf {
    serializersModule = ProjectSerializersModule
}