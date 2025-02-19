package com.squareup.anvil.compiler.testing.compilation

import com.squareup.anvil.compiler.k2.fir.AnvilFirSupertypeGenerationExtension
import com.squareup.anvil.compiler.testing.CompilationMode
import com.squareup.anvil.compiler.testing.CompilationModeTest
import com.squareup.anvil.compiler.testing.TestNames
import com.squareup.anvil.compiler.testing.classgraph.classIds
import com.squareup.anvil.compiler.testing.classgraph.injectClass
import com.squareup.anvil.compiler.testing.classgraph.squareupTest
import io.kotest.matchers.shouldBe
import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.fir.declarations.FirClassLikeDeclaration
import org.jetbrains.kotlin.fir.declarations.utils.classId
import org.jetbrains.kotlin.fir.extensions.FirSupertypeGenerationExtension
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.FirResolvedTypeRef
import org.jetbrains.kotlin.fir.types.constructClassLikeType
import org.junit.jupiter.api.TestFactory

class Compile2Test : CompilationModeTest(
  CompilationMode.K2(useKapt = false),
  CompilationMode.K2(useKapt = true),
) {

  @TestFactory
  fun `java source files are compiled without any Kotlin files`() = params
    .filter { (mode) -> !mode.useKapt }
    .asTests {

      compile2(
        javaSources = listOf(
          //language=java
          """
          package com.squareup.test;
  
          public class JavaClass { }
          """.trimIndent(),
        ),
      ) {

        scanResult shouldContainClass TestNames.javaClass
      }
    }

  @TestFactory
  fun `java source files are compiled alongside Kotlin files`() = params
    .filter { (mode) -> !mode.useKapt }
    .asTests {

      compile2(
        """
        package com.squareup.test
  
        import javax.inject.Inject
  
        class InjectClass @Inject constructor(javaclass: JavaClass)
        """,
        javaSources = listOf(
          //language=java
          """
          package com.squareup.test;
  
          public class JavaClass { }
          """.trimIndent(),
        ),
      ) {

        scanResult shouldContainClass TestNames.javaClass
      }
    }

  @TestFactory
  fun `kapt-generated java source files are compiled`() = params
    .filter { (mode) -> mode.useKapt }
    .asTests {

      compile2(
        """
        package com.squareup.test

        import dagger.Component
        import javax.inject.Inject
    
        @Component
        interface TestComponent {
          val a: A
          fun injectClass(): InjectClass
        }
    
        class A @Inject constructor()
    
        class InjectClass @Inject constructor(val a: A) 
        """,
      ) {
        exitCode shouldBe ExitCode.OK

        val testPackage = scanResult.squareupTest

        testPackage.classInfoRecursive.names shouldBe setOf(
          "com.squareup.test.A",
          "com.squareup.test.A_Factory",
          "com.squareup.test.A_Factory\$InstanceHolder",
          "com.squareup.test.DaggerTestComponent",
          "com.squareup.test.DaggerTestComponent\$Builder",
          "com.squareup.test.DaggerTestComponent\$TestComponentImpl",
          "com.squareup.test.InjectClass",
          "com.squareup.test.InjectClass_Factory",
          "com.squareup.test.TestComponent",
        )
      }
    }

  @TestFactory
  fun `a custom additional generator is invoked`() = params
    .filter { (mode) -> !mode.useKapt }
    .asTests {

      compile2(
        """
        package com.squareup.test

        import javax.inject.Inject
    
        interface ParentInterface
    
        class InjectClass 
        """,
        firExtensions = listOf(
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
          },
        ),
      ) {

        scanResult.injectClass.interfaces.classIds() shouldBe listOf(TestNames.parentInterface)
      }
    }
}
