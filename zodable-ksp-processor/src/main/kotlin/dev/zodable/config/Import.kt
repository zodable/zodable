package dev.zodable.config

data class Import(
    val name: String,
    val source: String,
    val isExternal: Boolean = false,
    val isInvariable: Boolean = false,
    val isDependency: Boolean = true,
    val dependencyVersion: String? = null,
)
