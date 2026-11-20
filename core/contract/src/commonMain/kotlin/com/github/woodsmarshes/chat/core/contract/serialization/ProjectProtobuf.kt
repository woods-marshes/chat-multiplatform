package com.github.woodsmarshes.chat.core.contract.serialization

import kotlinx.serialization.protobuf.ProtoBuf

val ProjectProtobuf =
    ProtoBuf {
        serializersModule = ProjectSerializersModule
    }
