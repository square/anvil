package com.squareup.anvil.compiler.testing.compilation

import com.squareup.anvil.compiler.k2.fir.AnvilFirSupertypeGenerationExtension
import com.squareup.anvil.compiler.testing.CompilationMode.K2
import com.squareup.anvil.compiler.testing.CompilationModeTest
import com.squareup.anvil.compiler.testing.TestNames
import com.squareup.anvil.compiler.testing.classgraph.fqNames
import com.squareup.anvil.compiler.testing.classgraph.shouldContainClass
import com.squareup.anvil.compiler.testing.injectClassInfo
import io.kotest.matchers.shouldBe
import org.jetbrains.kotlin.fir.declarations.FirClassLikeDeclaration
import org.jetbrains.kotlin.fir.declarations.utils.classId
import org.jetbrains.kotlin.fir.extensions.FirSupertypeGenerationExtension
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.FirResolvedTypeRef
import org.jetbrains.kotlin.fir.types.constructClassLikeType
import org.jetbrains.kotlin.name.ClassId
import org.junit.jupiter.api.Test

class Compile2Sample : CompilationModeTest(K2(useKapt = false), K2(useKapt = true)) {

  @Test
  fun compile_source_strings() = test(K2(useKapt = false)) {

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

      scanResult.shouldContainClass("com.squareup.test.KotlinClass")
      scanResult.shouldContainClass("com.squareup.test.JavaClass")
    }
  }

  @Test
  fun pass_a_custom_generator() = test(K2(useKapt = false)) {

    compile2(
      """
        package com.squareup.test

        import javax.inject.Inject
    
        interface ParentInterface
    
        class InjectClass
        """,
      firExtensions = listOf(myCustomGenerator()),
    ) {

      injectClassInfo.interfaces.fqNames() shouldBe listOf(TestNames.parentInterface)
    }
  }

  private fun myCustomGenerator(): AnvilFirSupertypeGenerationExtension.Factory =
    AnvilFirSupertypeGenerationExtension.Factory { ctx ->
      FirSupertypeGenerationExtension.Factory { session ->

        object : AnvilFirSupertypeGenerationExtension(ctx, session) {
          override fun needTransformSupertypes(
            declaration: FirClassLikeDeclaration,
          ): Boolean = declaration.classId.asSingleFqName() == TestNames.injectClass

          override fun computeAdditionalSupertypes(
            classLikeDeclaration: FirClassLikeDeclaration,
            resolvedSupertypes: List<FirResolvedTypeRef>,
            typeResolver: TypeResolveService,
          ): List<ConeKotlinType> = listOf(
            ClassId.topLevel(TestNames.parentInterface).constructClassLikeType(),
          )
        }
      }
    }
}
