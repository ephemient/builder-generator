plugins {
    id("java-library")
    maven
}

task<Jar>("sourcesJar") {
    dependsOn(tasks["classes"])
    classifier = "sources"
    from(java.sourceSets["main"].allSource)
}

task<Jar>("javadocJar") {
    dependsOn(tasks.withType<Javadoc>())
    classifier = "javadoc"
    from(tasks.withType<Javadoc>().map { it.destinationDir })
}

artifacts {
    add("archives", tasks["sourcesJar"])
    add("archives", tasks["javadocJar"])
}
