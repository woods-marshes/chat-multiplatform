package com.github.woodsmarshes.chat.core.common.utils

import io.github.oshai.kotlinlogging.KLogger

fun KLogger.verbose(tag: String, message: String = "") {
    this.trace {
        "TAG: ${tag}, MESSAGE => $message"
    }
}

fun KLogger.info(tag: String, message: String = "") {
    this.info {
        "TAG: ${tag}, MESSAGE => $message"
    }
}

fun KLogger.debug(tag: String, message: String = "") {
    this.debug {
        "TAG: ${tag}, MESSAGE => $message"
    }
}

fun KLogger.warn(tag: String, message: String = "") {
    this.warn {
        "TAG: ${tag}, MESSAGE => $message"
    }
}

fun KLogger.error(tag: String, message: String = "") {
    this.error {
        "TAG: ${tag}, MESSAGE => $message"
    }
}

fun KLogger.debug(tag: String, message: String = "", throwable: Throwable?) {
    this.debug(throwable = throwable) {
        "TAG: ${tag}, MESSAGE => $message"
    }
}

fun KLogger.warn(tag: String, message: String = "", throwable: Throwable?) {
    this.warn(throwable = throwable) {
        "TAG: ${tag}, MESSAGE => $message"
    }
}

fun KLogger.error(tag: String, message: String = "", throwable: Throwable?) {
    this.error(throwable = throwable) {
        "TAG: ${tag}, MESSAGE => $message"
    }
}