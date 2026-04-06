package dev.zodable

enum class Optionals(
    val zodType: String,
) {
    NULLABLE(".nullable()"),
    NULLISH(".nullish()"),
    OPTIONAL(".optional()");
}
