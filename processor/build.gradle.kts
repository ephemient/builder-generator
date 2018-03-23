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
}

task<Jar>("sourcesJar") {
    dependsOn(tasks["classes"])
    classifier = "sources"
    from(java.sourceSets["main"].allSource)
}

artifacts {
    add("archives", tasks["sourcesJar"])
}
