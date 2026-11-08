plugins {
    kotlin("jvm") version "2.4.21"
    application
}
kotlin { jvmToolchain(21) }
dependencies {
    implementation("com.squareup.okhttp3:okhttp:5.4.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
}
application { mainClass.set("BenchKt") }
