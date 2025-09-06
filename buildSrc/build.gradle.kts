plugins {
    kotlin("jvm") version "2.2.10"
}

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(8)
}

dependencies {
    implementation(gradleApi())
    implementation("org.simpleframework:simple-xml:2.7.1")
    implementation("com.soywiz.korlibs.korte:korte-jvm:4.0.10")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
}
