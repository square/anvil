package com.squareup.anvil.compiler.testing.temp

import com.squareup.anvil.annotations.ExperimentalAnvilApi
import com.squareup.kotlinpoet.ClassName
import org.jetbrains.kotlin.name.FqName
import java.security.MessageDigest
import kotlin.reflect.KClass

/**
 * Generates a unique hint file name by adding the package name as the first simple name,
 * then joining all simple names with the [separator] and [suffix].
 *
 * @see ClassName.joinSimpleNames for the joining logic
 */
@ExperimentalAnvilApi
public fun ClassName.generateHintFileName(
  separator: String = "",
  suffix: String = "",
  capitalizePackage: Boolean = true,
): String = ClassName(
  packageName,
  listOfNotNull(
    packageName
      .takeIf { it.isNotEmpty() }
      ?.replace('.', '_')
      ?.let { if (capitalizePackage) it.capitalize() else it },
    *simpleNames.toTypedArray(),
  ),
)
  .joinSimpleNamesPrivate(separator = separator, suffix = suffix)
  .truncate(hashParams = listOf(canonicalName), innerClassLength = 0)
  .simpleName

private fun ClassName.joinSimpleNamesPrivate(
  separator: String = "_",
  prefix: String = "",
  suffix: String = "",
): ClassName = ClassName(
  packageName = packageName,
  simpleNames.joinToString(separator = separator, prefix = prefix, postfix = suffix),
)

private const val HASH_STRING_LENGTH = 8
private const val MAX_FILE_NAME_LENGTH = 255
  .minus(14) // ".kapt_metadata" is the longest extension
  .minus(8) // "Provider" is the longest suffix that Dagger might add

@ExperimentalAnvilApi
public fun ClassName.truncate(
  hashParams: List<Any>,
  separator: String = "_",
  innerClassLength: Int = 0,
): ClassName {

  val maxLength = MAX_FILE_NAME_LENGTH
    // hash suffix with separator: `_a0b2c3d4`
    .minus(HASH_STRING_LENGTH + separator.length)
    // a nested type that will be appended to this canonical name
    // with a '$' separator, like `$ParentComponent`
    .minus(innerClassLength + 1)
    // The class file name contains all parent class names as well, separated by '$',
    // so the lengths of those names must be subtracted from the max length.
    .minus(simpleNames.dropLast(1).sumOf { it.length + 1 })

  val md5Hash = md5Hash(hashParams)

  val className = simpleName.take(maxLength)
    // The hash is appended after truncating so that it's always present.
    .plus("$separator$md5Hash")

  return ClassName(packageName, className).checkFileLength()
}

private fun ClassName.checkFileLength(): ClassName = apply {
  val len = simpleNames.sumOf { it.length + 1 }.minus(1)
  require(len <= MAX_FILE_NAME_LENGTH) {
    "Class name is too long: $len  --  ${toString()}"
  }
}
private fun md5Hash(params: List<Any>): String {
  return MessageDigest.getInstance("MD5")
    .apply {
      params.forEach {
        update(it.toString().toByteArray())
      }
    }
    .digest()
    .take(HASH_STRING_LENGTH / 2)
    .joinToString("") { "%02x".format(it) }
}

public val KClass<*>.fqName: FqName
  get() = FqName(
    requireNotNull(qualifiedName) {
      "An FqName cannot be created for a local class or class of an anonymous object."
    },
  )
