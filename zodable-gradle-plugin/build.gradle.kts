plugins {
    `kotlin-dsl`
    id("com.gradle.plugin-publish") version "1.2.1"
    id("com.google.devtools.ksp") version "2.2.20-2.0.4"
}

repositories {
    gradlePluginPortal()
    mavenCentral()
}

kotlin {
    jvmToolchain(21)
    dependencies {
        implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.2.20")
        implementation("com.google.devtools.ksp:com.google.devtools.ksp.gradle.plugin:2.2.20-2.0.4")
    }
}

group = "dev.zodable"
version = "1.7.4"

gradlePlugin {
    website = "https://github.com/zodable/zodable"
    vcsUrl = "https://github.com/zodable/zodable.git"

    plugins {
        create("zodable-gradle-plugin") {
            id = "dev.zodable"
            implementationClass = "dev.zodable.ZodablePlugin"
            displayName = "Zodable"
            description = "Generate zod schemas from Kotlin data classes."
            tags = listOf("zod", "ts", "ksp")
        }
    }
}
