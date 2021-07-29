package com.squareup.anvil.compiler.dagger

import com.google.common.truth.Truth.assertThat
import com.squareup.anvil.compiler.USE_IR
import com.squareup.anvil.compiler.assistedService
import com.squareup.anvil.compiler.assistedServiceFactory
import com.squareup.anvil.compiler.daggerModule1
import com.squareup.anvil.compiler.internal.testing.compileAnvil
import com.squareup.anvil.compiler.internal.testing.createInstance
import com.squareup.anvil.compiler.internal.testing.factoryClass
import com.squareup.anvil.compiler.internal.testing.getPropertyValue
import com.squareup.anvil.compiler.internal.testing.implClass
import com.squareup.anvil.compiler.internal.testing.isStatic
import com.squareup.anvil.compiler.internal.testing.moduleFactoryClass
import com.squareup.anvil.compiler.internal.testing.use
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
  private final Provider<List<String>> p2_52215Provider;

  public AssistedService_Factory(Provider<Integer> p0_52215Provider, Provider<List<String>> p2_52215Provider) {
    this.p0_52215Provider = p0_52215Provider;
    this.p2_52215Provider = p2_52215Provider;
  }

  public AssistedService get(String string) {
    return newInstance(p0_52215Provider.get(), string, p2_52215Provider.get());
  }

  public static AssistedService_Factory create(Provider<Integer> p0_52215Provider, Provider<List<String>> p2_52215Provider) {
    return new AssistedService_Factory(p0_52215Provider, p2_52215Provider);
  }

  public static AssistedService newInstance(int p0_52215, String string, List<String> p2_52215) {
    AssistedService instance = new AssistedService(p0_52215, string);
    AssistedService_MembersInjector.injectMember(instance, p2_52215);
    return instance;
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
      import javax.inject.Inject
      
      data class AssistedService @AssistedInject constructor(
        val int: Int,
        @Assisted val string: String
      ) {
        @Inject lateinit var member: List<String>
      }
      
      @AssistedFactory
      interface AssistedServiceFactory {
        fun create(string: String): AssistedService
      }
      """
    ) {
      val factoryImplClass = assistedServiceFactory.implClass()
      val generatedFactoryInstance = assistedService.factoryClass()
        .createInstance(Provider { 5 }, Provider { listOf("Hello") })
      val factoryImplInstance = factoryImplClass.createInstance(generatedFactoryInstance)

      val staticMethods = factoryImplClass.declaredMethods.filter { it.isStatic }
      assertThat(staticMethods).hasSize(1)

      val factoryProvider = staticMethods.single { it.name == "create" }
        .invoke(null, generatedFactoryInstance) as Provider<*>
      assertThat(factoryProvider.get()::class.java).isEqualTo(factoryImplClass)

      val assistedServiceInstance = factoryImplClass.declaredMethods
        .filterNot { it.isStatic }
        .single { it.name == "create" }
        .invoke(factoryImplInstance, "Hello")

      assertThat(assistedServiceInstance).isEqualTo(assistedService.createInstance(5, "Hello"))
      assertThat(assistedServiceInstance.getPropertyValue("member")).isEqualTo(listOf("Hello"))
    }
  }

  @Test fun `the factory function is allowed to be provided by a super type`() {
    compile(
      """
      package com.squareup.test
      
      import dagger.assisted.Assisted
      import dagger.assisted.AssistedFactory
      import dagger.assisted.AssistedInject
      
      data class AssistedService @AssistedInject constructor(
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
      val generatedFactoryInstance = assistedService.factoryClass().createInstance(Provider { 5 })
      val factoryImplInstance = factoryImplClass.createInstance(generatedFactoryInstance)

      val staticMethods = factoryImplClass.declaredMethods.filter { it.isStatic }
      assertThat(staticMethods).hasSize(1)

      val factoryProvider = staticMethods.single { it.name == "create" }
        .invoke(null, generatedFactoryInstance) as Provider<*>
      assertThat(factoryProvider.get()::class.java).isEqualTo(factoryImplClass)

      val assistedServiceInstance = factoryImplClass.declaredMethods
        .filterNot { it.isStatic }
        .single { it.name == "create" }
        .invoke(factoryImplInstance, "Hello")

      assertThat(assistedServiceInstance).isEqualTo(assistedService.createInstance(5, "Hello"))
    }
  }

  @Test fun `the implementation function name matches the factory name`() {
    compile(
      """
      package com.squareup.test
      
      import dagger.assisted.Assisted
      import dagger.assisted.AssistedFactory
      import dagger.assisted.AssistedInject
      
      data class AssistedService @AssistedInject constructor(
        val int: Int,
        @Assisted val string: String
      )
      
      interface AssistedServiceFactorySuper {
        fun hephaestus(string: String): AssistedService
      }
      
      @AssistedFactory
      interface AssistedServiceFactory : AssistedServiceFactorySuper
      """
    ) {
      val factoryImplClass = assistedServiceFactory.implClass()
      val generatedFactoryInstance = assistedService.factoryClass().createInstance(Provider { 5 })
      val factoryImplInstance = factoryImplClass.createInstance(generatedFactoryInstance)

      val staticMethods = factoryImplClass.declaredMethods.filter { it.isStatic }
      assertThat(staticMethods).hasSize(1)

      val factoryProvider = staticMethods.single { it.name == "create" }
        .invoke(null, generatedFactoryInstance) as Provider<*>
      assertThat(factoryProvider.get()::class.java).isEqualTo(factoryImplClass)

      val assistedServiceInstance = factoryImplClass.declaredMethods
        .filterNot { it.isStatic }
        .single { it.name == "hephaestus" }
        .invoke(factoryImplInstance, "Hello")

      assertThat(assistedServiceInstance).isEqualTo(assistedService.createInstance(5, "Hello"))
    }
  }

  @Test fun `an abstract class is supported`() {
    compile(
      """
      package com.squareup.test
      
      import dagger.assisted.Assisted
      import dagger.assisted.AssistedFactory
      import dagger.assisted.AssistedInject
      
      data class AssistedService @AssistedInject constructor(
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
      val generatedFactoryInstance = assistedService.factoryClass().createInstance(Provider { 5 })
      val factoryImplInstance = factoryImplClass.createInstance(generatedFactoryInstance)

      val staticMethods = factoryImplClass.declaredMethods.filter { it.isStatic }
      assertThat(staticMethods).hasSize(1)

      val factoryProvider = staticMethods.single { it.name == "create" }
        .invoke(null, generatedFactoryInstance) as Provider<*>
      assertThat(factoryProvider.get()::class.java).isEqualTo(factoryImplClass)

      val assistedServiceInstance = factoryImplClass.declaredMethods
        .filterNot { it.isStatic }
        .single { it.name == "create" }
        .invoke(factoryImplInstance, "Hello")

      assertThat(assistedServiceInstance).isEqualTo(assistedService.createInstance(5, "Hello"))
    }
  }

  @Test fun `a protected factory function in an abstract class is supported`() {
    compile(
      """
      package com.squareup.test
      
      import dagger.assisted.Assisted
      import dagger.assisted.AssistedFactory
      import dagger.assisted.AssistedInject
      
      data class AssistedService @AssistedInject constructor(
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
      val generatedFactoryInstance = assistedService.factoryClass().createInstance(Provider { 5 })
      val factoryImplInstance = factoryImplClass.createInstance(generatedFactoryInstance)

      val staticMethods = factoryImplClass.declaredMethods.filter { it.isStatic }
      assertThat(staticMethods).hasSize(1)

      val factoryProvider = staticMethods.single { it.name == "create" }
        .invoke(null, generatedFactoryInstance) as Provider<*>
      assertThat(factoryProvider.get()::class.java).isEqualTo(factoryImplClass)

      val assistedServiceInstance = factoryImplClass.declaredMethods
        .filterNot { it.isStatic }
        .single { it.name == "create" }
        .use {
          it.invoke(factoryImplInstance, "Hello")
        }

      assertThat(assistedServiceInstance).isEqualTo(assistedService.createInstance(5, "Hello"))
    }
  }

  @Test fun `an implementation for a factory class with generic parameters is generated`() {
    compile(
      """
      package com.squareup.test
      
      import dagger.assisted.Assisted
      import dagger.assisted.AssistedFactory
      import dagger.assisted.AssistedInject
      
      data class AssistedService @AssistedInject constructor(
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
      val generatedFactoryInstance = assistedService.factoryClass().createInstance(Provider { 5 })
      val factoryImplInstance = factoryImplClass.createInstance(generatedFactoryInstance)

      val staticMethods = factoryImplClass.declaredMethods.filter { it.isStatic }
      assertThat(staticMethods).hasSize(1)

      val factoryProvider = staticMethods.single { it.name == "create" }
        .invoke(null, generatedFactoryInstance) as Provider<*>
      assertThat(factoryProvider.get()::class.java).isEqualTo(factoryImplClass)

      val assistedServiceInstance = factoryImplClass.declaredMethods
        .filterNot { it.isStatic }
        .single { it.name == "create" }
        .invoke(factoryImplInstance, listOf("Hello"))

      assertThat(assistedServiceInstance)
        .isEqualTo(assistedService.createInstance(5, listOf("Hello")))
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
      
      data class AssistedService<T : CharSequence> @AssistedInject constructor(
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
      val generatedFactoryInstance = assistedService.factoryClass().createInstance(Provider { 5 })
      val factoryImplInstance = factoryImplClass.createInstance(generatedFactoryInstance)

      val staticMethods = factoryImplClass.declaredMethods.filter { it.isStatic }
      assertThat(staticMethods).hasSize(1)

      val factoryProvider = staticMethods.single { it.name == "create" }
        .invoke(null, generatedFactoryInstance) as Provider<*>
      assertThat(factoryProvider.get()::class.java).isEqualTo(factoryImplClass)

      val assistedServiceInstance = factoryImplClass.declaredMethods
        .filterNot { it.isStatic }
        .single { it.name == "create" }
        .invoke(factoryImplInstance, "Hello")

      assertThat(assistedServiceInstance).isEqualTo(assistedService.createInstance(5, "Hello"))
    }
  }

  @Test fun `an implementation for a factory class with a type parameter bound by a generic`() {
    compile(
      """
      package com.squareup.test
      
      import dagger.assisted.Assisted
      import dagger.assisted.AssistedFactory
      import dagger.assisted.AssistedInject
      
      data class AssistedService<T : List<String>> @AssistedInject constructor(
        val int: Int,
        @Assisted val strings: T
      )
      
      @AssistedFactory
      interface AssistedServiceFactory<T : List<String>> {
        fun create(strings: T): AssistedService<T>
      }
      """
    ) {
      val factoryImplClass = assistedServiceFactory.implClass()
      val generatedFactoryInstance = assistedService.factoryClass().createInstance(Provider { 5 })
      val factoryImplInstance = factoryImplClass.createInstance(generatedFactoryInstance)

      val staticMethods = factoryImplClass.declaredMethods.filter { it.isStatic }
      assertThat(staticMethods).hasSize(1)

      val factoryProvider = staticMethods.single { it.name == "create" }
        .invoke(null, generatedFactoryInstance) as Provider<*>
      assertThat(factoryProvider.get()::class.java).isEqualTo(factoryImplClass)

      val assistedServiceInstance = factoryImplClass.declaredMethods
        .filterNot { it.isStatic }
        .single { it.name == "create" }
        .invoke(factoryImplInstance, listOf("Hello"))

      assertThat(assistedServiceInstance).isEqualTo(
        assistedService.createInstance(
          5,
          listOf("Hello")
        )
      )
    }
  }

  @Test fun `an implementation for a factory class with a type parameter bound by a where clause`() {
    compile(
      """
      package com.squareup.test
      
      import dagger.assisted.Assisted
      import dagger.assisted.AssistedFactory
      import dagger.assisted.AssistedInject
      
      data class AssistedService<T> @AssistedInject constructor(
        val int: Int,
        @Assisted val stringBuilder: T
      ) where T : Appendable, T : CharSequence {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is AssistedService<*>) return false
    
            if (int != other.int) return false
            if (stringBuilder.toString() != other.stringBuilder.toString()) return false
    
            return true
        }
      }
      
      @AssistedFactory
      interface AssistedServiceFactory<T> where T : Appendable, T : CharSequence {
        fun create(stringBuilder: T): AssistedService<T>
      }
      """
    ) {
      val factoryImplClass = assistedServiceFactory.implClass()
      val generatedFactoryInstance = assistedService.factoryClass().createInstance(Provider { 5 })
      val factoryImplInstance = factoryImplClass.createInstance(generatedFactoryInstance)

      val staticMethods = factoryImplClass.declaredMethods.filter { it.isStatic }
      assertThat(staticMethods).hasSize(1)

      val factoryProvider = staticMethods.single { it.name == "create" }
        .invoke(null, generatedFactoryInstance) as Provider<*>
      assertThat(factoryProvider.get()::class.java).isEqualTo(factoryImplClass)

      val assistedServiceInstance = factoryImplClass.declaredMethods
        .filterNot { it.isStatic }
        .single { it.name == "create" }
        .invoke(factoryImplInstance, StringBuilder("Hello"))

      assertThat(assistedServiceInstance).isEqualTo(
        assistedService.createInstance(
          5,
          StringBuilder("Hello")
        )
      )
    }
  }

  @Test fun `an implementation for an inner factory class is generated`() {
    compile(
      """
      package com.squareup.test
      
      import dagger.assisted.Assisted
      import dagger.assisted.AssistedFactory
      import dagger.assisted.AssistedInject
      
      data class AssistedService @AssistedInject constructor(
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
      val generatedFactoryInstance = assistedService.factoryClass().createInstance(Provider { 5 })
      val factoryImplInstance = factoryImplClass.createInstance(generatedFactoryInstance)

      val staticMethods = factoryImplClass.declaredMethods.filter { it.isStatic }
      assertThat(staticMethods).hasSize(1)

      val factoryProvider = staticMethods.single { it.name == "create" }
        .invoke(null, generatedFactoryInstance) as Provider<*>
      assertThat(factoryProvider.get()::class.java).isEqualTo(factoryImplClass)

      val assistedServiceInstance = factoryImplClass.declaredMethods
        .filterNot { it.isStatic }
        .single { it.name == "create" }
        .invoke(factoryImplInstance, "Hello")

      assertThat(assistedServiceInstance).isEqualTo(assistedService.createInstance(5, "Hello"))
    }
  }

  @Test fun `an implementation for a factory class with nullable parameters is generated`() {
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
      
      data class AssistedService @AssistedInject constructor(
        val int: Int,
        @Assisted val string: String?
      )
      
      @AssistedFactory
      interface AssistedServiceFactory {
        fun create(string: String?): AssistedService
      }
      """
    ) {
      val factoryImplClass = assistedServiceFactory.implClass()
      val generatedFactoryInstance = assistedService.factoryClass().createInstance(Provider { 5 })
      val factoryImplInstance = factoryImplClass.createInstance(generatedFactoryInstance)

      val staticMethods = factoryImplClass.declaredMethods.filter { it.isStatic }
      assertThat(staticMethods).hasSize(1)

      val factoryProvider = staticMethods.single { it.name == "create" }
        .invoke(null, generatedFactoryInstance) as Provider<*>
      assertThat(factoryProvider.get()::class.java).isEqualTo(factoryImplClass)

      val assistedServiceInstance = factoryImplClass.declaredMethods
        .filterNot { it.isStatic }
        .single { it.name == "create" }
        .invoke(factoryImplInstance, null)

      assertThat(assistedServiceInstance).isEqualTo(assistedService.createInstance(5, null))
    }
  }

  @Test fun `an assisted inject type must be returned`() {
    compile(
      """
      package com.squareup.test
      
      import dagger.assisted.AssistedFactory
      
      data class AssistedService(
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

  @Test fun `the parameters of the factory function must match - different count`() {
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
        fun create(charSequence: CharSequence, other: String): AssistedService
      }
      """
    ) {
      assertThat(exitCode).isEqualTo(COMPILATION_ERROR)
      assertThat(messages).contains(
        "The parameters in the factory method must match the @Assisted parameters " +
          "in com.squareup.test.AssistedService."
      )
    }
  }

  @Test fun `the parameters of the factory function must match - different type`() {
    compile(
      """
      package com.squareup.test
      
      import dagger.assisted.Assisted
      import dagger.assisted.AssistedFactory
      import dagger.assisted.AssistedInject
      
      class AssistedService @AssistedInject constructor(
        @Assisted val int: Int,
        @Assisted val string: String
      )
      
      @AssistedFactory
      interface AssistedServiceFactory {
        fun create(charSequence: CharSequence, other: String): AssistedService
      }
      """
    ) {
      assertThat(exitCode).isEqualTo(COMPILATION_ERROR)
      assertThat(messages).contains(
        "The parameters in the factory method must match the @Assisted parameters " +
          "in com.squareup.test.AssistedService."
      )
    }
  }

  @Test fun `a different order for the parameters of the factory function is allowed`() {
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
  public AssistedService create(long p0_1663806, String other) {
    return delegateFactory.get(other, p0_1663806);
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
      
      data class AssistedService @AssistedInject constructor(
        val int: Int,
        @Assisted val string: String,
        @Assisted val long: Long
      )
      
      @AssistedFactory
      interface AssistedServiceFactory {
        fun create(long: Long, other: String): AssistedService
      }
      """
    ) {
      val factoryImplClass = assistedServiceFactory.implClass()
      val generatedFactoryInstance = assistedService.factoryClass().createInstance(Provider { 5 })
      val factoryImplInstance = factoryImplClass.createInstance(generatedFactoryInstance)

      val staticMethods = factoryImplClass.declaredMethods.filter { it.isStatic }
      assertThat(staticMethods).hasSize(1)

      val factoryProvider = staticMethods.single { it.name == "create" }
        .invoke(null, generatedFactoryInstance) as Provider<*>
      assertThat(factoryProvider.get()::class.java).isEqualTo(factoryImplClass)

      val assistedServiceInstance = factoryImplClass.declaredMethods
        .filterNot { it.isStatic }
        .single { it.name == "create" }
        .invoke(factoryImplInstance, 4L, "Hello")

      assertThat(assistedServiceInstance)
        .isEqualTo(assistedService.createInstance(5, "Hello", 4L))
    }
  }

  @Test fun `a different order for the parameters of the factory function is allowed for parameters`() {
    compile(
      """
      package com.squareup.test
      
      import dagger.assisted.Assisted
      import dagger.assisted.AssistedFactory
      import dagger.assisted.AssistedInject
      
      data class AssistedService @AssistedInject constructor(
        @Assisted val string: String,
        @Assisted val long1: Long,
        @Assisted("two") val long2: Long,
        @Assisted("three") val long3: Long,
      )
      
      @AssistedFactory
      interface AssistedServiceFactory {
        fun create(
          long11: Long, 
          other: String, 
          @Assisted("three") long33: Long,
          @Assisted("two") long22: Long
        ): AssistedService
      }
      """
    ) {
      val factoryImplClass = assistedServiceFactory.implClass()
      val generatedFactoryInstance = assistedService.factoryClass().createInstance()
      val factoryImplInstance = factoryImplClass.createInstance(generatedFactoryInstance)

      val staticMethods = factoryImplClass.declaredMethods.filter { it.isStatic }
      assertThat(staticMethods).hasSize(1)

      val factoryProvider = staticMethods.single { it.name == "create" }
        .invoke(null, generatedFactoryInstance) as Provider<*>
      assertThat(factoryProvider.get()::class.java).isEqualTo(factoryImplClass)

      val assistedServiceInstance = factoryImplClass.declaredMethods
        .filterNot { it.isStatic }
        .single { it.name == "create" }
        .invoke(factoryImplInstance, 1L, "Hello", 3L, 2L)

      assertThat(assistedServiceInstance)
        .isEqualTo(assistedService.createInstance("Hello", 1L, 2L, 3L))
    }
  }

  @Test
  fun `a different order for the parameters of the factory function is allowed for generic types`() {
    compile(
      """
      package com.squareup.test
      
      import dagger.assisted.Assisted
      import dagger.assisted.AssistedFactory
      import dagger.assisted.AssistedInject
      
      data class AssistedService @AssistedInject constructor(
        @Assisted val strings: List<String>,
        @Assisted val ints: List<Int>
      )
      
      @AssistedFactory
      interface AssistedServiceFactory {
        fun create(ints: List<Int>, strings: List<String>): AssistedService
      }
      """
    ) {
      val factoryImplClass = assistedServiceFactory.implClass()
      val generatedFactoryInstance = assistedService.factoryClass().createInstance()
      val factoryImplInstance = factoryImplClass.createInstance(generatedFactoryInstance)

      val staticMethods = factoryImplClass.declaredMethods.filter { it.isStatic }
      assertThat(staticMethods).hasSize(1)

      val factoryProvider = staticMethods.single { it.name == "create" }
        .invoke(null, generatedFactoryInstance) as Provider<*>
      assertThat(factoryProvider.get()::class.java).isEqualTo(factoryImplClass)

      val assistedServiceInstance = factoryImplClass.declaredMethods
        .filterNot { it.isStatic }
        .single { it.name == "create" }
        .invoke(factoryImplInstance, listOf(1, 2), listOf("Hello"))

      assertThat(assistedServiceInstance)
        .isEqualTo(assistedService.createInstance(listOf("Hello"), listOf(1, 2)))
    }
  }

  @Test fun `a different order for the parameters of the factory function is allowed for type parameters`() {
    compile(
      """
      package com.squareup.test
      
      import dagger.assisted.Assisted
      import dagger.assisted.AssistedFactory
      import dagger.assisted.AssistedInject
      
      data class AssistedService<T : CharSequence, S : Number> @AssistedInject constructor(
        @Assisted val string: T,
        @Assisted val number: S
      )
      
      @AssistedFactory
      interface AssistedServiceFactory<T : CharSequence, S : Number> {
        fun create(number: S, string: T): AssistedService<T, S>
      }
      """
    ) {
      val factoryImplClass = assistedServiceFactory.implClass()
      val generatedFactoryInstance = assistedService.factoryClass().createInstance()
      val factoryImplInstance = factoryImplClass.createInstance(generatedFactoryInstance)

      val staticMethods = factoryImplClass.declaredMethods.filter { it.isStatic }
      assertThat(staticMethods).hasSize(1)

      val factoryProvider = staticMethods.single { it.name == "create" }
        .invoke(null, generatedFactoryInstance) as Provider<*>
      assertThat(factoryProvider.get()::class.java).isEqualTo(factoryImplClass)

      val assistedServiceInstance = factoryImplClass.declaredMethods
        .filterNot { it.isStatic }
        .single { it.name == "create" }
        .invoke(factoryImplInstance, 1, "Hello")

      assertThat(assistedServiceInstance).isEqualTo(
        assistedService.createInstance("Hello", 1)
      )
    }
  }

  @Test fun `equal types must use an identifier`() {
    compile(
      """
      package com.squareup.test
      
      import dagger.assisted.Assisted
      import dagger.assisted.AssistedFactory
      import dagger.assisted.AssistedInject
      
      data class AssistedService @AssistedInject constructor(
        @Assisted("one") val string1: String,
        @Assisted("two") val string2: String
      )
      
      @AssistedFactory
      interface AssistedServiceFactory {
        fun create(
          @Assisted("two") string2: String, 
          @Assisted("one") string1: String
        ): AssistedService
      }
      """
    ) {
      val factoryImplClass = assistedServiceFactory.implClass()
      val generatedFactoryInstance = assistedService.factoryClass().createInstance()
      val factoryImplInstance = factoryImplClass.createInstance(generatedFactoryInstance)

      val staticMethods = factoryImplClass.declaredMethods.filter { it.isStatic }
      assertThat(staticMethods).hasSize(1)

      val factoryProvider = staticMethods.single { it.name == "create" }
        .invoke(null, generatedFactoryInstance) as Provider<*>
      assertThat(factoryProvider.get()::class.java).isEqualTo(factoryImplClass)

      val assistedServiceInstance = factoryImplClass.declaredMethods
        .filterNot { it.isStatic }
        .single { it.name == "create" }
        .invoke(factoryImplInstance, "Hello", "World")

      assertThat(assistedServiceInstance)
        .isEqualTo(assistedService.createInstance("World", "Hello"))
    }
  }

  @Test fun `an implementation for a factory class is generated without a package`() {
    compile(
      """
      import dagger.assisted.Assisted
      import dagger.assisted.AssistedFactory
      import dagger.assisted.AssistedInject
      
      data class AssistedService @AssistedInject constructor(
        val int: Int,
        @Assisted val string: String
      )
      
      @AssistedFactory
      interface AssistedServiceFactory {
        fun create(string: String): AssistedService
      }
      """
    ) {
      val factoryImplClass = classLoader.loadClass("AssistedServiceFactory").implClass()
      val generatedFactoryInstance = classLoader.loadClass("AssistedService")
        .factoryClass().createInstance(Provider { 5 })
      val factoryImplInstance = factoryImplClass.createInstance(generatedFactoryInstance)

      val staticMethods = factoryImplClass.declaredMethods.filter { it.isStatic }
      assertThat(staticMethods).hasSize(1)

      val factoryProvider = staticMethods.single { it.name == "create" }
        .invoke(null, generatedFactoryInstance) as Provider<*>
      assertThat(factoryProvider.get()::class.java).isEqualTo(factoryImplClass)

      val assistedServiceInstance = factoryImplClass.declaredMethods
        .filterNot { it.isStatic }
        .single { it.name == "create" }
        .invoke(factoryImplInstance, "Hello")

      assertThat(assistedServiceInstance)
        .isEqualTo(classLoader.loadClass("AssistedService").createInstance(5, "Hello"))
    }
  }

  @Test fun `equal types with equal identifiers aren't supported`() {
    compile(
      """
      package com.squareup.test
      
      import dagger.assisted.Assisted
      import dagger.assisted.AssistedFactory
      import dagger.assisted.AssistedInject
      
      class Type
      
      data class AssistedService @AssistedInject constructor(
        @Assisted("one") val type1: Type,
        @Assisted("two") val type2: Type
      )
      
      @AssistedFactory
      interface AssistedServiceFactory {
        fun create(
          @Assisted("one") type1: Type, 
          @Assisted("one") type2: Type
        ): AssistedService
      }
      """
    ) {
      assertThat(exitCode).isEqualTo(COMPILATION_ERROR)
      assertThat(messages).contains(
        "@AssistedFactory method has duplicate @Assisted types: " +
          "@Assisted(\"one\") com.squareup.test.Type"
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
      
      data class AssistedService @AssistedInject constructor(
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
      
      data class AssistedService @AssistedInject constructor(
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
      
      data class AssistedService @AssistedInject constructor(
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
      
      data class AssistedService @AssistedInject constructor(
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

  @Test fun `assisted injections can be provided with a qualifier`() {
    compile(
      """
      package com.squareup.test
      
      import dagger.assisted.Assisted
      import dagger.assisted.AssistedFactory
      import dagger.assisted.AssistedInject
      import dagger.Module
      import dagger.Provides
      import javax.inject.Named
      
      data class AssistedService @AssistedInject constructor(
        val int: Int,
        @Assisted val string: String
      )
      
      @AssistedFactory
      interface AssistedServiceFactory {
        fun create(string: String): AssistedService
      }
      
      @Module
      object DaggerModule1 {
        @Provides @Named("") fun provideService(): AssistedService = AssistedService(5, "Hello")
      }
      """
    ) {
      val factoryImplClass = assistedServiceFactory.implClass()
      val generatedFactoryInstance = assistedService.factoryClass().createInstance(Provider { 5 })
      val factoryImplInstance = factoryImplClass.createInstance(generatedFactoryInstance)

      val assistedServiceInstance = factoryImplClass.declaredMethods
        .filterNot { it.isStatic }
        .single { it.name == "create" }
        .invoke(factoryImplInstance, "Hello")

      val providedService = daggerModule1
        .moduleFactoryClass("provideService")
        .declaredMethods
        .filter { it.isStatic }
        .single { it.name == "provideService" }
        .invoke(null)

      assertThat(assistedServiceInstance).isEqualTo(providedService)
    }
  }

  @Test fun `assisted lazy parameters are supported`() {
    compile(
      """
      package com.squareup.test
      
      import dagger.assisted.Assisted
      import dagger.assisted.AssistedFactory
      import dagger.assisted.AssistedInject
      import dagger.Lazy
      import dagger.Module
      import dagger.Provides
      
      data class AssistedService @AssistedInject constructor(
        val int: Lazy<Int>,
        @Assisted val string: Lazy<String>,
        val long: Long
      ) {
        override fun equals(other: Any?): Boolean {
          if (this === other) return true
          if (javaClass != other?.javaClass) return false
    
          other as AssistedService
    
          if (int.get() != other.int.get()) return false
          if (string.get() != other.string.get()) return false
          if (long != other.long) return false
    
          return true
        }
    
        override fun hashCode(): Int {
          var result = int.get()
          result = 31 * result + string.get().hashCode()
          result = 31 * result + long.hashCode()
          return result
        }
      }
      
      @AssistedFactory
      interface AssistedServiceFactory {
        fun create(string: Lazy<String>): AssistedService
      }
      """
    ) {
      val factoryImplClass = assistedServiceFactory.implClass()
      val generatedFactoryInstance = assistedService.factoryClass()
        .createInstance(Provider { 5 }, Provider { 7L })
      val factoryImplInstance = factoryImplClass.createInstance(generatedFactoryInstance)

      val staticMethods = factoryImplClass.declaredMethods.filter { it.isStatic }
      assertThat(staticMethods).hasSize(1)

      val factoryProvider = staticMethods.single { it.name == "create" }
        .invoke(null, generatedFactoryInstance) as Provider<*>
      assertThat(factoryProvider.get()::class.java).isEqualTo(factoryImplClass)

      val assistedServiceInstance = factoryImplClass.declaredMethods
        .filterNot { it.isStatic }
        .single { it.name == "create" }
        .invoke(factoryImplInstance, dagger.Lazy { "Hello" })

      assertThat(assistedServiceInstance)
        .isEqualTo(assistedService.createInstance(dagger.Lazy { 5 }, dagger.Lazy { "Hello" }, 7L))
    }
  }

  @Test fun `assisted provider parameters are supported`() {
    compile(
      """
      package com.squareup.test
      
      import dagger.assisted.Assisted
      import dagger.assisted.AssistedFactory
      import dagger.assisted.AssistedInject
      import dagger.Module
      import dagger.Provides
      import javax.inject.Provider
      
      data class AssistedService @AssistedInject constructor(
        val int: Provider<Int>,
        @Assisted val string: Provider<String>,
        val long: Long
      ) {
        override fun equals(other: Any?): Boolean {
          if (this === other) return true
          if (javaClass != other?.javaClass) return false
    
          other as AssistedService
    
          if (int.get() != other.int.get()) return false
          if (string.get() != other.string.get()) return false
          if (long != other.long) return false
    
          return true
        }
    
        override fun hashCode(): Int {
          var result = int.get()
          result = 31 * result + string.get().hashCode()
          result = 31 * result + long.hashCode()
          return result
        }
      }
      
      @AssistedFactory
      interface AssistedServiceFactory {
        fun create(string: Provider<String>): AssistedService
      }
      """
    ) {
      val factoryImplClass = assistedServiceFactory.implClass()
      val generatedFactoryInstance = assistedService.factoryClass()
        .createInstance(Provider { 5 }, Provider { 7L })
      val factoryImplInstance = factoryImplClass.createInstance(generatedFactoryInstance)

      val staticMethods = factoryImplClass.declaredMethods.filter { it.isStatic }
      assertThat(staticMethods).hasSize(1)

      val factoryProvider = staticMethods.single { it.name == "create" }
        .invoke(null, generatedFactoryInstance) as Provider<*>
      assertThat(factoryProvider.get()::class.java).isEqualTo(factoryImplClass)

      val assistedServiceInstance = factoryImplClass.declaredMethods
        .filterNot { it.isStatic }
        .single { it.name == "create" }
        .invoke(factoryImplInstance, Provider { "Hello" })

      assertThat(assistedServiceInstance)
        .isEqualTo(assistedService.createInstance(Provider { 5 }, Provider { "Hello" }, 7L))
    }
  }

  @Suppress("CHANGING_ARGUMENTS_EXECUTION_ORDER_FOR_NAMED_VARARGS")
  private fun compile(
    vararg sources: String,
    block: Result.() -> Unit = { }
  ): Result = compileAnvil(
    sources = sources,
    enableDaggerAnnotationProcessor = useDagger,
    generateDaggerFactories = !useDagger,
    useIR = USE_IR,
    block = block
  )
}
