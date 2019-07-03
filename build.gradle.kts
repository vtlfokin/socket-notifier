import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.3.40"
}

group = "com.example.vtlfokin"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven(url = "https://jitpack.io" )
}

val ktorVersion = "1.1.4"

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    compile("io.ktor:ktor-server-core:$ktorVersion")
    compile("io.ktor:ktor-server-netty:$ktorVersion")
    compile("io.ktor:ktor-websockets:$ktorVersion")
    compile("io.ktor:ktor-auth:$ktorVersion")
    compile("io.ktor:ktor-auth-jwt:$ktorVersion")
    compile("io.ktor:ktor-gson:$ktorVersion")
    compile("org.slf4j:slf4j-simple:1.7.25")
    compile("com.google.code.gson:gson:2.8.5")
    compile("com.github.kotlin.kotlinx~cli:kotlinx-cli-jvm:-SNAPSHOT")
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}