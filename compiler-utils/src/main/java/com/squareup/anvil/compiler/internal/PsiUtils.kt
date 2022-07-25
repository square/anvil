@file:Suppress("unused")

package com.squareup.anvil.compiler.internal

import com.squareup.anvil.annotations.ExperimentalAnvilApi
import com.squareup.anvil.compiler.api.AnvilCompilationException
import com.squareup.anvil.compiler.internal.reference.ClassReference
import com.squareup.anvil.compiler.internal.reference.allSuperTypeClassReferences
import com.squareup.anvil.compiler.internal.reference.canResolveFqName
import com.squareup.anvil.compiler.internal.reference.toClassReferenceOrNull
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtClassLiteralExpression
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtConstructorCalleeExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtFunctionType
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtNullableType
import org.jetbrains.kotlin.psi.KtPureElement
import org.jetbrains.kotlin.psi.KtSuperTypeListEntry
import org.jetbrains.kotlin.psi.KtTypeArgumentList
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.psi.KtUserType
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import org.jetbrains.kotlin.psi.psiUtil.parents
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf
import org.jetbrains.kotlin.resolve.ImportPath

private val kotlinAnnotations = listOf(jvmSuppressWildcardsFqName, publishedApiFqName)

@ExperimentalAnvilApi
public fun KtNamedDeclaration.requireFqName(): FqName = requireNotNull(fqName) {
  "fqName was null for $this, $nameAsSafeName"
}

@ExperimentalAnvilApi
public fun PsiElement.ktFile(): KtFile {
  return if (this is KtPureElement) {
    containingKtFile
  } else {
    parentsWithSelf
      .filterIsInstance<KtPureElement>()
      .first()
      .containingKtFile
  }
}

@ExperimentalAnvilApi
public fun PsiElement.fqNameOrNull(
  module: ModuleDescriptor
): FqName? {
  // Usually it's the opposite way, the require*() method calls the nullable method. But in this
  // case we'd like to preserve the better error messages in case something goes wrong.
  return try {
    requireFqName(module)
  } catch (e: AnvilCompilationException) {
    null
  }
}

@ExperimentalAnvilApi
public fun PsiElement.requireFqName(
  module: ModuleDescriptor
): FqName {
  val containingKtFile = ktFile()

  fun failTypeHandling(): Nothing = throw AnvilCompilationException(
    "Don't know how to handle Psi element: $text",
    element = this
  )

  val classReference = when (this) {
    // If a fully qualified name is used, then we're done and don't need to do anything further.
    // An inner class reference like Abc.Inner is also considered a KtDotQualifiedExpression in
    // some cases.
    is KtDotQualifiedExpression -> {
      FqName(text).takeIf { it.canResolveFqName(module) }
        ?.let { return it }
        ?: text
    }
    is KtNameReferenceExpression -> getReferencedName()
    is KtUserType -> {
      val isGenericType = children.any { it is KtTypeArgumentList }
      if (isGenericType) {
        // For an expression like Lazy<Abc> the qualifier will be null. If the qualifier exists,
        // then it may refer to the package and the referencedName refers to the class name, e.g.
        // a KtUserType "abc.def.GenericType<String>" has three children: a qualifier "abc.def",
        // the referencedName "GenericType" and the KtTypeArgumentList.
        val qualifierText = qualifier?.text
        val className = referencedName

        if (qualifierText != null) {

          // The generic might be fully qualified. Try to resolve it and return early.
          FqName("$qualifierText.$className")
            .takeIf { it.canResolveFqName(module) }
            ?.let { return it }

          // If the name isn't fully qualified, then it's something like "Outer.Inner".
          // We can't use `text` here because that includes the type parameter(s).
          "$qualifierText.$className"
        } else {
          className ?: failTypeHandling()
        }
      } else {
        val text = text

        // Sometimes a KtUserType is a fully qualified name. Give it a try and return early.
        if (text.contains(".") && text[0].isLowerCase()) {
          FqName(text).takeIf { it.canResolveFqName(module) }
            ?.let { return it }
        }

        // We can't use referencedName here. For inner classes like "Outer.Inner" it would only
        // return "Inner", whereas text returns "Outer.Inner", what we expect.
        text
      }
    }
    is KtTypeReference -> {
      val children = children
      if (children.size == 1) {
        try {
          // Could be a KtNullableType or KtUserType.
          return children[0].requireFqName(module)
        } catch (e: AnvilCompilationException) {
          // Fallback to the text representation.
          text
        }
      } else {
        text
      }
    }
    is KtNullableType -> return innerType?.requireFqName(module) ?: failTypeHandling()
    is KtAnnotationEntry -> return typeReference?.requireFqName(module) ?: failTypeHandling()
    is KtClassLiteralExpression -> {
      // Returns "Abc" for "Abc::class".
      val element = children.singleOrNull() ?: throw AnvilCompilationException(
        "Expected a single child, but there were ${children.size} instead: $text",
        element = this
      )
      return element.requireFqName(module)
    }
    is KtSuperTypeListEntry -> return typeReference?.requireFqName(module) ?: failTypeHandling()
    is KtFunctionType -> {
      // KtFunctionType is a lambda. The compiler will translate lambdas to one of the function
      // interfaces. More details are available here:
      // https://github.com/JetBrains/kotlin/blob/master/spec-docs/function-types.md
      val parameterCount = parameters.size
      if (parameterCount !in 0..22) {
        throw AnvilCompilationException(
          element = this,
          message = "Couldn't find function type for $parameterCount parameters."
        )
      }
      return FqName("kotlin.jvm.functions.Function$parameterCount")
    }
    else -> failTypeHandling()
  }

  // E.g. OuterClass.InnerClass
  val classReferenceOuter = classReference.substringBefore(".")

  val importPaths = containingKtFile.importDirectives.mapNotNull { it.importPath }

  // First look in the imports for the reference name. If the class is imported, then we know the
  // fully qualified name.
  importPaths
    .filter { it.alias == null && it.fqName.shortName().asString() == classReference }
    .also { matchingImportPaths ->
      when {
        matchingImportPaths.size == 1 ->
          matchingImportPaths[0].fqName
            .takeIf { it.canResolveFqName(module) }
            ?.let { return it }
        matchingImportPaths.size > 1 ->
          return matchingImportPaths.first { importPath ->
            importPath.fqName.canResolveFqName(module)
          }.fqName
      }
    }

  importPaths
    .filter { it.alias == null && it.fqName.shortName().asString() == classReferenceOuter }
    .also { matchingImportPaths ->
      when {
        matchingImportPaths.size == 1 ->
          FqName("${matchingImportPaths[0].fqName.parent()}.$classReference")
            .takeIf { it.canResolveFqName(module) }
            ?.let { return it }
        matchingImportPaths.size > 1 ->
          return matchingImportPaths.first { importPath ->
            // Note that we must use the parent of the import FqName. An import is `com.abc.A` and
            // the classReference `A.Inner`, so we must try to resolve `com.abc.A.Inner` and not
            // `com.abc.A.A.Inner`.
            importPath.fqName.parent()
              .descendant(classReference)
              .canResolveFqName(module)
          }.fqName
      }
    }

  // If there's an import alias, then we know the FqName.
  importPaths
    .singleOrNull { it.alias?.asString() == classReference }
    ?.takeIf { it.fqName.canResolveFqName(module) }
    ?.let { return it.fqName }

  containingKtFile.importDirectives
    .asSequence()
    .filter { it.isAllUnder }
    .mapNotNull {
      // This fqName is everything in front of the star, e.g. for "import java.io.*" it
      // returns "java.io".
      it.importPath?.fqName
    }
    .forEach { importFqName ->
      if (importFqName.asString() == "java.util") {
        // If there's a star import for java.util.* and the import is a Collection type, then
        // the Kotlin compiler overrides these with Kotlin types.
        FqName("kotlin.collections.$classReference")
          .takeIf { it.canResolveFqName(module) }
          ?.let { return it }
      }

      importFqName.descendant(classReference)
        .takeIf { it.canResolveFqName(module) }
        ?.let { return it }
    }

  // If there is no import, then try to resolve the class with the same package as this file.
  containingKtFile.packageFqName.descendant(classReference)
    .takeIf { it.canResolveFqName(module) }
    ?.let { return it }

  // If the referenced type is declared within the same scope, it doesn't need to be imported.
  parents
    .filterIsInstance<KtClassOrObject>()
    .flatMap { it.collectDescendantsOfType<KtClassOrObject>() }
    .firstOrNull { it.nameAsSafeName.asString() == classReference }
    ?.let { return it.requireFqName() }

  // If this doesn't work, then maybe a class from the Kotlin package is used.
  FqName("kotlin.$classReference")
    .takeIf { it.canResolveFqName(module) }
    ?.let { return it }

  // If this doesn't work, then maybe a class from the Kotlin collection package is used.
  FqName("kotlin.collections.$classReference")
    .takeIf { it.canResolveFqName(module) }
    ?.let { return it }

  // If this doesn't work, then maybe a class from the Kotlin annotation package is used.
  FqName("kotlin.annotation.$classReference")
    .takeIf { it.canResolveFqName(module) }
    ?.let { return it }

  // If this doesn't work, then maybe a class from the Kotlin jvm package is used.
  FqName("kotlin.jvm.$classReference")
    .takeIf { it.canResolveFqName(module) }
    ?.let { return it }

  // Or java.lang.
  FqName("java.lang.$classReference")
    .takeIf { it.canResolveFqName(module) }
    ?.let { return it }

  findClassReferenceInSuperTypes(classReference, importPaths, module)
    ?.let { return it.fqName }

  // Check if it's an inner class in the hierarchy.
  parents.filterIsInstance<KtClassOrObject>()
    .map { FqName("${it.requireFqName()}.$classReference") }
    .firstOrNull { it.canResolveFqName(module) }
    ?.let { return it }

  // Check if it's a named import.
  containingKtFile.importDirectives
    .firstOrNull { classReference == it.importPath?.importedName?.asString() }
    ?.importedFqName
    ?.let { return it }

  // Everything else isn't supported.
  throw AnvilCompilationException(
    "Couldn't resolve FqName $classReference for Psi element: $text",
    element = this
  )
}

private fun PsiElement.findClassReferenceInSuperTypes(
  className: String,
  importPaths: List<ImportPath>,
  module: ModuleDescriptor,
): ClassReference? {
  return parents
    // Get all outer classes, because the type could be from any inner class of them. These
    // inner classes can be referenced without an import, e.g.
    //
    //   class ClassA : SomeType {
    //     class ClassB : InnerInterfaceOfSomeType
    //   }
    .filterIsInstance<KtClassOrObject>()
    .flatMap { ktClass ->
      ktClass.superTypeListEntries
        .map { superType ->
          val superTypeName = (
            // If it's a super constructor call, then we need to strip the argument list, e.g.
            // ClassA() -> ClassA
            //
            // Otherwise it's an interface and we don't need to do anything.
            superType.children
              .filterIsInstance<KtConstructorCalleeExpression>()
              .singleOrNull()?.typeReference?.text
              ?: superType.typeReference?.text
            // It might be a generic type, e.g. ClassA<Abc>. Remove the generic type.
            )?.substringBefore('<')
            ?: throw AnvilCompilationException(
              "Couldn't get the super type for ${superType.text}."
            )

          // Find the FqName for our super type. We check below if it's actually correct.
          importPaths
            .singleOrNull {
              it.alias?.asString() == superTypeName || it.fqName.asString().endsWith(superTypeName)
            }
            ?.fqName
            // No import? Must be in the same package.
            ?: superType.containingKtFile.packageFqName.descendant(superTypeName)
        }
    }
    .mapNotNull { it.toClassReferenceOrNull(module) }
    // Now go through all other super types and check if they contain the inner class.
    .flatMap { it.allSuperTypeClassReferences(includeSelf = true) }
    .firstNotNullOfOrNull { clazz ->
      clazz.fqName.descendant(className).toClassReferenceOrNull(module)
    }
}
