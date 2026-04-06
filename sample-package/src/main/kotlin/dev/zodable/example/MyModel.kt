package dev.zodable.example

import dev.zodable.Zodable
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
@Zodable
data class MyModel(
    val id: Uuid,
    val name: String,
)
