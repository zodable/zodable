package dev.zodable.example

// value classes do not need to be annotated with Zodable if valueClassUnwrap = true
@JvmInline
value class Street(val name: String)
