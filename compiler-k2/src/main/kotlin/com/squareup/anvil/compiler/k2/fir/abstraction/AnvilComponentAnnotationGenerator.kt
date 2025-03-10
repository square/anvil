package com.squareup.anvil.compiler.k2.fir.abstraction

import com.squareup.anvil.compiler.k2.fir.AnvilFirContext
import org.jetbrains.kotlin.GeneratedDeclarationKey
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.caches.FirCachesFactory
import org.jetbrains.kotlin.fir.extensions.FirExtension
import org.jetbrains.kotlin.name.ClassId

internal class AnvilComponentAnnotationGenerator(
  private val anvilFirContext: AnvilFirContext,
  private val session: FirSession,
) {
  // fun doThings(
  //   classLikeDeclaration: FirClassLikeDeclaration,
  //   typeResolveService: FirSupertypeGenerationExtension.TypeResolveService,
  // ): PendingAnnotation {
  // }
}

internal class PendingAnnotation(
  val classId: ClassId,
  val key: GeneratedDeclarationKey,
  cachesFactory: FirCachesFactory,
  firExtension: FirExtension,
)
