package dev.zodable.generators

import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSClassDeclaration
import dev.zodable.config.Export
import dev.zodable.config.GeneratorConfig
import dev.zodable.config.Import
import java.io.File

class TypescriptGenerator(
    env: SymbolProcessorEnvironment,
    config: GeneratorConfig,
) : ZodableGenerator(env, config) {
    override fun shouldKeepAnnotation(annotation: String, filter: String): Boolean {
        return listOf("*", "zod", "ts", "typescript").contains(filter)
    }

    override fun resolveSourceFolder(): File {
        return config.outputPath.resolve("src")
    }

    override fun resolveDependenciesFile(): File {
        return config.outputPath.resolve("dependencies.txt")
    }

    override fun resolveIndexFile(sourceFolder: File): File {
        return sourceFolder.resolve("index.ts")
    }

    override fun resolveClassFile(sourceFolder: File, packageName: String, name: String): File {
        return sourceFolder.resolve("$packageName/$name.ts")
    }

    override fun resolveInstallName(source: String, version: String?): String {
        return source + (version?.let { "@$it" } ?: "")
    }

    override fun resolveDefaultImports(classDeclaration: KSClassDeclaration): Set<Import> {
        return setOf(
            // Don't support Zod 4 yet (currently breaking)
            Import("z", "zod", isExternal = true, isInvariable = true, dependencyVersion = "^3.0.0")
        )
    }

    override fun generateImports(sourceFolder: File, currentFile: File, imports: Set<Import>): String {
        return imports.joinToString("\n") { import ->
            val source =
                if (import.isExternal) import.source
                else sourceFolder.resolve(import.source)
                    .relativeTo(currentFile.parentFile) // Folder containing current file
                    .path.let { if (!it.startsWith(".")) "./$it" else it }
            "import {${import.name}${if (!import.isInvariable) "Schema" else ""}} from \"$source\""
        }
    }

    override fun generateIndexExport(exports: Sequence<Export>): String {
        return exports.joinToString("\n") {
            val namesToExport = listOfNotNull(
                "${it.name}Schema",
                if (config.inferTypes) it.name else null
            ).joinToString(prefix = "{", postfix = "}", separator = ", ")
            "export $namesToExport from \"./${it.packageName}/${it.name}\""
        }
    }

    override fun generateClassSchema(
        name: String,
        arguments: List<String>,
        properties: Set<Pair<String, String>>,
    ): String {
        val body = properties.joinToString(",\n    ") { (name, type) -> "$name: $type" }
        val genericPrefix = generateGenericPrefixType(arguments) + generateGenericPrefixParams(arguments)
        return "export const ${name}Schema = ${genericPrefix}z.object({\n    $body\n})" +
                generateInferType(name, arguments)
    }

    override fun generateEnumSchema(name: String, arguments: List<String>, values: Set<String>): String {
        val body = values.joinToString(", ") { "\"$it\"" }
        val genericPrefix = generateGenericPrefixType(arguments) + generateGenericPrefixParams(arguments)
        return "export const ${name}Schema = ${genericPrefix}z.enum([\n    $body\n])" +
                generateInferType(name, arguments)
    }

    override fun generateUnionSchema(name: String, arguments: List<String>, values: Set<String>): String {
        val joinedValues = values.joinToString(",\n") { "    ${it}Schema" }
        val body =
            if (joinedValues.isEmpty()) "z.never()"
            else "z.discriminatedUnion(\"type\", [\n$joinedValues\n])"
        val genericPrefix = generateGenericPrefixType(arguments) + generateGenericPrefixParams(arguments)
        return "export const ${name}Schema = $genericPrefix$body" + generateInferType(name, arguments)
    }

    fun generateGenericPrefixType(arguments: List<String>, schema: Boolean = true, extends: Boolean = true): String {
        if (arguments.isEmpty()) return ""
        return arguments.joinToString(", ", prefix = "<", postfix = ">") {
            "${it}${if (schema) "Schema" else ""}${if (extends) " extends z.ZodTypeAny" else ""}"
        }
    }

    fun generateGenericPrefixParams(arguments: List<String>): String {
        if (arguments.isEmpty()) return ""
        return arguments.joinToString(", ", prefix = " (", postfix = ") => ") { argument ->
            "${argument.replaceFirstChar { it.lowercaseChar() }}Schema: ${argument}Schema"
        }
    }

    fun generateInferType(name: String, arguments: List<String>): String {
        if (!config.inferTypes) return ""
        val genericPrefix = generateGenericPrefixType(arguments, schema = false, extends = false)
        val typeOf = "typeof ${name}Schema".let { rawTypeOf ->
            if (arguments.isNotEmpty()) {
                val genericCall = arguments.joinToString(", ", prefix = "<", postfix = ">") {
                    "z.ZodType<${it}, any, any>"
                }
                "ReturnType<$rawTypeOf$genericCall>"
            } else rawTypeOf
        }
        return "\nexport type $name$genericPrefix = z.infer<$typeOf>"
    }

    override fun resolvePrimitiveType(kotlinType: String): Pair<String, List<Import>>? {
        return when (kotlinType) {
            "kotlin.String" -> "z.string()" to emptyList()
            "kotlin.Int" -> "z.number().int()" to emptyList()
            "kotlin.Long", "kotlin.Double", "kotlin.Float" -> "z.number()" to emptyList()
            "kotlin.Boolean" -> "z.boolean()" to emptyList()
            "kotlin.Pair" -> "KotlinPairSchema()" to listOf(
                Import("KotlinPairSchema", "zodable-kotlin-primitives", isExternal = true, isInvariable = true)
            )

            "kotlin.time.Instant",
            "kotlinx.datetime.Instant",
            "kotlinx.datetime.LocalDateTime",
            "kotlinx.datetime.LocalDate",
                -> "z.coerce.date()" to emptyList()

            "dev.kaccelero.models.UUID",
            "kotlin.uuid.Uuid",
                -> "z.string().uuid()" to emptyList()

            "kotlin.collections.List" -> "z.array()" to emptyList()
            "kotlin.collections.Map" -> "z.record()" to emptyList()
            else -> null
        }
    }

    override fun resolveZodableType(name: String, isGeneric: Boolean): Pair<String, List<Import>> {
        return "${name}Schema${if (isGeneric) "()" else ""}" to emptyList()
    }

    override fun resolveLiteralType(name: String): Pair<String, List<Import>> {
        return "z.literal(\"$name\")" to emptyList()
    }

    override fun resolveGenericArgument(name: String): Pair<String, List<Import>> {
        return "${name.replaceFirstChar { it.lowercaseChar() }}Schema" to emptyList()
    }

    override fun resolveUnknownType(): Pair<String, List<Import>> {
        return "z.unknown()" to emptyList()
    }

    override fun addGenericArguments(type: String, arguments: List<String>): Pair<String, List<Import>> {
        if (!type.endsWith("()")) return type to emptyList()

        // Coerce arguments if needed
        val coercedArguments = when (type) {
            // For map, we need to coerce the key type (always from string inside json)
            "z.record()" -> {
                val firstArgument = arguments.first()
                val coercedFirstArgument =
                    if (config.coerceMapKeys && firstArgument.startsWith("z."))
                        firstArgument.replaceFirst("z.", "z.coerce.")
                    else firstArgument
                listOf(coercedFirstArgument) + arguments.drop(1)
            }

            else -> arguments
        }
        return type.substring(0, type.length - 2) + "(${coercedArguments.joinToString(", ")})" to emptyList()
    }

    override fun markAsNullable(type: String): Pair<String, List<Import>> {
        return "$type${config.optionals}" to emptyList()
    }

    override fun extensionName(): String = "ts"
}
