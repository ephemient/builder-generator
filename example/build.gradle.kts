plugins {
    kotlin("jvm")
    kotlin("kapt")
    id("org.jmailen.kotlinter")
    application
}

repositories {
    mavenCentral()
}

application {
    mainClassName = "io.github.ephemient.builder_generator.example.ExampleKt"
}
defaultTasks = listOf("run")

dependencies {
    kapt(rootProject)
    compileOnly(rootProject)
    implementation(kotlin("stdlib"))
}
