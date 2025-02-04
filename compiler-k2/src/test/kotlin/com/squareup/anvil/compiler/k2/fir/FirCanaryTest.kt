package com.squareup.anvil.compiler.k2.fir

import com.squareup.anvil.compiler.k2.fir.internal.Names
import com.squareup.anvil.compiler.testing.CompilationMode
import com.squareup.anvil.compiler.testing.CompilationModeTest
import com.squareup.anvil.compiler.testing.classgraph.getAnnotationInfo
import com.squareup.anvil.compiler.testing.compilation.compile2
import io.github.classgraph.AnnotationClassRef
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.TestFactory

class FirCanaryTest : CompilationModeTest(
  CompilationMode.K2(useKapt = false),
  CompilationMode.K2(useKapt = true),
) {

  @TestFactory
  fun `a merged component implements functions from merged supertypes`() = params
    .filter { (mode) -> mode.useKapt }
    .asTests {

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
      ) {

        val testComponent = classGraph.getClassInfo("foo.TestComponent")
          .shouldNotBeNull()

        val generatedComponentAnnotation = testComponent.getAnnotationInfo(Names.dagger.component)
          .shouldNotBeNull()

        val moduleClasses = generatedComponentAnnotation.parameterValues
          .getValue("modules")
          .let { it as Array<*> }
          .map { (it as AnnotationClassRef).name }
          .sorted()

        moduleClasses shouldBe listOf(
          "foo.ABindingModule",
          "foo.BBindingModule",
        )

        val daggerTestComponent = classGraph.getClassInfo("foo.DaggerTestComponent")
          .shouldNotBeNull()

        val testComponentImpl = daggerTestComponent.innerClasses
          .get("foo.DaggerTestComponent\$TestComponentImpl")

        testComponentImpl.methodInfo.map { it.name } shouldBe listOf("injectClass", "getB")
      }
    }
}
