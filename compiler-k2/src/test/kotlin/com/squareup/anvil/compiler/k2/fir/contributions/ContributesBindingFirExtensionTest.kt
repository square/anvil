package com.squareup.anvil.compiler.k2.fir.contributions

import com.squareup.anvil.compiler.k2.utils.names.ClassIds
import com.squareup.anvil.compiler.testing.CompilationModeTest
import com.squareup.anvil.compiler.testing.classgraph.getAnnotationInfo
import com.squareup.anvil.compiler.testing.classgraph.moduleNames
import com.squareup.anvil.compiler.testing.reflect.contributesToAnnotation
import com.squareup.anvil.compiler.testing.reflect.contributingObject
import com.squareup.anvil.compiler.testing.reflect.generatedBindingModule
import com.squareup.anvil.compiler.testing.reflect.isAbstract
import com.squareup.anvil.compiler.testing.reflect.parentInterface
import dagger.Binds
import dagger.Module
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.TestFactory

class ContributesBindingFirExtensionTest : CompilationModeTest(
  MODE_DEFAULTS.filter { it.isK2 && !it.useKapt },
) {

  @TestFactory
  fun `generated class contributes to expected scope`() = testFactory {
    compile2(
      """
      package com.squareup.test.other

      interface AppScope
      """.trimIndent(),
      """
      package com.squareup.test

      import com.squareup.anvil.annotations.ContributesBinding
      import com.squareup.test.other.AppScope
      import javax.inject.Inject
      
      interface ParentInterface

      @ContributesBinding(
        scope = AppScope::class, 
        boundType = ParentInterface::class
      )
      object ContributingObject : ParentInterface
      """.trimIndent(),
    ) {
      val contributesToScope =
        classLoader.contributingObject.generatedBindingModule.contributesToAnnotation.scope

      contributesToScope.java.name shouldBe "com.squareup.test.other.AppScope"
    }
  }

  @TestFactory
  fun `generated class is a dagger module`() = testFactory {
    compile2(
      """
      package com.squareup.test

      import com.squareup.anvil.annotations.ContributesBinding
      import javax.inject.Inject
      
      interface ParentInterface

      @ContributesBinding(
        scope = Int::class, 
        boundType = ParentInterface::class
      )
      object ContributingObject : ParentInterface
      """.trimIndent(),
    ) {
      classLoader.contributingObject.generatedBindingModule
        .isAnnotationPresent(Module::class.java) shouldBe true
    }
  }

  @TestFactory
  fun `generated class has binds method`() = testFactory {
    compile2(
      """
      package com.squareup.test

      import com.squareup.anvil.annotations.ContributesBinding
      import javax.inject.Inject
      
      interface ParentInterface

      @ContributesBinding(
        scope = Int::class, 
        boundType = ParentInterface::class
      )
      object ContributingObject : ParentInterface
      """.trimIndent(),
    ) {
      val bindingMethod =
        classLoader.contributingObject.generatedBindingModule.declaredMethods.single()

      bindingMethod.returnType shouldBe classLoader.parentInterface
      bindingMethod.parameters.map { it.type } shouldBe listOf(classLoader.contributingObject)
      bindingMethod.isAbstract shouldBe true
      bindingMethod.isAnnotationPresent(Binds::class.java) shouldBe true
    }
  }

  @TestFactory
  fun `generated dagger binding module is picked up by annotation merging`() = testFactory {
    compile2(
      """
      package com.squareup.test
      
      import com.squareup.anvil.annotations.ContributesBinding
      import com.squareup.anvil.annotations.MergeComponent
      import dagger.Component
      
      interface ParentInterface
      
      @ContributesBinding(
        scope = Any::class, 
        boundType = ParentInterface::class
      )
      object ContributingObject : ParentInterface
      
      @MergeComponent(scope = Any::class)
      interface TestComponent {
        fun testClass(): ParentInterface 
      
        @Component.Factory
        interface Factory {
          fun create(): TestComponent
        }
      }
      """.trimIndent(),
    ) {
      val bindingModule = classLoader.contributingObject.generatedBindingModule.name
      val testComponent = scanResult.getClassInfo("com.squareup.test.TestComponent")
      val generatedComponentAnnotation = testComponent.getAnnotationInfo(ClassIds.daggerComponent)

      generatedComponentAnnotation.moduleNames shouldBe listOf(
        bindingModule,
      )
    }
  }

  @TestFactory
  fun `generated dagger binding module with different scope is not picked up by annotation merging`() =
    testFactory {
      compile2(
        """
        package com.squareup.test
        
        import com.squareup.anvil.annotations.ContributesBinding
        import com.squareup.anvil.annotations.MergeComponent
        import dagger.Component
        
        interface ParentInterface
        
        @ContributesBinding(
          scope = Int::class, 
          boundType = ParentInterface::class
        )
        object ContributingObject : ParentInterface
        
        @MergeComponent(scope = Any::class)
        interface TestComponent {
          fun testClass(): ParentInterface 
        
          @Component.Factory
          interface Factory {
            fun create(): TestComponent
          }
        }
        """.trimIndent(),
      ) {
        val testComponent = scanResult.getClassInfo("com.squareup.test.TestComponent")
        val generatedComponentAnnotation = testComponent.getAnnotationInfo(ClassIds.daggerComponent)

        generatedComponentAnnotation.moduleNames shouldBe emptyList()
      }
    }
}
