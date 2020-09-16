package com.squareup.anvil.compiler

import org.jetbrains.kotlin.codegen.CompilationException
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.com.intellij.psi.PsiNameIdentifierOwner
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.symbols.IrSymbol
import org.jetbrains.kotlin.ir.util.render
import org.jetbrains.kotlin.resolve.source.getPsi
import org.jetbrains.kotlin.util.getExceptionMessage

class AnvilCompilationIrException(
  message: String,
  cause: Throwable? = null,
  element: IrElement? = null
) : CompilationException(
    getExceptionMessage(
        subsystemName = "Anvil",
        message = message,
        cause = cause,
        location = element?.render()
    ),
    cause,
    element?.getPsi()
) {
  constructor(
    message: String,
    cause: Throwable? = null,
    element: IrSymbol? = null
  ) : this(message, cause = cause, element = element?.owner)

  init {
    if (element != null) {
      withAttachment("element.kt", element.render())
    }
  }
}

private fun IrElement.getPsi(): PsiElement? {
  return when (this) {
    is IrClass -> (this.source.getPsi() as? PsiNameIdentifierOwner)?.identifyingElement
    else -> null
  }
}
