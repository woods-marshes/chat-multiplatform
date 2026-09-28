package com.github.woodsmarshes.chat.utils

object Keys {
    const val USER_ID = "userId"
}

/** Upper bound for any client-supplied page size. */
const val MAX_PAGE_SIZE = 100

/** URL prefix of authenticated private attachments (files sent in chat). */
const val PRIVATE_FILE_URL_PREFIX = "/v1/files/content/"

/** URL prefix of the public static upload tree (images, media, avatars). */
const val PUBLIC_UPLOAD_URL_PREFIX = "/uploads/"
