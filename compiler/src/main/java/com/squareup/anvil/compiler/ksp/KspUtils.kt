package com.squareup.anvil.compiler.ksp

import com.google.devtools.ksp.symbol.KSClassDeclaration
import org.jetbrains.kotlin.name.FqName

val KSClassDeclaration.fqName: FqName get() {
  return FqName(qualifiedName!!.asString())
}
