// Shining's server. For now it only has the Samad discovery tool (plan Phase 0); the menu
// watcher comes in Phase 1.
plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.serialization")
    application
}

kotlin {
    jvmToolchain(17)
}

application {
    mainClass.set("app.shining.server.MainKt")
    applicationName = "shining-server"
}

val ktorVersion = "2.3.12"

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-cio:$ktorVersion")
    runtimeOnly("org.slf4j:slf4j-simple:2.0.13")

    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
}

tasks.test {
    useJUnitPlatform()
}
