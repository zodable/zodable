plugins {
    `kotlin-dsl`
    id("com.gradle.plugin-publish") version "1.2.1"
    id("com.google.devtools.ksp") version "2.2.20-2.0.4"
    id("com.vanniktech.maven.publish") version "0.28.0"
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
version = "1.7.5"

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

mavenPublishing {
    publishToMavenCentral(com.vanniktech.maven.publish.SonatypeHost.CENTRAL_PORTAL)
    signAllPublications()
    pom {
        name.set("zodable-gradle-plugin")
        description.set("Generate zod schemas from Kotlin data classes.")
        url.set("https://github.com/zodable/zodable")
        licenses {
            license {
                name.set("Apache 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }
        developers {
            developer {
                id.set("nathanfallet")
                name.set("Nathan Fallet")
                email.set("contact@nathanfallet.me")
                url.set("https://www.nathanfallet.me")
            }
        }
        scm {
            url.set("https://github.com/zodable/zodable.git")
        }
    }
}
