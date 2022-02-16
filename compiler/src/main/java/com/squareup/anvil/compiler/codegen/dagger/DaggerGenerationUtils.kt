package com.squareup.anvil.compiler.codegen.dagger

import com.squareup.anvil.compiler.api.AnvilCompilationException
import com.squareup.anvil.compiler.assistedFqName
import com.squareup.anvil.compiler.assistedInjectFqName
import com.squareup.anvil.compiler.codegen.ConstructorParameter
import com.squareup.anvil.compiler.codegen.MemberInjectParameter
import com.squareup.anvil.compiler.codegen.Parameter
import com.squareup.anvil.compiler.codegen.uniqueParameterName
import com.squareup.anvil.compiler.codegen.wrapInLazy
import com.squareup.anvil.compiler.codegen.wrapInProvider
import com.squareup.anvil.compiler.daggerLazyFqName
import com.squareup.anvil.compiler.injectFqName
import com.squareup.anvil.compiler.internal.findAnnotation
import com.squareup.anvil.compiler.internal.fqNameOrNull
import com.squareup.anvil.compiler.internal.generateClassName
import com.squareup.anvil.compiler.internal.hasAnnotation
import com.squareup.anvil.compiler.internal.isNullable
import com.squareup.anvil.compiler.internal.reference.toAnnotationReference
import com.squareup.anvil.compiler.internal.requireFqName
import com.squareup.anvil.compiler.internal.requireTypeName
import com.squareup.anvil.compiler.internal.requireTypeReference
import com.squareup.anvil.compiler.internal.withJvmSuppressWildcardsIfNeeded
import com.squareup.anvil.compiler.providerFqName
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.TypeVariableName
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtConstructor
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.KtTypeArgumentList
import org.jetbrains.kotlin.psi.KtTypeElement
import org.jetbrains.kotlin.psi.KtTypeProjection
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.psi.KtValueArgument
import org.jetbrains.kotlin.psi.allConstructors
import org.jetbrains.kotlin.psi.psiUtil.containingClass

/**
 * Returns the with [injectAnnotationFqName] annotated constructor for this class.
 * [injectAnnotationFqName] must be either `@Inject` or `@AssistedInject`. If the class contains
 * multiple constructors annotated with either of these annotations, then this method throws
 * an error as multiple injected constructors aren't allowed.
 */
internal fun KtClassOrObject.injectConstructor(
  injectAnnotationFqName: FqName,
  module: ModuleDescriptor
): KtConstructor<*>? {
  if (injectAnnotationFqName != injectFqName && injectAnnotationFqName != assistedInjectFqName) {
    throw IllegalArgumentException(
      "injectAnnotationFqName must be either $injectFqName or $assistedInjectFqName. " +
        "It was $injectAnnotationFqName."
    )
  }

  val constructors = allConstructors.filter {
    it.hasAnnotation(injectFqName, module) || it.hasAnnotation(assistedInjectFqName, module)
  }

  return when (constructors.size) {
    0 -> null
    1 -> if (constructors[0].hasAnnotation(injectAnnotationFqName, module)) {
      constructors[0]
    } else {
      null
    }
    else -> throw AnvilCompilationException(
      "Types may only contain one injected constructor.",
      element = this
    )
  }
}

internal fun KtClassOrObject.typeVariableNames(
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

internal fun List<KtCallableDeclaration>.mapToConstructorParameters(
  module: ModuleDescriptor
): List<ConstructorParameter> =
  fold(listOf()) { acc, ktCallableDeclaration ->

    val uniqueName = ktCallableDeclaration.nameAsSafeName.asString().uniqueParameterName(acc)

    acc + ktCallableDeclaration.toConstructorParameter(module = module, uniqueName = uniqueName)
  }

private fun KtCallableDeclaration.toConstructorParameter(
  module: ModuleDescriptor,
  uniqueName: String
): ConstructorParameter {

  val typeElement = typeReference?.typeElement
  val typeFqName = typeElement?.fqNameOrNull(module)

  val isWrappedInProvider = typeFqName == providerFqName
  val isWrappedInLazy = typeFqName == daggerLazyFqName

  var isLazyWrappedInProvider = false

  val typeName = when {
    requireTypeReference(module).isNullable() ->
      requireTypeReference(module).requireTypeName(module).copy(nullable = true)

    isWrappedInProvider || isWrappedInLazy -> {
      val typeParameterReference = typeElement!!.singleTypeArgument()

      if (isWrappedInProvider &&
        typeParameterReference.fqNameOrNull(module) == daggerLazyFqName
      ) {
        // This is a super rare case when someone injects Provider<Lazy<Type>> that requires
        // special care.
        isLazyWrappedInProvider = true
        typeParameterReference.typeElement!!.singleTypeArgument().requireTypeName(module)
      } else {
        typeParameterReference.requireTypeName(module)
      }
    }

    else -> requireTypeReference(module).requireTypeName(module)
  }.withJvmSuppressWildcardsIfNeeded(this, module)

  val assistedAnnotation = findAnnotation(assistedFqName, module)
  val assistedIdentifier =
    (assistedAnnotation?.valueArguments?.firstOrNull() as? KtValueArgument)
      ?.children
      ?.filterIsInstance<KtStringTemplateExpression>()
      ?.single()
      ?.children
      ?.first()
      ?.text
      ?: ""

  return ConstructorParameter(
    name = uniqueName,
    originalName = nameAsSafeName.asString(),
    typeName = typeName,
    providerTypeName = typeName.wrapInProvider(),
    lazyTypeName = typeName.wrapInLazy(),
    isWrappedInProvider = isWrappedInProvider,
    isWrappedInLazy = isWrappedInLazy,
    isLazyWrappedInProvider = isLazyWrappedInProvider,
    isAssisted = assistedAnnotation != null,
    assistedIdentifier = assistedIdentifier
  )
}

internal fun List<KtProperty>.mapToMemberInjectParameters(
  module: ModuleDescriptor,
  superParameters: List<Parameter>
): List<MemberInjectParameter> = fold(listOf()) { acc, ktProperty ->

  val uniqueName = ktProperty.nameAsSafeName.asString()
    .uniqueParameterName(superParameters, acc)

  acc + ktProperty.toMemberInjectParameter(module = module, uniqueName = uniqueName)
}

private fun KtProperty.toMemberInjectParameter(
  module: ModuleDescriptor,
  uniqueName: String
): MemberInjectParameter {

  val constructorParameter = toConstructorParameter(module, uniqueName)

  val containingClass = containingClass()!!
  val packageName = containingClass.containingKtFile.packageFqName.asString()

  val memberInjectorClassName = "${containingClass.generateClassName()}_MembersInjector"
  val memberInjectorClass = ClassName(packageName, memberInjectorClassName)

  val qualifierAnnotations = annotationEntries
    .map { it.toAnnotationReference(declaringClass = null, module = module) }
    .filter { it.isQualifier() }
    .map { it.toAnnotationSpec() }

  val isSetterInjected = isSetterInjected(module)

  val originalName = constructorParameter.originalName

  // setter delegates require a "set" prefix for their inject function
  val accessName = if (isSetterInjected) {
    "set${originalName.capitalize()}"
  } else {
    originalName
  }
  return MemberInjectParameter(
    name = constructorParameter.name,
    typeName = constructorParameter.typeName,
    providerTypeName = constructorParameter.providerTypeName,
    lazyTypeName = constructorParameter.lazyTypeName,
    isWrappedInProvider = constructorParameter.isWrappedInProvider,
    isWrappedInLazy = constructorParameter.isWrappedInLazy,
    isLazyWrappedInProvider = constructorParameter.isLazyWrappedInProvider,
    isAssisted = constructorParameter.isAssisted,
    assistedIdentifier = constructorParameter.assistedIdentifier,
    memberInjectorClassName = memberInjectorClass,
    originalName = originalName,
    isSetterInjected = isSetterInjected,
    accessName = accessName,
    qualifierAnnotationSpecs = qualifierAnnotations,
    injectedFieldSignature = requireFqName()
  )
}

private fun KtTypeElement.singleTypeArgument(): KtTypeReference {
  return children
    .filterIsInstance<KtTypeArgumentList>()
    .single()
    .children
    .filterIsInstance<KtTypeProjection>()
    .single()
    .children
    .filterIsInstance<KtTypeReference>()
    .single()
}
