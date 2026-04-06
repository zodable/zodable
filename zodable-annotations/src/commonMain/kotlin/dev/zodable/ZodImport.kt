package dev.zodable

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@Repeatable
annotation class ZodImport(
    val name: String,
    val source: String,
    val filter: String = "*",
    val isInvariable: Boolean = false,
)
