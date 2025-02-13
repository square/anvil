package com.squareup.anvil.compiler.k2.fir.constructor.inject

import com.squareup.anvil.compiler.testing.CompilationMode
import com.squareup.anvil.compiler.testing.CompilationModeTest
import com.squareup.anvil.compiler.testing.TestNames
import com.squareup.anvil.compiler.testing.reflect.getDeclaredFieldValue
import com.squareup.anvil.compiler.testing.reflect.injectClass_Factory
import com.squareup.anvil.compiler.testing.reflect.invokeGet
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.TestFactory
import javax.inject.Provider

class FirInjectConstructorFactoryGenerationExtensionTest : CompilationModeTest(
  CompilationMode.K2(useKapt = false),
) {
  @TestFactory
  fun `factory class is generated for @Inject annotation`() = testFactory {
    compile2(
      """
      package com.squareup.test

      class InjectClass @javax.inject.Inject constructor()
      """.trimIndent(),
    ) {
      classGraph shouldContainClass TestNames.injectClass_Factory
    }
  }

  @TestFactory
  fun `factory class is not generated if class is not annotated`() = testFactory {
    compile2(
      """
      package com.squareup.test

      class InjectClass
      """.trimIndent(),
    ) {

      scanResult shouldNotContainClass TestNames.injectClass_Factory
    }
  }

  @TestFactory
  fun `factory class creates source class with values from provider`() = testFactory {
    compile2(
      """
      package com.squareup.test

      class InjectClass @javax.inject.Inject constructor(
        val param0: String,
        val param1: Int,
      )
      """.trimIndent(),
    ) {
      val expectedString = "ExpectedString"
      val expectedInt = 77
      val factoryClass = classLoader.injectClass_Factory

      val factoryInstance = factoryClass.getMethod(
        "create",
        Provider::class.java,
        Provider::class.java,
      )
        .invoke(factoryClass, Provider { expectedString }, Provider { expectedInt })

      val injectClassInstance = factoryInstance.invokeGet()

      injectClassInstance.getDeclaredFieldValue("param0") shouldBe expectedString
      injectClassInstance.getDeclaredFieldValue("param1") shouldBe expectedInt
    }
  }
}
