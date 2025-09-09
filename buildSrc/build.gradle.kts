plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(8)
}

dependencies {
    implementation(libs.korte)
    implementation(libs.simplexml)
    implementation(libs.kotlinx.coroutines.core)
}
