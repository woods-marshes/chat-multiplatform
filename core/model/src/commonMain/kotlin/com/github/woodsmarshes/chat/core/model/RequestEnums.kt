package com.github.woodsmarshes.chat.core.model

enum class RequestStatus {
    PENDING,
    ACCEPTED,
    REJECTED,
    CANCELED,
}

enum class RequestType {
    ALL,
    SENT,
    RECEIVED,
}
