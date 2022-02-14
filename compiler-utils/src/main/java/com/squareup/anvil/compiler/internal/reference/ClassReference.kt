package com.squareup.anvil.compiler.internal.reference

import com.squareup.anvil.annotations.ExperimentalAnvilApi
import com.squareup.anvil.compiler.api.AnvilCompilationException
import com.squareup.anvil.compiler.internal.annotationOrNull
import com.squareup.anvil.compiler.internal.argumentType
import com.squareup.anvil.compiler.internal.asClassName
import com.squareup.anvil.compiler.internal.classDescriptor
import com.squareup.anvil.compiler.internal.contributesBindingFqName
import com.squareup.anvil.compiler.internal.contributesMultibindingFqName
import com.squareup.anvil.compiler.internal.contributesSubcomponentFqName
import com.squareup.anvil.compiler.internal.contributesToFqName
import com.squareup.anvil.compiler.internal.findAnnotation
import com.squareup.anvil.compiler.internal.findAnnotationArgument
import com.squareup.anvil.compiler.internal.functions
import com.squareup.anvil.compiler.internal.getAnnotationValue
import com.squareup.anvil.compiler.internal.isDaggerScope
import com.squareup.anvil.compiler.internal.isQualifier
import com.squareup.anvil.compiler.internal.reference.ClassReference.Descriptor
import com.squareup.anvil.compiler.internal.reference.ClassReference.Psi
import com.squareup.anvil.compiler.internal.reference.Visibility.INTERNAL
import com.squareup.anvil.compiler.internal.reference.Visibility.PRIVATE
import com.squareup.anvil.compiler.internal.reference.Visibility.PROTECTED
import com.squareup.anvil.compiler.internal.reference.Visibility.PUBLIC
import com.squareup.anvil.compiler.internal.requireFqName
import com.squareup.anvil.compiler.internal.scope
import com.squareup.anvil.compiler.internal.scopeOrNull
import com.squareup.anvil.compiler.internal.toAnnotationSpec
import com.squareup.anvil.compiler.internal.toFqNames
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.descriptors.Modality.ABSTRACT
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.lexer.KtTokens.ABSTRACT_KEYWORD
import org.jetbrains.kotlin.lexer.KtTokens.INTERNAL_KEYWORD
import org.jetbrains.kotlin.lexer.KtTokens.PRIVATE_KEYWORD
import org.jetbrains.kotlin.lexer.KtTokens.PROTECTED_KEYWORD
import org.jetbrains.kotlin.lexer.KtTokens.PUBLIC_KEYWORD
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtCollectionLiteralExpression
import org.jetbrains.kotlin.psi.psiUtil.parents
import org.jetbrains.kotlin.psi.psiUtil.visibilityModifierTypeOrDefault
import org.jetbrains.kotlin.resolve.constants.ArrayValue
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import org.jetbrains.kotlin.resolve.descriptorUtil.getSuperClassNotAny
import org.jetbrains.kotlin.resolve.descriptorUtil.getSuperInterfaces
import org.jetbrains.kotlin.resolve.descriptorUtil.parents
import org.jetbrains.kotlin.resolve.scopes.DescriptorKindFilter
import kotlin.LazyThreadSafetyMode.NONE

/**
 * Used to create a common type between [KtClassOrObject] class references and [ClassDescriptor]
 * references, to streamline parsing.
 *
 * @see toClassReference
 */
@ExperimentalAnvilApi
public sealed class ClassReference {

  public abstract val classId: ClassId
  public abstract val fqName: FqName
  public abstract val module: AnvilModuleDescriptor

  public val shortName: String get() = fqName.shortName().asString()
  public val packageFqName: FqName get() = classId.packageFqName

  public abstract val functions: List<FunctionReference>
  public abstract val annotations: List<AnnotationReference>

  public abstract fun isInterface(): Boolean
  public abstract fun isAbstract(): Boolean
  public abstract fun visibility(): Visibility

  /**
   * Returns only the super class (excluding [Any]) and implemented interfaces declared directly by
   * this class.
   */
  public abstract fun directSuperClassReferences(): List<ClassReference>

  /**
   * Returns all outer classes including this class. Imagine the inner class `Outer.Middle.Inner`,
   * then the returned list would contain `[Outer, Middle, Inner]` in that order.
   */
  public abstract fun enclosingClassesWithSelf(): List<ClassReference>

  override fun toString(): String {
    return "${this::class.qualifiedName}($fqName)"
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is ClassReference) return false

    if (fqName != other.fqName) return false

    return true
  }

  override fun hashCode(): Int {
    return fqName.hashCode()
  }

  public class Psi(
    public val clazz: KtClassOrObject,
    override val classId: ClassId,
    override val module: AnvilModuleDescriptor
  ) : ClassReference() {
    override val fqName: FqName = classId.asSingleFqName()

    override val functions: List<FunctionReference.Psi> by lazy(NONE) {
      clazz
        .functions(includeCompanionObjects = false)
        .map { it.toFunctionReference(this) }
    }

    override val annotations: List<AnnotationReference.Psi> by lazy(NONE) {
      clazz.annotationEntries.map { it.toAnnotationReference(module) }
    }

    private val directSuperClassReferences: List<ClassReference> by lazy(NONE) {
      clazz.superTypeListEntries.map { it.requireFqName(module).toClassReference(module) }
    }

    private val enclosingClassesWithSelf by lazy(NONE) {
      clazz.parents
        .filterIsInstance<KtClassOrObject>()
        .map { it.toClassReference(module) }
        .toList()
        .reversed()
        .plus(this)
    }

    override fun isInterface(): Boolean = clazz is KtClass && clazz.isInterface()

    override fun isAbstract(): Boolean = clazz.hasModifier(ABSTRACT_KEYWORD)

    override fun visibility(): Visibility {
      return when (val visibility = clazz.visibilityModifierTypeOrDefault()) {
        PUBLIC_KEYWORD -> PUBLIC
        INTERNAL_KEYWORD -> INTERNAL
        PROTECTED_KEYWORD -> PROTECTED
        PRIVATE_KEYWORD -> PRIVATE
        else -> throw AnvilCompilationExceptionClassReference(
          classReference = this,
          message = "Couldn't get visibility $visibility for class $fqName."
        )
      }
    }

    override fun directSuperClassReferences(): List<ClassReference> = directSuperClassReferences

    override fun enclosingClassesWithSelf(): List<Psi> = enclosingClassesWithSelf
  }

  public class Descriptor(
    public val clazz: ClassDescriptor,
    override val classId: ClassId,
    override val module: AnvilModuleDescriptor
  ) : ClassReference() {
    override val fqName: FqName = classId.asSingleFqName()

    override val functions: List<FunctionReference> by lazy(NONE) {
      clazz.unsubstitutedMemberScope
        .getContributedDescriptors(kindFilter = DescriptorKindFilter.FUNCTIONS)
        .filterIsInstance<FunctionDescriptor>()
        .map { it.toFunctionReference(this) }
    }

    override val annotations: List<AnnotationReference> by lazy(NONE) {
      clazz.annotations.map { it.toAnnotationReference(module) }
    }

    private val directSuperClassReferences: List<ClassReference> by lazy(NONE) {
      listOfNotNull(clazz.getSuperClassNotAny())
        .plus(clazz.getSuperInterfaces())
        .map { it.toClassReference(module) }
    }

    private val enclosingClassesWithSelf by lazy(NONE) {
      clazz.parents
        .filterIsInstance<ClassDescriptor>()
        .map { it.toClassReference(module) }
        .toList()
        .reversed()
        .plus(this)
    }

    override fun isInterface(): Boolean = clazz.kind == ClassKind.INTERFACE

    override fun isAbstract(): Boolean = clazz.modality == ABSTRACT

    override fun visibility(): Visibility {
      return when (val visibility = clazz.visibility) {
        DescriptorVisibilities.PUBLIC -> PUBLIC
        DescriptorVisibilities.INTERNAL -> INTERNAL
        DescriptorVisibilities.PROTECTED -> PROTECTED
        DescriptorVisibilities.PRIVATE -> PRIVATE
        else -> throw AnvilCompilationExceptionClassReference(
          classReference = this,
          message = "Couldn't get visibility $visibility for class $fqName."
        )
      }
    }

    override fun directSuperClassReferences(): List<ClassReference> = directSuperClassReferences

    override fun enclosingClassesWithSelf(): List<Descriptor> = enclosingClassesWithSelf
  }
}

@ExperimentalAnvilApi
public fun ClassDescriptor.toClassReference(module: ModuleDescriptor): Descriptor =
  module.asAnvilModuleDescriptor().getClassReference(this)

@ExperimentalAnvilApi
public fun KtClassOrObject.toClassReference(module: ModuleDescriptor): Psi =
  module.asAnvilModuleDescriptor().getClassReference(this)

/**
 * Attempts to resolve the [FqName] to a [ClassDescriptor] first, then falls back to a
 * [KtClassOrObject] if the descriptor resolution fails. This will happen if the code being parsed
 * was generated as part of the compilation round for this module.
 */
@ExperimentalAnvilApi
public fun FqName.toClassReferenceOrNull(module: ModuleDescriptor): ClassReference? =
  module.asAnvilModuleDescriptor().getClassReferenceOrNull(this)

@Suppress("DeprecatedCallableAddReplaceWith")
@Deprecated("Don't rely on PSI and make the code agnostic to the underlying implementation.")
@ExperimentalAnvilApi
public fun FqName.toClassReferencePsiOrNull(module: ModuleDescriptor): Psi? =
  module.asAnvilModuleDescriptor()
    .getClassReferenceOrNull(this, preferDescriptor = false) as? Psi

@ExperimentalAnvilApi
public fun FqName.toClassReference(module: ModuleDescriptor): ClassReference {
  return toClassReferenceOrNull(module)
    ?: throw AnvilCompilationException("Couldn't resolve ClassReference for $this.")
}

@ExperimentalAnvilApi
public fun ClassReference.isAnnotatedWith(fqName: FqName): Boolean =
  annotations.any { it.fqName == fqName }

@ExperimentalAnvilApi
public fun ClassReference.generateClassName(
  separator: String = "_"
): String {
  return enclosingClassesWithSelf().joinToString(separator = separator) {
    it.shortName
  }
}

@ExperimentalAnvilApi
public fun ClassReference.asClassName(): ClassName = classId.asClassName()

/**
 * This will return all super types as [ClassReference], whether they're parsed as [KtClassOrObject]
 * or [ClassDescriptor]. This will include generated code, assuming it has already been generated.
 * The returned sequence will be distinct by FqName, and Psi types are preferred over Descriptors.
 *
 * The first elements in the returned sequence represent the direct superclass to the receiver. The
 * last elements represent the types which are furthest up-stream.
 *
 * @param includeSelf If true, the receiver class is the first element of the sequence
 */
@ExperimentalAnvilApi
public fun ClassReference.allSuperTypeClassReferences(
  includeSelf: Boolean = false
): Sequence<ClassReference> {
  return generateSequence(sequenceOf(this)) { superTypes ->
    superTypes
      .map { classRef -> classRef.directSuperClassReferences() }
      .flatten()
      .takeIf { it.firstOrNull() != null }
  }
    .drop(if (includeSelf) 0 else 1)
    .flatten()
    .distinctBy { it.fqName }
}

@ExperimentalAnvilApi
public fun ClassReference.innerClasses(): Sequence<ClassReference> {
  return when (this) {
    is Descriptor ->
      clazz.unsubstitutedMemberScope
        .getContributedDescriptors(kindFilter = DescriptorKindFilter.CLASSIFIERS)
        .asSequence()
        .filterIsInstance<ClassDescriptor>()
        .map { it.toClassReference(module) }
    is Psi ->
      generateSequence(clazz.declarations.filterIsInstance<KtClassOrObject>()) { classes ->
        classes
          .flatMap { it.declarations }
          .filterIsInstance<KtClassOrObject>()
          .ifEmpty { null }
      }.flatten().map { it.toClassReference(module) }
  }
}

/**
 * @param parameterName The name of the parameter to be found, not including any variance modifiers.
 * @return The 0-based index of a declared generic type.
 */
@ExperimentalAnvilApi
public fun ClassReference.indexOfTypeParameter(parameterName: String): Int {
  return when (this) {
    is Descriptor ->
      clazz.declaredTypeParameters
        .indexOfFirst { it.name.asString() == parameterName }
    is Psi ->
      clazz.typeParameters
        .indexOfFirst { it.identifyingElement?.text == parameterName }
  }
}

@ExperimentalAnvilApi
public fun ClassReference.scopeOrNull(
  annotationFqName: FqName
): FqName? {
  return when (this) {
    is Descriptor -> clazz.annotationOrNull(annotationFqName)
      ?.scope(module)
      ?.fqNameSafe
    is Psi -> clazz.scopeOrNull(annotationFqName, module)
  }
}

private fun replacesIndex(annotationFqName: FqName): Int {
  return when (annotationFqName) {
    contributesToFqName -> 1
    contributesBindingFqName, contributesMultibindingFqName -> 2
    contributesSubcomponentFqName -> 4
    else -> throw NotImplementedError(
      "Couldn't find index of replaces argument for $annotationFqName."
    )
  }
}

@ExperimentalAnvilApi
public fun ClassReference.replaces(
  annotationFqName: FqName,
  index: Int = replacesIndex(annotationFqName)
): List<ClassReference> =
  when (this) {
    is Descriptor -> {
      val replacesValue = clazz.annotationOrNull(annotationFqName)
        ?.getAnnotationValue("replaces") as? ArrayValue

      replacesValue
        ?.value
        ?.map { it.argumentType(module).classDescriptor().toClassReference(module) }
    }
    is Psi ->
      clazz
        .findAnnotation(annotationFqName, module)
        ?.findAnnotationArgument<KtCollectionLiteralExpression>(name = "replaces", index = index)
        ?.toFqNames(module)
        ?.map { it.toClassReference(module) }
  }.orEmpty()

@ExperimentalAnvilApi
public fun ClassReference.qualifiers(): List<AnnotationSpec> {
  return when (this) {
    is Descriptor -> clazz.annotations.filter { it.isQualifier() }
      .map { it.toAnnotationSpec(module) }
    is Psi -> clazz.annotationEntries.filter { it.isQualifier(module) }
      .map { it.toAnnotationSpec(module) }
  }
}

@ExperimentalAnvilApi
public fun ClassReference.daggerScopes(): List<AnnotationSpec> = when (this) {
  is Descriptor -> clazz.annotations.filter { it.isDaggerScope() }
    .map { it.toAnnotationSpec(module) }
  is Psi -> clazz.annotationEntries.filter { it.isDaggerScope(module) }
    .map { it.toAnnotationSpec(module) }
}

@ExperimentalAnvilApi
@Suppress("FunctionName")
public fun AnvilCompilationExceptionClassReference(
  classReference: ClassReference,
  message: String,
  cause: Throwable? = null
): AnvilCompilationException = when (classReference) {
  is Psi -> AnvilCompilationException(
    element = classReference.clazz.identifyingElement ?: classReference.clazz,
    message = message,
    cause = cause
  )
  is Descriptor -> AnvilCompilationException(
    classDescriptor = classReference.clazz,
    message = message,
    cause = cause
  )
}
