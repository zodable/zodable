package dev.zodable.example

import dev.zodable.Zodable

@Zodable
data class Message<T>(
    val content: T,
)
