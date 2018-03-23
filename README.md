[![](https://jitpack.io/v/ephemient/builder-generator.svg)](https://jitpack.io/#ephemient/builder-generator/master-SNAPSHOT) [Dokka](https://jitpack.io/com/github/ephemient/builder-generator/build/master-SNAPSHOT/javadoc/build/index.html)

# Module builder-generator

Originally developed for a [Stack Overflow answer](https://stackoverflow.com/a/45540045/20713).

To use, add the following to `build.gradle`:

    repositories {
         maven { url 'https://jitpack.io' }
    }
    dependencies {
        annotationProcessor 'com.github.ephemient.builder-generator:processor:master-SNAPSHOT'
        compileOnly 'com.github.ephemient.builder-generator:annotations:master-SNAPSHOT'
    }

or see [example.kt](https://github.com/ephemient/builder-generator/blob/master/example/src/main/kotlin/example.kt) for an example in Kotlin.

TODO: handle Kotlin default arguments.
