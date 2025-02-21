package com.squareup.anvil.compiler.k2.fir.contributions

import com.squareup.anvil.compiler.k2.fir.AnvilFirSupertypeGenerationExtension
import com.squareup.anvil.compiler.testing.CompilationModeTest
import com.squareup.anvil.compiler.testing.classId
import io.kotest.matchers.shouldBe
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.FirClassLikeDeclaration
import org.jetbrains.kotlin.fir.extensions.FirSupertypeGenerationExtension
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.FirResolvedTypeRef
import org.junit.jupiter.api.TestFactory
import kotlin.properties.Delegates

class AnvilFirScopedContributionProviderTest : CompilationModeTest() {

  @TestFactory
  fun `doing stuff`() = testFactory {

    var session: FirSession by Delegates.notNull()
    var typeResolveService: FirSupertypeGenerationExtension.TypeResolveService by Delegates.notNull()

    val extension = AnvilFirSupertypeGenerationExtension.Factory { ctx ->
      FirSupertypeGenerationExtension.Factory {
        object : AnvilFirSupertypeGenerationExtension(ctx, it) {

          override fun needTransformSupertypes(
            declaration: FirClassLikeDeclaration,
          ): Boolean = true

          override fun computeAdditionalSupertypes(
            classLikeDeclaration: FirClassLikeDeclaration,
            resolvedSupertypes: List<FirResolvedTypeRef>,
            typeResolver: TypeResolveService,
          ): List<ConeKotlinType> {
            session = it
            typeResolveService = typeResolver
            return listOf()
          }
        }
      }
    }

    compile2(
      """
      package com.squareup.test

      import com.squareup.anvil.annotations.ContributesTo
      import javax.inject.Inject

      @ContributesTo(Unit::class)
      class InjectClass @Inject constructor() : java.io.Serializable
      """.trimIndent(),
      firExtensions = listOf(extension),
    ) {
      val provider = session.anvilFirScopedContributionProvider

      provider.getContributedThingsForScope(Unit::class.classId) shouldBe emptyList()
    }
  }
}
