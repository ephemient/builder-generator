plugins {
    id("java-library")
    kotlin("jvm")
    kotlin("kapt")
    id("org.jmailen.kotlinter")
    maven
}

repositories {
    mavenCentral()
}

dependencies {
    api(project(":annotations"))
    implementation(kotlin("stdlib"))
    implementation(kotlin("reflect"))
    implementation("com.squareup:javapoet:1.10.0")
    testImplementation("com.google.testing.compile:compile-testing:0.13")
    testImplementation("com.google.truth:truth:0.40")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.1.1")
    testImplementation("org.junit.jupiter:junit-jupiter-engine:5.1.1")
    testRuntime("org.junit.platform:junit-platform-launcher:1.1.1")
    testRuntime(files(org.gradle.internal.jvm.Jvm.current().getToolsJar()))
}

tasks.withType<Test> {
    useJUnitPlatform()
}

val sourcesJar by tasks.creating(Jar::class) {
    dependsOn("classes")
    classifier = "sources"
    from(java.sourceSets["main"].allSource)
}

artifacts.add("archives", sourcesJar)
