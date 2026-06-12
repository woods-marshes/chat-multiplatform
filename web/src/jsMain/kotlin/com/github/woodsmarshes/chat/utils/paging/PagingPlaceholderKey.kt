package com.github.woodsmarshes.chat.utils.paging

fun getPagingPlaceholderKey(index: Int): Any = PagingPlaceholderKey(index)

private data class PagingPlaceholderKey(private val index: Int)