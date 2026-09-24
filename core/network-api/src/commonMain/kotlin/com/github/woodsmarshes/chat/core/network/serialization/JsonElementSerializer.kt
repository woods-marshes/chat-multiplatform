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
            encoder.encodeJsonElement(value)
        } else {
            encoder.encodeString(value.toString())
        }
    }

    override fun deserialize(decoder: Decoder): JsonElement {
        return if (decoder is JsonDecoder) {
            decoder.decodeJsonElement()
        } else {
            Json.parseToJsonElement(decoder.decodeString())
        }
    }
}