
import org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
import org.gradle.api.tasks.testing.logging.TestLogEvent.FAILED
import org.gradle.api.tasks.testing.logging.TestLogEvent.SKIPPED
import org.gradle.api.tasks.testing.logging.TestLogEvent.STANDARD_ERROR

plugins {
    id("org.jetbrains.kotlin.jvm") version "1.6.20"
    id("org.jetbrains.kotlin.plugin.serialization") version "1.4.20"

    id("org.jlleitschuh.gradle.ktlint") version "10.2.1"

    application
}

repositories {
    google()
    mavenCentral()
}

dependencies {
    // Jackson
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.13.2")

    // http4k
    implementation(platform("org.http4k:http4k-bom:4.20.0.0"))
    implementation("org.http4k:http4k-core")
    implementation("org.http4k:http4k-server-jetty")
    implementation("org.http4k:http4k-client-okhttp:4.25.10.1")

    // kaml (Yaml)
    implementation("com.charleskorn.kaml:kaml:0.42.0")

    // Test deps
    testImplementation(kotlin("test"))
    testImplementation("com.natpryce:hamkrest")
    testImplementation("org.http4k:http4k-testing-hamkrest")
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.junit.jupiter:junit-jupiter-api")
    testImplementation("io.mockk:mockk:1.12.3")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging {
        events(FAILED, STANDARD_ERROR, SKIPPED)
        exceptionFormat = FULL
        showExceptions = true
        showCauses = true
        showStackTraces = true
    }
}

application {
    mainClass.set("com.cachigo.MainKt")
}
