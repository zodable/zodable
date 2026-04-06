plugins {
    kotlin("jvm")
    id("digital.guimauve.zodable")
    id("com.google.devtools.ksp")
}

dependencies {
    api(project(":sample-package-multiplatform"))
    api("org.jetbrains.kotlinx:kotlinx-datetime:0.7.1")
}

kotlin {
    jvmToolchain(21)
}

zodable {
    packageName = "zodable-sample-package"
    enablePython = true // Default is false
    valueClassUnwrap = true
}
