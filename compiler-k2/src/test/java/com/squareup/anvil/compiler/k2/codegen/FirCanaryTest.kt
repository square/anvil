package com.squareup.anvil.compiler.k2.codegen

import com.squareup.anvil.compiler.k2.ir.TestClass_Factory
import com.squareup.anvil.compiler.testing.CompilationMode
import com.squareup.anvil.compiler.testing.CompilationModeTest
import com.squareup.anvil.compiler.testing.compile2
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.TestFactory
import javax.inject.Provider
import kotlin.reflect.full.functions
import kotlin.reflect.full.primaryConstructor
import kotlin.test.Test

class FirCanaryTest : CompilationModeTest(
  CompilationMode.K2(useKapt = false),
  CompilationMode.K2(useKapt = true),
) {

  @TestFactory
  fun `compile2 version canary`() = testFactory {

    compile2(
      """
      package foo
  
      import com.squareup.anvil.annotations.ContributesTo
      import com.squareup.anvil.annotations.MergeComponent
      import dagger.Binds
      import dagger.Component
      import dagger.Module
      import dagger.Subcomponent
      import kotlin.reflect.KClass
      import javax.inject.Inject
  
      @Module
      @ContributesTo(Unit::class)
      interface BBindingModule {
        @Binds
        fun bindBImpl(bImpl: BImpl): B
      }
      
      @MergeComponent(Unit::class, modules = [ABindingModule::class], dependencies = [OtherComponent::class])
      interface TestComponent {
        val b: B
      }
  
      @ContributesTo(Unit::class)
      interface ComponentBase {
        fun injectClass(): InjectClass
      }
  
      @dagger.Component
      interface OtherComponent
  
      @Module
      interface ABindingModule {
        @Binds
        fun bindAImpl(aImpl: AImpl): A
      }
  
      interface A
      class AImpl @Inject constructor() : A
      
      interface B
      class BImpl @Inject constructor() : B
  
      class InjectClass @Inject constructor(val a: A, val b: B)
      """,
    ) shouldBe true
  }

  @TestFactory
  fun `top-level file generation`() = params
    .filter { (mode) -> !mode.useKapt }
    .asTests {

      compile2(
        """
        package foo
    
        import javax.inject.Inject

        class InjectClass @Inject constructor(val bananas: String)
        """.trimIndent(),
      ) {

        val factoryClass = classLoader.loadClass("foo.InjectClass_Factory")
        val factoryInstance = factoryClass.constructors.first().newInstance(Provider { "Bananas" })

        @Suppress("UNCHECKED_CAST")
        val bananasProvider = factoryClass.getMethod("getBananas")
          .invoke(factoryInstance)
          as Provider<String>

        bananasProvider.get() shouldBe "Bananas"

        // classLoader.loadClass("foo.InjectClass_Factory\$Companion")
      }
    }

  @Test
  fun `test_class_baseline`() {
    TestClass_Factory({"Hi"}).get().param0.shouldBe("Hi")
  }

  @TestFactory
  fun `generate companion object`() = params
    .filter { (mode) -> !mode.useKapt }
    .asTests {
      compile2(
        """
        package foo

        import javax.inject.Inject
        
        class TestClass @Inject constructor(
          val param0: String,
        )
        """.trimIndent(),
      ) {
        val testClass = classLoader.loadClass("foo.TestClass").kotlin
        val factoryClass = classLoader.loadClass("foo.TestClass_Factory").kotlin
        val factoryInstance =
          factoryClass.primaryConstructor!!.call(dagger.internal.Provider { "Bananas" })
        // val factoryInstance = factoryClass.constructors.first()
        // val factoryInstance = TestClass_Factory({"Hi"})
        factoryClass.functions.first { it.name == "get" }
          .call(factoryInstance)!!.javaClass == testClass

        // val companion = factoryClass.companionObjectInstance
        // val factoryCreateMethod = factoryClass.companionObject!!.functions.first { it.name == "create" }
        // val factoryInstance = factoryCreateMethod.call(companion, Provider { "Bananas" }, Provider { 0 })
        // factoryInstance!!::class.shouldBeEqual(factoryClass)
        // val factoryNewInstanceMethod = factoryClass.companionObject!!.functions.first { it.name == "newInstance" }
        // factoryNewInstanceMethod.call(companion, "Bananas", 0)!!::class.shouldBeEqual(testClass)
      }
    }

  @TestFactory
  fun `what is a create function`() = params
    .filter { (mode) -> !mode.useKapt }
    .asTests {

      compile2(
        """
        package foo
        class InjectClass(val param0: String)
        class InjectClass_Factory(
          private val param0: Provider<String>
        ) : com.`internal`.Dagger.Factory<InjectClass> {
          override fun `get`(): InjectClass = newInstance(param0.get())
          companion object {
            @JvmStatic
            fun create(param0: dagger.Provider<String>): InjectClass_Factory =
                InjectClass_Factory(param0)
            @JvmStatic
            fun newInstance(param0: String): InjectClass = InjectClass(param0)
          }
        }
        """.trimIndent(),
      ) {
      }
    }
}
