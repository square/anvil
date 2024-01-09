package com.squareup.anvil.compiler

import com.google.common.truth.Truth.assertThat
import com.squareup.anvil.annotations.MergeComponent
import com.squareup.anvil.annotations.MergeSubcomponent
import com.squareup.anvil.annotations.compat.MergeModules
import com.squareup.anvil.compiler.internal.testing.anyDaggerComponent
import com.squareup.anvil.compiler.internal.testing.daggerComponent
import com.squareup.anvil.compiler.internal.testing.withoutAnvilModule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters
import kotlin.reflect.KClass

@RunWith(Parameterized::class)
class ModuleMergerRepeatableTest(
  private val annotationClass: KClass<*>,
) {

  private val annotation = "@${annotationClass.simpleName}"
  private val import = "import ${annotationClass.java.canonicalName}"

  companion object {
    @Parameters(name = "{0}")
    @JvmStatic
    fun annotationClasses(): Collection<Any> {
      return buildList {
        add(MergeComponent::class)
        if (isFullTestRun()) {
          add(MergeSubcomponent::class)
          add(MergeModules::class)
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
      """,
    ) {
      assertThat(exitCode).isError()
      assertThat(messages).contains(
        "com.squareup.test.ComponentInterface merges multiple times to the same scope: [Any]. " +
          "Merging multiple times to the same scope is forbidden and all scopes must be distinct.",
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

  @Test fun `modules from different scopes are merged successfully`() {
    compile(
      """
        package com.squareup.test

        import com.squareup.anvil.annotations.ContributesTo
        import dagger.Module
        $import

        @ContributesTo(Any::class)
        @Module
        abstract class DaggerModule1

        @ContributesTo(Unit::class)
        @Module
        abstract class DaggerModule2

        $annotation(Any::class)
        $annotation(Unit::class)
        interface ComponentInterface
        """,
    ) {
      val component = componentInterface.anyDaggerComponent(annotationClass)
      assertThat(component.modules.withoutAnvilModule())
        .containsExactly(daggerModule1.kotlin, daggerModule2.kotlin)
    }
  }

  @Test fun `there are no duplicated modules`() {
    compile(
      """
        package com.squareup.test

        import com.squareup.anvil.annotations.ContributesTo
        import dagger.Module
        $import

        @ContributesTo(Any::class)
        @ContributesTo(Unit::class)
        @Module
        abstract class DaggerModule1

        @ContributesTo(Any::class)
        @ContributesTo(Unit::class)
        @Module
        abstract class DaggerModule2

        $annotation(Any::class)
        $annotation(Unit::class)
        interface ComponentInterface
        """,
    ) {
      val component = componentInterface.anyDaggerComponent(annotationClass)
      assertThat(component.modules.withoutAnvilModule())
        .containsExactly(daggerModule1.kotlin, daggerModule2.kotlin)
    }
  }

  @Test fun `a module replaced in one scope is not included by another scope`() {
    compile(
      """
        package com.squareup.test

        import com.squareup.anvil.annotations.ContributesTo
        import dagger.Module
        $import

        @ContributesTo(Any::class)
        @ContributesTo(Unit::class)
        @Module
        abstract class DaggerModule1

        @ContributesTo(
            Any::class,
            replaces = [DaggerModule1::class]
        )
        @Module
        abstract class DaggerModule2

        $annotation(Any::class)
        $annotation(Unit::class)
        interface ComponentInterface
        """,
    ) {
      val component = componentInterface.anyDaggerComponent(annotationClass)
      assertThat(component.modules.withoutAnvilModule()).containsExactly(daggerModule2.kotlin)
    }
  }

  @Test fun `a contributed binding replaced in one scope is not included by another scope`() {
    compile(
      """
        package com.squareup.test

        import com.squareup.anvil.annotations.ContributesBinding
        import com.squareup.anvil.annotations.ContributesTo
        $import

        interface ParentInterface

        @ContributesBinding(Any::class)
        @ContributesBinding(Unit::class)
        interface ContributingInterface : ParentInterface

        @ContributesTo(
            Any::class,
            replaces = [ContributingInterface::class]
        )
        @dagger.Module
        abstract class DaggerModule1

        $annotation(Any::class)
        $annotation(Unit::class)
        interface ComponentInterface
        """,
    ) {
      assertThat(
        componentInterface.anyDaggerComponent(annotationClass).modules.withoutAnvilModule(),
      ).containsExactly(daggerModule1.kotlin)

      assertThat(componentInterface.anvilModule.declaredMethods).isEmpty()
    }
  }

  @Test
  fun `a contributed module replaced by a binding in one scope is not included by another scope`() {
    compile(
      """
        package com.squareup.test

        import com.squareup.anvil.annotations.ContributesBinding
        import com.squareup.anvil.annotations.ContributesTo
        $import

        interface ParentInterface

        @ContributesTo(Any::class)
        @ContributesTo(Unit::class)
        @dagger.Module
        abstract class DaggerModule1

        @ContributesBinding(
            Any::class,
            replaces = [DaggerModule1::class]
        )
        interface ContributingInterface : ParentInterface

        $annotation(Any::class)
        $annotation(Unit::class)
        interface ComponentInterface
        """,
    ) {
      assertThat(
        componentInterface.anyDaggerComponent(annotationClass).modules.withoutAnvilModule(),
      ).isEmpty()
      assertThat(componentInterface.anvilModule.declaredMethods).hasLength(1)
    }
  }

  @Test fun `a contributed module excluded in one scope is not included by another scope`() {
    compile(
      """
        package com.squareup.test

        import com.squareup.anvil.annotations.ContributesTo
        $import

        @ContributesTo(Any::class)
        @ContributesTo(Unit::class)
        @dagger.Module
        abstract class DaggerModule1

        @ContributesTo(Any::class)
        @dagger.Module
        abstract class DaggerModule2

        $annotation(
            scope = Any::class,
            exclude = [
              DaggerModule1::class
            ]
        )
        $annotation(Unit::class)
        interface ComponentInterface
        """,
    ) {
      val component = componentInterface.anyDaggerComponent(annotationClass)
      assertThat(component.modules.withoutAnvilModule()).containsExactly(daggerModule2.kotlin)
    }
  }

  @Test fun `a contributed binding excluded in one scope is not included by another scope`() {
    compile(
      """
        package com.squareup.test

        import com.squareup.anvil.annotations.ContributesBinding
        $import

        interface ParentInterface

        @ContributesBinding(Any::class)
        @ContributesBinding(Unit::class)
        interface ContributingInterface : ParentInterface

        $annotation(
            scope = Any::class,
            exclude = [
              ContributingInterface::class
            ]
        )
        $annotation(Unit::class)
        interface ComponentInterface
        """,
    ) {
      assertThat(componentInterface.anvilModule.declaredMethods).isEmpty()
    }
  }

  @Test
  fun `modules and dependencies are added in the Dagger component with multiple merge annotations`() {
    assumeMergeComponent(annotationClass)

    compile(
      """
      package com.squareup.test
      
      $import
      
      $annotation(
          scope = Any::class,
          modules = [Boolean::class],
          dependencies = [Boolean::class]
      )
      $annotation(
          scope = Unit::class,
          modules = [Int::class],
          dependencies = [Int::class]
      )
      interface ComponentInterface
      """,
    ) {
      val component = componentInterface.daggerComponent
      assertThat(component.modules.withoutAnvilModule()).containsExactly(Boolean::class, Int::class)
      assertThat(component.dependencies.toList()).containsExactly(Boolean::class, Int::class)
    }
  }
}
