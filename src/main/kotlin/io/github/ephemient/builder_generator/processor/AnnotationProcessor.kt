package io.github.ephemient.builder_generator.processor

import com.squareup.javapoet.AnnotationSpec
import com.squareup.javapoet.ClassName
import com.squareup.javapoet.FieldSpec
import com.squareup.javapoet.JavaFile
import com.squareup.javapoet.MethodSpec
import com.squareup.javapoet.ParameterSpec
import com.squareup.javapoet.ParameterizedTypeName
import com.squareup.javapoet.TypeName
import com.squareup.javapoet.TypeSpec
import com.squareup.javapoet.TypeVariableName
import io.github.ephemient.builder_generator.annotations.GenerateBuilder
import javax.annotation.Generated
import javax.annotation.processing.AbstractProcessor
import javax.annotation.processing.Filer
import javax.annotation.processing.Messager
import javax.annotation.processing.ProcessingEnvironment
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.SourceVersion
import javax.lang.model.element.Element
import javax.lang.model.element.ElementKind
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.Modifier
import javax.lang.model.element.PackageElement
import javax.lang.model.element.Parameterizable
import javax.lang.model.element.TypeElement
import javax.tools.Diagnostic.Kind
import org.jetbrains.annotations.NotNull

internal class AnnotationProcessor : AbstractProcessor() {
    private lateinit var messager: Messager
    private lateinit var filer: Filer

    override fun init(env: ProcessingEnvironment) {
        super.init(env)
        messager = env.messager
        filer = env.filer
    }

    override fun getSupportedAnnotationTypes(): Set<String> =
        listOfNotNull(GenerateBuilder::class.qualifiedName).toSet()

    override fun getSupportedSourceVersion(): SourceVersion =
        SourceVersion.latestSupported()

    override fun process(annotations: Set<TypeElement>, env: RoundEnvironment): Boolean {
        for (element in env.getElementsAnnotatedWith(GenerateBuilder::class.java)) {
            val elementAnnotations = element.getAnnotationsByType(GenerateBuilder::class.java)
            when (element) {
                is TypeElement -> processClass(element, elementAnnotations)
                is ExecutableElement -> processMethod(element, elementAnnotations)
                else -> messager.printMessage(Kind.ERROR,
                    "Not a concrete class, constructor, or method", element)
            }
        }
        return false
    }

    private fun getName(element: TypeElement): String =
        generateSequence(element) { it.enclosingElement as? TypeElement }
            .apply { require(all { it.simpleName.isNotEmpty() }) }
            .asIterable().reversed().joinToString("_") { it.simpleName }

    private fun getName(element: ExecutableElement): String {
        val name = getName(element.enclosingElement as TypeElement)
        return element.simpleName.takeIf {
            it.isNotEmpty() && it[0].isJavaIdentifierStart() &&
            it.subSequence(1..it.lastIndex).all { it.isJavaIdentifierPart() }
        }?.let { "${name}_$it" } ?: name
    }

    private fun getPackage(element: Element): String =
        generateSequence(element) { it.enclosingElement }
            .mapNotNull { (it as? PackageElement)?.qualifiedName }
            .firstOrNull { it.isNotEmpty() }?.toString() ?: ""

    private fun processClass(element: TypeElement, annotations: Array<GenerateBuilder>) {
        if (element.kind != ElementKind.CLASS || Modifier.ABSTRACT in element.modifiers) {
            messager.printMessage(Kind.ERROR, "Not a concrete class", element)
            return
        }
        val packageName =
            annotations.lastOrNull { it.packageName.isNotEmpty() }?.packageName ?:
            getPackage(element)
        val className =
            annotations.lastOrNull { it.className.isNotEmpty() }?.className ?:
            "${getName(element).capitalize()}_Builder"
        val cons = element.enclosedElements.mapNotNull {
            (it as? ExecutableElement)?.takeIf {
                Modifier.PRIVATE !in it.modifiers &&
                it.kind == ElementKind.CONSTRUCTOR &&
                it.getAnnotation(GenerateBuilder::class.java) == null
            }
        }.maxBy { it.parameters.size }
        if (cons == null) {
            messager.printMessage(Kind.ERROR, "No accessible constructor found", element)
            return
        }
        generateClass(packageName, className, cons)
    }

    private fun processMethod(element: ExecutableElement, annotations: Array<GenerateBuilder>) {
        if (!(element.kind == ElementKind.CONSTRUCTOR ||
            element.kind == ElementKind.METHOD && Modifier.STATIC in element.modifiers)) {
            messager.printMessage(Kind.ERROR, "Not a constructor or static method", element)
            return
        }
        val packageName =
            annotations.lastOrNull { it.packageName.isNotEmpty() }?.packageName ?:
            getPackage(element)
        val className =
            annotations.lastOrNull { it.className.isNotEmpty() }?.className ?:
            "${getName(element).capitalize()}_Builder"
        generateClass(packageName, className, element)
    }

    private fun generateClass(
            packageName: String, simpleName: String, element: ExecutableElement,
            vararg modifiers: Modifier = arrayOf(Modifier.PUBLIC)) {
        val className = ClassName.get(packageName, simpleName)
        val typeArguments = generateSequence<Parameterizable>(element) { it.enclosingElement as? TypeElement }
            .asIterable().reversed().flatMap { it.typeParameters }
            .associateBy { it.simpleName }.values.map(TypeVariableName::get)
        val typeName = if (typeArguments.isEmpty()) className else
            ParameterizedTypeName.get(className, *typeArguments.toTypedArray())
        val returnType = TypeName.get(
                if (element.kind == ElementKind.CONSTRUCTOR) element.enclosingElement.asType()
                else element.returnType)
        TypeSpec.classBuilder(className).apply {
            addAnnotation(AnnotationSpec.builder(Generated::class.java)
                .addMember("value", "\$S", this@AnnotationProcessor.javaClass.canonicalName)
                .build())
            addModifiers(*modifiers)
            addTypeVariables(typeArguments)
            val isConstructor = element.kind == ElementKind.CONSTRUCTOR
            val builderArgs = mutableListOf<Any>(TypeName.get(element.enclosingElement.asType()))
            if (!isConstructor) builderArgs.add(element.simpleName)
            element.parameters.flatMapTo(builderArgs) { parameter ->
                val type = TypeName.get(parameter.asType())
                val name = parameter.simpleName.toString()
                val field = FieldSpec.builder(type.box(), name, Modifier.PRIVATE).build()
                val param = ParameterSpec.builder(type, name)
                    .addAnnotations(parameter.annotationMirrors.map(AnnotationSpec::get))
                    .build()
                addField(field)
                addMethod(MethodSpec.methodBuilder("get${name.capitalize()}")
                    .addModifiers(Modifier.PUBLIC).returns(type)
                    .addStatement("return \$N", field)
                    .build())
                addMethod(MethodSpec.methodBuilder("set${name.capitalize()}")
                    .addModifiers(Modifier.PUBLIC).returns(TypeName.VOID)
                    .addParameter(param)
                    .addStatement("this.\$N = \$N", field, param)
                    .build())
                addMethod(MethodSpec.methodBuilder("with${name.capitalize()}")
                    .addModifiers(Modifier.PUBLIC).returns(typeName)
                    .addParameter(param)
                    .addStatement("this.\$N = \$N", field, param)
                    .addStatement("return this")
                    .build())
                listOf(type, field)
            }
            addMethod(MethodSpec.methodBuilder("build")
                .addAnnotations(element.annotationMirrors.map(AnnotationSpec::get)
                    .filter { it.type != TypeName.get(GenerateBuilder::class.java) }
                    .let {
                        if (isConstructor) it + AnnotationSpec.builder(NotNull::class.java).build()
                        else it
                    })
                .addModifiers(Modifier.PUBLIC).returns(returnType)
                .addStatement(
                    element.parameters.joinToString(
                        prefix = "return ${if (isConstructor) "new \$T" else "\$T.\$N"}(",
                        postfix = ")") { "(\$T) \$N" },
                    *builderArgs.toTypedArray())
                .build())
        }.let {
            JavaFile.builder(packageName, it.build()).build().writeTo(filer)
        }
    }
}
