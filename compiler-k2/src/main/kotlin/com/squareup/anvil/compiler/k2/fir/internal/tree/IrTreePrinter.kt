package com.squareup.anvil.compiler.k2.fir.internal.tree

import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.util.DumpIrTreeOptions
import org.jetbrains.kotlin.ir.util.render
import org.jetbrains.kotlin.ir.visitors.IrElementVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid

internal class IrTreePrinter(
  whitespaceChar: Char = ' ',
) : AbstractTreePrinter<IrElement>(whitespaceChar) {

  private val childToParent = mutableMapOf<IrElement, IrElement>()

  override fun IrElement.text(): String = this@text.render(
    DumpIrTreeOptions(
      normalizeNames = false,
      stableOrder = true,
      verboseErrorTypes = false,
      printFacadeClassInFqNames = false,
      printFlagsInDeclarationReferences = false,
      renderOriginForExternalDeclarations = false,
      printSignatures = true,
      printTypeAbbreviations = false,
      printModuleName = false,
      printFilePath = false,
      isHiddenDeclaration = { false },
    ),
  )

  override fun IrElement.typeName(): String = this::class.java.simpleName

  override fun IrElement.parent(): IrElement? = childToParent[this]

  override fun IrElement.simpleClassName(): String = this::class.java.simpleName

  override fun IrElement.children(): Sequence<IrElement> {
    val children = mutableListOf<IrElement>()
    acceptChildrenVoid(
      object : IrElementVisitorVoid {
        override fun visitElement(element: IrElement) {
          childToParent[element] = this@children
          children += element
        }
      },
    )
    return children.asSequence()
  }

  companion object {

    fun <T : IrElement> T.printEverything(whitespaceChar: Char = ' '): T =
      apply { IrTreePrinter(whitespaceChar).printTreeString(this) }
  }
}
