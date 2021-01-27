package com.squareup.anvil.compiler.dagger

import com.google.common.truth.Truth.assertThat
import com.squareup.anvil.compiler.assistedService
import com.squareup.anvil.compiler.factoryClass
import com.squareup.anvil.compiler.invokeGet
import com.squareup.anvil.compiler.isStatic
import com.tschuchort.compiletesting.KotlinCompilation.Result
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters
import javax.inject.Provider

@RunWith(Parameterized::class)
class AssistedInjectGeneratorTest(
  private val useDagger: Boolean
) {

  companion object {
    @Parameters(name = "Use Dagger: {0}")
    @JvmStatic fun useDagger(): Collection<Any> {
      return listOf(true, false)
    }
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
      
      class AssistedService @AssistedInject constructor(
        val int: Int,
        @Assisted val string: String
      ) {
        override fun equals(other: Any?): Boolean {
          if (this === other) return true
          if (javaClass != other?.javaClass) return false
      
          other as AssistedService
      
          if (int != other.int) return false
          if (string != other.string) return false
      
          return true
        } 
      }
      """
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
      """
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
      
      class AssistedService @AssistedInject constructor(
        val int: Int,
        @Assisted val string1: String,
        @Assisted val string2: String
      ) {
        override fun equals(other: Any?): Boolean {
          if (this === other) return true
          if (javaClass != other?.javaClass) return false
      
          other as AssistedService
      
          if (int != other.int) return false
          if (string1 != other.string1) return false
          if (string2 != other.string2) return false
      
          return true
        } 
      }
      """
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
      
      class AssistedService @AssistedInject constructor(
        @Assisted val string1: String,
        @Assisted val string2: String
      ) {
        override fun equals(other: Any?): Boolean {
          if (this === other) return true
          if (javaClass != other?.javaClass) return false
      
          other as AssistedService
      
          if (string1 != other.string1) return false
          if (string2 != other.string2) return false
      
          return true
        } 
      }
      """
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

  @Test fun `a factory class is generated for without an assisted parameter`() {
    compile(
      """
      package com.squareup.test
      
      import dagger.assisted.Assisted
      import dagger.assisted.AssistedInject
      
      class AssistedService @AssistedInject constructor(
        val int: Int
      ) {
        override fun equals(other: Any?): Boolean {
          if (this === other) return true
          if (javaClass != other?.javaClass) return false
      
          other as AssistedService
      
          if (int != other.int) return false
      
          return true
        } 
      }
      """
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
      
      class AssistedService<T : CharSequence> @AssistedInject constructor(
        val int: Int,
        @Assisted val strings: List<String>
      ) {
        override fun equals(other: Any?): Boolean {
          if (this === other) return true
          if (javaClass != other?.javaClass) return false
      
          other as AssistedService<*>
      
          if (int != other.int) return false
          if (strings != other.strings) return false
      
          return true
        } 
      }
      """
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

  @Test fun `a factory class is generated with one assisted type parameter`() {
    compile(
      """
      package com.squareup.test
      
      import dagger.assisted.Assisted
      import dagger.assisted.AssistedInject
      
      class AssistedService<T : CharSequence> @AssistedInject constructor(
        val int: Int,
        @Assisted val string: T
      ) {
        override fun equals(other: Any?): Boolean {
          if (this === other) return true
          if (javaClass != other?.javaClass) return false
      
          other as AssistedService<*>
      
          if (int != other.int) return false
          if (string != other.string) return false
      
          return true
        } 
      }
      """
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
      
      class AssistedService<S : Any, T : CharSequence> @AssistedInject constructor(
        val int: S,
        @Assisted val string: T
      ) {
        override fun equals(other: Any?): Boolean {
          if (this === other) return true
          if (javaClass != other?.javaClass) return false
      
          other as AssistedService<*, *>
      
          if (int != other.int) return false
          if (string != other.string) return false
      
          return true
        } 
      }
      """
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
        class AssistedService @AssistedInject constructor(
          val int: Int,
          @Assisted val string: String
        ) {
          override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
        
            other as AssistedService
        
            if (int != other.int) return false
            if (string != other.string) return false
        
            return true
          } 
        }
      }
      
      """
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

  @Suppress("CHANGING_ARGUMENTS_EXECUTION_ORDER_FOR_NAMED_VARARGS")
  private fun compile(
    vararg sources: String,
    block: Result.() -> Unit = { }
  ): Result = com.squareup.anvil.compiler.compile(
    sources = sources,
    enableDaggerAnnotationProcessor = useDagger,
    generateDaggerFactories = !useDagger,
    block = block
  )
}
