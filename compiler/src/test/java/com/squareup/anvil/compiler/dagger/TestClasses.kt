@file:Suppress("unused")

package com.squareup.anvil.compiler.dagger

// These classes are used in some of the unit tests. Don't touch them.

object Factory

object Date

abstract class OuterClass(
  @Suppress("UNUSED_PARAMETER") innerClass: InnerClass,
) {
  class InnerClass
}

enum class TestEnum { A, B, C }

annotation class TestAnnotation(val value: String)
