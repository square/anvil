package com.squareup.anvil.compiler.ksp

import com.google.devtools.ksp.symbol.KSAnnotated

sealed class MergeAnnotationMapper {
  abstract fun remapAnnotated(annotated: KSAnnotated): KSAnnotated
}
