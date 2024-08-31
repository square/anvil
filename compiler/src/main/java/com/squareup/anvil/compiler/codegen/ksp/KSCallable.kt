package com.squareup.anvil.compiler.codegen.ksp

import com.google.devtools.ksp.isAbstract
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSType
import org.jetbrains.kotlin.name.FqName

/**
 * Abstraction over [KSFunctionDeclaration] and [KSPropertyDeclaration].
 */
internal sealed class KSCallable(
  declaration: KSDeclaration,
) : KSDeclaration by declaration {
  data class CacheEntry(
    val containingClass: FqName,
    val declarationName: String,
    val isFunction: Boolean,
  ) {
    fun materialize(resolver: Resolver): KSCallable {
      val clazz = resolver.requireClassDeclaration(containingClass, node = null)
      return if (isFunction) {
        val declaration =
          clazz.getAllFunctions().find { it.qualifiedName?.asString() == declarationName }
            ?: error("Could not materialize function $declarationName in class $declarationName")
        Function(declaration)
      } else {
        val declaration =
          clazz.getAllProperties().find { it.qualifiedName?.asString() == declarationName }
            ?: error("Could not materialize property $declarationName in class $declarationName")
        Property(declaration)
      }
    }
  }

  val originalDeclaration = declaration

  fun toCacheEntry(): CacheEntry {
    return CacheEntry(
      containingClass = (originalDeclaration.parentDeclaration as KSClassDeclaration).fqName,
      declarationName = originalDeclaration.qualifiedName!!.asString(),
      isFunction = this is Function,
    )
  }

  data class Function(val declaration: KSFunctionDeclaration) : KSCallable(declaration)

  data class Property(val declaration: KSPropertyDeclaration) : KSCallable(declaration)
}

internal val KSCallable.type: KSType?
  get() = when (this) {
    is KSCallable.Function -> declaration.returnType?.resolve()
    is KSCallable.Property -> declaration.type.resolve()
  }

internal val KSCallable.isAbstract: Boolean
  get() = when (this) {
    is KSCallable.Function -> declaration.isAbstract
    is KSCallable.Property -> declaration.isAbstract()
  }

internal fun KSPropertyDeclaration.asKSCallable(): KSCallable.Property = KSCallable.Property(this)
internal fun KSFunctionDeclaration.asKSCallable(): KSCallable.Function = KSCallable.Function(this)

internal fun KSClassDeclaration.getAllCallables(): Sequence<KSCallable> {
  val functions = getAllFunctions().map { it.asKSCallable() }
  val properties = getAllProperties().map { it.asKSCallable() }
  return functions + properties
}
