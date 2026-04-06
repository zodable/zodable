package dev.zodable.extensions

import dev.zodable.Optionals
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property

interface ZodableExtension {
    val enableTypescript: Property<Boolean>
    val enablePython: Property<Boolean>
    val inferTypes: Property<Boolean>
    val coerceMapKeys: Property<Boolean>
    val optionals: Property<Optionals>
    val packageName: Property<String>
    val packageVersion: Property<String>
    val additionalNpmCommands: ListProperty<List<String>>
    val externalPackageInstallCommands: MapProperty<String, List<String>>
    val externalPackageLocations: MapProperty<String, String>
    val valueClassUnwrap: Property<Boolean>
}
