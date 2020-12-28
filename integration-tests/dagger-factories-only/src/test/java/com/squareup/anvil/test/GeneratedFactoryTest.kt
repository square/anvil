package com.squareup.anvil.test

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.lang.reflect.Modifier
import kotlin.test.assertFailsWith

class GeneratedFactoryTest {

  @Test fun `a dagger factory is generated for a provider method`() {
    val string = Class.forName("com.squareup.anvil.test.DaggerModule_ProvideStringFactory")
      .declaredMethods
      .filter { Modifier.isStatic(it.modifiers) }
      .single { it.name == "provideString" }
      .invoke(null)

    assertThat(string).isEqualTo("Hello Anvil")
  }

  @Test fun `annotations aren't on the classpath`() {
    assertFailsWith<ClassNotFoundException> {
      Class.forName("com.squareup.anvil.annotations.ContributesTo")
    }
  }
}
