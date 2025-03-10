package com.squareup.anvil.compiler.k2.fir.abstraction

import com.squareup.anvil.compiler.k2.fir.contributions.wrapInSyntheticFile
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
internal class PendingTopLevelClass(
  val classId: ClassId,
  val key: GeneratedDeclarationKey,
  val classKind: ClassKind,
  val visibility: Visibility,
  val annotations: FirLazyValue<List<FirAnnotation>>,
  cachesFactory: FirCachesFactory,
  firExtension: FirExtension,
) {

  val generatedClass: FirLazyValue<FirRegularClass> = cachesFactory.createLazyValue {
    firExtension.createTopLevelClass(
      classId = classId,
      key = key,
      classKind = classKind,
    ) {
      visibility = this@PendingTopLevelClass.visibility
    }.apply {
      replaceAnnotations(this@PendingTopLevelClass.annotations.getValue())
    }
      .wrapInSyntheticFile(firExtension.session)
  }
}
