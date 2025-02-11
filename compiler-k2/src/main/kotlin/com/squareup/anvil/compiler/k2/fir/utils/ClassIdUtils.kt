package com.squareup.anvil.compiler.k2.fir.utils

import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

internal fun ClassId.append(suffix: String): ClassId {
  return ClassId(
    packageFqName = packageFqName,
    topLevelName = Name.identifier("${shortClassName}$suffix"),
  )
}

internal val FqName.classId: ClassId get() {
  return ClassId.topLevel(this)
}

/**
 * Joins the simple names of a class with the given [separator], [prefix], and [suffix].
 *
 * ```
 * val normalName = ClassName("com.example", "Outer", "Middle", "Inner")
 * val joinedName = normalName.joinSimpleNames(separator = "_", suffix = "Factory")
 *
 * println(joinedName) // com.example.Outer_Middle_InnerFactory
 * ```
 * @throws IllegalArgumentException if the resulting class name is too long to be a valid file name.
 */
internal fun ClassId.joinSimpleNames(
  separator: String = "_",
  prefix: String = "",
  suffix: String = "",
): ClassId = ClassId.topLevel(
  packageFqName.child(
    Name.identifier(
      relativeClassName.pathSegments()
        .joinToString(separator = separator, prefix = prefix, postfix = suffix),
    ),
  ),
)
