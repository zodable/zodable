package dev.zodable.example

import dev.zodable.Zodable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@Zodable
sealed interface NestedSealedFoo {
    sealed interface Bar : NestedSealedFoo {
        val foo1Value: String

        @Serializable
        @SerialName("Bar1")
        data class Bar1(val bar1Value: String, override val foo1Value: String) : Bar

        @Serializable
        @SerialName("Bar2")
        data class Bar2(val bar2Value: String, override val foo1Value: String) : Bar
    }

    sealed interface Baz : NestedSealedFoo {
        val foo1Value: String

        @Serializable
        @SerialName("Baz1")
        data class Baz1(val baz1Value: String, override val foo1Value: String) : Baz

        @Serializable
        @SerialName("Baz2")
        data class Baz2(val baz2Value: String, override val foo1Value: String) : Baz
    }
}
