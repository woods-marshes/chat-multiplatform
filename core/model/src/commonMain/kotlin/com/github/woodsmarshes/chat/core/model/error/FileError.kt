package com.github.woodsmarshes.chat.core.model.error

import kotlinx.serialization.Serializable

@Serializable
sealed interface FileError : DomainError {
    @Serializable data object FileTooLarge : FileError
    @Serializable data object UnsupportedFormat : FileError
    @Serializable data object UploadFailed : FileError
    @Serializable data object ProcessingFailed : FileError // 图片压缩、视频截帧失败等
    @Serializable data object IoError : FileError
    @Serializable data object NoFileProvided : FileError
    @Serializable data object MultipleFiles : FileError // 单次请求只允许一个文件

    @Serializable data class Unknown(override val message: String? = null) : FileError
}