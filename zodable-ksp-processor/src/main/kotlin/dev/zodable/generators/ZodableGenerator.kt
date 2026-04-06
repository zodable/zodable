package dev.zodable.generators

import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.*
import dev.zodable.*
import dev.zodable.config.Export
import dev.zodable.config.GeneratorConfig
import dev.zodable.config.Import
import kotlinx.serialization.SerialName
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter

abstract class ZodableGenerator(
    protected val env: SymbolProcessorEnvironment,
    protected val config: GeneratorConfig,
) {
    var round = 0

    fun generateFiles(annotatedClasses: Sequence<KSClassDeclaration>) {
        val append = round++ > 0
        val sourceFolder = resolveSourceFolder().also { it.mkdirs() }
        val importedPackages = mutableSetOf<String>()

        val exports = annotatedClasses.map { classDeclaration ->
            val name = classDeclaration.simpleName.asString()
            val packageName = classDeclaration.packageName.asString().replace(".", "/")
            val arguments = classDeclaration.typeParameters.map { it.name.asString() }
            val classFile = resolveClassFile(sourceFolder, packageName, name)
            classFile.parentFile.mkdirs()

            env.codeGenerator.associateByPath(
                listOf(classDeclaration.containingFile!!),
                classFile.path,
                extensionName()
            )

            OutputStreamWriter(classFile.outputStream(), Charsets.UTF_8).use { schemaWriter ->
                val imports = generateImports(classDeclaration).toMutableSet()

                val overriddenSchema = classDeclaration.annotations.firstNotNullOfOrNull { annotation ->
                    if (annotation.shortName.asString() != ZodOverrideSchema::class.simpleName) return@firstNotNullOfOrNull null
                    val zodOverride = annotation.toZodOverrideSchema()
                    if (!shouldKeepAnnotation("ZodOverrideSchema", zodOverride.filter)) return@firstNotNullOfOrNull null
                    zodOverride.content.trimIndent()
                }

                val sealedSubclasses = try {
                    classDeclaration.getSealedSubclasses().toList()
                } catch (_: Exception) {
                    emptyList()
                }

                val generatedBody = overriddenSchema ?: if (sealedSubclasses.isNotEmpty()) {
                    processSealedClass(name, arguments, sealedSubclasses, imports)
                } else when (classDeclaration.classKind) {
                    ClassKind.ENUM_CLASS -> processEnumClass(name, arguments, classDeclaration)
                    else -> processClass(name, arguments, classDeclaration, imports)
                }
                val generatedImports = generateImports(sourceFolder, classFile, imports) + "\n"

                schemaWriter.write(generatedImports + "\n")
                schemaWriter.write(generatedBody + "\n")

                importedPackages.addAll(
                    imports
                        .filter { it.isExternal && it.isDependency }
                        .map { resolveInstallName(it.source, it.dependencyVersion) }
                )
            }
            Export(name, packageName)
        }

        val indexFile = resolveIndexFile(sourceFolder)
        env.codeGenerator.associateWithClasses(annotatedClasses.toList(), "", indexFile.name, extensionName())
        OutputStreamWriter(FileOutputStream(indexFile, append), Charsets.UTF_8).use { indexWriter ->
            indexWriter.write(generateIndexExport(exports) + "\n")
        }

        val dependenciesFile = resolveDependenciesFile()
        env.codeGenerator.associateWithClasses(annotatedClasses.toList(), "", dependenciesFile.name, "txt")
        OutputStreamWriter(FileOutputStream(dependenciesFile, append), Charsets.UTF_8).use { depWriter ->
            importedPackages.forEach { depWriter.write("$it\n") }
        }
    }

    private fun processEnumClass(name: String, arguments: List<String>, classDeclaration: KSClassDeclaration): String {
        val values = classDeclaration.declarations.filterIsInstance<KSClassDeclaration>()
            .map { it.simpleName.asString() }
            .toSet()
        return generateEnumSchema(name, arguments, values)
    }

    private fun processSealedClass(
        name: String,
        arguments: List<String>,
        subclasses: List<KSClassDeclaration>,
        imports: MutableSet<Import>,
    ): String {
        val variants = subclasses.associate { subclass ->
            // Get the SerialName annotation value to use as the discriminator value
            val subclassName = subclass.simpleName.asString()
            val serialName = subclass.annotations.firstNotNullOfOrNull { annotation ->
                if (annotation.shortName.asString() != "SerialName") return@firstNotNullOfOrNull null
                val args = annotation.arguments.associateBy { it.name?.asString() }
                args["value"]?.value as? String
            } ?: subclass.simpleName.asString()
            val subclassArguments = subclass.typeParameters.map { it.name.asString() }
            val (literalType, literalImports) = resolveLiteralType(serialName)
            imports.addAll(literalImports)

            subclassName to processClass(
                name = subclassName,
                arguments = subclassArguments,
                classDeclaration = subclass,
                imports = imports,
                additionalProperties = setOf("type" to literalType),
            )
        }
        val union = generateUnionSchema(name, arguments, variants.keys)

        return variants.values.joinToString("\n\n") + "\n\n" + union
    }

    private fun processClass(
        name: String,
        arguments: List<String>,
        classDeclaration: KSClassDeclaration,
        imports: MutableSet<Import>,
        additionalProperties: Set<Pair<String, String>> = emptySet(),
    ): String {
        val properties = classDeclaration.getAllProperties()
            .filter { it.hasBackingField }
            .mapNotNull { prop ->
                val name = prop.annotations.firstNotNullOfOrNull { annotation ->
                    if (annotation.shortName.asString() != SerialName::class.simpleName) return@firstNotNullOfOrNull null
                    annotation.toSerialName().value
                } ?: prop.simpleName.asString()
                val (type, localImports) = resolveZodType(prop, classDeclaration)
                    ?: return@mapNotNull null
                localImports.forEach { import ->
                    if (imports.none { it.name == import.name }) imports.add(import)
                }
                name to type
            }
            .toSet()
        return generateClassSchema(name, arguments, properties + additionalProperties)
    }

    private fun generateImports(classDeclaration: KSClassDeclaration): Set<Import> =
        resolveDefaultImports(classDeclaration) + classDeclaration.annotations.mapNotNull { annotation ->
            if (annotation.shortName.asString() != ZodImport::class.simpleName) return@mapNotNull null
            val zodImport = annotation.toZodImport()
            if (!shouldKeepAnnotation("ZodImport", zodImport.filter)) return@mapNotNull null
            Import(zodImport.name, zodImport.source, true, zodImport.isInvariable)
        }.toSet()

    private fun resolveZodType(
        prop: KSPropertyDeclaration,
        classDeclaration: KSClassDeclaration,
    ): Pair<String, List<Import>>? {
        prop.annotations.forEach { annotation ->
            if (annotation.shortName.asString() != ZodIgnore::class.simpleName) return@forEach
            val zodIgnore = annotation.toZodIgnore()
            if (!shouldKeepAnnotation("ZodIgnore", zodIgnore.filter)) return@forEach
            return@resolveZodType null
        }
        val customZodType = prop.annotations.firstNotNullOfOrNull { annotation ->
            if (annotation.shortName.asString() != ZodType::class.simpleName) return@firstNotNullOfOrNull null
            val zodType = annotation.toZodType()
            if (!shouldKeepAnnotation("ZodType", zodType.filter)) return@firstNotNullOfOrNull null
            zodType.value
        }
        if (customZodType != null) return Pair(customZodType, emptyList())
        return resolveZodType(prop.type.resolve(), classDeclaration)
    }

    private fun resolveZodType(type: KSType, classDeclaration: KSClassDeclaration): Pair<String, List<Import>> {
        val isNullable = type.isMarkedNullable
        val imports = mutableListOf<Import>()

        // Check if this is a value class and resolve to its underlying type
        val typeDeclaration = type.declaration as? KSClassDeclaration
        if (typeDeclaration != null && typeDeclaration.isValueClass() && config.valueClassUnwrap) {
            val valueClassProperty = typeDeclaration.getAllProperties()
                .firstOrNull { it.hasBackingField }

            if (valueClassProperty != null) {
                val underlyingType = valueClassProperty.type.resolve()
                // Recursively resolve the underlying type, preserving nullability
                val (resolvedType, resolvedImports) = resolveZodType(underlyingType, classDeclaration)
                return if (isNullable) {
                    val (nullableType, nullableImports) = markAsNullable(resolvedType)
                    nullableType to (resolvedImports + nullableImports)
                } else {
                    resolvedType to resolvedImports
                }
            }
        }

        val (arguments, argumentImports) = type.arguments.map {
            val argument = it.type?.resolve() ?: return@map resolveUnknownType()
            resolveZodType(argument, classDeclaration)
        }.unzip().let { it.first to it.second.flatten() }

        val (resolvedType, resolvedImports) = resolvePrimitiveType(
            type.declaration.qualifiedName?.asString() ?: "kotlin.Any"
        ) ?: {
            val typeDeclaration = type.declaration as? KSClassDeclaration
            if (typeDeclaration != null && typeDeclaration.annotations.any { it.shortName.asString() == Zodable::class.simpleName }) {
                val import = typeDeclaration.packageName.asString()
                    .replace(".", "/") + "/" + typeDeclaration.simpleName.asString()
                imports += Import(import.split("/").last(), import)
                resolveZodableType(typeDeclaration.simpleName.asString(), typeDeclaration.typeParameters.isNotEmpty())
            } else if (classDeclaration.typeParameters.any { it.name.asString() == type.declaration.simpleName.asString() }) {
                resolveGenericArgument(type.declaration.simpleName.asString())
            } else {
                val unknownType = resolveUnknownType()
                env.logger.warn("Unsupported type ${type.declaration.simpleName.asString()} in class ${classDeclaration.qualifiedName?.asString()}, using ${unknownType.first}")
                unknownType
            }
        }()

        val allImports = imports + argumentImports + resolvedImports
        return (resolvedType to allImports)
            .let {
                if (arguments.isNotEmpty()) {
                    val (newType, newImports) = addGenericArguments(it.first, arguments)
                    newType to (it.second + newImports)
                } else it
            }
            .let {
                if (isNullable) {
                    val (newType, newImports) = markAsNullable(it.first)
                    newType to (it.second + newImports)
                } else it
            }
    }

    private fun KSAnnotation.toZodOverrideSchema(): ZodOverrideSchema {
        val args = arguments.associateBy { it.name?.asString() }
        return ZodOverrideSchema(
            content = args["content"]?.value as? String ?: error("Missing 'content'"),
            filter = args["filter"]?.value as? String ?: "*",
        )
    }

    private fun KSAnnotation.toZodImport(): ZodImport {
        val args = arguments.associateBy { it.name?.asString() }
        return ZodImport(
            name = args["name"]?.value as? String ?: error("Missing 'name'"),
            source = args["source"]?.value as? String ?: error("Missing 'source'"),
            filter = args["filter"]?.value as? String ?: "*",
            isInvariable = args["isInvariable"]?.value as? Boolean == true
        )
    }

    private fun KSAnnotation.toZodIgnore(): ZodIgnore {
        val args = arguments.associateBy { it.name?.asString() }
        return ZodIgnore(
            filter = args["filter"]?.value as? String ?: "*",
        )
    }

    private fun KSAnnotation.toZodType(): ZodType {
        val args = arguments.associateBy { it.name?.asString() }
        return ZodType(
            value = args["value"]?.value as? String ?: error("Missing 'type'"),
            filter = args["filter"]?.value as? String ?: "*",
        )
    }

    private fun KSAnnotation.toSerialName(): SerialName {
        val args = arguments.associateBy { it.name?.asString() }
        return SerialName(
            value = args["value"]?.value as? String ?: "*",
        )
    }

    private fun KSClassDeclaration.isValueClass(): Boolean {
        // when ksp runs against a compiled lib, it sees it as INLINE, not VALUE because of the JVM bytecode representation
        return modifiers.contains(Modifier.VALUE) || modifiers.contains(Modifier.INLINE)
    }

    abstract fun shouldKeepAnnotation(annotation: String, filter: String): Boolean
    abstract fun resolveSourceFolder(): File
    abstract fun resolveDependenciesFile(): File
    abstract fun resolveIndexFile(sourceFolder: File): File
    abstract fun resolveClassFile(sourceFolder: File, packageName: String, name: String): File
    abstract fun resolveInstallName(source: String, version: String?): String
    abstract fun resolveDefaultImports(classDeclaration: KSClassDeclaration): Set<Import>
    abstract fun generateImports(sourceFolder: File, currentFile: File, imports: Set<Import>): String
    abstract fun generateIndexExport(exports: Sequence<Export>): String
    abstract fun generateClassSchema(
        name: String,
        arguments: List<String>,
        properties: Set<Pair<String, String>>,
    ): String

    abstract fun generateEnumSchema(name: String, arguments: List<String>, values: Set<String>): String
    abstract fun generateUnionSchema(name: String, arguments: List<String>, values: Set<String>): String
    abstract fun resolvePrimitiveType(kotlinType: String): Pair<String, List<Import>>?
    abstract fun resolveZodableType(name: String, isGeneric: Boolean): Pair<String, List<Import>>
    abstract fun resolveLiteralType(name: String): Pair<String, List<Import>>
    abstract fun resolveGenericArgument(name: String): Pair<String, List<Import>>
    abstract fun resolveUnknownType(): Pair<String, List<Import>>
    abstract fun addGenericArguments(type: String, arguments: List<String>): Pair<String, List<Import>>
    abstract fun markAsNullable(type: String): Pair<String, List<Import>>
    abstract fun extensionName(): String
}
