package com.squareup.anvil.compiler.k2.internal

import org.jetbrains.kotlin.GeneratedDeclarationKey

internal abstract class DefaultGeneratedDeclarationKey : GeneratedDeclarationKey() {
  override fun toString(): String = this::class.java.enclosingClass.simpleName
}
