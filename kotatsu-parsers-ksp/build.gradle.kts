plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(8)
}

dependencies {
    implementation(libs.ksp.symbol.processing.api)
}
