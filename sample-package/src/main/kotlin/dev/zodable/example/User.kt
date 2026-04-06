package dev.zodable.example

import dev.zodable.ZodIgnore
import dev.zodable.ZodImport
import dev.zodable.ZodType
import dev.zodable.Zodable
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.SerialName
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@ZodImport("IdSchema", "zodable-idschema", isInvariable = true)
@ZodImport("MultiplatformUser", "zodable-sample-package-multiplatform")

@OptIn(ExperimentalUuidApi::class, ExperimentalTime::class)
@Zodable
data class User(
    val id: Uuid,
    val name: String,
    val email: String?,
    val followers: Int,
    val addresses: List<Address>, // List of another annotated class
    val tags: List<String>, // List of primitive type
    val settings: Map<String, Boolean>, // Map of primitive types
    val eventsByYear: Map<Int, List<String>>, // Map of primitive types, with non-string key
    val contactGroups: Map<String, List<Address>>, // Nested generics
    val coordinates: Pair<Double, Double>, // Pair of primitive types
    val createdAt: Instant,
    val day: LocalDate,
    val daytime: LocalDateTime,
    val externalUser: MultiplatformUser,
    @ZodType("z.date()", "ts") @ZodType("datetime", "py") val birthDate: String, // Custom mapping
    @ZodType("IdSchema") val otherId: Uuid,
    @ZodIgnore val ignored: String, // Ignored property
    @SerialName("custom_name") val customName: String, // Custom serialization name
    val message: Message<String>,
) {

    val notIncluded: Boolean
        get() = true

}
