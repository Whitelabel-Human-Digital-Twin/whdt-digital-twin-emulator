plugins {
    kotlin("jvm") version "2.3.0"
    kotlin("plugin.serialization") version "2.2.0"
}

group = "io.github.whdt"
version = "1.0-SNAPSHOT"

repositories {
    maven {
        url = uri("https://maven.pkg.github.com/Whitelabel-Human-Digital-Twin/whdt") // or the correct GitHub repo
        credentials {
            username = project.findProperty("gpr.user") as String? ?: System.getenv("GPR_USER")
            password = project.findProperty("gpr.key") as String? ?: System.getenv("GPR_TOKEN")
        }
    }
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
    implementation("io.github.whdt:whdt-core:0.3.0")
    implementation("io.github.whdt:whdt-distributed:0.1.0")
    implementation("com.hivemq:hivemq-mqtt-client:1.3.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
}

kotlin {
    jvmToolchain(23)
}

tasks.test {
    useJUnitPlatform()
}