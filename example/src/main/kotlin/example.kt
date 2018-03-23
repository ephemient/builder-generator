package com.github.ephemient.builder_generator.example

import com.github.ephemient.builder_generator.annotations.GenerateBuilder

// can annotate a class
@GenerateBuilder
data class MyData(val i: Int, val j: Int?, val r: String, val s: String?)

// generic types are preserved
data class Outer<T>(val t: T) {
    // can annotate a constructor
    // static nested classes work
    data class Static<T, U> @GenerateBuilder constructor (val outer: Outer<T>, val value: U)

    // non-static inner classes don't work...
    inner class Inner() {
        override fun toString() = "InnerHolder(outer=${this@Outer})"
    }
}

// but static methods are fine
@GenerateBuilder(className = "Inner_Builder")
fun <T> inner(outer: Outer<T>): Outer<T>.Inner = outer.Inner()

fun main(vararg args: String) {
    // property syntax
    val o1 = MyData_Builder().apply {
        i = 1
        j = 2
        r = "hello"
        s = null
    }.build()
    println(o1)

    // setters
    val o2 = Outer_Static_Builder<Int, String>().apply {
        setOuter(Outer(3))
        setValue("world")
    }.build()
    println(o2)

    // chaining accessors
    val o3 = Inner_Builder<Int>().withOuter(Outer(4)).build()
    println(o3)
}
