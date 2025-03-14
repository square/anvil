package com.squareup.anvil.compiler.k2.fir

import org.jetbrains.kotlin.GeneratedDeclarationKey
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.Visibility
import org.jetbrains.kotlin.fir.caches.FirCachesFactory
import org.jetbrains.kotlin.fir.caches.FirLazyValue
import org.jetbrains.kotlin.fir.declarations.FirRegularClass
import org.jetbrains.kotlin.fir.expressions.FirAnnotation
import org.jetbrains.kotlin.fir.extensions.ExperimentalTopLevelDeclarationsGenerationApi
import org.jetbrains.kotlin.fir.extensions.FirExtension
import org.jetbrains.kotlin.fir.plugin.createTopLevelClass
import org.jetbrains.kotlin.name.ClassId

@OptIn(ExperimentalTopLevelDeclarationsGenerationApi::class)
public class PendingTopLevelClass(
  public val classId: ClassId,
  public val key: GeneratedDeclarationKey,
  public val classKind: ClassKind,
  public val visibility: Visibility,
  public val annotations: FirLazyValue<List<FirAnnotation>>,
  cachesFactory: FirCachesFactory,
  firExtension: FirExtension,
) {

  public val generatedClass: FirLazyValue<FirRegularClass> = cachesFactory.createLazyValue {
    firExtension.createTopLevelClass(
      classId = classId,
      key = key,
      classKind = classKind,
    ) {
      visibility = this@PendingTopLevelClass.visibility
    }.apply {
      replaceAnnotations(this@PendingTopLevelClass.annotations.getValue())
    }
  }
}
