package com.squareup.anvil.compiler

import com.squareup.anvil.compiler.api.AnvilCompilationException
import org.jetbrains.kotlin.ir.declarations.IrDeclarationWithName
import org.jetbrains.kotlin.ir.util.fqNameWhenAvailable
import org.jetbrains.kotlin.name.FqName

internal val IrDeclarationWithName.fqName: FqName
  get() = fqNameWhenAvailable ?: throw AnvilCompilationException(
    message = "Couldn't find FqName for $name",
    element = this,
  )
