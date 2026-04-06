package digital.guimauve.zodable

import com.google.devtools.ksp.gradle.KspExtension
import digital.guimauve.zodable.Files.pythonCompatible
import digital.guimauve.zodable.extensions.ZodableExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.Exec
import org.gradle.process.ExecOperations
import javax.inject.Inject
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.support.serviceOf
import java.io.File

abstract class ZodablePlugin : Plugin<Project> {

    @get:Inject
    abstract val execOperations: ExecOperations

    private val zodableVersion = "1.7.3"

    override fun apply(project: Project) {
        val outputPath = project.file("build/zodable")

        project.pluginManager.apply("com.google.devtools.ksp")
        project.configureExtensions()
        project.configureKspProcessor()
        project.afterEvaluate {
            project.configureKspArgs(outputPath)
            project.configureTasks(outputPath)
        }
    }

    private fun Project.configureExtensions() {
        val extension = extensions.create<ZodableExtension>("zodable")
        extension.enableTypescript.convention(true)
        extension.enablePython.convention(false)
        extension.inferTypes.convention(true)
        extension.coerceMapKeys.convention(true)
        extension.optionals.convention(Optionals.NULLISH)
        extension.packageName.convention(project.name)
        extension.packageVersion.convention(project.version.toString())
        extension.additionalNpmCommands.convention(emptyList())
        extension.externalPackageInstallCommands.convention(emptyMap())
        extension.externalPackageLocations.convention(emptyMap())
        extension.valueClassUnwrap.convention(true)
    }

    private fun Project.getKspConfig(): KspConfig {
        return if (plugins.hasPlugin("org.jetbrains.kotlin.multiplatform")) KspConfig(
            apiConfigurationName = "commonMainApi",
            kspConfigurationName = "kspCommonMainMetadata",
            taskName = "kspCommonMainKotlinMetadata"
        ) else KspConfig(
            apiConfigurationName = "api",
            kspConfigurationName = "ksp",
            taskName = "kspKotlin"
        )
    }

    private fun Project.configureKspProcessor() {
        val kspConfig = getKspConfig()

        dependencies {
            if (project.group == "digital.guimauve.zodable") {
                add(kspConfig.apiConfigurationName, project(":zodable-annotations"))
                add(kspConfig.kspConfigurationName, project(":zodable-ksp-processor"))
            } else {
                add(kspConfig.apiConfigurationName, "digital.guimauve.zodable:zodable-annotations:$zodableVersion")
                add(kspConfig.kspConfigurationName, "digital.guimauve.zodable:zodable-ksp-processor:$zodableVersion")
            }
        }
    }

    private fun Project.configureKspArgs(outputPath: File) {
        val extension = extensions.getByType<ZodableExtension>()

        plugins.withId("com.google.devtools.ksp") {
            extensions.getByType<KspExtension>().apply {
                arg("zodableEnableTypescript", extension.enableTypescript.get().toString())
                arg("zodableEnablePython", extension.enablePython.get().toString())
                arg("zodablePackageName", extension.packageName.get())
                arg("zodableOutputPath", outputPath.absolutePath)
                arg("zodableInferTypes", extension.inferTypes.get().toString())
                arg("zodableCoerceMapKeys", extension.coerceMapKeys.get().toString())
                arg("zodableOptionals", extension.optionals.get().zodType)
                arg("zodableValueClassUnwrap", extension.valueClassUnwrap.get().toString())
            }
        }
    }

    private fun Project.configureTasks(outputPath: File) {
        val extension = extensions.getByType<ZodableExtension>()
        val kspConfig = getKspConfig()

        // typescript
        val srcDir = outputPath.resolve("src")
        val dependenciesFile = outputPath.resolve("dependencies.txt")

        // python
        val pythonOutputPath = outputPath.parentFile.resolve("pydantable")
        val pythonSrcDir = extension.packageName.get().pythonCompatible()
        val resolvedPythonSrcDir = pythonOutputPath.resolve(pythonSrcDir)
        val requirementsFile = pythonOutputPath.resolve("requirements.txt")

        // ensure we set the src dir and dependencies file as outputs of the ksp task for proper ordering
        // and gradle output tracking
        tasks.configureEach {
            if (name == kspConfig.taskName) {
                outputs.dir(srcDir)
                outputs.dir(resolvedPythonSrcDir)
                outputs.file(dependenciesFile)
                outputs.file(requirementsFile)
            }
        }

        val setupZodablePackage = tasks.register<Exec>("setupZodablePackage") {
            group = "build"
            description = "Setup zodable npm package"

            workingDir = outputPath

            // define the gradle plugin input and output dependencies
            inputs.dir(srcDir)
            inputs.file(dependenciesFile)
            outputs.file(outputPath.resolve("package.json"))
            outputs.dir(outputPath.resolve("node_modules"))
            outputs.file(outputPath.resolve("package-lock.json"))
            outputs.file(outputPath.resolve("tsconfig.json"))

            commandLine = listOf("npm", "init", "-y")

            dependsOn(kspConfig.taskName)

            doLast {
                buildList {
                    add(ExecCommand(listOf("npm", "pkg", "set", "name=${extension.packageName.get()}")))
                    add(ExecCommand(listOf("npm", "pkg", "set", "version=${extension.packageVersion.get()}")))
                    add(ExecCommand(listOf("npm", "pkg", "set", "description=${Files.ZOD_DESCRIPTION}")))
                    add(ExecCommand(listOf("npm", "pkg", "set", "type=module")))
                    add(ExecCommand(listOf("npm", "pkg", "set", "main=src/index.js")))
                    add(ExecCommand(listOf("npm", "pkg", "set", "types=src/index.d.ts")))
                    add(ExecCommand(listOf("npm", "pkg", "set", "files[0]=src/**/*")))
                    add(ExecCommand(listOf("npm", "install", "typescript@^5.0.0", "--save-dev")))
                    File(outputPath, "dependencies.txt").readLines().forEach { dep ->
                        val npmPackage = extension.externalPackageLocations.get()[dep] ?: dep
                        val installCommand =
                            extension.externalPackageInstallCommands.get()[dep] ?: listOf("npm", "install", npmPackage)
                        add(ExecCommand(installCommand))
                    }
                    extension.additionalNpmCommands.get()?.forEach { cmd ->
                        add(ExecCommand(cmd))
                    }
                    add(
                        ExecCommand(
                            listOf(
                                "npx", "tsc", "--init",
                                "-d",
                                "--module", "esnext",
                                "--moduleResolution", "bundler",
                                "--baseUrl", "./",
                                "--isolatedModules", "false",
                                "--verbatimModuleSyntax", "false"
                            )
                        )
                    )
                    add(ExecCommand(listOf("npx", "tsc")))
                }.forEach { command ->
                    execOperations.exec {
                        workingDir = outputPath
                        commandLine = command.commandLine
                        command.standardInput?.let {
                            standardInput = outputPath.resolve(it).inputStream()
                        }
                    }
                }
            }
        }
        val setupPydantablePackage = tasks.register<Exec>("setupPydantablePackage") {
            val venvPath = pythonOutputPath.resolve(".venv")
            val pythonExec = venvPath.resolve("bin/python").absolutePath
            val pipExec = venvPath.resolve("bin/pip").absolutePath

            group = "build"
            description = "Setup zodable pypi package"

            workingDir = pythonOutputPath

            // define the gradle plugin input and output dependencies
            inputs.dir(resolvedPythonSrcDir)
            inputs.file(requirementsFile)
            outputs.dir(pythonOutputPath.resolve("dist"))
            outputs.dir(pythonOutputPath.resolve(".venv"))
            outputs.file(pythonOutputPath.resolve("pyproject.toml"))
            outputs.dir(pythonOutputPath.resolve("$pythonSrcDir.egg-info"))

            commandLine = listOf("python3", "-m", "venv", ".venv")

            dependsOn(kspConfig.taskName)

            doLast {
                listOf(
                    ExecCommand(listOf(pipExec, "install", "-r", "requirements.txt")),
                    ExecCommand(listOf(pipExec, "install", "toml")),
                    ExecCommand(
                        listOf(
                            pythonExec, "-c", Files.generatePyProjectToml(
                                extension.packageName.get(),
                                extension.packageVersion.get(),
                            )
                        )
                    ),
                    ExecCommand(listOf("touch", "$pythonSrcDir/py.typed")),
                    ExecCommand(listOf(pipExec, "install", "mypy", "build", "twine")),
                    ExecCommand(listOf(pythonExec, "-m", "mypy", pythonSrcDir)),
                    ExecCommand(listOf(pythonExec, "-m", "build")),
                ).forEach { command ->
                    execOperations.exec {
                        workingDir = pythonOutputPath
                        commandLine = command.commandLine
                    }
                }
            }
        }
        tasks.named("build").configure {
            if (extension.enableTypescript.get()) dependsOn(setupZodablePackage)
            if (extension.enablePython.get()) dependsOn(setupPydantablePackage)
        }
    }

}
