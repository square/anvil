package com.squareup.anvil.compiler.k2.fir.constructor.inject

import com.squareup.anvil.compiler.testing.CompilationMode
import com.squareup.anvil.compiler.testing.CompilationModeTest
import com.squareup.anvil.compiler.testing.TestNames
import com.squareup.anvil.compiler.testing.injectClass
import com.squareup.anvil.compiler.testing.injectClassFactory
import dagger.internal.Provider
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.TestFactory

class FirInjectConstructorFactoryGenerationExtensionTest : CompilationModeTest(
  CompilationMode.K2(useKapt = false),
) {
  @TestFactory
  fun `factory class is generated for @Inject annotation`() = testFactory {
    compile2(
      """
      ${TestNames.testPackage}
      class InjectClass @javax.inject.Inject constructor()
      """.trimIndent(),
    ) {
      injectClassFactory.shouldNotBeNull()
    }
  }

  @TestFactory
  fun `factory class is not generated if class is not annotated`() = testFactory {
    compile2(
      """
      ${TestNames.testPackage}
      class InjectClass()
      """.trimIndent(),
    ) {
      classGraph.allClassesAsMap[TestNames.injectClassFactory.asString()].shouldBeNull()
    }
  }

  @TestFactory
  fun `factory class creates source class with values from provider`() = testFactory {
    compile2(
      """
      ${TestNames.testPackage}
      class InjectClass @javax.inject.Inject constructor(
        val param0: String,
        val param1: Int,
      )
      """.trimIndent(),
    ) {
      val expectedString = "ExpectedString"
      val expectedInt = 77
      val factoryInstance = injectClassFactory.methods.first { it.name == "create" }
        .invoke(injectClassFactory, Provider { expectedString }, Provider { expectedInt })

      val injectClassInstance = injectClassFactory.getMethod("get").invoke(factoryInstance)

      injectClass.getMethod("getParam0").invoke(injectClassInstance).shouldBe(expectedString)
      injectClass.getMethod("getParam1").invoke(injectClassInstance).shouldBe(expectedInt)
    }
  }
}
