package com.squareup.anvil.compiler.dagger

import com.google.common.truth.Truth.assertThat
import com.squareup.anvil.compiler.assistedService
import com.squareup.anvil.compiler.assistedServiceFactory
import com.squareup.anvil.compiler.factoryClass
import com.squareup.anvil.compiler.implClass
import com.squareup.anvil.compiler.isStatic
import com.squareup.anvil.compiler.use
import com.tschuchort.compiletesting.KotlinCompilation.ExitCode.COMPILATION_ERROR
import com.tschuchort.compiletesting.KotlinCompilation.Result
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters
import javax.inject.Provider

@RunWith(Parameterized::class)
class AssistedFactoryGeneratorTest(
  private val useDagger: Boolean
) {

  companion object {
    @Parameters(name = "Use Dagger: {0}")
    @JvmStatic fun useDagger(): Collection<Any> {
      return listOf(true, false)
    }
  }

  @Test fun `an implementation for a factory class is generated`() {
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

  public static AssistedService newInstance(int p0_52215, String string) {
    return new AssistedService(p0_52215, string);
  }
}
     */
    /*
package com.squareup.test;

import dagger.internal.InstanceFactory;
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
public final class AssistedServiceFactory_Impl implements AssistedServiceFactory {
  private final AssistedService_Factory delegateFactory;

  AssistedServiceFactory_Impl(AssistedService_Factory delegateFactory) {
    this.delegateFactory = delegateFactory;
  }

  @Override
  public AssistedService create(String string) {
    return delegateFactory.get(string);
  }

  public static Provider<AssistedServiceFactory> create(AssistedService_Factory delegateFactory) {
    return InstanceFactory.create(new AssistedServiceFactory_Impl(delegateFactory));
  }
}
     */
    compile(
      """
      package com.squareup.test
      
      import dagger.assisted.Assisted
      import dagger.assisted.AssistedFactory
      import dagger.assisted.AssistedInject
      
      class AssistedService @AssistedInject constructor(
        val int: Int,
        @Assisted val string: String
      )
      
      @AssistedFactory
      interface AssistedServiceFactory {
        fun create(string: String): AssistedService
      }
      """
    ) {
      val factoryImplClass = assistedServiceFactory.implClass()
      val generatedFactoryInstance = assistedService.factoryClass()
        .declaredConstructors.single().newInstance(Provider { 5 })

      val constructor = factoryImplClass.declaredConstructors.single()
      assertThat(constructor.parameterTypes.toList())
        .containsExactly(assistedService.factoryClass())

      val staticMethods = factoryImplClass.declaredMethods.filter { it.isStatic }
      assertThat(staticMethods).hasSize(1)

      val factoryProvider = staticMethods.single { it.name == "create" }
        .invoke(null, generatedFactoryInstance) as Provider<*>
      assertThat(factoryProvider.get()::class.java).isEqualTo(factoryImplClass)

      val factoryImplInstance = constructor.use { it.newInstance(generatedFactoryInstance) }

      val assistedServiceInstance = factoryImplClass.declaredMethods
        .filterNot { it.isStatic }
        .single { it.name == "create" }
        .invoke(factoryImplInstance, "Hello")

      assertThat(assistedServiceInstance::class.java).isEqualTo(assistedService)
    }
  }

  @Test fun `the factory function is allowed to be provided by a super type`() {
    compile(
      """
      package com.squareup.test
      
      import dagger.assisted.Assisted
      import dagger.assisted.AssistedFactory
      import dagger.assisted.AssistedInject
      
      class AssistedService @AssistedInject constructor(
        val int: Int,
        @Assisted val string: String
      )
      
      interface AssistedServiceFactorySuper {
        fun create(string: String): AssistedService
      }
      
      @AssistedFactory
      interface AssistedServiceFactory : AssistedServiceFactorySuper
      """
    ) {
      val factoryImplClass = assistedServiceFactory.implClass()
      val generatedFactoryInstance = assistedService.factoryClass()
        .declaredConstructors.single().newInstance(Provider { 5 })

      val constructor = factoryImplClass.declaredConstructors.single()
      assertThat(constructor.parameterTypes.toList())
        .containsExactly(assistedService.factoryClass())

      val staticMethods = factoryImplClass.declaredMethods.filter { it.isStatic }
      assertThat(staticMethods).hasSize(1)

      val factoryProvider = staticMethods.single { it.name == "create" }
        .invoke(null, generatedFactoryInstance) as Provider<*>
      assertThat(factoryProvider.get()::class.java).isEqualTo(factoryImplClass)

      val factoryImplInstance = constructor.use { it.newInstance(generatedFactoryInstance) }

      val assistedServiceInstance = factoryImplClass.declaredMethods
        .filterNot { it.isStatic }
        .single { it.name == "create" }
        .invoke(factoryImplInstance, "Hello")

      assertThat(assistedServiceInstance::class.java).isEqualTo(assistedService)
    }
  }

  @Test fun `an abstract class is supported`() {
    compile(
      """
      package com.squareup.test
      
      import dagger.assisted.Assisted
      import dagger.assisted.AssistedFactory
      import dagger.assisted.AssistedInject
      
      class AssistedService @AssistedInject constructor(
        val int: Int,
        @Assisted val string: String
      )
      
      @AssistedFactory
      abstract class AssistedServiceFactory {
        abstract fun create(string: String): AssistedService
      }
      """
    ) {
      val factoryImplClass = assistedServiceFactory.implClass()
      val generatedFactoryInstance = assistedService.factoryClass()
        .declaredConstructors.single().newInstance(Provider { 5 })

      val constructor = factoryImplClass.declaredConstructors.single()
      assertThat(constructor.parameterTypes.toList())
        .containsExactly(assistedService.factoryClass())

      val staticMethods = factoryImplClass.declaredMethods.filter { it.isStatic }
      assertThat(staticMethods).hasSize(1)

      val factoryProvider = staticMethods.single { it.name == "create" }
        .invoke(null, generatedFactoryInstance) as Provider<*>
      assertThat(factoryProvider.get()::class.java).isEqualTo(factoryImplClass)

      val factoryImplInstance = constructor.use { it.newInstance(generatedFactoryInstance) }

      val assistedServiceInstance = factoryImplClass.declaredMethods
        .filterNot { it.isStatic }
        .single { it.name == "create" }
        .invoke(factoryImplInstance, "Hello")

      assertThat(assistedServiceInstance::class.java).isEqualTo(assistedService)
    }
  }

  @Test fun `a protected factory function in an abstract class is supported`() {
    compile(
      """
      package com.squareup.test
      
      import dagger.assisted.Assisted
      import dagger.assisted.AssistedFactory
      import dagger.assisted.AssistedInject
      
      class AssistedService @AssistedInject constructor(
        val int: Int,
        @Assisted val string: String
      )
      
      @AssistedFactory
      abstract class AssistedServiceFactory {
        protected abstract fun create(string: String): AssistedService
      }
      """
    ) {
      val factoryImplClass = assistedServiceFactory.implClass()
      val generatedFactoryInstance = assistedService.factoryClass()
        .declaredConstructors.single().newInstance(Provider { 5 })

      val constructor = factoryImplClass.declaredConstructors.single()
      assertThat(constructor.parameterTypes.toList())
        .containsExactly(assistedService.factoryClass())

      val staticMethods = factoryImplClass.declaredMethods.filter { it.isStatic }
      assertThat(staticMethods).hasSize(1)

      val factoryProvider = staticMethods.single { it.name == "create" }
        .invoke(null, generatedFactoryInstance) as Provider<*>
      assertThat(factoryProvider.get()::class.java).isEqualTo(factoryImplClass)

      val factoryImplInstance = constructor.use { it.newInstance(generatedFactoryInstance) }

      val assistedServiceInstance = factoryImplClass.declaredMethods
        .filterNot { it.isStatic }
        .single { it.name == "create" }
        .use {
          it.invoke(factoryImplInstance, "Hello")
        }

      assertThat(assistedServiceInstance::class.java).isEqualTo(assistedService)
    }
  }

  @Test fun `an implementation for a factory class with generic parameters is generated`() {
    compile(
      """
      package com.squareup.test
      
      import dagger.assisted.Assisted
      import dagger.assisted.AssistedFactory
      import dagger.assisted.AssistedInject
      
      class AssistedService @AssistedInject constructor(
        val int: Int,
        @Assisted val strings: List<String>
      )
      
      @AssistedFactory
      interface AssistedServiceFactory {
        fun create(strings: List<String>): AssistedService
      }
      """
    ) {
      val factoryImplClass = assistedServiceFactory.implClass()
      val generatedFactoryInstance = assistedService.factoryClass()
        .declaredConstructors.single().newInstance(Provider { 5 })

      val constructor = factoryImplClass.declaredConstructors.single()
      assertThat(constructor.parameterTypes.toList())
        .containsExactly(assistedService.factoryClass())

      val staticMethods = factoryImplClass.declaredMethods.filter { it.isStatic }
      assertThat(staticMethods).hasSize(1)

      val factoryProvider = staticMethods.single { it.name == "create" }
        .invoke(null, generatedFactoryInstance) as Provider<*>
      assertThat(factoryProvider.get()::class.java).isEqualTo(factoryImplClass)

      val factoryImplInstance = constructor.use { it.newInstance(generatedFactoryInstance) }

      val assistedServiceInstance = factoryImplClass.declaredMethods
        .filterNot { it.isStatic }
        .single { it.name == "create" }
        .invoke(factoryImplInstance, listOf("Hello"))

      assertThat(assistedServiceInstance::class.java).isEqualTo(assistedService)
    }
  }

  @Test fun `an implementation for a factory class with type parameters is generated`() {
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
public final class AssistedService_Factory<T extends CharSequence> {
  private final Provider<Integer> p0_52215Provider;

  public AssistedService_Factory(Provider<Integer> p0_52215Provider) {
    this.p0_52215Provider = p0_52215Provider;
  }

  public AssistedService<T> get(T string) {
    return newInstance(p0_52215Provider.get(), string);
  }

  public static <T extends CharSequence> AssistedService_Factory<T> create(
      Provider<Integer> p0_52215Provider) {
    return new AssistedService_Factory<T>(p0_52215Provider);
  }

  public static <T extends CharSequence> AssistedService<T> newInstance(int p0_52215, T string) {
    return new AssistedService<T>(p0_52215, string);
  }
}
     */
    /*
package com.squareup.test;

import dagger.internal.InstanceFactory;
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
public final class AssistedServiceFactory_Impl<T extends CharSequence> implements AssistedServiceFactory<T> {
  private final AssistedService_Factory<T> delegateFactory;

  AssistedServiceFactory_Impl(AssistedService_Factory<T> delegateFactory) {
    this.delegateFactory = delegateFactory;
  }

  @Override
  public AssistedService<T> create(T string) {
    return delegateFactory.get(string);
  }

  public static <T extends CharSequence> Provider<AssistedServiceFactory<T>> create(
      AssistedService_Factory<T> delegateFactory) {
    return InstanceFactory.create(new AssistedServiceFactory_Impl(delegateFactory));
  }
}
     */
    compile(
      """
      package com.squareup.test
      
      import dagger.assisted.Assisted
      import dagger.assisted.AssistedFactory
      import dagger.assisted.AssistedInject
      
      class AssistedService<T : CharSequence> @AssistedInject constructor(
        val int: Int,
        @Assisted val string: T
      )
      
      @AssistedFactory
      interface AssistedServiceFactory<T : CharSequence> {
        fun create(string: T): AssistedService<T>
      }
      """
    ) {
      val factoryImplClass = assistedServiceFactory.implClass()
      val generatedFactoryInstance = assistedService.factoryClass()
        .declaredConstructors.single().newInstance(Provider { 5 })

      val constructor = factoryImplClass.declaredConstructors.single()
      assertThat(constructor.parameterTypes.toList())
        .containsExactly(assistedService.factoryClass())

      val staticMethods = factoryImplClass.declaredMethods.filter { it.isStatic }
      assertThat(staticMethods).hasSize(1)

      val factoryProvider = staticMethods.single { it.name == "create" }
        .invoke(null, generatedFactoryInstance) as Provider<*>
      assertThat(factoryProvider.get()::class.java).isEqualTo(factoryImplClass)

      val factoryImplInstance = constructor.use { it.newInstance(generatedFactoryInstance) }

      val assistedServiceInstance = factoryImplClass.declaredMethods
        .filterNot { it.isStatic }
        .single { it.name == "create" }
        .invoke(factoryImplInstance, "Hello")

      assertThat(assistedServiceInstance::class.java).isEqualTo(assistedService)
    }
  }

  @Test fun `an implementation for an inner factory class is generated`() {
    compile(
      """
      package com.squareup.test
      
      import dagger.assisted.Assisted
      import dagger.assisted.AssistedFactory
      import dagger.assisted.AssistedInject
      
      class AssistedService @AssistedInject constructor(
        val int: Int,
        @Assisted val string: String
      ) {
        @AssistedFactory
        interface Factory {
          fun create(string: String): AssistedService
        }
      }
      """
    ) {
      val factoryImplClass = classLoader
        .loadClass("com.squareup.test.AssistedService\$Factory").implClass()
      val generatedFactoryInstance = assistedService.factoryClass()
        .declaredConstructors.single().newInstance(Provider { 5 })

      val constructor = factoryImplClass.declaredConstructors.single()
      assertThat(constructor.parameterTypes.toList())
        .containsExactly(assistedService.factoryClass())

      val staticMethods = factoryImplClass.declaredMethods.filter { it.isStatic }
      assertThat(staticMethods).hasSize(1)

      val factoryProvider = staticMethods.single { it.name == "create" }
        .invoke(null, generatedFactoryInstance) as Provider<*>
      assertThat(factoryProvider.get()::class.java).isEqualTo(factoryImplClass)

      val factoryImplInstance = constructor.use { it.newInstance(generatedFactoryInstance) }

      val assistedServiceInstance = factoryImplClass.declaredMethods
        .filterNot { it.isStatic }
        .single { it.name == "create" }
        .invoke(factoryImplInstance, "Hello")

      assertThat(assistedServiceInstance::class.java).isEqualTo(assistedService)
    }
  }

  @Test fun `an assisted inject type must be returned`() {
    compile(
      """
      package com.squareup.test
      
      import dagger.assisted.AssistedFactory
      
      class AssistedService(
        val int: Int,
        val string: String
      )
        
      @AssistedFactory
      interface Factory {
        fun create(string: String): AssistedService
      }
      """
    ) {
      assertThat(exitCode).isEqualTo(COMPILATION_ERROR)
      assertThat(messages).contains(
        "Invalid return type: com.squareup.test.AssistedService. An assisted factory's " +
          "abstract method must return a type with an @AssistedInject-annotated constructor."
      )
    }
  }

  @Test fun `an assisted inject type must be returned - no return type`() {
    compile(
      """
      package com.squareup.test
      
      import dagger.assisted.AssistedFactory
      
      class AssistedService(
        val int: Int,
        val string: String
      )
        
      @AssistedFactory
      interface Factory {
        fun create(string: String)
      }
      """
    ) {
      assertThat(exitCode).isEqualTo(COMPILATION_ERROR)
      assertThat(messages).contains("Invalid return type:")
      assertThat(messages).contains(
        "An assisted factory's abstract method must return a type with an " +
          "@AssistedInject-annotated constructor."
      )
    }
  }

  @Test fun `the parameters of the factory function must match`() {
    compile(
      """
      package com.squareup.test
      
      import dagger.assisted.Assisted
      import dagger.assisted.AssistedFactory
      import dagger.assisted.AssistedInject
      
      class AssistedService @AssistedInject constructor(
        val int: Int,
        @Assisted val string: String
      )
      
      @AssistedFactory
      interface AssistedServiceFactory {
        fun create(string: String, other: String): AssistedService
      }
      """
    ) {
      assertThat(exitCode).isEqualTo(COMPILATION_ERROR)
      assertThat(messages).contains(
        "The parameters of the factory method must be assignable to the list of @Assisted " +
          "parameters in com.squareup.test.AssistedService."
      )
    }
  }

  @Test fun `the parameters of the factory function must match - different order`() {
    compile(
      """
      package com.squareup.test
      
      import dagger.assisted.Assisted
      import dagger.assisted.AssistedFactory
      import dagger.assisted.AssistedInject
      
      class AssistedService @AssistedInject constructor(
        val int: Int,
        @Assisted val string: String,
        @Assisted val string2: CharSequence
      )
      
      @AssistedFactory
      interface AssistedServiceFactory {
        fun create(string: CharSequence, other: String): AssistedService
      }
      """
    ) {
      assertThat(exitCode).isEqualTo(COMPILATION_ERROR)
      assertThat(messages).contains(
        "The parameters of the factory method must be assignable to the list of @Assisted " +
          "parameters in com.squareup.test.AssistedService."
      )
    }
  }

  @Test fun `two factory functions aren't supported`() {
    compile(
      """
      package com.squareup.test
      
      import dagger.assisted.Assisted
      import dagger.assisted.AssistedFactory
      import dagger.assisted.AssistedInject
      
      class AssistedService @AssistedInject constructor(
        val int: Int,
        @Assisted val string: String
      )
      
      @AssistedFactory
      interface AssistedServiceFactory {
        fun create(string: String): AssistedService
        fun create2(string: String): AssistedService
      }
      """
    ) {
      assertThat(exitCode).isEqualTo(COMPILATION_ERROR)
      assertThat(messages).contains(
        "The @AssistedFactory-annotated type should contain a single abstract, non-default " +
          "method but found multiple"
      )
    }
  }

  @Test fun `two factory functions aren't supported - extended class`() {
    compile(
      """
      package com.squareup.test
      
      import dagger.assisted.Assisted
      import dagger.assisted.AssistedFactory
      import dagger.assisted.AssistedInject
      
      class AssistedService @AssistedInject constructor(
        val int: Int,
        @Assisted val string: String
      )
      
      interface AssistedServiceFactory1 {
        fun createParent(string: String): AssistedService
      }
      
      @AssistedFactory
      interface AssistedServiceFactory : AssistedServiceFactory1 {
        fun create(string: String): AssistedService
      }
      """
    ) {
      assertThat(exitCode).isEqualTo(COMPILATION_ERROR)
      assertThat(messages).contains(
        "The @AssistedFactory-annotated type should contain a single abstract, non-default " +
          "method but found multiple"
      )
    }
  }

  @Test fun `a factory function is required`() {
    compile(
      """
      package com.squareup.test
      
      import dagger.assisted.Assisted
      import dagger.assisted.AssistedFactory
      import dagger.assisted.AssistedInject
      
      class AssistedService @AssistedInject constructor(
        val int: Int,
        @Assisted val string: String
      )
      
      @AssistedFactory
      interface AssistedServiceFactory
      """
    ) {
      assertThat(exitCode).isEqualTo(COMPILATION_ERROR)
      assertThat(messages).contains(
        "The @AssistedFactory-annotated type is missing an abstract, non-default method " +
          "whose return type matches the assisted injection type."
      )
    }
  }

  @Test fun `in an abstract class the factory function must be abstract`() {
    compile(
      """
      package com.squareup.test
      
      import dagger.assisted.Assisted
      import dagger.assisted.AssistedFactory
      import dagger.assisted.AssistedInject
      
      class AssistedService @AssistedInject constructor(
        val int: Int,
        @Assisted val string: String
      )
      
      @AssistedFactory
      abstract class AssistedServiceFactory {
        fun create(string: String): AssistedService = TODO()
      }
      """
    ) {
      assertThat(exitCode).isEqualTo(COMPILATION_ERROR)
      assertThat(messages).contains(
        "The @AssistedFactory-annotated type is missing an abstract, non-default method " +
          "whose return type matches the assisted injection type."
      )
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
