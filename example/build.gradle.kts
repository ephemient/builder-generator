plugins {
    kotlin("jvm")
    kotlin("kapt")
    id("org.jmailen.kotlinter")
    application
}

repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

application {
    mainClassName = "com.github.ephemient.builder_generator.example.ExampleKt"
}
defaultTasks = listOf("run")

dependencies {
    kapt("com.github.ephemient.builder-generator:processor:master-SNAPSHOT")
    compileOnly("com.github.ephemient.builder-generator:annotations:master-SNAPSHOT")
    implementation(kotlin("stdlib"))
}
