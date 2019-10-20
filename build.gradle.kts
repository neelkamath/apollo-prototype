plugins {
    application
    kotlin("jvm") version "1.3.50"
    id("com.github.johnrengelman.shadow") version "5.1.0"
}

application.mainClassName = "io.ktor.server.netty.EngineMain"

repositories { jcenter() }

dependencies {
    val ktorVersion = "1.2.4"
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-gson:$ktorVersion")
    implementation("io.ktor:ktor-client-core:$ktorVersion")
    implementation("io.ktor:ktor-client-apache:$ktorVersion")
    implementation("io.ktor:ktor-client-json:$ktorVersion")
    implementation("io.ktor:ktor-client-gson:$ktorVersion")
    implementation("ch.qos.logback:logback-classic:1.2.3")
    implementation("org.mongodb:mongodb-driver-sync:3.11.1")
    implementation(kotlin("stdlib-jdk8"))
}

kotlin.sourceSets { getByName("main").kotlin.srcDirs("src/main") }

tasks.withType<Jar> {
    manifest { attributes(mapOf("Main-Class" to application.mainClassName)) }
}