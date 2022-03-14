package com.squareup.anvil.test

import com.google.common.truth.Truth.assertThat
import com.squareup.anvil.annotations.ContributesTo
import com.squareup.anvil.annotations.MergeComponent
import com.squareup.anvil.annotations.MergeSubcomponent
import com.squareup.anvil.compiler.internal.testing.extends
import com.squareup.anvil.compiler.internal.testing.withoutAnvilModule
import dagger.Component
import dagger.Module
import dagger.Provides
import dagger.Subcomponent
import org.junit.Test

class RepeatedContributionsTest {

  @Test fun `repeated contributions are merged properly`() {
    val componentAnnotation = RepeatedComponent1::class.java
      .getAnnotation(Component::class.java)!!
    assertThat(componentAnnotation.modules.withoutAnvilModule())
      .containsExactly(RepeatedModule::class, RepeatedModuleInt::class)

    assertThat(RepeatedComponent1::class extends RepeatedInterface::class).isTrue()
    assertThat(RepeatedComponent1::class extends RepeatedInterfaceInt::class).isTrue()

    val component = DaggerRepeatedContributionsTest_RepeatedComponent1.create()
    assertThat((component as RepeatedInterface).string()).isEqualTo("Hello")
    assertThat((component as RepeatedInterfaceInt).integer()).isEqualTo(5)

    val subcomponentAnnotation = RepeatedComponent2::class.java
      .getAnnotation(Subcomponent::class.java)!!
    assertThat(subcomponentAnnotation.modules.withoutAnvilModule())
      .containsExactly(RepeatedModule::class, RepeatedModuleInt::class)

    assertThat(RepeatedComponent2::class extends RepeatedInterface::class).isTrue()
    assertThat(RepeatedComponent2::class extends RepeatedInterfaceInt::class).isTrue()

    val subcomponent = component.subcomponent()
    assertThat((subcomponent as RepeatedInterface).string()).isEqualTo("Hello")
    assertThat((subcomponent as RepeatedInterfaceInt).integer()).isEqualTo(5)
  }

  @Test fun `repeated merge annotation work`() {
    val componentAnnotation = RepeatedComponent3::class.java
      .getAnnotation(Component::class.java)!!
    assertThat(componentAnnotation.modules.withoutAnvilModule())
      .containsExactly(RepeatedModule::class, RepeatedModuleInt::class)

    assertThat(RepeatedComponent3::class extends RepeatedInterface::class).isTrue()
    assertThat(RepeatedComponent3::class extends RepeatedInterfaceInt::class).isTrue()

    val component = DaggerRepeatedContributionsTest_RepeatedComponent3.create()
    assertThat((component as RepeatedInterface).string()).isEqualTo("Hello")
    assertThat((component as RepeatedInterfaceInt).integer()).isEqualTo(5)
  }

  @MergeComponent(RepeatedScope1::class)
  interface RepeatedComponent1 {
    fun subcomponent(): RepeatedComponent2
  }

  @MergeSubcomponent(RepeatedScope2::class)
  interface RepeatedComponent2

  @MergeComponent(RepeatedScope1::class)
  @MergeComponent(RepeatedScope2::class)
  interface RepeatedComponent3

  @ContributesTo(RepeatedScope1::class)
  @ContributesTo(RepeatedScope2::class)
  interface RepeatedInterfaceInt {
    fun integer(): Int
  }

  @ContributesTo(RepeatedScope1::class)
  @ContributesTo(RepeatedScope2::class)
  @Module
  object RepeatedModuleInt {
    @Provides fun provideInteger(): Int = 5
  }
}
