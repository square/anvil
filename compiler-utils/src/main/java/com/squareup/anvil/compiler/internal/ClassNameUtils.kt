package com.squareup.anvil.compiler.internal

import com.squareup.anvil.annotations.ExperimentalAnvilApi
import com.squareup.kotlinpoet.ClassName

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

private const val MAX_FILE_NAME_LENGTH = 255
  .minus(14) // ".kapt_metadata" is the longest extension
  .minus(8) // "Provider" is the longest suffix that Dagger might add

private fun ClassName.checkFileLength(): ClassName = apply {
  val len = simpleNames.sumOf { it.length + 1 }.minus(1)
  require(len <= MAX_FILE_NAME_LENGTH) {
    "Class name is too long: $len  --  ${toString()}"
  }
}
