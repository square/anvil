package com.squareup.anvil.compiler

import com.google.common.truth.Truth.assertThat
import com.squareup.anvil.annotations.MergeComponent
import com.squareup.anvil.annotations.MergeSubcomponent
import com.squareup.anvil.annotations.compat.MergeInterfaces
import com.squareup.anvil.compiler.internal.testing.extends
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters
import kotlin.reflect.KClass

@RunWith(Parameterized::class)
class InterfaceMergerRepeatableTest(
  private val annotationClass: KClass<*>
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
          add(MergeInterfaces::class)
        }
      }
    }
  }

  @Test fun `duplicate scopes are an error`() {
    compile(
      """
      package com.squareup.test
      
      $import
      
      $annotation(Any::class)
      $annotation(Any::class)
      interface ComponentInterface
      """
    ) {
      assertThat(exitCode).isError()
      assertThat(messages).contains(
        "com.squareup.test.ComponentInterface merges multiple times to the same scope: [Any]. " +
          "Merging multiple times to the same scope is forbidden and all scopes must be distinct."
      )
    }
  }

  @Test fun `different kind of merge annotations are forbidden`() {
    assumeMergeComponent(annotationClass)

    compile(
      """
      package com.squareup.test
      
      import com.squareup.anvil.annotations.MergeComponent
      import com.squareup.anvil.annotations.MergeSubcomponent
      
      @MergeComponent(Any::class)
      @MergeSubcomponent(Unit::class)
      interface ComponentInterface
      """
    ) {
      assertThat(exitCode).isError()
      assertThat(messages).contains(
        "It's only allowed to have one single type of @Merge* annotation, however multiple " +
          "instances of the same annotation are allowed. You mix " +
          "[MergeComponent, MergeSubcomponent] and this is forbidden."
      )
    }
  }

  @Test fun `interfaces from different scopes are merged successfully`() {
    compile(
      """
      package com.squareup.test
      
      import com.squareup.anvil.annotations.ContributesTo
      $import
      
      @ContributesTo(Any::class)
      interface ContributingInterface
      
      @ContributesTo(Unit::class)
      interface SecondContributingInterface
      
      $annotation(Any::class)
      $annotation(Unit::class)
      interface ComponentInterface
      """
    ) {
      assertThat(componentInterface extends contributingInterface).isTrue()
      assertThat(componentInterface extends secondContributingInterface).isTrue()
    }
  }

  @Test fun `there are no duplicated interfaces`() {
    compile(
      """
      package com.squareup.test
      
      import com.squareup.anvil.annotations.ContributesTo
      $import
      
      @ContributesTo(Any::class)
      @ContributesTo(Unit::class)
      interface ContributingInterface
      
      @ContributesTo(Any::class)
      @ContributesTo(Unit::class)
      interface SecondContributingInterface
      
      $annotation(Any::class)
      $annotation(Unit::class)
      interface ComponentInterface
      """
    ) {
      assertThat(componentInterface extends contributingInterface).isTrue()
      assertThat(componentInterface extends secondContributingInterface).isTrue()
    }
  }

  @Test fun `a contributed interface replaced in one scope is not included by another scope`() {
    compile(
      """
      package com.squareup.test

      import com.squareup.anvil.annotations.ContributesTo
      $import

      @ContributesTo(Any::class)
      @ContributesTo(Unit::class)
      interface ContributingInterface

      @ContributesTo(
          Any::class,
          replaces = [ContributingInterface::class]
      )
      interface SecondContributingInterface

      $annotation(Any::class)
      $annotation(Unit::class)
      interface ComponentInterface
      """
    ) {
      assertThat(componentInterface extends contributingInterface).isFalse()
      assertThat(componentInterface extends secondContributingInterface).isTrue()
    }
  }

  @Test fun `a contributed interface excluded in one scope is not included by another scope`() {
    compile(
      """
      package com.squareup.test

      import com.squareup.anvil.annotations.ContributesTo
      $import

      @ContributesTo(Any::class)
      @ContributesTo(Unit::class)
      interface ContributingInterface

      $annotation(Any::class, exclude = [ContributingInterface::class])
      $annotation(Unit::class)
      interface ComponentInterface
      """
    ) {
      assertThat(componentInterface extends contributingInterface).isFalse()
    }
  }
}
