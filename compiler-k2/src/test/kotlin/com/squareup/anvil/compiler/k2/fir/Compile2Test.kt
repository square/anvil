package com.squareup.anvil.compiler.k2.fir

import com.squareup.anvil.compiler.k2.fir.internal.Names
import com.squareup.anvil.compiler.testing.CompilationMode
import com.squareup.anvil.compiler.testing.CompilationModeTest
import com.squareup.anvil.compiler.testing.classgraph.getAnnotationInfo
import com.squareup.anvil.compiler.testing.compilation.compile2
import io.github.classgraph.AnnotationClassRef
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.jetbrains.kotlin.cli.common.ExitCode
import org.junit.jupiter.api.TestFactory

class Compile2Test : CompilationModeTest(
  CompilationMode.K2(useKapt = false),
  CompilationMode.K2(useKapt = true),
) {

  @TestFactory
  fun `compile2 version canary`() = testFactory {

    compile2(
      """
      package com.squareup.test
  
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

      val testComponent = classGraph.getClassInfo("com.squareup.test.TestComponent")
        .shouldNotBeNull()

      val generatedComponentAnnotation = testComponent.getAnnotationInfo(Names.dagger.component)
        .shouldNotBeNull()

      val moduleClasses = generatedComponentAnnotation.parameterValues
        .getValue("modules")
        .let { it as Array<*> }
        .map { (it as AnnotationClassRef).name }
        .sorted()

      moduleClasses shouldBe listOf(
        "com.squareup.test.ABindingModule",
        "com.squareup.test.BBindingModule",
      )
    }
  }

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

        val javaClass = classGraph.getClassInfo("com.squareup.test.JavaClass")

        javaClass.shouldNotBeNull()
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

        val javaClass = classGraph.getClassInfo("com.squareup.test.JavaClass")

        javaClass.shouldNotBeNull()
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

        val testPackage = classGraph.getPackageInfo("com.squareup.test")

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
}
