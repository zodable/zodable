package dev.zodable.example

import dev.zodable.Zodable
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
@Zodable
data class MultiplatformUser(
    val id: Uuid,
    val type: MultiplatformType,
)
