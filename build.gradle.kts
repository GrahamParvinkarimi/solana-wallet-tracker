plugins {
    kotlin("jvm") version "2.0.21"
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven {
        url = uri("https://jitpack.io")  // Sometimes needed for some Solana SDK dependencies
    }
}

dependencies {
    // OkHttp for HTTP requests
    //implementation("com.squareup.okhttp3:okhttp:5.0.0-alpha.2") // OkHttp client

    // Gson for JSON parsing
    implementation("com.google.code.gson:gson:2.10.1")

    // Coroutines (async)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.0")

    implementation("com.squareup.okhttp3:okhttp:5.0.0-alpha.2")
    implementation("com.squareup.okhttp3:logging-interceptor:5.0.0-alpha.2")

    implementation("com.github.doyaaaaaken:kotlin-csv-jvm:1.9.0")
    //implementation("ch.qos.logback:logback-classic:1.4.11")
    implementation("com.opencsv:opencsv:5.7.1")
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(17)
}