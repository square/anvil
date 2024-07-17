package com.squareup.anvil.compiler.codegen

import com.google.common.truth.Truth.assertThat
import com.squareup.anvil.annotations.MergeComponent
import com.squareup.anvil.annotations.MergeSubcomponent
import com.squareup.anvil.annotations.compat.MergeInterfaces
import com.squareup.anvil.annotations.compat.MergeModules
import com.squareup.anvil.compiler.anyQualifier
import com.squareup.anvil.compiler.componentInterface
import com.squareup.anvil.compiler.contributingInterface
import com.squareup.anvil.compiler.generatedBindingModule
import com.squareup.anvil.compiler.generatedMultiBindingModule
import com.squareup.anvil.compiler.internal.testing.isAbstract
import com.squareup.anvil.compiler.mergedModules
import com.squareup.anvil.compiler.parentInterface
import com.squareup.anvil.compiler.subcomponentInterface
import com.squareup.anvil.compiler.testing.AnvilAnnotationsTest
import dagger.Binds
import dagger.Provides
import dagger.multibindings.IntoSet
import org.junit.jupiter.api.TestFactory
import javax.inject.Named

class BindingModuleQualifierTest : AnvilAnnotationsTest(
  MergeComponent::class,
  MergeSubcomponent::class,
  MergeModules::class,
) {

  @TestFactory fun `the Dagger binding method has a qualifier without parameter`() = testFactory {
    compile(
      """
      package com.squareup.test
      
      import com.squareup.anvil.annotations.ContributesBinding
      import javax.inject.Qualifier
      $import
      
      @Qualifier
      annotation class AnyQualifier

      interface ParentInterface
      
      @ContributesBinding(Any::class)
      @AnyQualifier
      interface ContributingInterface : ParentInterface
      
      $annotation(Any::class)
      interface ComponentInterface
      """,
    ) {
      val bindingMethod = contributingInterface.generatedBindingModule.declaredMethods.single()

      with(bindingMethod) {
        assertThat(returnType).isEqualTo(parentInterface)
        assertThat(parameterTypes.toList()).containsExactly(contributingInterface)
        assertThat(isAbstract).isTrue()

        assertThat(annotations.map { it.annotationClass })
          .containsExactly(Binds::class, anyQualifier.kotlin)
      }
    }
  }

  @TestFactory fun `the Dagger multibinding method has a qualifier without parameter`() =
    testFactory {
      compile(
        """
        package com.squareup.test
        
        import com.squareup.anvil.annotations.ContributesMultibinding
        import javax.inject.Qualifier
        $import
        
        @Qualifier
        annotation class AnyQualifier
  
        interface ParentInterface
        
        @ContributesMultibinding(Any::class)
        @AnyQualifier
        interface ContributingInterface : ParentInterface
        
        $annotation(Any::class)
        interface ComponentInterface
        """,
      ) {
        val bindingMethod =
          contributingInterface.generatedMultiBindingModule.declaredMethods.single()

        with(bindingMethod) {
          assertThat(returnType).isEqualTo(parentInterface)
          assertThat(parameterTypes.toList()).containsExactly(contributingInterface)
          assertThat(isAbstract).isTrue()

          assertThat(annotations.map { it.annotationClass })
            .containsExactly(Binds::class, IntoSet::class, anyQualifier.kotlin)
        }
      }
    }

  @TestFactory fun `the Dagger provider method for an object has a qualifier`() = testFactory {
    compile(
      """
      package com.squareup.test
      
      import com.squareup.anvil.annotations.ContributesBinding
      import javax.inject.Qualifier
      $import
      
      @Qualifier
      annotation class AnyQualifier

      interface ParentInterface
      
      @ContributesBinding(Any::class)
      @AnyQualifier
      object ContributingInterface : ParentInterface
      
      $annotation(Any::class)
      interface ComponentInterface
      """,
    ) {
      val bindingMethod = contributingInterface.generatedBindingModule.declaredMethods.single()

      with(bindingMethod) {
        assertThat(returnType).isEqualTo(parentInterface)
        assertThat(parameterTypes.toList()).isEmpty()
        assertThat(isAbstract).isFalse()

        assertThat(annotations.map { it.annotationClass })
          .containsExactly(Provides::class, anyQualifier.kotlin)
      }
    }
  }

  @TestFactory fun `the Dagger multibind provider method for an object has a qualifier`() =
    testFactory {
      compile(
        """
      package com.squareup.test
      
      import com.squareup.anvil.annotations.ContributesMultibinding
      import javax.inject.Qualifier
      $import
      
      @Qualifier
      annotation class AnyQualifier

      interface ParentInterface
      
      @ContributesMultibinding(Any::class)
      @AnyQualifier
      object ContributingInterface : ParentInterface
      
      $annotation(Any::class)
      interface ComponentInterface
      """,
      ) {
        val bindingMethod =
          contributingInterface.generatedMultiBindingModule.declaredMethods.single()

        with(bindingMethod) {
          assertThat(returnType).isEqualTo(parentInterface)
          assertThat(parameterTypes.toList()).isEmpty()
          assertThat(isAbstract).isFalse()

          assertThat(annotations.map { it.annotationClass })
            .containsExactly(Provides::class, IntoSet::class, anyQualifier.kotlin)
        }
      }
    }

  @TestFactory fun `the Dagger binding method has a qualifier with string value`() = testFactory {
    compile(
      """
      package com.squareup.test
      
      import com.squareup.anvil.annotations.ContributesBinding
      import javax.inject.Named
      $import

      interface ParentInterface
      
      @ContributesBinding(Any::class)
      @Named("abc")
      interface ContributingInterface : ParentInterface
      
      $annotation(Any::class)
      interface ComponentInterface
      """,
    ) {
      val bindingMethod = contributingInterface.generatedBindingModule.declaredMethods.single()

      with(bindingMethod) {
        assertThat(returnType).isEqualTo(parentInterface)
        assertThat(parameterTypes.toList()).containsExactly(contributingInterface)
        assertThat(isAbstract).isTrue()
        assertThat(isAnnotationPresent(Binds::class.java)).isTrue()
        assertThat(isAnnotationPresent(Named::class.java)).isTrue()

        val namedAnnotation = getAnnotationsByType(Named::class.java).single()
        assertThat(namedAnnotation.value).isEqualTo("abc")
      }
    }
  }

  @TestFactory fun `the Dagger binding method has a qualifier with a class value`() = testFactory {
    compile(
      """
      package com.squareup.test
      
      import com.squareup.anvil.annotations.ContributesBinding
      import javax.inject.Qualifier
      import kotlin.reflect.KClass
      $import
      
      @Qualifier
      annotation class AnyQualifier(
        val abc: KClass<*>
      )

      interface ParentInterface
      
      @ContributesBinding(Any::class)
      @AnyQualifier(abc = Int::class)
      interface ContributingInterface : ParentInterface
      
      $annotation(Any::class)
      interface ComponentInterface
      """,
    ) {
      val bindingMethod = contributingInterface.generatedBindingModule.declaredMethods.single()

      with(bindingMethod) {
        assertThat(returnType).isEqualTo(parentInterface)
        assertThat(parameterTypes.toList()).containsExactly(contributingInterface)
        assertThat(isAbstract).isTrue()

        assertThat(annotations.map { it.annotationClass })
          .containsExactly(Binds::class, anyQualifier.kotlin)

        val qualifierAnnotation = annotations.single { it.annotationClass == anyQualifier.kotlin }
        assertThat(qualifierAnnotation.toString())
          .isEqualTo("@com.squareup.test.AnyQualifier(abc=int.class)")
      }
    }
  }

  @TestFactory fun `the Dagger binding method has a qualifier with a value`() = testFactory {
    compile(
      """
      package com.squareup.test
      
      import com.squareup.anvil.annotations.ContributesBinding
      import com.squareup.test.AnyQualifier.Values.ABC
      import javax.inject.Qualifier
      import kotlin.reflect.KClass
      $import
      
      @Qualifier
      annotation class AnyQualifier(
        val abc: Values
      ) {
        enum class Values {
          ABC
        }
      }

      interface ParentInterface
      
      @ContributesBinding(Any::class)
      @AnyQualifier(ABC)
      interface ContributingInterface : ParentInterface
      
      $annotation(Any::class)
      interface ComponentInterface
      """,
    ) {
      val bindingMethod = contributingInterface.generatedBindingModule.declaredMethods.single()

      with(bindingMethod) {
        assertThat(returnType).isEqualTo(parentInterface)
        assertThat(parameterTypes.toList()).containsExactly(contributingInterface)
        assertThat(isAbstract).isTrue()

        assertThat(annotations.map { it.annotationClass })
          .containsExactly(Binds::class, anyQualifier.kotlin)

        val qualifierAnnotation = annotations.single { it.annotationClass == anyQualifier.kotlin }
        assertThat(qualifierAnnotation.toString())
          .isEqualTo("@com.squareup.test.AnyQualifier(abc=ABC)")
      }
    }
  }

  @TestFactory fun `the Dagger binding method has a qualifier with multiple arguments`() =
    testFactory {
      compile(
        """
      package com.squareup.test
      
      import com.squareup.anvil.annotations.ContributesBinding
      import javax.inject.Qualifier
      import kotlin.reflect.KClass
      $import
      
      @Qualifier
      annotation class AnyQualifier(
        val abc: KClass<*>,
        val def: Int
      )

      interface ParentInterface
      
      @ContributesBinding(Any::class)
      @AnyQualifier(abc = String::class, def = 1)
      interface ContributingInterface : ParentInterface
      
      $annotation(Any::class)
      interface ComponentInterface
      """,
      ) {
        val bindingMethod = contributingInterface.generatedBindingModule.declaredMethods.single()

        with(bindingMethod) {
          assertThat(returnType).isEqualTo(parentInterface)
          assertThat(parameterTypes.toList()).containsExactly(contributingInterface)
          assertThat(isAbstract).isTrue()

          assertThat(annotations.map { it.annotationClass })
            .containsExactly(Binds::class, anyQualifier.kotlin)

          val qualifierAnnotation = annotations.single { it.annotationClass == anyQualifier.kotlin }
          assertThat(qualifierAnnotation.toString())
            .isEqualTo("@com.squareup.test.AnyQualifier(abc=java.lang.String.class, def=1)")
        }
      }
    }

  @TestFactory fun `the Dagger binding method has no other annotation that is not a qualifier`() =
    testFactory {
      compile(
        """
      package com.squareup.test
      
      import com.squareup.anvil.annotations.ContributesBinding
      $import
      
      annotation class AnyQualifier

      interface ParentInterface
      
      @ContributesBinding(Any::class)
      @AnyQualifier
      interface ContributingInterface : ParentInterface
      
      $annotation(Any::class)
      interface ComponentInterface
      """,
      ) {
        val bindingMethod = contributingInterface.generatedBindingModule.declaredMethods.single()

        with(bindingMethod) {
          assertThat(returnType).isEqualTo(parentInterface)
          assertThat(parameterTypes.toList()).containsExactly(contributingInterface)
          assertThat(isAbstract).isTrue()

          assertThat(annotations.map { it.annotationClass }).containsExactly(Binds::class)
        }
      }
    }

  @TestFactory fun `the Dagger binding method has no qualifier when disabled`() = testFactory {
    compile(
      """
      package com.squareup.test
      
      import com.squareup.anvil.annotations.ContributesBinding
      import javax.inject.Qualifier
      $import
      
      @Qualifier
      annotation class AnyQualifier

      interface ParentInterface
      
      @ContributesBinding(Any::class, ignoreQualifier = true)
      @AnyQualifier
      interface ContributingInterface : ParentInterface
      
      $annotation(Any::class)
      interface ComponentInterface
      """,
    ) {
      val bindingMethod = contributingInterface.generatedBindingModule.declaredMethods.single()
      val annotations = bindingMethod.annotations.map { it.annotationClass }

      assertThat(annotations).doesNotContain(anyQualifier.kotlin)
      assertThat(annotations).contains(Binds::class)
    }
  }

  @TestFactory fun `the Dagger multibinding method has no qualifier when disabled`() = testFactory {
    compile(
      """
      package com.squareup.test
      
      import com.squareup.anvil.annotations.ContributesMultibinding
      import javax.inject.Qualifier
      $import
      
      @Qualifier
      annotation class AnyQualifier

      interface ParentInterface
      
      @ContributesMultibinding(Any::class, ignoreQualifier = true)
      @AnyQualifier
      interface ContributingInterface : ParentInterface
      
      $annotation(Any::class)
      interface ComponentInterface
      """,
    ) {
      val bindingMethod = contributingInterface.generatedMultiBindingModule.declaredMethods.single()
      val annotations = bindingMethod.annotations.map { it.annotationClass }

      assertThat(annotations).doesNotContain(anyQualifier.kotlin)
      assertThat(annotations).containsAtLeast(Binds::class, IntoSet::class)
    }
  }

  @TestFactory fun `the Dagger binding method has a qualifier for multiple contributions`() =
    params
      // MergeInterfaces doesn't make a component or subcomponent, so it's not applicable here.
      .filterNot { it.a1 == MergeInterfaces::class }
      .asTests {
        compile(
          """ 
          package com.squareup.test
          import com.squareup.anvil.annotations.ContributesBinding
          import javax.inject.Qualifier
          $import
          
          @Qualifier
          annotation class AnyQualifier
          
          interface ParentInterface
          
          @ContributesBinding(Any::class)
          @ContributesBinding(Unit::class)
          @AnyQualifier
          interface ContributingInterface : ParentInterface
          
          $annotation(Any::class)
          interface ComponentInterface
          
          $annotation(Unit::class)
          interface SubcomponentInterface
          """,
        ) {
          with(
            componentInterface.mergedModules(annotationClass)
              .single().java.declaredMethods.single(),
          ) {
            assertThat(returnType).isEqualTo(parentInterface)
            assertThat(parameterTypes.toList()).containsExactly(contributingInterface)
            assertThat(isAbstract).isTrue()

            assertThat(annotations.map { it.annotationClass })
              .containsExactly(Binds::class, anyQualifier.kotlin)
          }

          with(
            subcomponentInterface.mergedModules(annotationClass)
              .single().java.declaredMethods.single(),
          ) {
            assertThat(returnType).isEqualTo(parentInterface)
            assertThat(parameterTypes.toList()).containsExactly(contributingInterface)
            assertThat(isAbstract).isTrue()

            assertThat(annotations.map { it.annotationClass })
              .containsExactly(Binds::class, anyQualifier.kotlin)
          }
        }
      }
}
