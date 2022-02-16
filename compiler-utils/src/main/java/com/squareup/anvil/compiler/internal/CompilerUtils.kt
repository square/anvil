@file:Suppress("unused")

package com.squareup.anvil.compiler.internal

import com.squareup.anvil.annotations.ExperimentalAnvilApi
import com.squareup.anvil.compiler.api.AnvilCompilationException
import org.jetbrains.kotlin.codegen.asmType
import org.jetbrains.kotlin.codegen.state.KotlinTypeMapper
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.descriptors.annotations.AnnotationDescriptor
import org.jetbrains.kotlin.descriptors.findClassAcrossModuleDependencies
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.platform.has
import org.jetbrains.kotlin.platform.jvm.JvmPlatform
import org.jetbrains.kotlin.resolve.constants.ConstantValue
import org.jetbrains.kotlin.resolve.constants.KClassValue
import org.jetbrains.kotlin.resolve.constants.KClassValue.Value.NormalClass
import org.jetbrains.kotlin.resolve.descriptorUtil.annotationClass
import org.jetbrains.kotlin.resolve.descriptorUtil.classId
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import org.jetbrains.kotlin.resolve.descriptorUtil.module
import org.jetbrains.kotlin.types.ErrorType
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.TypeUtils
import org.jetbrains.kotlin.types.typeUtil.supertypes
import org.jetbrains.org.objectweb.asm.Type

@ExperimentalAnvilApi
public fun ClassDescriptor.annotationOrNull(
  annotationFqName: FqName,
  scope: FqName? = null
): AnnotationDescriptor? {
  // Must be JVM, we don't support anything else.
  if (!module.platform.has<JvmPlatform>()) return null
  val annotationDescriptor = try {
    annotations.findAnnotation(annotationFqName)
  } catch (e: IllegalStateException) {
    // In some scenarios this exception is thrown. Throw a new exception with a better explanation.
    // Caused by: java.lang.IllegalStateException: Should not be called!
    // at org.jetbrains.kotlin.types.ErrorUtils$1.getPackage(ErrorUtils.java:95)
    throw AnvilCompilationException(
      this,
      message = "It seems like you tried to contribute an inner class to its outer class. This " +
        "is not supported and results in a compiler error.",
      cause = e
    )
  }
  return if (scope == null || annotationDescriptor == null) {
    annotationDescriptor
  } else {
    annotationDescriptor.takeIf { scope == it.scope(module).fqNameSafe }
  }
}

@ExperimentalAnvilApi
public fun ClassDescriptor.annotation(
  annotationFqName: FqName,
  scope: FqName? = null
): AnnotationDescriptor = requireNotNull(annotationOrNull(annotationFqName, scope)) {
  "Couldn't find $annotationFqName with scope $scope for $fqNameSafe."
}

@ExperimentalAnvilApi
public fun ConstantValue<*>.toType(
  module: ModuleDescriptor,
  typeMapper: KotlinTypeMapper
): Type {
  // This is a Kotlin class with the actual type as argument: KClass<OurType>
  return argumentType(module).asmType(typeMapper)
}

// When the Kotlin type is of the form: KClass<OurType>.
@ExperimentalAnvilApi
public fun KotlinType.argumentType(): KotlinType = arguments.first().type

@ExperimentalAnvilApi
public fun KotlinType.classDescriptorOrNull(): ClassDescriptor? {
  return TypeUtils.getClassDescriptor(this)
}

@ExperimentalAnvilApi
public fun KotlinType.classDescriptor(): ClassDescriptor {
  return classDescriptorOrNull()
    ?: throw AnvilCompilationException(
      "Unable to resolve type for ${this.asTypeName()}"
    )
}

@ExperimentalAnvilApi
public fun AnnotationDescriptor.getAnnotationValue(key: String): ConstantValue<*>? =
  allValueArguments[Name.identifier(key)]

@ExperimentalAnvilApi
@Suppress("DeprecatedCallableAddReplaceWith")
@Deprecated(
  "Don't rely on descriptors and make the code agnostic to the underlying implementation. " +
    "See [AnnotationReference#scope]"
)
public fun AnnotationDescriptor.scope(module: ModuleDescriptor): ClassDescriptor {
  return scopeOrNull(module)
    ?: throw AnvilCompilationException(
      annotationDescriptor = this,
      message = "Couldn't find scope for $fqName."
    )
}

@ExperimentalAnvilApi
@Suppress("DeprecatedCallableAddReplaceWith")
@Deprecated(
  "Don't rely on descriptors and make the code agnostic to the underlying implementation. " +
    "See [AnnotationReference#scopeOrNull]"
)
public fun AnnotationDescriptor.scopeOrNull(module: ModuleDescriptor): ClassDescriptor? {
  val annotationValue = getAnnotationValue("scope") as? KClassValue
  return annotationValue?.argumentType(module)?.classDescriptor()
}

@ExperimentalAnvilApi
@Suppress("DeprecatedCallableAddReplaceWith")
@Deprecated(
  "Don't rely on descriptors and make the code agnostic to the underlying implementation. " +
    "See [AnnotationReference#parentScope]"
)
public fun AnnotationDescriptor.parentScope(module: ModuleDescriptor): ClassDescriptor {
  val annotationValue = getAnnotationValue("parentScope") as? KClassValue
    ?: throw AnvilCompilationException(
      annotationDescriptor = this,
      message = "Couldn't find parentScope for $fqName."
    )

  return annotationValue.argumentType(module).classDescriptor()
}

@ExperimentalAnvilApi
public fun ConstantValue<*>.argumentType(module: ModuleDescriptor): KotlinType {
  val argumentType = getType(module).argumentType()
  if (argumentType !is ErrorType) return argumentType

  // Handle inner classes explicitly. When resolving the Kotlin type of inner class from
  // dependencies the compiler might fail. It tries to load my.package.Class$Inner and fails
  // whereas is should load my.package.Class.Inner.
  val normalClass = this.value
  if (normalClass !is NormalClass) return argumentType

  val classId = normalClass.value.classId

  return module
    .findClassAcrossModuleDependencies(
      classId = ClassId(
        classId.packageFqName,
        FqName(classId.relativeClassName.asString().replace('$', '.')),
        false
      )
    )
    ?.defaultType
    ?: throw AnvilCompilationException(
      "Couldn't resolve class across module dependencies for class ID: $classId"
    )
}

@ExperimentalAnvilApi
public fun List<KotlinType>.getAllSuperTypes(): Sequence<FqName> =
  generateSequence(this) { kotlinTypes ->
    kotlinTypes.ifEmpty { null }?.flatMap { it.supertypes() }
  }
    .flatMap { it.asSequence() }
    .map { it.classDescriptor().fqNameSafe }

@ExperimentalAnvilApi
@Suppress("DeprecatedCallableAddReplaceWith")
@Deprecated(
  "Don't rely on descriptors and make the code agnostic to the underlying implementation. " +
    "See [AnnotationReference#isQualifier]"
)
public fun AnnotationDescriptor.isQualifier(): Boolean {
  return annotationClass?.annotations?.hasAnnotation(qualifierFqName) ?: false
}

@ExperimentalAnvilApi
public fun AnnotationDescriptor.requireClass(): ClassDescriptor {
  return annotationClass ?: throw AnvilCompilationException(
    message = "Couldn't find the annotation class for $fqName",
  )
}

@ExperimentalAnvilApi
public fun AnnotationDescriptor.requireFqName(): FqName {
  return fqName ?: throw AnvilCompilationException(
    message = "Couldn't find the fqName for $this",
  )
}

/**
 * This function should only be used for package names. If the FqName is the root (no package at
 * all), then this function returns an empty string whereas `toString()` would return "<root>". For
 * a more convenient string concatenation the returned result can be prefixed and suffixed with an
 * additional dot. The root package never will use a prefix or suffix.
 */
@ExperimentalAnvilApi
public fun FqName.safePackageString(
  dotPrefix: Boolean = false,
  dotSuffix: Boolean = true
): String =
  if (isRoot) {
    ""
  } else {
    val prefix = if (dotPrefix) "." else ""
    val suffix = if (dotSuffix) "." else ""
    "$prefix$this$suffix"
  }

@ExperimentalAnvilApi
public fun FqName.classIdBestGuess(): ClassId {
  val segments = pathSegments().map { it.asString() }
  val classNameIndex = segments.indexOfFirst { it[0].isUpperCase() }
  if (classNameIndex < 0) {
    return ClassId.topLevel(this)
  }

  val packageFqName = FqName.fromSegments(segments.subList(0, classNameIndex))
  val relativeClassName = FqName.fromSegments(segments.subList(classNameIndex, segments.size))
  return ClassId(packageFqName, relativeClassName, false)
}

@ExperimentalAnvilApi
public fun String.capitalize(): String = replaceFirstChar(Char::uppercaseChar)

@ExperimentalAnvilApi
public fun String.decapitalize(): String = replaceFirstChar(Char::lowercaseChar)

@ExperimentalAnvilApi
public fun FqName.generateClassName(
  separator: String = "_",
  suffix: String = ""
): ClassId = classIdBestGuess().generateClassName(separator = separator, suffix = suffix)

@ExperimentalAnvilApi
public fun ClassId.generateClassName(
  separator: String = "_",
  suffix: String = ""
): ClassId {
  val className = relativeClassName.asString().replace(".", separator)
  return ClassId(packageFqName, FqName(className + suffix), false)
}

@ExperimentalAnvilApi
public fun ClassDescriptor.requireClassId(): ClassId {
  return classId ?: throw AnvilCompilationException(
    classDescriptor = this,
    message = "Couldn't find the classId for $fqNameSafe."
  )
}
