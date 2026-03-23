plugins {
    kotlin("multiplatform").version("2.2.20").apply(false)
    kotlin("plugin.serialization").version("2.2.20").apply(false)
    id("org.jetbrains.kotlinx.kover").version("0.8.0").apply(false)
    id("com.google.devtools.ksp").version("2.2.20-2.0.4").apply(false)
    id("com.vanniktech.maven.publish").version("0.28.0").apply(false)
}

allprojects {
    group = "digital.guimauve.zodable"
    version = "1.7.3"
    project.ext.set("url", "https://github.com/guimauvedigital/zodable")
    project.ext.set("license.name", "Apache 2.0")
    project.ext.set("license.url", "https://www.apache.org/licenses/LICENSE-2.0.txt")
    project.ext.set("developer.id", "nathanfallet")
    project.ext.set("developer.name", "Nathan Fallet")
    project.ext.set("developer.email", "contact@nathanfallet.me")
    project.ext.set("developer.url", "https://www.nathanfallet.me")
    project.ext.set("scm.url", "https://github.com/guimauvedigital/zodable.git")

    repositories {
        mavenCentral()
    }
}
