package com.squareup.anvil.compiler.codegen.dagger

import com.squareup.anvil.compiler.api.AnvilCompilationException
import com.squareup.anvil.compiler.assistedFqName
import com.squareup.anvil.compiler.assistedInjectFqName
import com.squareup.anvil.compiler.daggerDoubleCheckFqNameString
import com.squareup.anvil.compiler.daggerLazyFqName
import com.squareup.anvil.compiler.injectFqName
import com.squareup.anvil.compiler.internal.argumentType
import com.squareup.anvil.compiler.internal.asClassName
import com.squareup.anvil.compiler.internal.asTypeName
import com.squareup.anvil.compiler.internal.capitalize
import com.squareup.anvil.compiler.internal.classDescriptorOrNull
import com.squareup.anvil.compiler.internal.findAnnotation
import com.squareup.anvil.compiler.internal.fqNameOrNull
import com.squareup.anvil.compiler.internal.hasAnnotation
import com.squareup.anvil.compiler.internal.isNullable
import com.squareup.anvil.compiler.internal.reference.ClassReference
import com.squareup.anvil.compiler.internal.reference.ClassReference.Descriptor
import com.squareup.anvil.compiler.internal.reference.ClassReference.Psi
import com.squareup.anvil.compiler.internal.reference.allSuperTypeClassReferences
import com.squareup.anvil.compiler.internal.reference.generateClassName
import com.squareup.anvil.compiler.internal.reference.toAnnotationReference
import com.squareup.anvil.compiler.internal.reference.toClassReference
import com.squareup.anvil.compiler.internal.requireFqName
import com.squareup.anvil.compiler.internal.requireTypeName
import com.squareup.anvil.compiler.internal.requireTypeReference
import com.squareup.anvil.compiler.internal.withJvmSuppressWildcardsIfNeeded
import com.squareup.anvil.compiler.providerFqName
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeVariableName
import com.squareup.kotlinpoet.asClassName
import dagger.Lazy
import dagger.internal.ProviderOfLazy
import org.jetbrains.kotlin.descriptors.CallableMemberDescriptor
import org.jetbrains.kotlin.descriptors.CallableMemberDescriptor.Kind
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.descriptors.PropertyDescriptor
import org.jetbrains.kotlin.descriptors.annotations.AnnotationUseSiteTarget
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtCallableDeclaration
import org.jetbrains.kotlin.psi.KtClassBody
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
import org.jetbrains.kotlin.psi.psiUtil.visibilityModifierTypeOrDefault
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameOrNull
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import org.jetbrains.kotlin.resolve.scopes.DescriptorKindFilter
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.isNullable
import org.jetbrains.kotlin.utils.addToStdlib.safeAs
import javax.inject.Provider

internal fun TypeName.wrapInProvider(): ParameterizedTypeName {
  return Provider::class.asClassName().parameterizedBy(this)
}

internal fun TypeName.wrapInLazy(): ParameterizedTypeName {
  return Lazy::class.asClassName().parameterizedBy(this)
}

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

internal fun KtCallableDeclaration.toConstructorParameter(
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

// TODO
//  Include methods: https://github.com/square/anvil/issues/339

// TODO: Use ClassReference
internal fun KtClassOrObject.injectedMembers(module: ModuleDescriptor) = children
  .asSequence()
  .filterIsInstance<KtClassBody>()
  .flatMap { it.properties }
  .filterNot { it.visibilityModifierTypeOrDefault() == KtTokens.PRIVATE_KEYWORD }
  .filter {
    it.hasAnnotation(injectFqName, module) ||
      it.isSetterInjected(module)
  }
  .toList()

private fun KtProperty.isSetterInjected(module: ModuleDescriptor): Boolean {
  return setter?.hasAnnotation(injectFqName, module) == true || findAnnotation(injectFqName, module)
    ?.useSiteTarget?.getAnnotationUseSiteTarget() == AnnotationUseSiteTarget.PROPERTY_SETTER
}

internal fun FunSpec.Builder.addMemberInjection(
  memberInjectParameters: List<MemberInjectParameter>,
  instanceName: String
): FunSpec.Builder = apply {

  memberInjectParameters.forEach { parameter ->

    val functionName = "inject${parameter.accessName.capitalize()}"

    val param = when {
      parameter.isLazyWrappedInProvider ->
        "${ProviderOfLazy::class.qualifiedName}.create(${parameter.name})"
      parameter.isWrappedInProvider -> parameter.name
      parameter.isWrappedInLazy ->
        "$daggerDoubleCheckFqNameString.lazy(${parameter.name})"
      else -> parameter.name + ".get()"
    }

    addStatement("%T.$functionName($instanceName, $param)", parameter.memberInjectorClassName)
  }
}

/**
 * Returns all member-injected parameters for the receiver class *and any superclasses*.
 *
 * We use Psi whenever possible, to support generated code.
 *
 * Order is important. Dagger expects the properties of the most-upstream class to be listed first
 * in a factory's constructor.
 *
 * Given the hierarchy:
 * Impl -> Middle -> Base
 * The order of dependencies in `Impl_Factory`'s constructor should be:
 * Base -> Middle -> Impl
 *
 * @param inheritedOnly If true, only returns the injected properties declared in superclasses.
 *   If false, the injected properties of the receiver class will be included.
 */
internal fun ClassReference.memberInjectParameters(
  inheritedOnly: Boolean = false
): List<MemberInjectParameter> {
  return allSuperTypeClassReferences(includeSelf = !inheritedOnly)
    .filterNot { it.isInterface() }
    .toList()
    .foldRight(listOf()) { classReference, acc ->
      acc + classReference.declaredMemberInjectParameters(acc)
    }
}

/**
 * @param superParameters injected parameters from any super-classes, regardless of whether they're
 * overridden by the receiver class
 * @return the member-injected parameters for this class only, not including any super-classes
 */
private fun ClassReference.declaredMemberInjectParameters(
  superParameters: List<Parameter>
): List<MemberInjectParameter> {
  return when (this) {
    is Descriptor -> clazz.memberInjectedPropertyDescriptors()
      .mapToMemberInjectParameters(module, clazz.asClassName(), superParameters)
    is Psi -> clazz.injectedMembers(module)
      .mapToMemberInjectParameters(module, superParameters)
  }
}

/**
 * Converts the parameter list to comma separated argument list that can be used to call other
 * functions, e.g.
 * ```
 * [param0: String, param1: Int] -> "param0, param1"
 * ```
 * [asProvider] allows you to decide if each parameter is wrapped in a `Provider` interface. If
 * true, then the `get()` function will be called for the provider parameter. If false, then
 * then always only the parameter name will used in the argument list:
 * ```
 * "param0.get()" vs "param0"
 * ```
 * Set [includeModule] to true if a Dagger module instance is part of the argument list.
 */
internal fun List<Parameter>.asArgumentList(
  asProvider: Boolean,
  includeModule: Boolean
): String {
  return this
    .let { list ->
      if (asProvider) {
        list.map { parameter ->
          when {
            parameter.isLazyWrappedInProvider ->
              "${ProviderOfLazy::class.qualifiedName}.create(${parameter.name})"
            parameter.isWrappedInProvider -> parameter.name
            // Normally Dagger changes Lazy<Type> parameters to a Provider<Type> (usually the
            // container is a joined type), therefore we use `.lazy(..)` to convert the Provider
            // to a Lazy. Assisted parameters behave differently and the Lazy type is not changed
            // to a Provider and we can simply use the parameter name in the argument list.
            parameter.isWrappedInLazy && parameter.isAssisted -> parameter.name
            parameter.isWrappedInLazy -> "$daggerDoubleCheckFqNameString.lazy(${parameter.name})"
            parameter.isAssisted -> parameter.name
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

private fun ClassDescriptor.memberInjectedPropertyDescriptors(): List<PropertyDescriptor> {
  // TODO: clean up
  return unsubstitutedMemberScope
    .getContributedDescriptors(DescriptorKindFilter.VARIABLES)
    .filterIsInstance<PropertyDescriptor>()
    // only return properties which are directly declared (including overrides)
    .filter { it.kind == Kind.DECLARATION }
    .filter { it.hasAnnotation(injectFqName) }
}

// TODO: use PropertyReference
internal fun PropertyDescriptor.hasAnnotation(annotationFqName: FqName): Boolean {
  // `@Inject lateinit var` is really `@field:Inject lateinit var`, which needs `backingField`
  return backingField?.annotations?.hasAnnotation(annotationFqName) == true ||
    setter?.annotations?.hasAnnotation(annotationFqName) == true ||
    annotations.hasAnnotation(annotationFqName)
}

// TODO: Move to PropertyReference
internal fun PropertyDescriptor.isSetterInjected(): Boolean {
  return setter?.annotations?.hasAnnotation(injectFqName) == true
}

// TODO: Return ClassReference
internal fun KotlinType.fqNameOrNull(): FqName? = classDescriptorOrNull()?.fqNameOrNull()

private fun List<CallableMemberDescriptor>.mapToMemberInjectParameters(
  module: ModuleDescriptor,
  containingClassName: ClassName,
  superParameters: List<Parameter>
): List<MemberInjectParameter> {

  return fold(listOf()) { acc, descriptor ->

    val originalName = descriptor.name.asString()

    val uniqueName = originalName.uniqueParameterName(superParameters, acc)

    val type = descriptor.safeAs<PropertyDescriptor>()?.type
      ?: descriptor.valueParameters.first().type

    val typeFqName = type.fqNameOrNull()

    val isWrappedInProvider = typeFqName == providerFqName
    val isWrappedInLazy = typeFqName == daggerLazyFqName

    var isLazyWrappedInProvider = false

    val typeName = when {
      type.isNullable() -> type.asTypeName()
        .copy(nullable = true)

      isWrappedInLazy || isWrappedInProvider -> {
        val singleTypeParamType = type.arguments
          .singleOrNull()
          ?.type

        if (isWrappedInProvider && singleTypeParamType?.fqNameOrNull() == daggerLazyFqName) {

          // This is a super rare case when someone injects Provider<Lazy<Type>> that requires
          // special care.
          isLazyWrappedInProvider = true
          singleTypeParamType.asTypeName()
        } else {
          type.argumentType().asTypeName()
        }
      }

      else -> type.asTypeName()
    }.withJvmSuppressWildcardsIfNeeded(descriptor)

    val assistedAnnotation = descriptor.annotations
      .findAnnotation(assistedFqName)

    val assistedIdentifier = assistedAnnotation
      ?.allValueArguments
      ?.values
      ?.firstOrNull()
      ?.value as? String
      ?: ""

    val memberInjectorClassName = containingClassName.simpleNames
      .joinToString("_") + "_MembersInjector"

    val memberInjectorClass = ClassName(
      containingClassName.packageName, memberInjectorClassName
    )

    val isSetterInjected = descriptor.safeAs<PropertyDescriptor>()
      ?.isSetterInjected() == true

    // setter delegates require a "set" prefix for their inject function
    val accessName = if (isSetterInjected) {
      "set${originalName.capitalize()}"
    } else {
      originalName
    }

    val qualifierAnnotations = descriptor.annotations
      .map { it.toAnnotationReference(declaringClass = null, module) }
      .filter { it.isQualifier() }
      .map { it.toAnnotationSpec() }

    acc + MemberInjectParameter(
      name = uniqueName,
      originalName = originalName,
      typeName = typeName,
      providerTypeName = typeName.wrapInProvider(),
      lazyTypeName = typeName.wrapInLazy(),
      isWrappedInProvider = isWrappedInProvider,
      isWrappedInLazy = isWrappedInLazy,
      isLazyWrappedInProvider = isLazyWrappedInProvider,
      isAssisted = assistedAnnotation != null,
      assistedIdentifier = assistedIdentifier,
      memberInjectorClassName = memberInjectorClass,
      isSetterInjected = isSetterInjected,
      accessName = accessName,
      qualifierAnnotationSpecs = qualifierAnnotations,
      injectedFieldSignature = descriptor.fqNameSafe
    )
  }
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

  // TODO: remove deprecated function call
  val memberInjectorClassName =
    "${containingClass.toClassReference(module).generateClassName().relativeClassName}" +
      "_MembersInjector"
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
