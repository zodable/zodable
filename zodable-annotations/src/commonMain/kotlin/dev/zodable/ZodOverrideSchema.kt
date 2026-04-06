package dev.zodable

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@Repeatable
annotation class ZodOverrideSchema(
    val content: String,
    val filter: String = "*",
)
