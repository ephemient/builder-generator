plugins {
    kotlin("jvm") version "1.2.30"
    kotlin("kapt") version "1.2.30"
    id("org.jetbrains.dokka") version "0.9.16"
    id("org.jmailen.kotlinter") version "1.10.0"
    maven
}

val dokka by tasks.getting(org.jetbrains.dokka.gradle.DokkaTask::class) {
    doFirst {
        includeNonPublic = true
        includes = arrayListOf("README.md")
        jdkVersion = 8
        linkMappings = arrayListOf(org.jetbrains.dokka.gradle.LinkMapping().apply {
            dir = "$rootDir"
            url = "https://github.com/ephemient/builder-generator/blob/master"
            suffix = "#L"
        })
        sourceDirs += project(":annotations").file("src/main/java")
        sourceDirs += project(":processor").file("src/main/kotlin")
    }
}

val dokkaJar by tasks.creating(Jar::class) {
    dependsOn(dokka)
    classifier = "javadoc"
    from(dokka.outputDirectory)
}

configurations["archives"].artifacts.clear()
artifacts.add("archives", dokkaJar)

task<JavaExec>("ktlintIdea") {
    group = "IDE"
    description = "Apply ktlint style to IntelliJ IDEA project."
    main = "com.github.shyiko.ktlint.idea.Main"
    classpath = buildscript.configurations["classpath"]
    args("apply", "-y")
}

task<Wrapper>("wrapper") {
    gradleVersion = "4.6"
}
