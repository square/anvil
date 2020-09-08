@file:Suppress("unused")

package com.squareup.anvil.compiler.dagger

// These classes are used in some of the unit tests. Don't touch them.

object Factory

abstract class OuterClass constructor(innerClass: InnerClass) {
  class InnerClass
}
