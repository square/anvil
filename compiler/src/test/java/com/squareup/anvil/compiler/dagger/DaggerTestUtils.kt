package com.squareup.anvil.compiler.dagger

/**
 * Removes parameters of the functions in a String like
 * ```
 * [create([string]), get([])]
 * ```
 * That's necessary, because the output of the parameters is slightly different between Anvil
 * and Dagger. Dagger will utilize Java types while Anvil will utilize Kotlin types,
 * e.g. int vs Int.
 *
 * Dagger also doesn't guarantee any order of functions.
 */
internal fun String.removeParametersAndSort(): String {
  val start = 1 + (indexOf('[').takeIf { it >= 0 } ?: return this)
  val end = indexOfLast { it == ']' }.takeIf { it >= 0 } ?: return this

  val sortedMethodNames = substring(start, end)
    .replace(Regex("""\(.*?\)"""), "")
    .split(',')
    .map { it.trim() }
    .sorted()
    .joinToString()

  return replaceRange(start, end, sortedMethodNames)
}
