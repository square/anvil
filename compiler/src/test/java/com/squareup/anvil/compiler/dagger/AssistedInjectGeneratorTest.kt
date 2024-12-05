package com.squareup.anvil.compiler.dagger

import com.google.common.truth.Truth.assertThat
import com.squareup.anvil.compiler.assistedService
import com.squareup.anvil.compiler.compilationErrorLine
import com.squareup.anvil.compiler.internal.testing.AnvilCompilationMode
import com.squareup.anvil.compiler.internal.testing.factoryClass
import com.squareup.anvil.compiler.internal.testing.invokeGet
import com.squareup.anvil.compiler.internal.testing.isStatic
import com.squareup.anvil.compiler.testParams
import com.tschuchort.compiletesting.JvmCompilationResult
import com.tschuchort.compiletesting.KotlinCompilation
import org.intellij.lang.annotations.Language
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters
import javax.inject.Provider

@RunWith(Parameterized::class)
class AssistedInjectGeneratorTest(
  private val useDagger: Boolean,
  private val mode: AnvilCompilationMode,
) {

  companion object {
    @Parameters(name = "Use Dagger: {0}, mode: {1}")
    @JvmStatic
    fun params() = testParams()
  }

  @Test fun `a factory class is generated with one assisted parameter`() {
    /*
package com.squareup.test;

import javax.annotation.processing.Generated;
import javax.inject.Provider;

@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes"
})
public final class AssistedService_Factory {
  private final Provider<Integer> integerProvider;

  public AssistedService_Factory(Provider<Integer> integerProvider) {
    this.integerProvider = integerProvider;
  }

  public AssistedService get(String string) {
    return newInstance(integerProvider.get(), string);
  }

  public static AssistedService_Factory create(Provider<Integer> integerProvider) {
    return new AssistedService_Factory(integerProvider);
  }

  public static AssistedService newInstance(int integer, String string) {
    return new AssistedService(integer, string);
  }
}
     */
    compile(
      """
      package com.squareup.test
      
      import dagger.assisted.Assisted
      import dagger.assisted.AssistedInject
      
      data class AssistedService @AssistedInject constructor(
        val int: Int,
        @Assisted val string: String
      )
      """,
    ) {
      val factoryClass = assistedService.factoryClass()

      val constructor = factoryClass.declaredConstructors.single()
      assertThat(constructor.parameterTypes.toList()).containsExactly(Provider::class.java)

      val staticMethods = factoryClass.declaredMethods.filter { it.isStatic }
      assertThat(staticMethods).hasSize(2)

      val factoryInstance = staticMethods.single { it.name == "create" }
        .invoke(null, Provider { 5 })
      assertThat(factoryInstance::class.java).isEqualTo(factoryClass)

      val newInstance = staticMethods.single { it.name == "newInstance" }
        .invoke(null, 5, "Hello")

      assertThat(factoryInstance.invokeGet("Hello")).isEqualTo(newInstance)
    }
  }

  @Test fun `a factory class is generated with a suspend lambda assisted parameter`() {
    compile(
      """
      package com.squareup.test
      
      import dagger.assisted.Assisted
      import dagger.assisted.AssistedInject
      
      data class AssistedService @AssistedInject constructor(
        @Assisted val action: suspend () -> String?
      )
      """,
    ) {
      val factoryClass = assistedService.factoryClass()

      val constructor = factoryClass.declaredConstructors.single()
      assertThat(constructor.parameterTypes.toList()).isEmpty()

      val staticMethods = factoryClass.declaredMethods.filter { it.isStatic }
      assertThat(staticMethods).hasSize(2)

      val factoryInstance = staticMethods.single { it.name == "create" }
        .invoke(null)
      assertThat(factoryInstance::class.java).isEqualTo(factoryClass)

      val action: suspend () -> String? = { "Hello " }
      val newInstance = staticMethods.single { it.name == "newInstance" }
        .invoke(null, action)
      assertThat(factoryInstance.invokeGet(action)).isEqualTo(newInstance)
    }
  }

  @Test fun `a factory class is generated without any parameter`() {
    compile(
      """
      package com.squareup.test
      
      import dagger.assisted.Assisted
      import dagger.assisted.AssistedInject
      
      class AssistedService @AssistedInject constructor() {
        override fun equals(other: Any?): Boolean {
          if (this === other) return true
          if (javaClass != other?.javaClass) return false
      
          other as AssistedService
      
          return true
        } 
      }
      """,
    ) {
      val factoryClass = assistedService.factoryClass()

      val constructor = factoryClass.declaredConstructors.single()
      assertThat(constructor.parameterTypes.toList()).isEmpty()

      val staticMethods = factoryClass.declaredMethods.filter { it.isStatic }
      assertThat(staticMethods).hasSize(2)

      val factoryInstance = staticMethods.single { it.name == "create" }.invoke(null)
      assertThat(factoryInstance::class.java).isEqualTo(factoryClass)

      val newInstance = staticMethods.single { it.name == "newInstance" }.invoke(null)

      assertThat(factoryInstance.invokeGet()).isEqualTo(newInstance)
    }
  }

  @Test fun `a factory class is generated with two assisted parameters`() {
    compile(
      """
      package com.squareup.test
      
      import dagger.assisted.Assisted
      import dagger.assisted.AssistedInject
      
      data class AssistedService @AssistedInject constructor(
        val int: Int,
        @Assisted("one") val string1: String,
        @Assisted("two") val string2: String
      )
      """,
    ) {
      val factoryClass = assistedService.factoryClass()

      val constructor = factoryClass.declaredConstructors.single()
      assertThat(constructor.parameterTypes.toList()).containsExactly(Provider::class.java)

      val staticMethods = factoryClass.declaredMethods.filter { it.isStatic }
      assertThat(staticMethods).hasSize(2)

      val factoryInstance = staticMethods.single { it.name == "create" }
        .invoke(null, Provider { 5 })
      assertThat(factoryInstance::class.java).isEqualTo(factoryClass)

      val newInstance = staticMethods.single { it.name == "newInstance" }
        .invoke(null, 5, "Hello", "World")

      assertThat(factoryInstance.invokeGet("Hello", "World")).isEqualTo(newInstance)
    }
  }

  @Test fun `a factory class is generated with only two assisted parameters`() {
    compile(
      """
      package com.squareup.test
      
      import dagger.assisted.Assisted
      import dagger.assisted.AssistedInject
      
      data class AssistedService @AssistedInject constructor(
        @Assisted("one") val string1: String,
        @Assisted("two") val string2: String
      )
      """,
    ) {
      val factoryClass = assistedService.factoryClass()

      val constructor = factoryClass.declaredConstructors.single()
      assertThat(constructor.parameterTypes.toList()).isEmpty()

      val staticMethods = factoryClass.declaredMethods.filter { it.isStatic }
      assertThat(staticMethods).hasSize(2)

      val factoryInstance = staticMethods.single { it.name == "create" }.invoke(null)
      assertThat(factoryInstance::class.java).isEqualTo(factoryClass)

      val newInstance = staticMethods.single { it.name == "newInstance" }
        .invoke(null, "Hello", "World")

      assertThat(factoryInstance.invokeGet("Hello", "World")).isEqualTo(newInstance)
    }
  }

  @Test fun `two identical assisted types must use an identifier`() {
    compile(
      """
      package com.squareup.test
      
      import dagger.assisted.Assisted
      import dagger.assisted.AssistedInject
      
      data class SomeType(val int: Int)
      
      data class AssistedService @AssistedInject constructor(
        @Assisted val type1: SomeType,
        @Assisted val type2: SomeType
      )
      """,
      expectExitCode = KotlinCompilation.ExitCode.COMPILATION_ERROR,
    ) {
      assertThat(messages).contains(
        "@AssistedInject constructor has duplicate @Assisted type: " +
          "@Assisted com.squareup.test.SomeType",
      )
    }
  }

  @Test fun `two identical assisted types must use a different identifier`() {
    compile(
      """
      package com.squareup.test
      
      import dagger.assisted.Assisted
      import dagger.assisted.AssistedInject
      
      data class SomeType(val int: Int)
      
      data class AssistedService @AssistedInject constructor(
        @Assisted("one") val type1: SomeType,
        @Assisted(value = "one") val type2: SomeType
      )
      """,
      expectExitCode = KotlinCompilation.ExitCode.COMPILATION_ERROR,
    ) {
      assertThat(messages).contains(
        "@AssistedInject constructor has duplicate @Assisted type: " +
          "@Assisted(\"one\") com.squareup.test.SomeType",
      )
    }
  }

  @Test fun `a factory class is generated for without an assisted parameter`() {
    compile(
      """
      package com.squareup.test
      
      import dagger.assisted.Assisted
      import dagger.assisted.AssistedInject
      
      data class AssistedService @AssistedInject constructor(
        val int: Int
      )
      """,
    ) {
      val factoryClass = assistedService.factoryClass()

      val constructor = factoryClass.declaredConstructors.single()
      assertThat(constructor.parameterTypes.toList()).containsExactly(Provider::class.java)

      val staticMethods = factoryClass.declaredMethods.filter { it.isStatic }
      assertThat(staticMethods).hasSize(2)

      val factoryInstance = staticMethods.single { it.name == "create" }
        .invoke(null, Provider { 5 })
      assertThat(factoryInstance::class.java).isEqualTo(factoryClass)

      val newInstance = staticMethods.single { it.name == "newInstance" }
        .invoke(null, 5)

      assertThat(factoryInstance.invokeGet()).isEqualTo(newInstance)
    }
  }

  @Test fun `a factory class is generated with one generic parameter`() {
    compile(
      """
      package com.squareup.test
      
      import dagger.assisted.Assisted
      import dagger.assisted.AssistedInject
      
      data class AssistedService<T : CharSequence> @AssistedInject constructor(
        val int: Int,
        @Assisted val strings: List<String>
      )
      """,
    ) {
      val factoryClass = assistedService.factoryClass()

      val constructor = factoryClass.declaredConstructors.single()
      assertThat(constructor.parameterTypes.toList()).containsExactly(Provider::class.java)

      val staticMethods = factoryClass.declaredMethods.filter { it.isStatic }
      assertThat(staticMethods).hasSize(2)

      val factoryInstance = staticMethods.single { it.name == "create" }
        .invoke(null, Provider { 5 })
      assertThat(factoryInstance::class.java).isEqualTo(factoryClass)

      val newInstance = staticMethods.single { it.name == "newInstance" }
        .invoke(null, 5, listOf("Hello"))

      assertThat(factoryInstance.invokeGet(listOf("Hello"))).isEqualTo(newInstance)
    }
  }

  @Test fun `a factory class is generated with one generic parameter bound by a generic`() {
    compile(
      """
      package com.squareup.test
      
      import dagger.assisted.Assisted
      import dagger.assisted.AssistedInject
      
      data class AssistedService<T : List<String>> @AssistedInject constructor(
        val int: Int,
        @Assisted val strings: T
      )
      """,
    ) {
      val factoryClass = assistedService.factoryClass()

      val constructor = factoryClass.declaredConstructors.single()
      assertThat(constructor.parameterTypes.toList()).containsExactly(Provider::class.java)

      val staticMethods = factoryClass.declaredMethods.filter { it.isStatic }
      assertThat(staticMethods).hasSize(2)

      val factoryInstance = staticMethods.single { it.name == "create" }
        .invoke(null, Provider { 5 })
      assertThat(factoryInstance::class.java).isEqualTo(factoryClass)

      val newInstance = staticMethods.single { it.name == "newInstance" }
        .invoke(null, 5, listOf("Hello"))

      assertThat(factoryInstance.invokeGet(listOf("Hello"))).isEqualTo(newInstance)
    }
  }

  @Test fun `a factory class is generated with one generic parameter bound with a where clause`() {
    compile(
      """
      package com.squareup.test
      
      import dagger.assisted.Assisted
      import dagger.assisted.AssistedInject
      
      data class AssistedService<T> @AssistedInject constructor(
        val int: Int,
        @Assisted val stringBuilder : T
      ) where T : Appendable, T : CharSequence
      """,
    ) {
      val factoryClass = assistedService.factoryClass()

      val constructor = factoryClass.declaredConstructors.single()
      assertThat(constructor.parameterTypes.toList()).containsExactly(Provider::class.java)

      val staticMethods = factoryClass.declaredMethods.filter { it.isStatic }
      assertThat(staticMethods).hasSize(2)

      val factoryInstance = staticMethods.single { it.name == "create" }
        .invoke(null, Provider { 5 })
      assertThat(factoryInstance::class.java).isEqualTo(factoryClass)

      staticMethods.single { it.name == "newInstance" }
        .invoke(null, 5, StringBuilder())
    }
  }

  @Test fun `a factory class is generated with two generic parameters`() {
    compile(
      """
      package com.squareup.test
      
      import dagger.assisted.Assisted
      import dagger.assisted.AssistedInject
      
      data class AssistedService<T : CharSequence> @AssistedInject constructor(
        val int: Int,
        @Assisted val strings: List<String>,
        @Assisted val ints: List<Int>
      )
      """,
    ) {
      val factoryClass = assistedService.factoryClass()

      val constructor = factoryClass.declaredConstructors.single()
      assertThat(constructor.parameterTypes.toList()).containsExactly(Provider::class.java)

      val staticMethods = factoryClass.declaredMethods.filter { it.isStatic }
      assertThat(staticMethods).hasSize(2)

      val factoryInstance = staticMethods.single { it.name == "create" }
        .invoke(null, Provider { 5 })
      assertThat(factoryInstance::class.java).isEqualTo(factoryClass)

      val newInstance = staticMethods.single { it.name == "newInstance" }
        .invoke(null, 5, listOf("Hello"), listOf(1, 2))

      assertThat(factoryInstance.invokeGet(listOf("Hello"), listOf(1, 2))).isEqualTo(newInstance)
    }
  }

  @Test fun `a factory class is generated with one assisted type parameter`() {
    compile(
      """
      package com.squareup.test
      
      import dagger.assisted.Assisted
      import dagger.assisted.AssistedInject
      
      data class AssistedService<T : CharSequence> @AssistedInject constructor(
        val int: Int,
        @Assisted val string: T
      )
      """,
    ) {
      val factoryClass = assistedService.factoryClass()

      val constructor = factoryClass.declaredConstructors.single()
      assertThat(constructor.parameterTypes.toList()).containsExactly(Provider::class.java)

      val staticMethods = factoryClass.declaredMethods.filter { it.isStatic }
      assertThat(staticMethods).hasSize(2)

      val factoryInstance = staticMethods.single { it.name == "create" }
        .invoke(null, Provider { 5 })
      assertThat(factoryInstance::class.java).isEqualTo(factoryClass)

      val newInstance = staticMethods.single { it.name == "newInstance" }
        .invoke(null, 5, "Hello")

      assertThat(factoryInstance.invokeGet("Hello")).isEqualTo(newInstance)
    }
  }

  @Test fun `a factory class is generated with two type parameters`() {
    compile(
      """
      package com.squareup.test
      
      import dagger.assisted.Assisted
      import dagger.assisted.AssistedInject
      
      data class AssistedService<S : Any, T : CharSequence> @AssistedInject constructor(
        val int: S,
        @Assisted val string: T
      )
      """,
    ) {
      val factoryClass = assistedService.factoryClass()

      val constructor = factoryClass.declaredConstructors.single()
      assertThat(constructor.parameterTypes.toList()).containsExactly(Provider::class.java)

      val staticMethods = factoryClass.declaredMethods.filter { it.isStatic }
      assertThat(staticMethods).hasSize(2)

      val factoryInstance = staticMethods.single { it.name == "create" }
        .invoke(null, Provider { 5 })
      assertThat(factoryInstance::class.java).isEqualTo(factoryClass)

      val newInstance = staticMethods.single { it.name == "newInstance" }
        .invoke(null, 5, "Hello")

      assertThat(factoryInstance.invokeGet("Hello")).isEqualTo(newInstance)
    }
  }

  @Test fun `a factory class is generated for an inner class`() {
    compile(
      """
      package com.squareup.test
      
      import dagger.assisted.Assisted
      import dagger.assisted.AssistedInject
      
      class Outer {
        data class AssistedService @AssistedInject constructor(
          val int: Int,
          @Assisted val string: String
        )
      }
      
      """,
    ) {
      val factoryClass = classLoader
        .loadClass("com.squareup.test.Outer\$AssistedService")
        .factoryClass()

      val constructor = factoryClass.declaredConstructors.single()
      assertThat(constructor.parameterTypes.toList()).containsExactly(Provider::class.java)

      val staticMethods = factoryClass.declaredMethods.filter { it.isStatic }
      assertThat(staticMethods).hasSize(2)

      val factoryInstance = staticMethods.single { it.name == "create" }
        .invoke(null, Provider { 5 })
      assertThat(factoryInstance::class.java).isEqualTo(factoryClass)

      val newInstance = staticMethods.single { it.name == "newInstance" }
        .invoke(null, 5, "Hello")

      assertThat(factoryInstance.invokeGet("Hello")).isEqualTo(newInstance)
    }
  }

  @Test fun `a factory class is generated for nullable parameters`() {
    /*
package com.squareup.test;

import javax.annotation.processing.Generated;
import javax.inject.Provider;

@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes"
})
public final class AssistedService_Factory {
  private final Provider<Integer> p0_52215Provider;

  public AssistedService_Factory(Provider<Integer> p0_52215Provider) {
    this.p0_52215Provider = p0_52215Provider;
  }

  public AssistedService get(String string) {
    return newInstance(p0_52215Provider.get(), string);
  }

  public static AssistedService_Factory create(Provider<Integer> p0_52215Provider) {
    return new AssistedService_Factory(p0_52215Provider);
  }

  public static AssistedService newInstance(Integer p0_52215, String string) {
    return new AssistedService(p0_52215, string);
  }
}
     */
    compile(
      """
      package com.squareup.test
      
      import dagger.assisted.Assisted
      import dagger.assisted.AssistedInject
      
      data class AssistedService @AssistedInject constructor(
        val int: Int?,
        @Assisted val string: String?
      )
      """,
    ) {
      val factoryClass = assistedService.factoryClass()

      val constructor = factoryClass.declaredConstructors.single()
      assertThat(constructor.parameterTypes.toList()).containsExactly(Provider::class.java)

      val staticMethods = factoryClass.declaredMethods.filter { it.isStatic }
      assertThat(staticMethods).hasSize(2)

      val factoryInstance = staticMethods.single { it.name == "create" }
        .invoke(null, Provider { null })
      assertThat(factoryInstance::class.java).isEqualTo(factoryClass)

      val newInstance = staticMethods.single { it.name == "newInstance" }
        .invoke(null, null, null)

      assertThat(factoryInstance.invokeGet(null)).isEqualTo(newInstance)
    }
  }

  @Test fun `two assisted inject constructors aren't supported`() {
    compile(
      """
      package com.squareup.test
      
      import dagger.assisted.Assisted
      import dagger.assisted.AssistedInject
      
      class AssistedService @AssistedInject constructor(
        int: Int,
        @Assisted string: String
      ) {
        @AssistedInject constructor(@Assisted string: String)
      }
      """,
      expectExitCode = KotlinCompilation.ExitCode.COMPILATION_ERROR,
    ) {
      assertThat(
        compilationErrorLine()
          .removeParametersAndSort(),
      ).contains(
        "Type com.squareup.test.AssistedService may only contain one injected constructor. " +
          "Found: [@dagger.assisted.AssistedInject com.squareup.test.AssistedService, " +
          "@dagger.assisted.AssistedInject com.squareup.test.AssistedService]",
      )
    }
  }

  @Test fun `one inject and one assisted inject constructor aren't supported`() {
    compile(
      """
      package com.squareup.test
      
      import dagger.assisted.Assisted
      import dagger.assisted.AssistedInject
      import javax.inject.Inject
      
      class AssistedService @AssistedInject constructor(
        int: Int,
        @Assisted string: String
      ) {
        @Inject constructor(@Assisted string: String)
      }
      """,
      expectExitCode = KotlinCompilation.ExitCode.COMPILATION_ERROR,
    ) {
      assertThat(
        compilationErrorLine()
          .removeParametersAndSort(),
      ).contains(
        "Type com.squareup.test.AssistedService may only contain one injected constructor. " +
          "Found: [@Inject com.squareup.test.AssistedService, " +
          "@dagger.assisted.AssistedInject com.squareup.test.AssistedService]",
      )
    }
  }

  private fun compile(
    @Language("kotlin") vararg sources: String,
    expectExitCode: KotlinCompilation.ExitCode = KotlinCompilation.ExitCode.OK,
    block: JvmCompilationResult.() -> Unit = { },
  ): JvmCompilationResult {
    return com.squareup.anvil.compiler.compile(
      *sources,
      expectExitCode = expectExitCode,
      enableDaggerAnnotationProcessor = useDagger,
      generateDaggerFactories = !useDagger,
      mode = mode,
      block = block,
    )
  }
}
