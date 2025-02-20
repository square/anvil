package com.squareup.anvil.compiler.testing.compilation

import com.squareup.anvil.compiler.k2.fir.AnvilFirSupertypeGenerationExtension
import com.squareup.anvil.compiler.testing.CompilationModeTest
import com.squareup.anvil.compiler.testing.TestNames
import com.squareup.anvil.compiler.testing.classgraph.injectClass
import io.kotest.matchers.shouldBe
import org.jetbrains.kotlin.fir.declarations.FirClassLikeDeclaration
import org.jetbrains.kotlin.fir.declarations.utils.classId
import org.jetbrains.kotlin.fir.extensions.FirSupertypeGenerationExtension
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.FirResolvedTypeRef
import org.jetbrains.kotlin.fir.types.constructClassLikeType
import org.junit.jupiter.api.TestFactory

class Compile2Sample : CompilationModeTest() {

  @TestFactory
  fun compile_source_strings() = testFactory {

    compile2(
      """
      package com.squareup.test
      
      class KotlinClass(javaClass: JavaClass)
      """.trimIndent(),
      javaSources = listOf(
        //language=java
        """
        package com.squareup.test;

        public class JavaClass { }
        """.trimIndent(),
      ),
    ) {

      scanResult shouldContainClass "com.squareup.test.KotlinClass"
      scanResult shouldContainClass "com.squareup.test.JavaClass"
    }
  }

  @TestFactory
  fun pass_a_custom_generator() = params.filter { it.isK2 }.asTests {

    compile2(
      """
        package com.squareup.test

        import javax.inject.Inject
    
        interface ParentInterface
    
        class InjectClass
        """,
      firExtensions = listOf(myCustomGenerator()),
    ) {

      scanResult.injectClass.interfaces.names shouldBe listOf("com.squareup.test.ParentInterface")
    }
  }

  private fun myCustomGenerator(): AnvilFirSupertypeGenerationExtension.Factory =
    AnvilFirSupertypeGenerationExtension.Factory { ctx ->
      FirSupertypeGenerationExtension.Factory { session ->

        object : AnvilFirSupertypeGenerationExtension(ctx, session) {
          override fun needTransformSupertypes(
            declaration: FirClassLikeDeclaration,
          ): Boolean = declaration.classId == TestNames.injectClass

          override fun computeAdditionalSupertypes(
            classLikeDeclaration: FirClassLikeDeclaration,
            resolvedSupertypes: List<FirResolvedTypeRef>,
            typeResolver: TypeResolveService,
          ): List<ConeKotlinType> = listOf(
            TestNames.parentInterface.constructClassLikeType(),
          )
        }
      }
    }
}
