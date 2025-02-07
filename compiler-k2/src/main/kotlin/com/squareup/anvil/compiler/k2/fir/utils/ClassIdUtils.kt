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
