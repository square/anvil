package com.squareup.anvil.compiler.codegen

import com.google.common.truth.Truth.assertThat
import com.squareup.anvil.annotations.MergeComponent
import com.squareup.anvil.annotations.MergeSubcomponent
import com.squareup.anvil.annotations.compat.MergeModules
import com.squareup.anvil.compiler.anvilModule
import com.squareup.anvil.compiler.anyQualifier
import com.squareup.anvil.compiler.compile
import com.squareup.anvil.compiler.componentInterface
import com.squareup.anvil.compiler.contributingInterface
import com.squareup.anvil.compiler.internal.testing.isAbstract
import com.squareup.anvil.compiler.isFullTestRun
import com.squareup.anvil.compiler.parentInterface
import com.squareup.anvil.compiler.subcomponentInterface
import dagger.Binds
import dagger.Provides
import dagger.multibindings.IntoSet
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters
import javax.inject.Named
import kotlin.reflect.KClass

@RunWith(Parameterized::class)
class BindingModuleQualifierTest(
  annotationClass: KClass<*>
) {

  private val annotation = "@${annotationClass.simpleName}"
  private val import = "import ${annotationClass.java.canonicalName}"

  companion object {
    @Parameters(name = "{0}")
    @JvmStatic fun annotationClasses(): Collection<Any> {
      return buildList {
        add(MergeComponent::class)
        if (isFullTestRun()) {
          add(MergeSubcomponent::class)
          add(MergeModules::class)
        }
      }
    }
  }

  @Test fun `the Dagger binding method has a qualifier without parameter`() {
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
      """
    ) {
      val bindingMethod = componentInterface.anvilModule.declaredMethods.single()

      with(bindingMethod) {
        assertThat(returnType).isEqualTo(parentInterface)
        assertThat(parameterTypes.toList()).containsExactly(contributingInterface)
        assertThat(isAbstract).isTrue()

        assertThat(annotations.map { it.annotationClass })
          .containsExactly(Binds::class, anyQualifier.kotlin)
      }
    }
  }

  @Test fun `the Dagger multibinding method has a qualifier without parameter`() {
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
      """
    ) {
      val bindingMethod = componentInterface.anvilModule.declaredMethods.single()

      with(bindingMethod) {
        assertThat(returnType).isEqualTo(parentInterface)
        assertThat(parameterTypes.toList()).containsExactly(contributingInterface)
        assertThat(isAbstract).isTrue()

        assertThat(annotations.map { it.annotationClass })
          .containsExactly(Binds::class, IntoSet::class, anyQualifier.kotlin)
      }
    }
  }

  @Test fun `the Dagger provider method for an object has a qualifier`() {
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
      """
    ) {
      val bindingMethod = componentInterface.anvilModule.declaredMethods.single()

      with(bindingMethod) {
        assertThat(returnType).isEqualTo(parentInterface)
        assertThat(parameterTypes.toList()).isEmpty()
        assertThat(isAbstract).isFalse()

        assertThat(annotations.map { it.annotationClass })
          .containsExactly(Provides::class, anyQualifier.kotlin)
      }
    }
  }

  @Test fun `the Dagger multibind provider method for an object has a qualifier`() {
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
      """
    ) {
      val bindingMethod = componentInterface.anvilModule.declaredMethods.single()

      with(bindingMethod) {
        assertThat(returnType).isEqualTo(parentInterface)
        assertThat(parameterTypes.toList()).isEmpty()
        assertThat(isAbstract).isFalse()

        assertThat(annotations.map { it.annotationClass })
          .containsExactly(Provides::class, IntoSet::class, anyQualifier.kotlin)
      }
    }
  }

  @Test fun `the Dagger binding method has a qualifier with string value`() {
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
      """
    ) {
      val bindingMethod = componentInterface.anvilModule.declaredMethods.single()

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

  @Test fun `the Dagger binding method has a qualifier with a class value`() {
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
      """
    ) {
      val bindingMethod = componentInterface.anvilModule.declaredMethods.single()

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

  @Test fun `the Dagger binding method has a qualifier with a value`() {
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
      """
    ) {
      val bindingMethod = componentInterface.anvilModule.declaredMethods.single()

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

  @Test fun `the Dagger binding method has a qualifier with multiple arguments`() {
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
      """
    ) {
      val bindingMethod = componentInterface.anvilModule.declaredMethods.single()

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

  @Test fun `the Dagger binding method has no other annotation that is not a qualifier`() {
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
      """
    ) {
      val bindingMethod = componentInterface.anvilModule.declaredMethods.single()

      with(bindingMethod) {
        assertThat(returnType).isEqualTo(parentInterface)
        assertThat(parameterTypes.toList()).containsExactly(contributingInterface)
        assertThat(isAbstract).isTrue()

        assertThat(annotations.map { it.annotationClass }).containsExactly(Binds::class)
      }
    }
  }

  @Test fun `the Dagger binding method has no qualifier when disabled`() {
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
      """
    ) {
      val bindingMethod = componentInterface.anvilModule.declaredMethods.single()
      val annotations = bindingMethod.annotations.map { it.annotationClass }

      assertThat(annotations).doesNotContain(anyQualifier.kotlin)
      assertThat(annotations).contains(Binds::class)
    }
  }

  @Test fun `the Dagger multibinding method has no qualifier when disabled`() {
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
      """
    ) {
      val bindingMethod = componentInterface.anvilModule.declaredMethods.single()
      val annotations = bindingMethod.annotations.map { it.annotationClass }

      assertThat(annotations).doesNotContain(anyQualifier.kotlin)
      assertThat(annotations).containsAtLeast(Binds::class, IntoSet::class)
    }
  }

  @Test fun `the Dagger binding method has a qualifier for multiple contributions`() {
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
      """
    ) {
      with(componentInterface.anvilModule.declaredMethods.single()) {
        assertThat(returnType).isEqualTo(parentInterface)
        assertThat(parameterTypes.toList()).containsExactly(contributingInterface)
        assertThat(isAbstract).isTrue()

        assertThat(annotations.map { it.annotationClass })
          .containsExactly(Binds::class, anyQualifier.kotlin)
      }

      with(subcomponentInterface.anvilModule.declaredMethods.single()) {
        assertThat(returnType).isEqualTo(parentInterface)
        assertThat(parameterTypes.toList()).containsExactly(contributingInterface)
        assertThat(isAbstract).isTrue()

        assertThat(annotations.map { it.annotationClass })
          .containsExactly(Binds::class, anyQualifier.kotlin)
      }
    }
  }
}
