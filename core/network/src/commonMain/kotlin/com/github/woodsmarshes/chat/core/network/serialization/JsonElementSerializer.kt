package com.github.woodsmarshes.chat.core.network.serialization

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder

object JsonElementSerializer : KSerializer<JsonElement> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("JsonElement", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: JsonElement) {
        if (encoder is JsonEncoder) {
            // 如果是 JSON 格式，正常作为 JsonElement 编码
            encoder.encodeJsonElement(value)
        } else {
            // 如果是 Protobuf 等非 JSON 格式，安全地降级序列化为 String
            encoder.encodeString(value.toString())
        }
    }

    override fun deserialize(decoder: Decoder): JsonElement {
        return if (decoder is JsonDecoder) {
            // 如果是 JSON 格式，正常反序列化为 JsonElement
            decoder.decodeJsonElement()
        } else {
            // 如果是 Protobuf 等非 JSON 格式，先读取 String，再转换为 JsonElement
            Json.parseToJsonElement(decoder.decodeString())
        }
    }
}