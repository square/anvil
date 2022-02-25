@file:Suppress("unused")

package com.squareup.anvil.compiler.internal

import com.squareup.anvil.annotations.ExperimentalAnvilApi
import com.squareup.anvil.compiler.api.AnvilCompilationException
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.descriptors.findClassAcrossModuleDependencies
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.resolve.constants.ConstantValue
import org.jetbrains.kotlin.resolve.constants.KClassValue.Value.NormalClass
import org.jetbrains.kotlin.types.ErrorType
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.TypeUtils

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
      "Unable to resolve type for $this."
    )
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
