import com.google.protobuf.gradle.*

plugins {
    id("java")
    kotlin("jvm") version "2.2.0"
    id("com.google.protobuf") version "0.9.4"
    kotlin("plugin.serialization") version "2.2.0"
    application
}

group = "simpleweaponmodgenerator"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation("com.google.protobuf:protobuf-java:4.31.1")
    implementation("com.google.protobuf:protobuf-kotlin:4.31.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-core-jvm:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json-jvm:1.9.0")
    implementation("com.charleskorn.kaml:kaml-jvm:0.85.0")
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(20)
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:4.31.1"
    }
    generateProtoTasks {
        ofSourceSet("main").forEach {
            it.builtins {
                id("kotlin")
            }
        }
    }
}

application {
    mainClass = "simpleweaponmodgenerator.Main"
    executableDir = "out/"
}
