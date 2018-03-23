plugins {
    kotlin("jvm") version "1.2.30"
    kotlin("kapt") version "1.2.30"
    id("org.jmailen.kotlinter") version "1.10.0"
}

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
