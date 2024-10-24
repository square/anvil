package com.squareup.anvil.compiler.fir.internal

import org.jetbrains.kotlin.GeneratedDeclarationKey

internal abstract class DefaultGeneratedDeclarationKey : GeneratedDeclarationKey() {
  override fun toString(): String = this::class.java.enclosingClass.simpleName
}
