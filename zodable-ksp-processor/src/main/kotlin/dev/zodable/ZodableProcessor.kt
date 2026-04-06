package dev.zodable

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import dev.zodable.config.GeneratorConfig
import dev.zodable.generators.PythonGenerator
import dev.zodable.generators.TypescriptGenerator
import java.io.File
import java.nio.file.Paths

class ZodableProcessor(
    private val env: SymbolProcessorEnvironment,
) : SymbolProcessor {
    val enableTypescript = env.options["zodableEnableTypescript"].equals("true")
    val enablePython = env.options["zodableEnablePython"].equals("true")
    val packageName = env.options["zodablePackageName"] ?: "zodable"
    val outputPath = env.options["zodableOutputPath"] ?: ""
    val inferTypes = env.options["zodableInferTypes"].equals("true")
    val coerceMapKeys = env.options["zodableCoerceMapKeys"].equals("true")
    val optionals = env.options["zodableOptionals"] ?: ""
    val valueClassUnwrap = env.options["zodableValueClassUnwrap"].equals("true")

    val outputPathFile: File by lazy { Paths.get(outputPath).toFile().also { it.mkdirs() } }
    val config by lazy {
        GeneratorConfig(
            packageName,
            outputPathFile,
            inferTypes,
            coerceMapKeys,
            optionals,
            valueClassUnwrap
        )
    }
    val typescriptGenerator by lazy { TypescriptGenerator(env, config) }
    val pythonGenerator by lazy {
        val pythonOutputPath = outputPathFile.parentFile.resolve("pydantable").also { it.mkdirs() }
        PythonGenerator(env, config.copy(outputPath = pythonOutputPath))
    }

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val annotatedClasses = resolver.getSymbolsWithAnnotation(Zodable::class.qualifiedName!!)
            .filterIsInstance<KSClassDeclaration>()

        if (enableTypescript) {
            typescriptGenerator.generateFiles(annotatedClasses)
        }
        if (enablePython) {
            pythonGenerator.generateFiles(annotatedClasses)
        }

        return emptyList()
    }
}
