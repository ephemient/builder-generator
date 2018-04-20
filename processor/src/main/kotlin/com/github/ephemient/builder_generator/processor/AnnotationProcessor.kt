package com.github.ephemient.builder_generator.processor

import com.github.ephemient.builder_generator.annotations.GenerateBuilder
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
import javax.lang.model.type.TypeKind
import javax.tools.Diagnostic.Kind
import org.jetbrains.annotations.NotNull

/**
 * Writes Java sources of Builder classes for [GenerateBuilder]-annotated elements.
 */
internal class AnnotationProcessor : AbstractProcessor() {
    /** @suppress */
    private lateinit var messager: Messager
    /** @suppress */
    private lateinit var filer: Filer

    /** @suppress */
    override fun init(env: ProcessingEnvironment) {
        super.init(env)
        messager = env.messager
        filer = env.filer
    }

    /** @suppress */
    override fun getSupportedAnnotationTypes(): Set<String> =
        listOfNotNull(GenerateBuilder::class.qualifiedName).toSet()

    /** @suppress */
    override fun getSupportedSourceVersion(): SourceVersion = SourceVersion.latestSupported()

    /** @suppress */
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

    /**
     * Default package name unless overridden in [GenerateBuilder] annotation.
     * @return package name
     */
    private fun getPackage(element: Element): String =
        generateSequence(element) { it.enclosingElement }
            .filterIsInstance<PackageElement>().firstOrNull()?.qualifiedName?.toString() ?: ""

    /**
     * Default base class name (without `_Builder` suffix) unless overridden.
     * @return underscore-joined simple names (e.g. `Outer.Inner` → `Outer_Inner`)
     */
    private fun getName(element: TypeElement): String =
        generateSequence(element) { it.enclosingElement as? TypeElement }
            .filter { it.simpleName.isNotEmpty() }.asIterable().reversed()
            .joinToString("_") { it.simpleName }

    /**
     * Default base class name (without `_Builder` suffix) unless overridden.
     * @return base name, including method name when applicable
     */
    private fun getName(element: ExecutableElement): String = when (element.kind) {
        ElementKind.CONSTRUCTOR -> getName(element.enclosingElement as TypeElement)
        else -> "${getName(element.enclosingElement as TypeElement)}_${element.simpleName}"
    }

    /** Generate a builder for the best constructor of a class. */
    private fun processClass(element: TypeElement, annotations: Array<GenerateBuilder>) {
        if (element.kind != ElementKind.CLASS || Modifier.ABSTRACT in element.modifiers) {
            messager.printMessage(Kind.ERROR, "Not a concrete class", element)
            return
        }
        val packageName = annotations.lastOrNull { it.packageName.isNotEmpty() }?.packageName
            ?: getPackage(element)
        val className = annotations.lastOrNull { it.className.isNotEmpty() }?.className
            ?: "${getName(element).capitalize()}_Builder"
        val isPublic = annotations.any { it.isPublic }
        // This is a heuristic for the primary constructor, assuming that those with fewer params or
        // annotation are convenience constructors. Also exclude private as they are unusable.
        val cons = element.enclosedElements.asSequence()
            .filterIsInstance<ExecutableElement>().filter {
                Modifier.PRIVATE !in it.modifiers && it.kind == ElementKind.CONSTRUCTOR &&
                it.getAnnotation(GenerateBuilder::class.java) == null
            }.maxBy { it.parameters.size }
        if (cons == null) {
            messager.printMessage(Kind.ERROR, "No accessible constructor found", element)
            return
        }
        generateClass(packageName, className, cons, isPublic)
    }

    /** Generate a builder for a constructor or static method. */
    private fun processMethod(element: ExecutableElement, annotations: Array<GenerateBuilder>) {
        if (!(element.kind == ElementKind.CONSTRUCTOR ||
            element.kind == ElementKind.METHOD && Modifier.STATIC in element.modifiers)) {
            messager.printMessage(Kind.ERROR, "Not a constructor or static method", element)
            return
        }
        val packageName = annotations.lastOrNull { it.packageName.isNotEmpty() }?.packageName
            ?: getPackage(element)
        val className = annotations.lastOrNull { it.className.isNotEmpty() }?.className
            ?: "${getName(element).capitalize()}_Builder"
        val isPublic = annotations.any { it.isPublic }
        generateClass(packageName, className, element, isPublic)
    }

    /**
     * Writes out a Java class.
     *
     * For example, given
     *
     *     @GenerateBuilder class Pair<A, B> {
     *         Pair(A a, B b);
     *     }
     *
     * it will produce something like
     *
     *     class Pair_Builder<A, B> {
     *         A a;
     *         B b;
     *         void setA(A a) { this.a = a; }
     *         void setB(B b) { this.b = b; }
     *         Pair<A, B> build() { return new Pair<A, B>(a, b); }
     *     }
     */
    private fun generateClass(
        packageName: String,
        simpleName: String,
        element: ExecutableElement,
        isPublic: Boolean
    ) {
        val isConstructor = element.kind == ElementKind.CONSTRUCTOR
        val className = ClassName.get(packageName, simpleName)
        val typeArguments =
            generateSequence<Parameterizable>(element) { it.enclosingElement as? TypeElement }
                .asIterable().reversed().flatMap { it.typeParameters }.associateBy { it.simpleName }
                .values.map(TypeVariableName::get)
        val typeName = if (typeArguments.isEmpty()) className else
            ParameterizedTypeName.get(className, *typeArguments.toTypedArray())
        val returnType = TypeName.get(
            if (isConstructor) element.enclosingElement.asType() else element.returnType)
        with(TypeSpec.classBuilder(className)) {
            addAnnotation(AnnotationSpec.builder(Generated::class.java)
                .addMember("value", "\$S", this@AnnotationProcessor.javaClass.canonicalName)
                .build())
            if (isPublic) addModifiers(Modifier.PUBLIC)
            addTypeVariables(typeArguments)
            // When isConstructor, the builder() method body is { return new Type(args...) }.
            val builderArgs: MutableList<Any> =
                if (isConstructor) mutableListOf(TypeName.get(element.enclosingElement.asType()))
                else mutableListOf(
                    ClassName.get(element.enclosingElement.asType()),
                    element.simpleName)
            element.parameters.flatMapTo(builderArgs) { parameter ->
                val type = TypeName.get(parameter.asType())
                val name = parameter.simpleName.toString()
                val getterPrefix = if (parameter.asType().kind == TypeKind.BOOLEAN) "is" else "get"
                // Use boxed fields to avoid defaults for primitive arguments.
                // It would be nice to make use of Kotlin's default args when if they exist, but
                // unfortunately that info is only found in a private format in @kotlin.MetaData.
                val field = FieldSpec.builder(type.box(), name, Modifier.PRIVATE).build()
                // Copy annotations to the setter param, such as @NotNull.
                val param = ParameterSpec.builder(type, name)
                    .addAnnotations(parameter.annotationMirrors.map(AnnotationSpec::get)).build()
                addField(field)
                addMethod(MethodSpec.methodBuilder("$getterPrefix${name.capitalize()}")
                    .addModifiers(Modifier.PUBLIC).returns(type.box())
                    .addStatement("return \$N", field).build())
                addMethod(MethodSpec.methodBuilder("set${name.capitalize()}")
                    .addModifiers(Modifier.PUBLIC).returns(TypeName.VOID).addParameter(param)
                    .addStatement("this.\$N = \$N", field, param).build())
                addMethod(MethodSpec.methodBuilder("with${name.capitalize()}")
                    .addModifiers(Modifier.PUBLIC).returns(typeName).addParameter(param)
                    .addStatement("this.\$N = \$N", field, param).addStatement("return this")
                    .build())
                // The constructor/method is called with "(type) field" arguments to avoid
                // accidentally resolving to a different overload due the field type being boxed.
                listOf(type, field)
            }
            addMethod(MethodSpec.methodBuilder("build")
                .addAnnotations(element.annotationMirrors.map(AnnotationSpec::get).filter {
                    it.type != TypeName.get(GenerateBuilder::class.java)
                }.let {
                    if (isConstructor) it + AnnotationSpec.builder(NotNull::class.java).build()
                    else it
                }).addModifiers(Modifier.PUBLIC).returns(returnType)
                .addExceptions(element.thrownTypes.map(TypeName::get)).addStatement(
                    element.parameters.joinToString(
                        prefix = "return ${if (isConstructor) "new \$T" else "\$T.\$N"}(",
                        postfix = ")") { "(\$T) \$N" },
                    *builderArgs.toTypedArray())
                .build())
            JavaFile.builder(packageName, build()).build().writeTo(filer)
        }
    }
}
