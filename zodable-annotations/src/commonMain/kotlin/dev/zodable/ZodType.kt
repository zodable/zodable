package dev.zodable

@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
@Repeatable
annotation class ZodType(
    val value: String,
    val filter: String = "*",
)
