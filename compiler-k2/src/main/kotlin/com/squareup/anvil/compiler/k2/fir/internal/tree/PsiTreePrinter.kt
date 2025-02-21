package com.squareup.anvil.compiler.k2.fir.internal.tree

import org.jetbrains.kotlin.com.intellij.psi.PsiElement

/**
 * prints a tree starting at any arbitrary psi element,
 * showing all its children types and their text
 */
internal class PsiTreePrinter(
  whitespaceChar: Char = ' ',
) : AbstractTreePrinter<PsiElement>(whitespaceChar) {

  override fun PsiElement.text(): String = text
  override fun PsiElement.typeName(): String = node.elementType.toString()
  override fun PsiElement.parent(): PsiElement? = parent
  override fun PsiElement.simpleClassName(): String = this::class.java.simpleName
  override fun PsiElement.children(): Sequence<PsiElement> = children.asSequence()

  companion object {

    fun <T : PsiElement> T.printEverything(whitespaceChar: Char = ' '): T =
      apply { PsiTreePrinter(whitespaceChar).printTreeString(this) }
  }
}
