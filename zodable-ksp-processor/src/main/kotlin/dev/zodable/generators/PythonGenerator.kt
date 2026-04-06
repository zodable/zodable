package dev.zodable.generators

import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import dev.zodable.config.Export
import dev.zodable.config.GeneratorConfig
import dev.zodable.config.Import
import java.io.File

class PythonGenerator(
    env: SymbolProcessorEnvironment,
    config: GeneratorConfig,
) : ZodableGenerator(env, config) {
    fun String.pythonCompatible() = this.replace("-", "_")

    override fun shouldKeepAnnotation(annotation: String, filter: String): Boolean {
        return listOf("*", "pydantic", "py", "python").contains(filter)
    }

    override fun resolveSourceFolder(): File {
        return config.outputPath.resolve(config.packageName.pythonCompatible())
    }

    override fun resolveDependenciesFile(): File {
        return config.outputPath.resolve("requirements.txt")
    }

    override fun resolveIndexFile(sourceFolder: File): File {
        return sourceFolder.resolve("__init__.py")
    }

    override fun resolveClassFile(sourceFolder: File, packageName: String, name: String): File {
        return sourceFolder.resolve("$packageName/$name.py")
    }

    override fun resolveInstallName(source: String, version: String?): String {
        return source + (version ?: "")
    }

    override fun resolveDefaultImports(classDeclaration: KSClassDeclaration): Set<Import> {
        val sealedSubclasses = try {
            classDeclaration.getSealedSubclasses().toList()
        } catch (_: Exception) {
            emptyList()
        }

        return when (classDeclaration.classKind) {
            ClassKind.ENUM_CLASS -> setOf(
                Import("Enum", "enum", isExternal = true, isInvariable = true, isDependency = false)
            )

            else -> setOf(
                Import("BaseModel", "pydantic", isExternal = true, isInvariable = true)
            )
        }.let {
            if (classDeclaration.typeParameters.isNotEmpty()) it + setOf(
                Import("Generic", "typing", isExternal = true, isInvariable = true),
                Import("TypeVar", "typing", isExternal = true, isInvariable = true)
            ) else it
        }.let {
            if (sealedSubclasses.isNotEmpty()) it + setOf(
                Import("Union", "typing", isExternal = true, isInvariable = true),
            ) else it
        }
    }

    override fun generateImports(sourceFolder: File, currentFile: File, imports: Set<Import>): String {
        return imports.joinToString("\n") { import ->
            val source =
                if (import.isExternal) import.source.pythonCompatible()
                else sourceFolder.resolve(import.source)
                    .relativeTo(config.outputPath)
                    .path.replace("/", ".")
            "from $source import ${import.name}"
        }
    }

    override fun generateIndexExport(exports: Sequence<Export>): String {
        return exports.joinToString("\n") {
            val source = "${config.packageName.pythonCompatible()}.${it.packageName.replace("/", ".")}.${it.name}"
            "from $source import ${it.name}"
        } + "\n__all__ = [" + exports.joinToString(", ") { "\"${it.name}\"" } + "]"
    }

    override fun generateClassSchema(
        name: String,
        arguments: List<String>,
        properties: Set<Pair<String, String>>,
    ): String {
        val typeVar = if (arguments.isNotEmpty()) arguments.joinToString("\n", postfix = "\n") {
            "$it = TypeVar('$it')"
        } else ""
        val generics = if (arguments.isNotEmpty()) ", Generic[${arguments.joinToString(", ")}]" else ""
        val body = properties.joinToString("\n") { (name, type) -> "    $name: $type" }
        return "${typeVar}class ${name}(BaseModel$generics):\n" + body.ifEmpty { "    pass" }
    }

    override fun generateEnumSchema(name: String, arguments: List<String>, values: Set<String>): String {
        return "class ${name}(str, Enum):\n" + values.joinToString("\n") { name -> "    $name = '$name'" }
    }

    override fun generateUnionSchema(name: String, arguments: List<String>, values: Set<String>): String {
        return "$name = Union[${values.joinToString(", ")}]"
    }

    override fun resolvePrimitiveType(kotlinType: String): Pair<String, List<Import>>? {
        return when (kotlinType) {
            "kotlin.String" -> "str" to emptyList()
            "kotlin.Int", "kotlin.Long" -> "int" to emptyList()
            "kotlin.Double", "kotlin.Float" -> "float" to emptyList()
            "kotlin.Boolean" -> "bool" to emptyList()
            "kotlin.Pair" -> "KotlinPair[]" to listOf(
                Import("KotlinPair", "zodable-kotlin-primitives", isExternal = true, isInvariable = true)
            )

            "kotlin.time.Instant",
            "kotlinx.datetime.Instant",
            "kotlinx.datetime.LocalDateTime",
            "kotlinx.datetime.LocalDate",
                -> "datetime" to listOf(
                Import("datetime", "datetime", isExternal = true, isInvariable = true)
            )

            "dev.kaccelero.models.UUID",
            "kotlin.uuid.Uuid",
                -> "UUID" to listOf(
                Import("UUID", "uuid", isExternal = true, isInvariable = true)
            )

            "kotlin.collections.List" -> "list[]" to emptyList()
            "kotlin.collections.Map" -> "dict[]" to emptyList()
            else -> null
        }
    }

    override fun resolveZodableType(name: String, isGeneric: Boolean): Pair<String, List<Import>> {
        return "${name}${if (isGeneric) "[]" else ""}" to emptyList()
    }

    override fun resolveLiteralType(name: String): Pair<String, List<Import>> {
        return "Literal[\"$name\"]" to listOf(
            Import("Literal", "typing", isExternal = true, isInvariable = true)
        )
    }

    override fun resolveGenericArgument(name: String): Pair<String, List<Import>> {
        return name to emptyList()
    }

    override fun resolveUnknownType(): Pair<String, List<Import>> {
        return "Any" to listOf(Import("Any", "typing", isExternal = true, isInvariable = true))
    }

    override fun addGenericArguments(type: String, arguments: List<String>): Pair<String, List<Import>> {
        if (!type.endsWith("[]")) return type to emptyList()
        return type.substring(0, type.length - 2) + "[${arguments.joinToString(", ")}]" to emptyList()
    }

    override fun markAsNullable(type: String): Pair<String, List<Import>> {
        return "Optional[$type] = None" to listOf(Import("Optional", "typing", isExternal = true, isInvariable = true))
    }

    override fun extensionName(): String = "py"
}
