package com.squareup.anvil.compiler.internal.reference

import com.squareup.anvil.annotations.ExperimentalAnvilApi
import com.squareup.anvil.compiler.api.AnvilCompilationException
import com.squareup.anvil.compiler.internal.asClassName
import com.squareup.anvil.compiler.internal.capitalize
import com.squareup.anvil.compiler.internal.containingFileAsJavaFile
import com.squareup.anvil.compiler.internal.reference.ClassReference.Descriptor
import com.squareup.anvil.compiler.internal.reference.ClassReference.Psi
import com.squareup.anvil.compiler.internal.reference.Visibility.INTERNAL
import com.squareup.anvil.compiler.internal.reference.Visibility.PRIVATE
import com.squareup.anvil.compiler.internal.reference.Visibility.PROTECTED
import com.squareup.anvil.compiler.internal.reference.Visibility.PUBLIC
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.TypeName
import org.jetbrains.kotlin.descriptors.CallableMemberDescriptor.Kind.DECLARATION
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.ConstructorDescriptor
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.descriptors.Modality.ABSTRACT
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.descriptors.PropertyDescriptor
import org.jetbrains.kotlin.lexer.KtTokens.ABSTRACT_KEYWORD
import org.jetbrains.kotlin.lexer.KtTokens.INTERNAL_KEYWORD
import org.jetbrains.kotlin.lexer.KtTokens.PRIVATE_KEYWORD
import org.jetbrains.kotlin.lexer.KtTokens.PROTECTED_KEYWORD
import org.jetbrains.kotlin.lexer.KtTokens.PUBLIC_KEYWORD
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassBody
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.allConstructors
import org.jetbrains.kotlin.psi.psiUtil.isPropertyParameter
import org.jetbrains.kotlin.psi.psiUtil.parents
import org.jetbrains.kotlin.psi.psiUtil.visibilityModifierTypeOrDefault
import org.jetbrains.kotlin.resolve.DescriptorUtils
import org.jetbrains.kotlin.resolve.descriptorUtil.parents
import org.jetbrains.kotlin.resolve.scopes.DescriptorKindFilter
import org.jetbrains.kotlin.resolve.scopes.getDescriptorsFiltered
import org.jetbrains.kotlin.resolve.source.getPsi
import java.io.File
import kotlin.LazyThreadSafetyMode.NONE

/**
 * Used to create a common type between [KtClassOrObject] class references and [ClassDescriptor]
 * references, to streamline parsing.
 *
 * @see toClassReference
 */
@ExperimentalAnvilApi
public sealed class ClassReference : Comparable<ClassReference>, AnnotatedReference {

  public abstract val classId: ClassId
  public abstract val fqName: FqName
  public abstract val module: AnvilModuleDescriptor

  public abstract val containingFileAsJavaFile: File

  public val shortName: String get() = fqName.shortName().asString()
  public val packageFqName: FqName get() = classId.packageFqName

  public abstract val constructors: List<MemberFunctionReference>
  public abstract val functions: List<MemberFunctionReference>
  public abstract val properties: List<MemberPropertyReference>
  public abstract val typeParameters: List<TypeParameterReference>

  protected abstract val innerClassesAndObjects: List<ClassReference>

  public abstract fun isInterface(): Boolean
  public abstract fun isAbstract(): Boolean
  public abstract fun isObject(): Boolean
  public abstract fun isCompanion(): Boolean
  public abstract fun isGenericClass(): Boolean
  public abstract fun isAnnotationClass(): Boolean
  public abstract fun visibility(): Visibility

  /**
   * Returns only the super types (excluding [Any]) and implemented interfaces declared directly by
   * this class.
   */
  public abstract fun directSuperTypeReferences(): List<TypeReference>

  /**
   * Returns all outer classes including this class. Imagine the inner class `Outer.Middle.Inner`,
   * then the returned list would contain `[Outer, Middle, Inner]` in that order.
   */
  public abstract fun enclosingClassesWithSelf(): List<ClassReference>

  public fun enclosingClass(): ClassReference? {
    val classes = enclosingClassesWithSelf()
    val index = classes.indexOf(this)
    return if (index == 0) null else classes[index - 1]
  }

  public open fun innerClasses(): List<ClassReference> =
    innerClassesAndObjects.filterNot { it.isCompanion() }

  /**
   * @param parameterName The name of the parameter to be found, not including any variance modifiers.
   * @return The 0-based index of a declared generic type.
   */
  internal abstract fun indexOfTypeParameter(parameterName: String): Int

  public open fun companionObjects(): List<ClassReference> =
    innerClassesAndObjects.filter { it.isCompanion() && it.enclosingClass() == this }

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

  override fun compareTo(other: ClassReference): Int {
    return fqName.asString().compareTo(other.fqName.asString())
  }

  public class Psi(
    public val clazz: KtClassOrObject,
    override val classId: ClassId,
    override val module: AnvilModuleDescriptor,
  ) : ClassReference() {
    override val fqName: FqName = classId.asSingleFqName()

    override val constructors: List<MemberFunctionReference.Psi> by lazy(NONE) {
      clazz.allConstructors.map { it.toFunctionReference(this) }
    }

    override val containingFileAsJavaFile: File by lazy(NONE) {
      clazz.containingFileAsJavaFile()
    }

    override val functions: List<MemberFunctionReference.Psi> by lazy(NONE) {
      clazz
        .children
        .filterIsInstance<KtClassBody>()
        .flatMap { it.functions }
        .map { it.toFunctionReference(this) }
    }

    override val annotations: List<AnnotationReference.Psi> by lazy(NONE) {
      clazz.annotationEntries.map { it.toAnnotationReference(this, module) }
    }

    override val properties: List<MemberPropertyReference.Psi> by lazy(NONE) {
      buildList {
        // Order kind of matters here, since the Descriptor APIs will list body/member properties
        // before the constructor properties.
        clazz.body
          ?.properties
          ?.forEach { add(it.toPropertyReference(this@Psi)) }

        clazz.primaryConstructor
          ?.valueParameters
          ?.filter { it.isPropertyParameter() }
          ?.forEach { add(it.toPropertyReference(this@Psi)) }
      }
    }

    override val typeParameters: List<TypeParameterReference.Psi> by lazy(NONE) {
      getTypeParameterReferences()
    }

    private val directSuperTypeReferences: List<TypeReference> by lazy(NONE) {
      clazz.superTypeListEntries.mapNotNull { it.typeReference?.toTypeReference(this, module) }
    }

    private val enclosingClassesWithSelf by lazy(NONE) {
      clazz.parents
        .filterIsInstance<KtClassOrObject>()
        .map { it.toClassReference(module) }
        .toList()
        .reversed()
        .plus(this)
    }

    override val innerClassesAndObjects: List<Psi> by lazy(NONE) {
      generateSequence(clazz.declarations.filterIsInstance<KtClassOrObject>()) { classes ->
        classes
          .flatMap { it.declarations }
          .filterIsInstance<KtClassOrObject>()
          .ifEmpty { null }
      }
        .flatten()
        .map { it.toClassReference(module) }
        .toList()
    }

    override fun isInterface(): Boolean = clazz is KtClass && clazz.isInterface()

    override fun isAbstract(): Boolean = clazz.hasModifier(ABSTRACT_KEYWORD)

    override fun isObject(): Boolean = clazz is KtObjectDeclaration

    override fun isCompanion(): Boolean = clazz is KtObjectDeclaration && clazz.isCompanion()

    override fun isGenericClass(): Boolean = clazz.typeParameterList != null

    override fun isAnnotationClass(): Boolean = clazz is KtClass && clazz.isAnnotation()

    override fun visibility(): Visibility {
      return when (val visibility = clazz.visibilityModifierTypeOrDefault()) {
        PUBLIC_KEYWORD -> PUBLIC
        INTERNAL_KEYWORD -> INTERNAL
        PROTECTED_KEYWORD -> PROTECTED
        PRIVATE_KEYWORD -> PRIVATE
        else -> throw AnvilCompilationExceptionClassReference(
          classReference = this,
          message = "Couldn't get visibility $visibility for class $fqName.",
        )
      }
    }

    override fun directSuperTypeReferences(): List<TypeReference> = directSuperTypeReferences

    override fun enclosingClassesWithSelf(): List<Psi> = enclosingClassesWithSelf

    @Suppress("UNCHECKED_CAST")
    override fun innerClasses(): List<Psi> =
      super.innerClasses() as List<Psi>

    @Suppress("UNCHECKED_CAST")
    override fun companionObjects(): List<Psi> =
      super.companionObjects() as List<Psi>

    override fun indexOfTypeParameter(parameterName: String): Int =
      clazz.typeParameters.indexOfFirst { it.identifyingElement?.text == parameterName }
  }

  public class Descriptor(
    public val clazz: ClassDescriptor,
    override val classId: ClassId,
    override val module: AnvilModuleDescriptor,
  ) : ClassReference() {
    override val fqName: FqName = classId.asSingleFqName()

    override val constructors: List<MemberFunctionReference.Descriptor> by lazy(NONE) {
      clazz.constructors.map { it.toFunctionReference(this) }
    }

    override val containingFileAsJavaFile: File by lazy(NONE) {
      clazz.source.getPsi()
        ?.containingFileAsJavaFile()
        ?: throw AnvilCompilationExceptionClassReference(
          classReference = this,
          message = "Couldn't find Psi element for class $fqName.",
        )
    }

    override val functions: List<MemberFunctionReference.Descriptor> by lazy(NONE) {
      clazz.unsubstitutedMemberScope
        .getContributedDescriptors(kindFilter = DescriptorKindFilter.FUNCTIONS)
        .filterIsInstance<FunctionDescriptor>()
        .filterNot { it is ConstructorDescriptor }
        .map { it.toFunctionReference(this) }
    }

    override val annotations: List<AnnotationReference.Descriptor> by lazy(NONE) {
      clazz.annotations.map { it.toAnnotationReference(this, module) }
    }

    override val properties: List<MemberPropertyReference.Descriptor> by lazy(NONE) {
      clazz.unsubstitutedMemberScope
        .getDescriptorsFiltered(kindFilter = DescriptorKindFilter.VARIABLES)
        .filterIsInstance<PropertyDescriptor>()
        .filter {
          // Remove inherited properties that aren't overridden in this class.
          it.kind == DECLARATION
        }
        .map { it.toPropertyReference(this) }
    }

    private val directSuperTypeReferences: List<TypeReference> by lazy(NONE) {
      clazz.typeConstructor.supertypes.map { it.toTypeReference(this, module) }
        .filterNot { it.asClassReference().fqName.asString() == "kotlin.Any" }
    }

    private val enclosingClassesWithSelf by lazy(NONE) {
      clazz.parents
        .filterIsInstance<ClassDescriptor>()
        .map { it.toClassReference(module) }
        .toList()
        .reversed()
        .plus(this)
    }

    override val innerClassesAndObjects: List<Descriptor> by lazy(NONE) {
      clazz.unsubstitutedMemberScope
        .getContributedDescriptors(kindFilter = DescriptorKindFilter.CLASSIFIERS)
        .filterIsInstance<ClassDescriptor>()
        .map { it.toClassReference(module) }
    }

    override val typeParameters: List<TypeParameterReference.Descriptor> by lazy(NONE) {
      clazz.declaredTypeParameters
        .map { typeParameter ->
          typeParameter.toTypeParameterReference(this)
        }
    }

    override fun isInterface(): Boolean = clazz.kind == ClassKind.INTERFACE

    override fun isAbstract(): Boolean = clazz.modality == ABSTRACT

    override fun isObject(): Boolean = DescriptorUtils.isObject(clazz)

    override fun isCompanion(): Boolean = DescriptorUtils.isCompanionObject(clazz)

    override fun isAnnotationClass(): Boolean = DescriptorUtils.isAnnotationClass(clazz)

    override fun isGenericClass(): Boolean = clazz.declaredTypeParameters.isNotEmpty()

    override fun visibility(): Visibility {
      return when (val visibility = clazz.visibility) {
        DescriptorVisibilities.PUBLIC -> PUBLIC
        DescriptorVisibilities.INTERNAL -> INTERNAL
        DescriptorVisibilities.PROTECTED -> PROTECTED
        DescriptorVisibilities.PRIVATE -> PRIVATE
        else -> throw AnvilCompilationExceptionClassReference(
          classReference = this,
          message = "Couldn't get visibility $visibility for class $fqName.",
        )
      }
    }

    override fun directSuperTypeReferences(): List<TypeReference> = directSuperTypeReferences

    override fun enclosingClassesWithSelf(): List<Descriptor> = enclosingClassesWithSelf

    @Suppress("UNCHECKED_CAST")
    override fun innerClasses(): List<Descriptor> =
      super.innerClasses() as List<Descriptor>

    @Suppress("UNCHECKED_CAST")
    override fun companionObjects(): List<Descriptor> =
      super.companionObjects() as List<Descriptor>

    override fun indexOfTypeParameter(parameterName: String): Int =
      clazz.declaredTypeParameters.indexOfFirst { it.name.asString() == parameterName }
  }
}

@ExperimentalAnvilApi
public fun ClassDescriptor.toClassReference(module: ModuleDescriptor): Descriptor =
  module.asAnvilModuleDescriptor().getClassReference(this)

@ExperimentalAnvilApi
public fun KtClassOrObject.toClassReference(module: ModuleDescriptor): Psi =
  module.asAnvilModuleDescriptor().getClassReference(this)

/**
 * Attempts to find the [KtClassOrObject] for the [FqName] first, then falls back to the
 * [ClassDescriptor] if the Psi element cannot be found. This will happen if the class for
 * [FqName] is not part of this compilation unit.
 */
@ExperimentalAnvilApi
public fun FqName.toClassReferenceOrNull(module: ModuleDescriptor): ClassReference? =
  module.asAnvilModuleDescriptor().getClassReferenceOrNull(this)

@ExperimentalAnvilApi
public fun FqName.toClassReference(module: ModuleDescriptor): ClassReference {
  return toClassReferenceOrNull(module)
    ?: throw AnvilCompilationException("Couldn't resolve ClassReference for $this.")
}

@ExperimentalAnvilApi
public fun ClassReference.generateClassName(
  separator: String = "_",
  suffix: String = "",
): ClassId {
  return asClassName().generateClassName(separator, suffix).asClassId()
}

@ExperimentalAnvilApi
public fun ClassName.generateClassName(
  separator: String = "_",
  suffix: String = "",
): ClassName {
  val className = simpleNames.joinToString(separator = separator)
  return ClassName(packageName, className + suffix)
}

@ExperimentalAnvilApi
public fun ClassName.generateClassNameString(
  separator: String = "",
  suffix: String = "",
  capitalizePackage: Boolean = true,
): String {
  return packageName.split('.').plus(simpleNames).joinToString(separator = separator) {
    if (capitalizePackage) it.capitalize() else it
  } + suffix
}

@ExperimentalAnvilApi
public fun ClassName.asClassId(local: Boolean = false): ClassId = ClassId(
  FqName(packageName),
  FqName(simpleNames.joinToString(".")),
  local,
)

@ExperimentalAnvilApi
public fun ClassReference.asClassName(): ClassName = classId.asClassName()

@ExperimentalAnvilApi
public fun ClassReference.asTypeName(): TypeName {
  return if (!isGenericClass()) {
    asClassName()
  } else {
    asClassName().parameterizedBy(typeParameters.map { it.typeVariableName })
  }
}

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
  includeSelf: Boolean = false,
): Sequence<ClassReference> {
  return generateSequence(listOf(this)) { superTypes ->
    superTypes
      .flatMap { classRef ->
        classRef.directSuperTypeReferences().mapNotNull { it.asClassReferenceOrNull() }
      }
      .takeIf { it.isNotEmpty() }
  }
    .drop(if (includeSelf) 0 else 1)
    .flatten()
    .distinct()
}

@ExperimentalAnvilApi
@Suppress("FunctionName")
public fun AnvilCompilationExceptionClassReference(
  classReference: ClassReference,
  message: String,
  cause: Throwable? = null,
): AnvilCompilationException = when (classReference) {
  is Psi -> AnvilCompilationException(
    element = classReference.clazz.identifyingElement ?: classReference.clazz,
    message = message,
    cause = cause,
  )
  is Descriptor -> AnvilCompilationException(
    classDescriptor = classReference.clazz,
    message = message,
    cause = cause,
  )
}
