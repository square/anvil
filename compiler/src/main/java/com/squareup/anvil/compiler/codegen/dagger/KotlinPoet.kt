package com.squareup.anvil.compiler.codegen.dagger

import com.squareup.anvil.compiler.AnvilCompilationException
import com.squareup.anvil.compiler.AnvilComponentRegistrar
import com.squareup.anvil.compiler.codegen.fqNameOrNull
import com.squareup.anvil.compiler.codegen.hasAnnotation
import com.squareup.anvil.compiler.codegen.isFunctionType
import com.squareup.anvil.compiler.codegen.isGenericType
import com.squareup.anvil.compiler.codegen.isNullable
import com.squareup.anvil.compiler.codegen.requireFqName
import com.squareup.anvil.compiler.daggerDoubleCheckFqNameString
import com.squareup.anvil.compiler.daggerLazyFqName
import com.squareup.anvil.compiler.jvmSuppressWildcardsFqName
import com.squareup.anvil.compiler.providerFqName
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.TypeVariableName
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.jvm.jvmSuppressWildcards
import dagger.Lazy
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtTypeArgumentList
import org.jetbrains.kotlin.psi.KtTypeProjection
import org.jetbrains.kotlin.psi.KtTypeReference
import java.io.ByteArrayOutputStream
import javax.annotation.Generated
import javax.inject.Provider

internal fun KtClassOrObject.asClassName(): ClassName =
  ClassName.bestGuess(requireFqName().asString())

internal fun KtCallableDeclaration.requireTypeName(module: ModuleDescriptor): TypeName {
  fun fail(): Nothing = throw AnvilCompilationException(
      message = "Couldn't resolve type of function: ${requireFqName()}",
      element = this
  )

  val typeReference = typeReference ?: fail()

  fun typeVariableName(): TypeVariableName {
    return TypeVariableName(name = typeReference.typeElement?.text ?: fail())
  }

  if (typeReference.isGenericType()) return typeVariableName()

  val fqName = typeReference.fqNameOrNull(module) ?: return typeVariableName()
  return ClassName.bestGuess(fqName.asString())
}

internal data class Parameter(
  val name: String,
  val typeName: TypeName,
  val providerTypeName: ParameterizedTypeName,
  val lazyTypeName: ParameterizedTypeName,
  val isWrappedInProvider: Boolean,
  val isWrappedInLazy: Boolean
) {
  val originalTypeName: TypeName = when {
    isWrappedInProvider -> providerTypeName
    isWrappedInLazy -> lazyTypeName
    else -> typeName
  }
}

internal fun List<KtCallableDeclaration>.mapToParameter(module: ModuleDescriptor): List<Parameter> =
  mapIndexed { index, parameter ->
    val typeElement = parameter.typeReference?.typeElement
    val typeFqName = typeElement?.fqNameOrNull(module)

    val isWrappedInProvider = typeFqName == providerFqName
    val isWrappedInLazy = typeFqName == daggerLazyFqName

    val typeName = when {
      parameter.typeReference?.isNullable() ?: false -> parameter.requireTypeName(module)
      isWrappedInProvider || isWrappedInLazy -> {
        TypeVariableName(
            name = typeElement!!.children
                .filterIsInstance<KtTypeArgumentList>()
                .single()
                .children
                .filterIsInstance<KtTypeProjection>()
                .single()
                .children
                .filterIsInstance<KtTypeReference>()
                .single()
                .text
        )
      }
      else -> parameter.requireTypeName(module)
    }.withJvmSuppressWildcardsIfNeeded(parameter)

    Parameter(
        name = "param$index",
        typeName = typeName,
        providerTypeName = typeName.wrapInProvider(),
        lazyTypeName = typeName.wrapInLazy(),
        isWrappedInProvider = isWrappedInProvider,
        isWrappedInLazy = isWrappedInLazy
    )
  }

internal fun <T : KtCallableDeclaration> TypeName.withJvmSuppressWildcardsIfNeeded(
  callableDeclaration: T
): TypeName {
  // If the parameter is annotated with @JvmSuppressWildcards, then add the annotation
  // to our type so that this information is forwarded when our Factory is compiled.
  val hasJvmSuppressWildcards =
    callableDeclaration.typeReference?.hasAnnotation(jvmSuppressWildcardsFqName) ?: false

  // Add the @JvmSuppressWildcards annotation even for simple generic return types like
  // Set<String>. This avoids some edge cases where Dagger chokes.
  val isGenericType = callableDeclaration.typeReference?.isGenericType() ?: false

  // Same for functions.
  val isFunctionType = callableDeclaration.typeReference?.isFunctionType() ?: false

  return when {
    hasJvmSuppressWildcards || isGenericType -> this.jvmSuppressWildcards()
    isFunctionType -> this.jvmSuppressWildcardsKt31734()
    else -> this
  }
}

// TODO: remove with Kotlin 1.4.
// Notice the empty member. Instead of generating `@JvmSuppressWildcards Type` it generates
// `@JvmSuppressWildcards() Type`. This is necessary to avoid KT-31734 where the type is a function.
private fun TypeName.jvmSuppressWildcardsKt31734() =
  copy(annotations = this.annotations + AnnotationSpec.builder(JvmSuppressWildcards::class)
    .addMember("")
    .build())

internal fun List<Parameter>.asArgumentList(
  asProvider: Boolean,
  includeModule: Boolean
): String {
  return this
      .let { list ->
        if (asProvider) {
          list.map { parameter ->
            when {
              parameter.isWrappedInProvider -> parameter.name
              parameter.isWrappedInLazy ->
                "$daggerDoubleCheckFqNameString.lazy(${parameter.name})"
              else -> "${parameter.name}.get()"
            }
          }
        } else list.map { it.name }
      }
      .let {
        if (includeModule) {
          val result = it.toMutableList()
          result.add(0, "module")
          result.toList()
        } else {
          it
        }
      }
      .joinToString()
}

private fun TypeName.wrapInProvider(): ParameterizedTypeName {
  return Provider::class.asClassName().parameterizedBy(this)
}

private fun TypeName.wrapInLazy(): ParameterizedTypeName {
  return Lazy::class.asClassName().parameterizedBy(this)
}

internal fun TypeSpec.Builder.addAnvilAnnotation(): TypeSpec.Builder {
  return addAnnotation(
      AnnotationSpec.builder(Generated::class)
          .addMember("value = [%S]", AnvilComponentRegistrar::class.java.canonicalName)
          .addMember("comments = %S", "https://github.com/square/anvil")
          .build()
  )
}

internal fun FileSpec.writeToString(): String {
  val stream = ByteArrayOutputStream()
  stream.writer().use {
    writeTo(it)
  }
  return stream.toString()
}

/**
 * Removes invalid imports that can be caused by [requireTypeName] and copies imports from the
 * source file. Copying imports is necessary for star imports.
 */
internal fun String.replaceImports(clazz: KtClassOrObject): String {
  val originalLines = lines()
  val linesWithoutImports = originalLines
      .filterNot { it.startsWith("import ") }
      .toMutableList()

  val copiedImports = clazz.containingKtFile
      .importDirectives
      .map { it.text.trim() }

  val importDirectives = copiedImports
      .plus(
          originalLines
              .filter {
                it.startsWith("import ") && it.contains(".")
              }
              .map { it.trim() }
      )
      .distinct()
      .toMutableList()

  // Now that we copied the imports from the original file it could happen that we have ambiguous
  // imports. Remove the import that is used with a fully qualified name elsewhere in the code or
  // remove the copied import again if it's unused.
  importDirectives
      .filterNot { it.endsWith(".*") }
      .groupBy { it.substringAfterLast(".") }
      .filterValues { it.size > 1 }
      .values
      .forEach { ambiguousImports ->
        val usedImport = ambiguousImports.firstOrNull { ambiguousImport ->
          val fqName = ambiguousImport.substringAfter("import ")
          linesWithoutImports.any { it.contains(fqName) }
        }

        if (usedImport != null) {
          importDirectives.remove(usedImport)
        } else {
          // It's possible that the copied import was unused.
          ambiguousImports.forEach {
            if (it in copiedImports) {
              importDirectives.remove(it)
            }
          }
        }
      }

  linesWithoutImports.addAll(2, importDirectives.sorted())
  return linesWithoutImports.joinToString(separator = "\n")
}
