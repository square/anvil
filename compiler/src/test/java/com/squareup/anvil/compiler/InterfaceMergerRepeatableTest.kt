package com.squareup.anvil.compiler

import com.google.common.truth.Truth.assertThat
import com.squareup.anvil.annotations.MergeComponent
import com.squareup.anvil.annotations.MergeSubcomponent
import com.squareup.anvil.annotations.compat.MergeInterfaces
import com.squareup.anvil.compiler.internal.testing.extends
import org.junit.jupiter.api.TestFactory

class InterfaceMergerRepeatableTest : AnnotationsTest(
  MergeComponent::class,
  MergeSubcomponent::class,
  MergeInterfaces::class,
) {

  @TestFactory
  fun `duplicate scopes are an error`() = testFactory {
    compile(
      """
      package com.squareup.test
      
      $import
      
      $annotation(Any::class)
      $annotation(Any::class)
      interface ComponentInterface
      """,
    ) {
      assertThat(exitCode).isError()
      assertThat(messages).contains(
        "com.squareup.test.ComponentInterface merges multiple times to the same scope: [Any]. " +
          "Merging multiple times to the same scope is forbidden and all scopes must be distinct.",
      )
    }
  }

  @TestFactory
  fun `different kind of merge annotations are forbidden`() = testFactory {
    assumeMergeComponent(annotationClass)

    compile(
      """
      package com.squareup.test
      
      import com.squareup.anvil.annotations.MergeComponent
      import com.squareup.anvil.annotations.MergeSubcomponent
      
      @MergeComponent(Any::class)
      @MergeSubcomponent(Unit::class)
      interface ComponentInterface
      """,
    ) {
      assertThat(exitCode).isError()
      assertThat(messages).contains(
        "It's only allowed to have one single type of @Merge* annotation, however multiple " +
          "instances of the same annotation are allowed. You mix " +
          "[MergeComponent, MergeSubcomponent] and this is forbidden.",
      )
    }
  }

  @TestFactory
  fun `interfaces from different scopes are merged successfully`() = testFactory {
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
      """,
    ) {
      assertThat(componentInterface extends contributingInterface).isTrue()
      assertThat(componentInterface extends secondContributingInterface).isTrue()
    }
  }

  @TestFactory
  fun `there are no duplicated interfaces`() = testFactory {
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
      """,
    ) {
      assertThat(componentInterface extends contributingInterface).isTrue()
      assertThat(componentInterface extends secondContributingInterface).isTrue()
    }
  }

  @TestFactory
  fun `a contributed interface replaced in one scope is not included by another scope`() =
    testFactory {
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
      """,
      ) {
        assertThat(componentInterface extends contributingInterface).isFalse()
        assertThat(componentInterface extends secondContributingInterface).isTrue()
      }
    }

  @TestFactory
  fun `a contributed interface excluded in one scope is not included by another scope`() =
    testFactory {
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
      """,
      ) {
        assertThat(componentInterface extends contributingInterface).isFalse()
      }
    }
}
