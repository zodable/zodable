package dev.zodable.example

import dev.zodable.Zodable

@Zodable
data class Address(
    val street: Street,
    val city: String,
    val country: Country,
)
