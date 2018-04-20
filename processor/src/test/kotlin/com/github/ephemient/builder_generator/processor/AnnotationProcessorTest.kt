package com.github.ephemient.builder_generator.processor

import com.google.common.truth.Truth.assertThat
import com.google.testing.compile.Compiler.javac
import com.google.testing.compile.CompilationSubject.assertThat
import com.google.testing.compile.JavaFileObjects
import org.junit.jupiter.api.Test

class AnnotationProcessorTest {
    @Test
    fun `non-annotated class should not be processed`() {
        val compilation = javac()
            .withProcessors(AnnotationProcessor())
            .compile(testSource())
        assertThat(compilation).succeededWithoutWarnings()
        assertThat(compilation.generatedSourceFiles()).isEmpty()
    }

    @Test
    fun `annotated class should be processed`() {
        val compilation = javac()
            .withProcessors(AnnotationProcessor())
            .compile(testSource(annotations = "@GenerateBuilder"))
        assertThat(compilation).succeededWithoutWarnings()
        assertThat(compilation.generatedSourceFiles()).hasSize(1)
        assertThat(compilation)
            .generatedSourceFile("com.github.ephemient.builder_generator.test.Data_Builder")
            .hasSourceEquivalentTo(
                testGeneratedSource(
                    extraImports = listOf(
                        "javax.annotation.Generated",
                        "org.jetbrains.annotations.NotNull"
                    ),
                    returnAnnotations = "@NotNull"
                )
            )
    }

    @Test
    fun `annotation settings should be observed`() {
        val compilation = javac()
            .withProcessors(AnnotationProcessor())
            .compile(
                testSource(
                    annotations = "@GenerateBuilder(className = \"DaBuilder\", packageName = \"example\", isPublic = false)"
                )
            )
        assertThat(compilation).succeededWithoutWarnings()
        assertThat(compilation.generatedSourceFiles()).hasSize(1)
        assertThat(compilation)
            .generatedSourceFile("example.DaBuilder")
            .hasSourceEquivalentTo(
                testGeneratedSource(
                    packageName = "example",
                    extraImports = listOf(
                        "com.github.ephemient.builder_generator.test.Data",
                        "javax.annotation.Generated",
                        "org.jetbrains.annotations.NotNull"
                    ),
                    isPublic = false,
                    name = "DaBuilder",
                    returnAnnotations = "@NotNull"
                )
            )
    }

    @Test
    fun `longest constructor and explicitly annotated constructors should be chosen`() {
        val compilation = javac()
            .withProcessors(AnnotationProcessor())
            .compile(
                testSource(
                    annotations = "@GenerateBuilder(className = \"Data_Builder_1\")",
                    body = """
                    |Data() {}
                    |Data(int primitive) {}
                    |@GenerateBuilder(className = "Data_Builder_2") Data(int primitive, String object) {}
                    """.trimMargin()
                )
            )
        assertThat(compilation).succeededWithoutWarnings()
        assertThat(compilation.generatedSourceFiles()).hasSize(2)
        assertThat(compilation)
            .generatedSourceFile("com.github.ephemient.builder_generator.test.Data_Builder_1")
            .hasSourceEquivalentTo(
                testGeneratedSource(
                    extraImports = listOf(
                        "java.lang.Integer",
                        "javax.annotation.Generated",
                        "org.jetbrains.annotations.NotNull"
                    ),
                    name = "Data_Builder_1",
                    fields = listOf(GeneratedField("primitive", "Integer", "int")),
                    returnAnnotations = "@NotNull"
                )
            )
        assertThat(compilation)
            .generatedSourceFile("com.github.ephemient.builder_generator.test.Data_Builder_2")
            .hasSourceEquivalentTo(
                testGeneratedSource(
                    extraImports = listOf(
                        "java.lang.Integer",
                        "java.lang.String",
                        "javax.annotation.Generated",
                        "org.jetbrains.annotations.NotNull"
                    ),
                    name = "Data_Builder_2",
                    fields = listOf(
                        GeneratedField("primitive", "Integer", "int"),
                        GeneratedField("object", "String")
                    ),
                    returnAnnotations = "@NotNull"
                )
            )
    }

    @Test
    fun `generics, annotations, nested classes, and methods should work`() {
        val compilation = javac()
            .withProcessors(AnnotationProcessor())
            .compile(
                testSource(
                    extraImports = listOf("java.util.Map"),
                    body = """
                    |@GenerateBuilder static class Item<T extends Comparable<T>, U extends Iterable<T>> {
                    |    Item(@NotNull T key, @Nullable U values) {}
                    |}
                    |@GenerateBuilder @Nullable
                    |static <T extends Comparable<T>, U extends Iterable<T>> Map<T, U> create(
                    |    @NotNull T key, @Nullable U values) { return null; }
                    """.trimMargin()
                )
            )
        assertThat(compilation).succeededWithoutWarnings()
        assertThat(compilation.generatedSourceFiles()).hasSize(2)
        assertThat(compilation)
            .generatedSourceFile("com.github.ephemient.builder_generator.test.Data_Item_Builder")
            .hasSourceEquivalentTo(
                testGeneratedSource(
                    extraImports = listOf(
                        "java.lang.Comparable",
                        "java.lang.Iterable",
                        "javax.annotation.Generated",
                        "org.jetbrains.annotations.NotNull",
                        "org.jetbrains.annotations.Nullable"
                    ),
                    name = "Data_Item_Builder",
                    generics = "<T extends Comparable<T>, U extends Iterable<T>>",
                    shortGenerics = "<T, U>",
                    fields = listOf(
                        GeneratedField("key", "T", annotations = "@NotNull"),
                        GeneratedField("values", "U", annotations = "@Nullable")
                    ),
                    toName = "Data.Item<T, U>",
                    returnAnnotations = "@NotNull"
                )
            )
        assertThat(compilation)
            .generatedSourceFile("com.github.ephemient.builder_generator.test.Data_create_Builder")
            .hasSourceEquivalentTo(
                testGeneratedSource(
                    extraImports = listOf(
                        "java.lang.Comparable",
                        "java.lang.Iterable",
                        "java.util.Map",
                        "javax.annotation.Generated",
                        "org.jetbrains.annotations.NotNull",
                        "org.jetbrains.annotations.Nullable"
                    ),
                    name = "Data_create_Builder",
                    generics = "<T extends Comparable<T>, U extends Iterable<T>>",
                    shortGenerics = "<T, U>",
                    fields = listOf(
                        GeneratedField("key", "T", annotations = "@NotNull"),
                        GeneratedField("values", "U", annotations = "@Nullable")
                    ),
                    toName = "Map<T, U>",
                    returnAnnotations = "@Nullable",
                    call = "Data.create"
                )
            )
    }

    private fun testSource(
        extraImports: List<String> = listOf(),
        annotations: String = "",
        name: String = "Data",
        generics: String = "",
        body: String = ""
    ) = JavaFileObjects.forSourceString(
        name,
        """
        |package com.github.ephemient.builder_generator.test;
        |${extraImports.joinToString("\n") { "import $it;" }}
        |import com.github.ephemient.builder_generator.annotations.GenerateBuilder;
        |import org.jetbrains.annotations.NotNull;
        |import org.jetbrains.annotations.Nullable;
        |$annotations
        |public class $name$generics {
        |$body
        |}
        """.trimMargin()
    )

    private fun testGeneratedSource(
        packageName: String = "com.github.ephemient.builder_generator.test",
        extraImports: List<String> = listOf(),
        isPublic: Boolean = true,
        name: String = "Data_Builder",
        generics: String = "",
        shortGenerics: String = "",
        fields: List<GeneratedField> = listOf(),
        toName: String = "Data$shortGenerics",
        returnAnnotations: String = "",
        call: String = "new $toName"
    ) = JavaFileObjects.forSourceString(
        name,
        """
        |package $packageName;
        |${extraImports.joinToString("\n") { "import $it;" }}
        |@Generated("com.github.ephemient.builder_generator.processor.AnnotationProcessor")
        |${if (isPublic) "public " else ""}class $name$generics {
        |${fields.joinToString("\n") { "private ${it.objectType} ${it.name};\n" }}
        |${
            fields.joinToString("\n") {
                "public ${it.objectType} get${it.name.capitalize()}() { return ${it.name}; }\n" +
                "public void set${it.name.capitalize()}(${it.annotations} ${it.type} ${it.name}) { this.${it.name} = ${it.name}; }\n" +
                "public $name$shortGenerics with${it.name.capitalize()}(${it.annotations} ${it.type} ${it.name}) { this.${it.name} = ${it.name}; return this; }"
            }
        }
        |$returnAnnotations
        |public $toName build() {
        |return $call(${fields.joinToString(", ") { "(${it.type}) ${it.name}" }});
        |}
        |}
        """.trimMargin()
    )

    private data class GeneratedField(
        val name: String,
        val objectType: String,
        val primitiveType: String? = null,
        val annotations: String = ""
    ) {
        val type: String
            get() = primitiveType ?: objectType
    }
}
