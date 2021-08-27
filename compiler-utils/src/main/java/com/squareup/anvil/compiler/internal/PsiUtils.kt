@file:Suppress("unused")

package com.squareup.anvil.compiler.internal

import com.squareup.anvil.annotations.ExperimentalAnvilApi
import com.squareup.anvil.compiler.api.AnvilCompilationException
import com.squareup.kotlinpoet.TypeVariableName
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.descriptors.resolveClassByFqName
import org.jetbrains.kotlin.incremental.KotlinLookupLocation
import org.jetbrains.kotlin.incremental.components.NoLookupLocation.FROM_BACKEND
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtAnnotated
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassBody
import org.jetbrains.kotlin.psi.KtClassLiteralExpression
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtFunctionType
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtNullableType
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPureElement
import org.jetbrains.kotlin.psi.KtSuperTypeListEntry
import org.jetbrains.kotlin.psi.KtTypeArgumentList
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.psi.KtUserType
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.KtValueArgumentName
import org.jetbrains.kotlin.psi.psiUtil.parents
import org.jetbrains.kotlin.psi.psiUtil.parentsWithSelf
import kotlin.reflect.KClass

private val kotlinAnnotations = listOf(jvmSuppressWildcardsFqName, publishedApiFqName)

/** Returns the computed [FqName] representation of this [KClass]. */
public val KClass<*>.fqName: FqName get() = FqName(java.canonicalName)

@ExperimentalAnvilApi
public fun KtNamedDeclaration.requireFqName(): FqName = requireNotNull(fqName) {
  "fqName was null for $this, $nameAsSafeName"
}

@ExperimentalAnvilApi
public fun KtAnnotated.isInterface(): Boolean = this is KtClass && this.isInterface()

@ExperimentalAnvilApi
public fun KtAnnotated.hasAnnotation(
  fqName: FqName,
  module: ModuleDescriptor
): Boolean {
  return findAnnotation(fqName, module) != null
}

@ExperimentalAnvilApi
public fun KtAnnotated.requireAnnotation(
  fqName: FqName,
  module: ModuleDescriptor
): KtAnnotationEntry {
  return findAnnotation(fqName, module) ?: throw AnvilCompilationException(
    element = this,
    message = "Couldn't find the annotation $fqName."
  )
}

@ExperimentalAnvilApi
public fun KtAnnotated.findAnnotation(
  fqName: FqName,
  module: ModuleDescriptor
): KtAnnotationEntry? {
  val annotationEntries = annotationEntries
  if (annotationEntries.isEmpty()) return null

  // Look first if it's a Kotlin annotation. These annotations are usually not imported and the
  // remaining checks would fail.
  if (fqName in kotlinAnnotations) {
    annotationEntries.firstOrNull { annotation ->
      val text = annotation.text
      text.startsWith("@${fqName.shortName()}") || text.startsWith("@$fqName")
    }?.let { return it }
  }

  // Check if the fully qualified name is used, e.g. `@dagger.Module`.
  val annotationEntry = annotationEntries.firstOrNull {
    it.text.startsWith("@${fqName.asString()}")
  }
  if (annotationEntry != null) return annotationEntry

  // Check if the simple name is used, e.g. `@Module`.
  val annotationEntryShort = annotationEntries
    .firstOrNull {
      it.shortName == fqName.shortName()
    }
    ?: return null

  val importPaths = containingKtFile.importDirectives.mapNotNull { it.importPath }

  // If the simple name is used, check that the annotation is imported.
  val hasImport = importPaths.any { it.fqName == fqName }
  if (hasImport) return annotationEntryShort

  // Look for star imports and make a guess.
  val hasStarImport = importPaths
    .filter { it.isAllUnder }
    .any {
      fqName.asString().startsWith(it.fqName.asString())
    }
  if (hasStarImport) return annotationEntryShort

  // At this point we know that the class is annotated with an annotation that has the same simple
  // name as fqName. We couldn't find any imports, so the annotation is likely part of the same
  // package or Kotlin namespace. Leverage our existing utility function and to find the FqName
  // and then compare the result.
  val fqNameOfShort = annotationEntryShort.fqNameOrNull(module)
  if (fqName == fqNameOfShort) {
    return annotationEntryShort
  }

  return null
}

@ExperimentalAnvilApi
public fun KtClassOrObject.scope(
  annotationFqName: FqName,
  module: ModuleDescriptor
): FqName {
  return requireAnnotation(annotationFqName, module)
    .findAnnotationArgument<KtClassLiteralExpression>(name = "scope", index = 0)
    .let { classLiteralExpression ->
      if (classLiteralExpression == null) {
        throw AnvilCompilationException(
          "Couldn't find scope for $annotationFqName.",
          element = this
        )
      }

      classLiteralExpression.requireFqName(module)
    }
}

/**
 * Finds the argument in the given annotation. [name] refers to the parameter name
 * in the annotation and [index] to the position of the argument, e.g. if you look for the scope in
 * `@ContributesBinding(Int::class, boundType = Unit::class)`, then [name] would be "scope" and the
 * index 0. If you look for the bound type, then [name] would be "boundType" and the index 1.
 */
@ExperimentalAnvilApi
public inline fun <reified T> KtAnnotationEntry.findAnnotationArgument(
  name: String,
  index: Int
): T? {
  val annotationValues = valueArguments
    .asSequence()
    .filterIsInstance<KtValueArgument>()

  // First check if the is any named parameter. Named parameters allow a different order of
  // arguments.
  annotationValues
    .firstNotNullOfOrNull { valueArgument ->
      val children = valueArgument.children
      if (children.size == 2 && children[0] is KtValueArgumentName &&
        (children[0] as KtValueArgumentName).asName.asString() == name &&
        children[1] is T
      ) {
        children[1] as T
      } else {
        null
      }
    }
    ?.let { return it }

  // If there is no named argument, then take the first argument, which must be a class literal
  // expression, e.g. @ContributesTo(Unit::class)
  return annotationValues
    .elementAtOrNull(index)
    ?.let { valueArgument ->
      valueArgument.children.firstOrNull() as? T
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
  val containingKtFile = parentsWithSelf
    .filterIsInstance<KtPureElement>()
    .first()
    .containingKtFile

  fun failTypeHandling(): Nothing = throw AnvilCompilationException(
    "Don't know how to handle Psi element: $text",
    element = this
  )

  val classReference = when (this) {
    // If a fully qualified name is used, then we're done and don't need to do anything further.
    // An inner class reference like Abc.Inner is also considered a KtDotQualifiedExpression in
    // some cases.
    is KtDotQualifiedExpression -> {
      module.resolveFqNameOrNull(FqName(text))
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
          module.resolveFqNameOrNull(FqName("$qualifierText.$className"))
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
          module.resolveFqNameOrNull(FqName(text))
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
          return matchingImportPaths[0].fqName
        matchingImportPaths.size > 1 ->
          return matchingImportPaths.first { importPath ->
            module.canResolveFqName(importPath.fqName)
          }.fqName
      }
    }

  importPaths
    .filter { it.alias == null && it.fqName.shortName().asString() == classReferenceOuter }
    .also { matchingImportPaths ->
      when {
        matchingImportPaths.size == 1 ->
          return FqName("${matchingImportPaths[0].fqName.parent()}.$classReference")
        matchingImportPaths.size > 1 ->
          return matchingImportPaths.first { importPath ->
            module.canResolveFqName(importPath.fqName, classReference)
          }.fqName
      }
    }

  containingKtFile.importDirectives
    .asSequence()
    .filter { it.isAllUnder }
    .mapNotNull {
      // This fqName is everything in front of the star, e.g. for "import java.io.*" it
      // returns "java.io".
      it.importPath?.fqName
    }
    .forEach { importFqName ->
      module.resolveFqNameOrNull(importFqName, classReference)
        ?.let { return it }
    }

  // If there is no import, then try to resolve the class with the same package as this file.
  module.resolveFqNameOrNull(containingKtFile.packageFqName, classReference)
    ?.let { return it }

  // If this doesn't work, then maybe a class from the Kotlin package is used.
  module.resolveFqNameOrNull(FqName("kotlin.$classReference"))
    ?.let { return it }

  // If this doesn't work, then maybe a class from the Kotlin collection package is used.
  module.resolveFqNameOrNull(FqName("kotlin.collections.$classReference"))
    ?.let { return it }

  // If this doesn't work, then maybe a class from the Kotlin jvm package is used.
  module.resolveFqNameOrNull(FqName("kotlin.jvm.$classReference"))
    ?.let { return it }

  // Or java.lang.
  module.resolveFqNameOrNull(FqName("java.lang.$classReference"))
    ?.let { return it }

  findFqNameInSuperTypes(module, classReference)
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

private fun PsiElement.findFqNameInSuperTypes(
  module: ModuleDescriptor,
  classReference: String
): FqName? {
  fun tryToResolveClassFqName(outerClass: FqName): FqName? =
    module.resolveFqNameOrNull(FqName("$outerClass.$classReference"))

  return parents.filterIsInstance<KtClassOrObject>()
    .flatMap { clazz ->
      tryToResolveClassFqName(clazz.requireFqName())?.let { return@flatMap sequenceOf(it) }

      // At this point we can't work with Psi APIs anymore. We need to resolve the super types
      // and try to find inner class in them.
      val descriptor = clazz.requireClassDescriptor(module)
      listOf(descriptor.defaultType).getAllSuperTypes()
        .mapNotNull { tryToResolveClassFqName(it) }
    }
    .firstOrNull()
}

@ExperimentalAnvilApi
public fun KtClassOrObject.typeVariableNames(
  module: ModuleDescriptor
): List<TypeVariableName> {
  // Any type which is constrained in a `where` clause is also defined as a type parameter.
  // It's also technically possible to have one constraint in the type parameter spot, like this:
  // class MyClass<T : Any> where T : Set<*>, T : MutableCollection<*>
  // Merge both groups of type parameters in order to get the full list of bounds.
  val boundsByVariableName = typeParameterList
    ?.parameters
    ?.filter { it.fqNameOrNull(module) == null }
    ?.associateTo(mutableMapOf()) { parameter ->
      val variableName = parameter.nameAsSafeName.asString()
      val extendsBound = parameter.extendsBound?.requireTypeName(module)

      variableName to mutableListOf(extendsBound)
    } ?: mutableMapOf()

  typeConstraintList
    ?.constraints
    ?.filter { it.fqNameOrNull(module) == null }
    ?.forEach { constraint ->
      val variableName = constraint.subjectTypeParameterName
        ?.getReferencedName()
        ?: return@forEach
      val extendsBound = constraint.boundTypeReference?.requireTypeName(module)

      boundsByVariableName
        .getValue(variableName)
        .add(extendsBound)
    }

  return boundsByVariableName
    .map { (variableName, bounds) ->
      TypeVariableName(variableName, bounds.filterNotNull())
    }
}

@ExperimentalAnvilApi
public fun KtClassOrObject.functions(
  includeCompanionObjects: Boolean
): List<KtNamedFunction> = classBodies(includeCompanionObjects).flatMap { it.functions }

@ExperimentalAnvilApi
public fun KtClassOrObject.properties(
  includeCompanionObjects: Boolean
): List<KtProperty> = classBodies(includeCompanionObjects).flatMap { it.properties }

private fun KtClassOrObject.classBodies(includeCompanionObjects: Boolean): List<KtClassBody> {
  val elements = children.toMutableList()
  if (includeCompanionObjects) {
    elements += companionObjects.flatMap { it.children.toList() }
  }
  return elements.filterIsInstance<KtClassBody>()
}

@ExperimentalAnvilApi
public fun KtTypeReference.isNullable(): Boolean = typeElement is KtNullableType

@ExperimentalAnvilApi
public fun KtTypeReference.isGenericType(): Boolean {
  val typeElement = typeElement ?: return false
  val children = typeElement.children

  if (children.size != 2) return false
  return children[1] is KtTypeArgumentList
}

@ExperimentalAnvilApi
public fun KtTypeReference.isFunctionType(): Boolean = typeElement is KtFunctionType

@ExperimentalAnvilApi
public fun KtClassOrObject.isGenericClass(): Boolean = typeParameterList != null

@ExperimentalAnvilApi
public fun KtCallableDeclaration.requireTypeReference(module: ModuleDescriptor): KtTypeReference {
  typeReference?.let { return it }

  if (this is KtFunction && hasAnnotation(daggerProvidesFqName, module)) {
    throw AnvilCompilationException(
      message = "Dagger provider methods must specify the return type explicitly when using " +
        "Anvil. The return type cannot be inferred implicitly.",
      element = this
    )
  }

  throw AnvilCompilationException(
    message = "Couldn't obtain type reference. Did you declare an explicit type for ${this.name}?",
    element = this
  )
}

@ExperimentalAnvilApi
public fun KtUserType.isTypeParameter(): Boolean {
  return parents.filterIsInstance<KtClassOrObject>().first().typeParameters.any {
    val typeParameter = it.text.split(":").first().trim()
    typeParameter == text
  }
}

@ExperimentalAnvilApi
public fun KtUserType.findExtendsBound(): List<FqName> {
  return parents.filterIsInstance<KtClassOrObject>()
    .first()
    .typeParameters
    .mapNotNull { it.fqName }
}

@ExperimentalAnvilApi
public fun KtClassOrObject.requireClassDescriptor(module: ModuleDescriptor): ClassDescriptor {
  return module.resolveClassByFqName(requireFqName(), KotlinLookupLocation(this))
    ?: throw AnvilCompilationException(
      "Couldn't resolve class for ${requireFqName()}.",
      element = this
    )
}

@ExperimentalAnvilApi
public fun FqName.requireClassDescriptor(module: ModuleDescriptor): ClassDescriptor {
  return module.resolveClassByFqName(this, FROM_BACKEND)
    ?: throw AnvilCompilationException("Couldn't resolve class for $this.")
}

@ExperimentalAnvilApi
public fun KtAnnotationEntry.isQualifier(module: ModuleDescriptor): Boolean {
  return typeReference
    ?.requireFqName(module)
    // Often entries are annotated with @Inject, in this case we know it's not a qualifier and we
    // can stop early.
    ?.takeIf { it != injectFqName }
    ?.requireClassDescriptor(module)
    ?.annotations
    ?.hasAnnotation(qualifierFqName)
    ?: false
}

@ExperimentalAnvilApi
public fun KtClassOrObject.generateClassName(
  separator: String = "_"
): String =
  parentsWithSelf
    .filterIsInstance<KtClassOrObject>()
    .toList()
    .reversed()
    .joinToString(separator = separator) {
      it.requireFqName()
        .shortName()
        .asString()
    }
