package com.squareup.anvil.compiler.k2.fir.internal.tree

import org.jetbrains.kotlin.fir.FirElement
import org.jetbrains.kotlin.fir.render
import org.jetbrains.kotlin.fir.visitors.FirDefaultVisitorVoid

internal class FirTreePrinter(
  whitespaceChar: Char = ' ',
) : AbstractTreePrinter<FirElement>(whitespaceChar) {

  private val childToParent = mutableMapOf<FirElement, FirElement>()

  override fun FirElement.text(): String = this@text.render()

  override fun FirElement.typeName(): String = this::class.java.simpleName

  override fun FirElement.parent(): FirElement? = childToParent[this]

  override fun FirElement.simpleClassName(): String = this::class.java.simpleName

  override fun FirElement.children(): Sequence<FirElement> {
    val children = mutableListOf<FirElement>()
    acceptChildren(
      object : FirDefaultVisitorVoid() {
        override fun visitElement(element: FirElement) {
          childToParent[element] = this@children
          children += element
        }
      },
    )
    return children.asSequence()
  }

  companion object {

    fun <T : FirElement> T.printEverything(whitespaceChar: Char = ' '): T =
      apply { FirTreePrinter(whitespaceChar).printTreeString(this) }
  }
}
