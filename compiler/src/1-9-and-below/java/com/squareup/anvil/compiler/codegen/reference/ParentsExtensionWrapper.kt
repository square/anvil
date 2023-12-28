package com.squareup.anvil.compiler.codegen.reference

import org.jetbrains.kotlin.backend.common.lower.parents
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrDeclarationParent

// The package changed in Kotlin 2.x. Can be deleted when we drop support for 1.9
val IrDeclaration.parentsWrapped: Sequence<IrDeclarationParent>
  get() = parents
