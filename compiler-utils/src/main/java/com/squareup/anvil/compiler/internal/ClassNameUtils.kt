package com.squareup.anvil.compiler.internal

import com.squareup.anvil.annotations.ExperimentalAnvilApi
import com.squareup.kotlinpoet.ClassName
import java.security.MessageDigest

private const val HASH_STRING_LENGTH = 8
private const val MAX_FILE_NAME_LENGTH = 255
  .minus(14) // ".kapt_metadata" is the longest extension
  .minus(8) // "Provider" is the longest suffix that Dagger might add

/**
 * Joins the simple names of a class with the given [separator] and [suffix].
 *
 * ```
 * val normalName = ClassName("com.example", "Outer", "Middle", "Inner")
 * val joinedName = normalName.joinSimpleNames(separator = "_", suffix = "Factory")
 *
 * println(joinedName) // com.example.Outer_Middle_InnerFactory
 * ```
 * @throws IllegalArgumentException if the resulting class name is too long to be a valid file name.
 */
@ExperimentalAnvilApi
public fun ClassName.joinSimpleNames(
  separator: String = "_",
  suffix: String = "",
): ClassName = joinSimpleNamesPrivate(separator = separator, suffix = suffix)
  .checkFileLength()

private fun ClassName.joinSimpleNamesPrivate(
  separator: String = "_",
  suffix: String = "",
): ClassName = ClassName(
  packageName = packageName,
  simpleNames.joinToString(separator = separator, postfix = suffix),
)

private fun ClassName.checkFileLength(): ClassName = apply {
  val len = simpleNames.sumOf { it.length + 1 }.minus(1)
  require(len <= MAX_FILE_NAME_LENGTH) {
    "Class name is too long: $len  --  ${toString()}"
  }
}

/**
 * Joins the simple names of a class with the given [separator] and [suffix].
 *
 * The end of the name will be the separator followed by a hash of the
 * [hashParams], so that generated class names are unique.
 * If the resulting class name is too long to be a valid file name,
 * it will be truncated by removing the last characters *before* the hash,
 * but the hash be unchanged.
 *
 * ```
 * val someScope = ClassName("com.example", "SomeScope")
 * val boundType = ClassName("com.example", "BoundType")
 * val normalName = ClassName("com.example", "Outer", "Middle", "Inner")
 * val joinedName = normalName.joinSimpleNamesAndTruncate(
 *   hashParams = listOf(someScope, boundType),
 *   separator = "_",
 *   suffix = "Factory"
 * )
 * println(joinedName) // com.example.Outer_Middle_InnerFactory_0a1b2c3d
 * ```
 * @throws IllegalArgumentException if the resulting class name is too long to be a valid file name
 *   even after truncating.
 * @see ClassName.joinSimpleNames for a version that doesn't truncate the class name.
 */
@ExperimentalAnvilApi
public fun ClassName.joinSimpleNamesAndTruncate(
  hashParams: List<Any>,
  separator: String = "_",
  suffix: String = "",
  innerClassLength: Int = 0,
): ClassName = joinSimpleNamesPrivate(separator = separator, suffix = suffix)
  .truncate(
    hashParams = listOf(this) + hashParams,
    separator = separator,
    innerClassLength = innerClassLength,
  )

/**
 * Truncates the class name to a valid file name length by removing characters from the end of the
 * class name. The hash of the [hashParams] will be appended to the class name with the given
 * [separator].
 * If the class name is too long, it will be truncated by removing the last characters *before* the
 * hash, but the hash will be unchanged.
 *
 * ```
 * val someScope = ClassName("com.example", "SomeScope")
 * val boundType = ClassName("com.example", "BoundType")
 * val normalName = ClassName("com.example", "Outer", "Middle", "Inner")
 * val truncatedName = normalName.truncate(
 *   hashParams = listOf(someScope, boundType),
 *   separator = "_",
 *   innerClassLength = 0
 * )
 * println(truncatedName) // com.example.Outer_Middle_Inner_0a1b2c3d
 * ```
 * @throws IllegalArgumentException if the resulting class name is too long to be a valid file name
 *   even after truncating.
 */
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
