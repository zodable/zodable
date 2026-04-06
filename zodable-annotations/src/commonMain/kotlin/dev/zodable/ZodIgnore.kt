package dev.zodable

@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
@Repeatable
annotation class ZodIgnore(
    val filter: String = "*",
)
