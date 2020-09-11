package com.squareup.anvil.compiler.dagger

import com.google.common.truth.Truth.assertThat
import com.squareup.anvil.compiler.componentInterfaceAnvilModule
import com.squareup.anvil.compiler.daggerModule1
import com.squareup.anvil.compiler.innerModule
import com.squareup.anvil.compiler.isStatic
import com.squareup.anvil.compiler.moduleFactoryClass
import com.tschuchort.compiletesting.KotlinCompilation.ExitCode.COMPILATION_ERROR
import com.tschuchort.compiletesting.KotlinCompilation.Result
import dagger.Lazy
import dagger.internal.Factory
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters
import java.io.File
import javax.inject.Provider

@Suppress("UNCHECKED_CAST")
@RunWith(Parameterized::class)
class ProvidesMethodFactoryGeneratorTest(
  private val useDagger: Boolean
) {

  companion object {
    @Parameters(name = "Use Dagger: {0}")
    @JvmStatic fun useDagger(): Collection<Any> {
      return listOf(true, false)
    }
  }

  @Test fun `a factory class is generated for a provider method`() {
    /*
package com.squareup.test;

import dagger.internal.Factory;
import dagger.internal.Preconditions;
import javax.annotation.Generated;

@Generated(
  value = "dagger.internal.codegen.ComponentProcessor",
  comments = "https://dagger.dev"
)
@SuppressWarnings({
  "unchecked",
  "rawtypes"
})
public final class DaggerModule1_ProvideStringFactory implements Factory<String> {
  private final DaggerModule1 module;

  public DaggerModule1_ProvideStringFactory(DaggerModule1 module) {
    this.module = module;
  }

  @Override
  public String get() {
    return provideString(module);
  }

  public static DaggerModule1_ProvideStringFactory create(DaggerModule1 module) {
    return new DaggerModule1_ProvideStringFactory(module);
  }

  public static String provideString(DaggerModule1 instance) {
    return Preconditions.checkNotNull(instance.provideString(), "Cannot return null from a non-@Nullable @Provides method");
  }
}
     */

    compile(
        """
        package com.squareup.test
        
        @dagger.Module
        class DaggerModule1 {
          @dagger.Provides fun provideString(): String = "abc"
        }
        """
    ) {
      val factoryClass = daggerModule1.moduleFactoryClass("provideString")

      val constructor = factoryClass.declaredConstructors.single()
      assertThat(constructor.parameterTypes.toList()).containsExactly(daggerModule1)

      val staticMethods = factoryClass.declaredMethods.filter { it.isStatic }
      assertThat(staticMethods).hasSize(2)

      val module = daggerModule1.newInstance()

      val factoryInstance = staticMethods.single { it.name == "create" }
          .invoke(null, module)
      assertThat(factoryInstance::class.java).isEqualTo(factoryClass)

      val providedString = staticMethods.single { it.name == "provideString" }
          .invoke(null, module) as String

      assertThat(providedString).isEqualTo("abc")
      assertThat((factoryInstance as Factory<String>).get()).isEqualTo("abc")
    }
  }

  @Test fun `a factory class is generated for a provided Factory and avoids ambiguous import`() {
    /*
package com.squareup.test;

import dagger.internal.Factory;
import dagger.internal.Preconditions;
import javax.annotation.Generated;

@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes"
})
public final class DaggerModule1_ProvideFactoryFactory implements Factory<com.squareup.anvil.compiler.dagger.Factory> {
  private final DaggerModule1 module;

  public DaggerModule1_ProvideFactoryFactory(DaggerModule1 module) {
    this.module = module;
  }

  @Override
  public com.squareup.anvil.compiler.dagger.Factory get() {
    return provideFactory(module);
  }

  public static DaggerModule1_ProvideFactoryFactory create(DaggerModule1 module) {
    return new DaggerModule1_ProvideFactoryFactory(module);
  }

  public static com.squareup.anvil.compiler.dagger.Factory provideFactory(DaggerModule1 instance) {
    return Preconditions.checkNotNull(instance.provideFactory(), "Cannot return null from a non-@Nullable @Provides method");
  }
}
     */
    compile(
        """
        package com.squareup.test

        import com.squareup.anvil.compiler.dagger.Factory
        
        @dagger.Module
        class DaggerModule1 {
          @dagger.Provides fun provideFactory(): Factory = Factory
        }
        """
    ) {
      val factoryClass = daggerModule1.moduleFactoryClass("provideFactory")

      val constructor = factoryClass.declaredConstructors.single()
      assertThat(constructor.parameterTypes.toList()).containsExactly(daggerModule1)

      val staticMethods = factoryClass.declaredMethods.filter { it.isStatic }
      assertThat(staticMethods).hasSize(2)

      val module = daggerModule1.newInstance()

      val factoryInstance = staticMethods.single { it.name == "create" }
          .invoke(null, module)
      assertThat(factoryInstance::class.java).isEqualTo(factoryClass)

      val providedFactory = staticMethods.single { it.name == "provideFactory" }
          .invoke(null, module) as Any

      assertThat((factoryInstance as Factory<*>).get()).isSameInstanceAs(providedFactory)
    }
  }

  @Test fun `a factory class is generated for a provider method with imports`() {
    /*
package com.squareup.test;

import dagger.internal.Factory;
import dagger.internal.Preconditions;
import javax.annotation.Generated;

@Generated(
  value = "dagger.internal.codegen.ComponentProcessor",
  comments = "https://dagger.dev"
)
@SuppressWarnings({
  "unchecked",
  "rawtypes"
})
public final class DaggerModule1_ProvideStringFactory implements Factory<String> {
  private final DaggerModule1 module;

  public DaggerModule1_ProvideStringFactory(DaggerModule1 module) {
    this.module = module;
  }

  @Override
  public String get() {
    return provideString(module);
  }

  public static DaggerModule1_ProvideStringFactory create(DaggerModule1 module) {
    return new DaggerModule1_ProvideStringFactory(module);
  }

  public static String provideString(DaggerModule1 instance) {
    return Preconditions.checkNotNull(instance.provideString(), "Cannot return null from a non-@Nullable @Provides method");
  }
}
     */

    compile(
        """
        package com.squareup.test
        
        import dagger.Module
        import dagger.Provides
        
        @Module
        class DaggerModule1 {
          @Provides fun provideString(): String = "abc"
        }
        """
    ) {
      val factoryClass = daggerModule1.moduleFactoryClass("provideString")

      val constructor = factoryClass.declaredConstructors.single()
      assertThat(constructor.parameterTypes.toList()).containsExactly(daggerModule1)

      val staticMethods = factoryClass.declaredMethods.filter { it.isStatic }
      assertThat(staticMethods).hasSize(2)

      val module = daggerModule1.newInstance()

      val factoryInstance = staticMethods.single { it.name == "create" }
          .invoke(null, module)
      assertThat(factoryInstance::class.java).isEqualTo(factoryClass)

      val providedString = staticMethods.single { it.name == "provideString" }
          .invoke(null, module) as String

      assertThat(providedString).isEqualTo("abc")
      assertThat((factoryInstance as Factory<String>).get()).isEqualTo("abc")
    }
  }

  @Test
  fun `a factory class is generated for a provider method with imports and fully qualified return type`() { // ktlint-disable max-line-length
    /*
package com.squareup.test;

import dagger.internal.Factory;
import dagger.internal.Preconditions;
import java.io.File;
import javax.annotation.Generated;

@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes"
})
public final class DaggerModule1_ProvideFileFactory implements Factory<File> {
  private final DaggerModule1 module;

  public DaggerModule1_ProvideFileFactory(DaggerModule1 module) {
    this.module = module;
  }

  @Override
  public File get() {
    return provideFile(module);
  }

  public static DaggerModule1_ProvideFileFactory create(DaggerModule1 module) {
    return new DaggerModule1_ProvideFileFactory(module);
  }

  public static File provideFile(DaggerModule1 instance) {
    return Preconditions.checkNotNull(instance.provideFile(), "Cannot return null from a non-@Nullable @Provides method");
  }
}
     */

    compile(
        """
        package com.squareup.test
        
        import dagger.Module
        import dagger.Provides
        
        @Module
        class DaggerModule1 {
          @Provides fun provideFile(): java.io.File = java.io.File("")
        }
        """
    ) {
      val factoryClass = daggerModule1.moduleFactoryClass("provideFile")

      val constructor = factoryClass.declaredConstructors.single()
      assertThat(constructor.parameterTypes.toList()).containsExactly(daggerModule1)

      val staticMethods = factoryClass.declaredMethods.filter { it.isStatic }
      assertThat(staticMethods).hasSize(2)

      val module = daggerModule1.newInstance()

      val factoryInstance = staticMethods.single { it.name == "create" }
          .invoke(null, module)
      assertThat(factoryInstance::class.java).isEqualTo(factoryClass)

      val providedFile = staticMethods.single { it.name == "provideFile" }
          .invoke(null, module) as File

      assertThat(providedFile).isEqualTo(File(""))
      assertThat((factoryInstance as Factory<File>).get()).isEqualTo(File(""))
    }
  }

  @Test fun `a factory class is generated for a provider method with star imports`() {
    /*
package com.squareup.test;

import dagger.internal.Factory;
import dagger.internal.Preconditions;
import java.io.File;
import javax.annotation.Generated;

@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes"
})
public final class DaggerModule1_ProvideFileFactory implements Factory<File> {
  private final DaggerModule1 module;

  public DaggerModule1_ProvideFileFactory(DaggerModule1 module) {
    this.module = module;
  }

  @Override
  public File get() {
    return provideFile(module);
  }

  public static DaggerModule1_ProvideFileFactory create(DaggerModule1 module) {
    return new DaggerModule1_ProvideFileFactory(module);
  }

  public static File provideFile(DaggerModule1 instance) {
    return Preconditions.checkNotNull(instance.provideFile(), "Cannot return null from a non-@Nullable @Provides method");
  }
}
     */

    compile(
        """
        package com.squareup.test
        
        import dagger.*
        import java.io.File
        
        @Module
        class DaggerModule1 {
          @Provides fun provideFile(): File = File("")
        }
        """
    ) {
      val factoryClass = daggerModule1.moduleFactoryClass("provideFile")

      val constructor = factoryClass.declaredConstructors.single()
      assertThat(constructor.parameterTypes.toList()).containsExactly(daggerModule1)

      val staticMethods = factoryClass.declaredMethods.filter { it.isStatic }
      assertThat(staticMethods).hasSize(2)

      val module = daggerModule1.newInstance()

      val factoryInstance = staticMethods.single { it.name == "create" }
          .invoke(null, module)
      assertThat(factoryInstance::class.java).isEqualTo(factoryClass)

      val providedFile = staticMethods.single { it.name == "provideFile" }
          .invoke(null, module) as File

      assertThat(providedFile).isEqualTo(File(""))
      assertThat((factoryInstance as Factory<File>).get()).isEqualTo(File(""))
    }
  }

  @Test fun `a factory class is generated for a provider method with generic type`() {
    /*
package com.squareup.test;

import dagger.internal.Factory;
import dagger.internal.Preconditions;
import java.util.List;
import javax.annotation.Generated;

@Generated(
  value = "dagger.internal.codegen.ComponentProcessor",
  comments = "https://dagger.dev"
)
@SuppressWarnings({
  "unchecked",
  "rawtypes"
})
public final class DaggerModule1_ProvideStringListFactory implements Factory<List<String>> {
  private final DaggerModule1 module;

  public DaggerModule1_ProvideStringListFactory(DaggerModule1 module) {
    this.module = module;
  }

  @Override
  public List<String> get() {
    return provideStringList(module);
  }

  public static DaggerModule1_ProvideStringListFactory create(DaggerModule1 module) {
    return new DaggerModule1_ProvideStringListFactory(module);
  }

  public static List<String> provideStringList(DaggerModule1 instance) {
    return Preconditions.checkNotNull(instance.provideStringList(), "Cannot return null from a non-@Nullable @Provides method");
  }
}
     */

    compile(
        """
        package com.squareup.test
        
        @dagger.Module
        class DaggerModule1 {
          @dagger.Provides fun provideStringList(): List<String> = listOf("abc")
        }
        """
    ) {
      val factoryClass = daggerModule1.moduleFactoryClass("provideStringList")

      val constructor = factoryClass.declaredConstructors.single()
      assertThat(constructor.parameterTypes.toList()).containsExactly(daggerModule1)

      val staticMethods = factoryClass.declaredMethods.filter { it.isStatic }
      assertThat(staticMethods).hasSize(2)

      val module = daggerModule1.newInstance()

      val factoryInstance = staticMethods.single { it.name == "create" }
          .invoke(null, module)
      assertThat(factoryInstance::class.java).isEqualTo(factoryClass)

      val providedString = staticMethods.single { it.name == "provideStringList" }
          .invoke(null, module) as List<String>

      assertThat(providedString).containsExactly("abc")
      assertThat((factoryInstance as Factory<List<String>>).get()).containsExactly("abc")
    }
  }

  @Test fun `a factory class is generated for a provider method with advanced generics`() {
    /*
package com.squareup.test;

import dagger.internal.Factory;
import dagger.internal.Preconditions;
import java.util.List;
import javax.annotation.Generated;
import kotlin.Pair;

@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes"
})
public final class DaggerModule1_ProvidePairFactory implements Factory<Pair<Pair<String, Integer>, List<String>>> {
  private final DaggerModule1 module;

  public DaggerModule1_ProvidePairFactory(DaggerModule1 module) {
    this.module = module;
  }

  @Override
  public Pair<Pair<String, Integer>, List<String>> get() {
    return providePair(module);
  }

  public static DaggerModule1_ProvidePairFactory create(DaggerModule1 module) {
    return new DaggerModule1_ProvidePairFactory(module);
  }

  public static Pair<Pair<String, Integer>, List<String>> providePair(DaggerModule1 instance) {
    return Preconditions.checkNotNull(instance.providePair(), "Cannot return null from a non-@Nullable @Provides method");
  }
}
     */

    compile(
        """
        package com.squareup.test
        
        import dagger.*
        
        @Module
        class DaggerModule1 {
          @Provides fun providePair(): Pair<Pair<String, Int>, List<String>> = Pair(Pair("", 1), listOf(""))
        }
        """
    ) {
      val factoryClass = daggerModule1.moduleFactoryClass("providePair")

      val constructor = factoryClass.declaredConstructors.single()
      assertThat(constructor.parameterTypes.toList()).containsExactly(daggerModule1)

      val staticMethods = factoryClass.declaredMethods.filter { it.isStatic }

      val module = daggerModule1.newInstance()

      val factoryInstance = staticMethods.single { it.name == "create" }
          .invoke(null, module)
      assertThat(factoryInstance::class.java).isEqualTo(factoryClass)

      val providedString = staticMethods.single { it.name == "providePair" }
          .invoke(null, module) as Pair<Pair<String, Int>, List<String>>

      val expected = Pair(Pair("", 1), listOf(""))
      assertThat(providedString).isEqualTo(expected)
      assertThat((factoryInstance as Factory<Pair<Pair<String, Int>, List<String>>>).get())
          .isEqualTo(expected)
    }
  }

  @Test fun `two factory classes are generated for two provider methods`() {
    /*
package com.squareup.test;

import dagger.internal.Factory;
import dagger.internal.Preconditions;
import javax.annotation.Generated;

@Generated(
  value = "dagger.internal.codegen.ComponentProcessor",
  comments = "https://dagger.dev"
)
@SuppressWarnings({
  "unchecked",
  "rawtypes"
})
public final class DaggerModule1_ProvideStringFactory implements Factory<String> {
  private final DaggerModule1 module;

  public DaggerModule1_ProvideStringFactory(DaggerModule1 module) {
    this.module = module;
  }

  @Override
  public String get() {
    return provideString(module);
  }

  public static DaggerModule1_ProvideStringFactory create(DaggerModule1 module) {
    return new DaggerModule1_ProvideStringFactory(module);
  }

  public static String provideString(DaggerModule1 instance) {
    return Preconditions.checkNotNull(instance.provideString(), "Cannot return null from a non-@Nullable @Provides method");
  }
}
     */
    /*
package com.squareup.test;

import dagger.internal.Factory;
import javax.annotation.Generated;

@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes"
})
public final class DaggerModule1_ProvideIntFactory implements Factory<Integer> {
  private final DaggerModule1 module;

  public DaggerModule1_ProvideIntFactory(DaggerModule1 module) {
    this.module = module;
  }

  @Override
  public Integer get() {
    return provideInt(module);
  }

  public static DaggerModule1_ProvideIntFactory create(DaggerModule1 module) {
    return new DaggerModule1_ProvideIntFactory(module);
  }

  public static int provideInt(DaggerModule1 instance) {
    return instance.provideInt();
  }
}
     */

    compile(
        """
        package com.squareup.test
        
        @dagger.Module
        class DaggerModule1 {
          @dagger.Provides fun provideString(): String = "abc"
          @dagger.Provides fun provideInt(): Int = 5
        }
        """
    ) {
      fun <T> verifyClassGenerated(
        providerMethodName: String,
        expectedResult: T
      ) {
        val factoryClass = daggerModule1.moduleFactoryClass(providerMethodName)

        val constructor = factoryClass.declaredConstructors.single()
        assertThat(constructor.parameterTypes.toList()).containsExactly(daggerModule1)

        val staticMethods = factoryClass.declaredMethods.filter { it.isStatic }
        assertThat(staticMethods).hasSize(2)

        val module = daggerModule1.newInstance()

        val factoryInstance = staticMethods.single { it.name == "create" }
            .invoke(null, module)
        assertThat(factoryInstance::class.java).isEqualTo(factoryClass)

        val providedString = staticMethods.single { it.name == providerMethodName }
            .invoke(null, module) as T

        assertThat(providedString).isEqualTo(expectedResult)
        assertThat((factoryInstance as Factory<T>).get()).isEqualTo(expectedResult)
      }

      verifyClassGenerated("provideString", "abc")
      verifyClassGenerated("provideInt", 5)
    }
  }

  @Test fun `a factory class is generated for a provider method in an object`() {
    /*
package com.squareup.test;

import dagger.internal.Factory;
import dagger.internal.Preconditions;
import javax.annotation.Generated;

@Generated(
  value = "dagger.internal.codegen.ComponentProcessor",
  comments = "https://dagger.dev"
)
@SuppressWarnings({
  "unchecked",
  "rawtypes"
})
public final class DaggerModule1_ProvideStringFactory implements Factory<String> {
  @Override
  public String get() {
    return provideString();
  }

  public static DaggerModule1_ProvideStringFactory create() {
    return InstanceHolder.INSTANCE;
  }

  public static String provideString() {
    return Preconditions.checkNotNull(DaggerModule1.INSTANCE.provideString(), "Cannot return null from a non-@Nullable @Provides method");
  }

  private static final class InstanceHolder {
    private static final DaggerModule1_ProvideStringFactory INSTANCE = new DaggerModule1_ProvideStringFactory();
  }
}
     */

    compile(
        """
        package com.squareup.test
        
        @dagger.Module
        object DaggerModule1 {
          @dagger.Provides fun provideString(): String = "abc"
        }
        """
    ) {
      val factoryClass = daggerModule1.moduleFactoryClass("provideString")

      val constructor = factoryClass.declaredConstructors.single()
      assertThat(constructor.parameterTypes.toList()).isEmpty()

      val staticMethods = factoryClass.declaredMethods.filter { it.isStatic }

      val factoryInstance = staticMethods.single { it.name == "create" }
          .invoke(null)
      assertThat(factoryInstance::class.java).isEqualTo(factoryClass)

      val providedString = staticMethods.single { it.name == "provideString" }
          .invoke(null) as String

      assertThat(providedString).isEqualTo("abc")
      assertThat((factoryInstance as Factory<String>).get()).isEqualTo("abc")
    }
  }

  @Test fun `a factory class is generated for a provider method with parameters`() {
    /*
package com.squareup.test;

import dagger.internal.Factory;
import dagger.internal.Preconditions;
import javax.annotation.Generated;
import javax.inject.Provider;

@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes"
})
public final class DaggerModule1_ProvideStringFactory implements Factory<String> {
  private final DaggerModule1 module;

  private final Provider<String> param1Provider;

  private final Provider<CharSequence> param2Provider;

  public DaggerModule1_ProvideStringFactory(DaggerModule1 module, Provider<String> param1Provider,
      Provider<CharSequence> param2Provider) {
    this.module = module;
    this.param1Provider = param1Provider;
    this.param2Provider = param2Provider;
  }

  @Override
  public String get() {
    return provideString(module, param1Provider.get(), param2Provider.get());
  }

  public static DaggerModule1_ProvideStringFactory create(DaggerModule1 module,
      Provider<String> param1Provider, Provider<CharSequence> param2Provider) {
    return new DaggerModule1_ProvideStringFactory(module, param1Provider, param2Provider);
  }

  public static String provideString(DaggerModule1 instance, String param1, CharSequence param2) {
    return Preconditions.checkNotNull(instance.provideString(param1, param2), "Cannot return null from a non-@Nullable @Provides method");
  }
}
     */

    compile(
        """
        package com.squareup.test
        
        import dagger.Module
        import dagger.Provides
        import javax.inject.Named
        
        @Module
        class DaggerModule1 {
          @Provides fun provideString(
            @Named("abc") param1: String, 
            param2: CharSequence 
          ): String = param1 + param2
        }
        """
    ) {
      val factoryClass = daggerModule1.moduleFactoryClass("provideString")

      val constructor = factoryClass.declaredConstructors.single()
      assertThat(constructor.parameterTypes.toList())
          .containsExactly(daggerModule1, Provider::class.java, Provider::class.java)
          .inOrder()

      val staticMethods = factoryClass.declaredMethods.filter { it.isStatic }

      val module = daggerModule1.newInstance()

      val factoryInstance = staticMethods.single { it.name == "create" }
          .invoke(null, module, Provider { "a" }, Provider<CharSequence> { "b" })
      assertThat(factoryInstance::class.java).isEqualTo(factoryClass)

      val providedString = staticMethods.single { it.name == "provideString" }
          .invoke(null, module, "a", "b" as CharSequence) as String

      assertThat(providedString).isEqualTo("ab")
      assertThat((factoryInstance as Factory<String>).get()).isEqualTo("ab")
    }
  }

  @Test fun `a factory class is generated for a provider method with provider parameters`() {
    /*
package com.squareup.test;

import dagger.internal.Factory;
import dagger.internal.Preconditions;
import java.util.List;
import javax.annotation.Generated;
import javax.inject.Provider;

@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes"
})
public final class DaggerModule1_ProvideStringFactory implements Factory<String> {
  private final DaggerModule1 module;

  private final Provider<String> param1Provider;

  private final Provider<CharSequence> param2Provider;

  private final Provider<List<String>> param3Provider;

  public DaggerModule1_ProvideStringFactory(DaggerModule1 module, Provider<String> param1Provider,
      Provider<CharSequence> param2Provider, Provider<List<String>> param3Provider) {
    this.module = module;
    this.param1Provider = param1Provider;
    this.param2Provider = param2Provider;
    this.param3Provider = param3Provider;
  }

  @Override
  public String get() {
    return provideString(module, param1Provider.get(), param2Provider, param3Provider);
  }

  public static DaggerModule1_ProvideStringFactory create(DaggerModule1 module,
      Provider<String> param1Provider, Provider<CharSequence> param2Provider,
      Provider<List<String>> param3Provider) {
    return new DaggerModule1_ProvideStringFactory(module, param1Provider, param2Provider, param3Provider);
  }

  public static String provideString(DaggerModule1 instance, String param1,
      Provider<CharSequence> param2, Provider<List<String>> param3) {
    return Preconditions.checkNotNull(instance.provideString(param1, param2, param3), "Cannot return null from a non-@Nullable @Provides method");
  }
}
     */

    compile(
        """
        package com.squareup.test
        
        import dagger.Module
        import dagger.Provides
        import javax.inject.Named
        import javax.inject.Provider
        
        @Module
        class DaggerModule1 {
          @Provides fun provideString(
            @Named("abc") param1: String, 
            param2: Provider<CharSequence>, 
            param3: Provider<List<String>> 
          ): String = param1 + param2.get() + param3.get()[0]
        }
        """
    ) {
      val factoryClass = daggerModule1.moduleFactoryClass("provideString")

      val constructor = factoryClass.declaredConstructors.single()
      assertThat(constructor.parameterTypes.toList())
          .containsExactly(
              daggerModule1, Provider::class.java, Provider::class.java, Provider::class.java
          )
          .inOrder()

      val staticMethods = factoryClass.declaredMethods.filter { it.isStatic }

      val module = daggerModule1.newInstance()

      val factoryInstance = staticMethods.single { it.name == "create" }
          .invoke(null, module, Provider { "a" }, Provider { "b" }, Provider { listOf("c") })
      assertThat(factoryInstance::class.java).isEqualTo(factoryClass)

      val providedString = staticMethods.single { it.name == "provideString" }
          .invoke(null, module, "a", Provider<CharSequence> { "b" }, Provider { listOf("c") })
          as String

      assertThat(providedString).isEqualTo("abc")
      assertThat((factoryInstance as Factory<String>).get()).isEqualTo("abc")
    }
  }

  @Test fun `a factory class is generated for a provider method with lazy parameters`() {
    /*
package com.squareup.test;

import dagger.Lazy;
import dagger.internal.DoubleCheck;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import java.util.List;
import javax.annotation.Generated;
import javax.inject.Provider;

@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes"
})
public final class DaggerModule1_ProvideStringFactory implements Factory<String> {
  private final DaggerModule1 module;

  private final Provider<String> param1Provider;

  private final Provider<CharSequence> param2Provider;

  private final Provider<List<String>> param3Provider;

  public DaggerModule1_ProvideStringFactory(DaggerModule1 module, Provider<String> param1Provider,
      Provider<CharSequence> param2Provider, Provider<List<String>> param3Provider) {
    this.module = module;
    this.param1Provider = param1Provider;
    this.param2Provider = param2Provider;
    this.param3Provider = param3Provider;
  }

  @Override
  public String get() {
    return provideString(module, param1Provider.get(), DoubleCheck.lazy(param2Provider), DoubleCheck.lazy(param3Provider));
  }

  public static DaggerModule1_ProvideStringFactory create(DaggerModule1 module,
      Provider<String> param1Provider, Provider<CharSequence> param2Provider,
      Provider<List<String>> param3Provider) {
    return new DaggerModule1_ProvideStringFactory(module, param1Provider, param2Provider, param3Provider);
  }

  public static String provideString(DaggerModule1 instance, String param1,
      Lazy<CharSequence> param2, Lazy<List<String>> param3) {
    return Preconditions.checkNotNull(instance.provideString(param1, param2, param3), "Cannot return null from a non-@Nullable @Provides method");
  }
}
     */

    compile(
        """
        package com.squareup.test
        
        import dagger.Lazy
        import dagger.Module
        import dagger.Provides
        import javax.inject.Named
        
        @Module
        class DaggerModule1 {
          @Provides fun provideString(
            @Named("abc") param1: String, 
            param2: Lazy<CharSequence>, 
            param3: Lazy<List<String>> 
          ): String = param1 + param2.get() + param3.get()[0]
        }
        """
    ) {
      val factoryClass = daggerModule1.moduleFactoryClass("provideString")

      val constructor = factoryClass.declaredConstructors.single()
      assertThat(constructor.parameterTypes.toList())
          .containsExactly(
              daggerModule1, Provider::class.java, Provider::class.java, Provider::class.java
          )
          .inOrder()

      val staticMethods = factoryClass.declaredMethods.filter { it.isStatic }

      val module = daggerModule1.newInstance()

      val factoryInstance = staticMethods.single { it.name == "create" }
          .invoke(null, module, Provider { "a" }, Provider { "b" }, Provider { listOf("c") })
          as Factory<String>
      assertThat(factoryInstance::class.java).isEqualTo(factoryClass)

      val providedString = staticMethods.single { it.name == "provideString" }
          .invoke(null, module, "a", Lazy<CharSequence> { "b" }, Lazy { listOf("c") })
          as String

      assertThat(providedString).isEqualTo("abc")
      assertThat(factoryInstance.get()).isEqualTo("abc")
    }
  }

  @Test fun `a factory class is generated for a provider method with generic parameters`() {
    /*
package com.squareup.test;

import dagger.internal.Factory;
import dagger.internal.Preconditions;
import java.util.List;
import javax.annotation.Generated;
import javax.inject.Provider;
import kotlin.Pair;

@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes"
})
public final class DaggerModule1_ProvideStringFactory implements Factory<String> {
  private final DaggerModule1 module;

  private final Provider<List<String>> param1Provider;

  private final Provider<Pair<Pair<String, Integer>, ? extends List<String>>> param2Provider;

  public DaggerModule1_ProvideStringFactory(DaggerModule1 module,
      Provider<List<String>> param1Provider,
      Provider<Pair<Pair<String, Integer>, ? extends List<String>>> param2Provider) {
    this.module = module;
    this.param1Provider = param1Provider;
    this.param2Provider = param2Provider;
  }

  @Override
  public String get() {
    return provideString(module, param1Provider.get(), param2Provider.get());
  }

  public static DaggerModule1_ProvideStringFactory create(DaggerModule1 module,
      Provider<List<String>> param1Provider,
      Provider<Pair<Pair<String, Integer>, ? extends List<String>>> param2Provider) {
    return new DaggerModule1_ProvideStringFactory(module, param1Provider, param2Provider);
  }

  public static String provideString(DaggerModule1 instance, List<String> param1,
      Pair<Pair<String, Integer>, ? extends List<String>> param2) {
    return Preconditions.checkNotNull(instance.provideString(param1, param2), "Cannot return null from a non-@Nullable @Provides method");
  }
}
     */

    compile(
        """
        package com.squareup.test
        
        import dagger.Module
        import dagger.Provides
        import javax.inject.Named
        
        @Module
        class DaggerModule1 {
          @Provides fun provideString(
            @Named("abc") param1: List<String>, 
            param2: Pair<Pair<String, Int>, List<String>> 
          ): String = param1[0] + param2.first.first + param2.second[0]
        }
        """
    ) {
      val factoryClass = daggerModule1.moduleFactoryClass("provideString")

      val constructor = factoryClass.declaredConstructors.single()
      assertThat(constructor.parameterTypes.toList())
          .containsExactly(daggerModule1, Provider::class.java, Provider::class.java)
          .inOrder()

      val staticMethods = factoryClass.declaredMethods.filter { it.isStatic }

      val module = daggerModule1.newInstance()

      val factoryInstance = staticMethods.single { it.name == "create" }
          .invoke(null, module, Provider { listOf("a") },
              Provider { Pair(Pair("b", 1), listOf("c")) }
          )
      assertThat(factoryInstance::class.java).isEqualTo(factoryClass)

      val providedString = staticMethods.single { it.name == "provideString" }
          .invoke(null, module, listOf("a"), Pair(Pair("b", 1), listOf("c"))) as String

      assertThat(providedString).isEqualTo("abc")
      assertThat((factoryInstance as Factory<String>).get()).isEqualTo("abc")
    }
  }

  @Test fun `a factory class is generated for a provider method with parameters in an object`() {
    /*
package com.squareup.test;

import dagger.internal.Factory;
import dagger.internal.Preconditions;
import javax.annotation.Generated;
import javax.inject.Provider;

@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes"
})
public final class DaggerModule1_ProvideStringFactory implements Factory<String> {
  private final Provider<String> param1Provider;

  private final Provider<CharSequence> param2Provider;

  public DaggerModule1_ProvideStringFactory(Provider<String> param1Provider,
      Provider<CharSequence> param2Provider) {
    this.param1Provider = param1Provider;
    this.param2Provider = param2Provider;
  }

  @Override
  public String get() {
    return provideString(param1Provider.get(), param2Provider.get());
  }

  public static DaggerModule1_ProvideStringFactory create(Provider<String> param1Provider,
      Provider<CharSequence> param2Provider) {
    return new DaggerModule1_ProvideStringFactory(param1Provider, param2Provider);
  }

  public static String provideString(String param1, CharSequence param2) {
    return Preconditions.checkNotNull(DaggerModule1.INSTANCE.provideString(param1, param2), "Cannot return null from a non-@Nullable @Provides method");
  }
}
     */

    compile(
        """
        package com.squareup.test
        
        import dagger.Module
        import dagger.Provides
        import javax.inject.Named
        
        @Module
        object DaggerModule1 {
          @Provides fun provideString(
            @Named("abc") param1: String, 
            param2: CharSequence 
          ): String = param1 + param2
        }
        """
    ) {
      val factoryClass = daggerModule1.moduleFactoryClass("provideString")

      val constructor = factoryClass.declaredConstructors.single()
      assertThat(constructor.parameterTypes.toList())
          .containsExactly(Provider::class.java, Provider::class.java)
          .inOrder()

      val staticMethods = factoryClass.declaredMethods.filter { it.isStatic }

      val factoryInstance = staticMethods.single { it.name == "create" }
          .invoke(null, Provider { "a" }, Provider<CharSequence> { "b" })
      assertThat(factoryInstance::class.java).isEqualTo(factoryClass)

      val providedString = staticMethods.single { it.name == "provideString" }
          .invoke(null, "a", "b" as CharSequence) as String

      assertThat(providedString).isEqualTo("ab")
      assertThat((factoryInstance as Factory<String>).get()).isEqualTo("ab")
    }
  }

  @Test
  fun `a factory class is generated for a provider method with parameters in a companion object`() {
    /*
package com.squareup.test;

import dagger.internal.Factory;
import dagger.internal.Preconditions;
import javax.annotation.Generated;
import javax.inject.Provider;

@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes"
})
public final class DaggerModule1_Companion_ProvideStringFactory implements Factory<String> {
  private final Provider<String> param1Provider;

  private final Provider<CharSequence> param2Provider;

  public DaggerModule1_Companion_ProvideStringFactory(Provider<String> param1Provider,
      Provider<CharSequence> param2Provider) {
    this.param1Provider = param1Provider;
    this.param2Provider = param2Provider;
  }

  @Override
  public String get() {
    return provideString(param1Provider.get(), param2Provider.get());
  }

  public static DaggerModule1_Companion_ProvideStringFactory create(Provider<String> param1Provider,
      Provider<CharSequence> param2Provider) {
    return new DaggerModule1_Companion_ProvideStringFactory(param1Provider, param2Provider);
  }

  public static String provideString(String param1, CharSequence param2) {
    return Preconditions.checkNotNull(DaggerModule1.Companion.provideString(param1, param2), "Cannot return null from a non-@Nullable @Provides method");
  }
}
     */

    compile(
        """
        package com.squareup.test
        
        import dagger.Module
        import dagger.Provides
        import javax.inject.Named
        
        @Module
        abstract class DaggerModule1 {
          @dagger.Binds abstract fun bindString(string: String): CharSequence
          
          companion object {
            @Provides fun provideString(
              @Named("abc") param1: String, 
              param2: CharSequence 
            ): String = param1 + param2
          }
        }
        """
    ) {
      val factoryClass = daggerModule1.moduleFactoryClass("provideString", companion = true)

      val constructor = factoryClass.declaredConstructors.single()
      assertThat(constructor.parameterTypes.toList())
          .containsExactly(Provider::class.java, Provider::class.java)
          .inOrder()

      val staticMethods = factoryClass.declaredMethods.filter { it.isStatic }

      val factoryInstance = staticMethods.single { it.name == "create" }
          .invoke(null, Provider { "a" }, Provider<CharSequence> { "b" })
      assertThat(factoryInstance::class.java).isEqualTo(factoryClass)

      val providedString = staticMethods.single { it.name == "provideString" }
          .invoke(null, "a", "b" as CharSequence) as String

      assertThat(providedString).isEqualTo("ab")
      assertThat((factoryInstance as Factory<String>).get()).isEqualTo("ab")
    }
  }

  @Test fun `a factory class is generated for a nullable provider method`() {
    /*
package com.squareup.test;

import dagger.internal.Factory;
import javax.annotation.Generated;
import org.jetbrains.annotations.Nullable;

@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes"
})
public final class DaggerModule1_ProvideStringFactory implements Factory<String> {
  private final DaggerModule1 module;

  public DaggerModule1_ProvideStringFactory(DaggerModule1 module) {
    this.module = module;
  }

  @Override
  @Nullable
  public String get() {
    return provideString(module);
  }

  public static DaggerModule1_ProvideStringFactory create(DaggerModule1 module) {
    return new DaggerModule1_ProvideStringFactory(module);
  }

  @Nullable
  public static String provideString(DaggerModule1 instance) {
    return instance.provideString();
  }
}
     */

    compile(
        """
        package com.squareup.test
        
        import dagger.Module
        import dagger.Provides
        import javax.inject.Named
        
        @Module
        class DaggerModule1 {
          @Provides fun provideString(): String? = null
        }
        """
    ) {
      val factoryClass = daggerModule1.moduleFactoryClass("provideString")

      val constructor = factoryClass.declaredConstructors.single()
      assertThat(constructor.parameterTypes.toList()).containsExactly(daggerModule1)

      val staticMethods = factoryClass.declaredMethods.filter { it.isStatic }

      val module = daggerModule1.newInstance()

      val factoryInstance = staticMethods.single { it.name == "create" }
          .invoke(null, module)
      assertThat(factoryInstance::class.java).isEqualTo(factoryClass)

      val providedString = staticMethods.single { it.name == "provideString" }
          .invoke(null, module) as? String

      assertThat(providedString).isNull()
      assertThat((factoryInstance as Factory<String>).get()).isNull()
    }
  }

  @Test fun `a factory class is generated for a nullable provider method in an object`() {
    /*
package com.squareup.test;

import dagger.internal.Factory;
import javax.annotation.Generated;
import org.jetbrains.annotations.Nullable;

@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes"
})
public final class DaggerModule1_ProvideStringFactory implements Factory<String> {
  @Override
  @Nullable
  public String get() {
    return provideString();
  }

  public static DaggerModule1_ProvideStringFactory create() {
    return InstanceHolder.INSTANCE;
  }

  @Nullable
  public static String provideString() {
    return DaggerModule1.INSTANCE.provideString();
  }

  private static final class InstanceHolder {
    private static final DaggerModule1_ProvideStringFactory INSTANCE = new DaggerModule1_ProvideStringFactory();
  }
}
     */

    compile(
        """
        package com.squareup.test
        
        import dagger.Module
        import dagger.Provides
        import javax.inject.Named
        
        @Module
        object DaggerModule1 {
          @Provides fun provideString(): String? = null
        }
        """
    ) {
      val factoryClass = daggerModule1.moduleFactoryClass("provideString")

      val constructor = factoryClass.declaredConstructors.single()
      assertThat(constructor.parameterTypes.toList()).isEmpty()

      val staticMethods = factoryClass.declaredMethods.filter { it.isStatic }

      val factoryInstance = staticMethods.single { it.name == "create" }
          .invoke(null)
      assertThat(factoryInstance::class.java).isEqualTo(factoryClass)

      val providedString = staticMethods.single { it.name == "provideString" }
          .invoke(null) as? String

      assertThat(providedString).isNull()
      assertThat((factoryInstance as Factory<String>).get()).isNull()
    }
  }

  @Test
  fun `a factory class is generated for a nullable provider method with nullable parameters`() {
    /*
package com.squareup.test;

import dagger.internal.Factory;
import javax.annotation.Generated;
import javax.inject.Provider;
import org.jetbrains.annotations.Nullable;

@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes"
})
public final class DaggerModule1_ProvideStringFactory implements Factory<String> {
  private final DaggerModule1 module;

  private final Provider<String> param1Provider;

  private final Provider<CharSequence> param2Provider;

  public DaggerModule1_ProvideStringFactory(DaggerModule1 module, Provider<String> param1Provider,
      Provider<CharSequence> param2Provider) {
    this.module = module;
    this.param1Provider = param1Provider;
    this.param2Provider = param2Provider;
  }

  @Override
  @Nullable
  public String get() {
    return provideString(module, param1Provider.get(), param2Provider.get());
  }

  public static DaggerModule1_ProvideStringFactory create(DaggerModule1 module,
      Provider<String> param1Provider, Provider<CharSequence> param2Provider) {
    return new DaggerModule1_ProvideStringFactory(module, param1Provider, param2Provider);
  }

  @Nullable
  public static String provideString(DaggerModule1 instance, String param1, CharSequence param2) {
    return instance.provideString(param1, param2);
  }
}
     */

    compile(
        """
        package com.squareup.test
        
        import dagger.Module
        import dagger.Provides
        import javax.inject.Named
        
        @Module
        class DaggerModule1 {
          @Provides fun provideString(
            @Named("abc") param1: String?, 
            param2: CharSequence? 
          ): String? {
            check(param1 == null)
            check(param2 == null)
            return null
          }
        }
        """
    ) {
      val factoryClass = daggerModule1.moduleFactoryClass("provideString")

      val constructor = factoryClass.declaredConstructors.single()
      assertThat(constructor.parameterTypes.toList())
          .containsExactly(daggerModule1, Provider::class.java, Provider::class.java)
          .inOrder()

      val staticMethods = factoryClass.declaredMethods.filter { it.isStatic }

      val module = daggerModule1.newInstance()

      val factoryInstance = staticMethods.single { it.name == "create" }
          .invoke(null, module, Provider { null }, Provider { null })
      assertThat(factoryInstance::class.java).isEqualTo(factoryClass)

      val providedString = staticMethods.single { it.name == "provideString" }
          .invoke(null, module, null, null) as? String

      assertThat(providedString).isNull()
      assertThat((factoryInstance as Factory<String>).get()).isNull()
    }
  }

  @Test fun `no factory class is generated for a binding method in an abstract class`() {
    compile(
        """
        package com.squareup.test
        
        @dagger.Module
        abstract class DaggerModule1 {
          @dagger.Binds abstract fun bindString(string: String): CharSequence
        }
        """
    ) {
      if (useDagger) {
        assertThat(sourcesGeneratedByAnnotationProcessor).isEmpty()
      }
    }
  }

  @Test fun `a factory class is generated for a provider method in a companion object`() {
    /*
package com.squareup.test;

import dagger.internal.Factory;
import dagger.internal.Preconditions;
import javax.annotation.Generated;

@Generated(
  value = "dagger.internal.codegen.ComponentProcessor",
  comments = "https://dagger.dev"
)
@SuppressWarnings({
  "unchecked",
  "rawtypes"
})
public final class DaggerModule1_Companion_ProvideStringFactory implements Factory<String> {
  @Override
  public String get() {
    return provideString();
  }

  public static DaggerModule1_Companion_ProvideStringFactory create() {
    return InstanceHolder.INSTANCE;
  }

  public static String provideString() {
    return Preconditions.checkNotNull(DaggerModule1.Companion.provideString(), "Cannot return null from a non-@Nullable @Provides method");
  }

  private static final class InstanceHolder {
    private static final DaggerModule1_Companion_ProvideStringFactory INSTANCE = new DaggerModule1_Companion_ProvideStringFactory();
  }
}
     */

    compile(
        """
        package com.squareup.test
        
        @dagger.Module
        abstract class DaggerModule1 {
          @dagger.Binds abstract fun bindString(string: String): CharSequence
          
          companion object {
            @dagger.Provides fun provideString(): String = "abc"          
          }
        }
        """
    ) {
      val factoryClass = daggerModule1.moduleFactoryClass("provideString", companion = true)

      val constructor = factoryClass.declaredConstructors.single()
      assertThat(constructor.parameterTypes.toList()).isEmpty()

      val staticMethods = factoryClass.declaredMethods.filter { it.isStatic }

      val factoryInstance = staticMethods.single { it.name == "create" }
          .invoke(null)
      assertThat(factoryInstance::class.java).isEqualTo(factoryClass)

      val providedString = staticMethods.single { it.name == "provideString" }
          .invoke(null) as String

      assertThat(providedString).isEqualTo("abc")
      assertThat((factoryInstance as Factory<String>).get()).isEqualTo("abc")
    }
  }

  @Test fun `a factory class is generated for a provider method in an inner module`() {
    /*
package com.squareup.test;

import dagger.internal.Factory;
import dagger.internal.Preconditions;
import javax.annotation.Generated;

@Generated(
  value = "dagger.internal.codegen.ComponentProcessor",
  comments = "https://dagger.dev"
)
@SuppressWarnings({
  "unchecked",
  "rawtypes"
})
public final class ComponentInterface_InnerModule_ProvideStringFactory implements Factory<String> {
  @Override
  public String get() {
    return provideString();
  }

  public static ComponentInterface_InnerModule_ProvideStringFactory create() {
    return InstanceHolder.INSTANCE;
  }

  public static String provideString() {
    return Preconditions.checkNotNull(ComponentInterface.InnerModule.INSTANCE.provideString(), "Cannot return null from a non-@Nullable @Provides method");
  }

  private static final class InstanceHolder {
    private static final ComponentInterface_InnerModule_ProvideStringFactory INSTANCE = new ComponentInterface_InnerModule_ProvideStringFactory();
  }
}
     */

    compile(
        """
        package com.squareup.test
        
        interface ComponentInterface {
          @dagger.Module
          object InnerModule {
            @dagger.Provides fun provideString(): String = "abc"          
          }
        }
        """
    ) {
      val factoryClass = innerModule.moduleFactoryClass("provideString")

      val constructor = factoryClass.declaredConstructors.single()
      assertThat(constructor.parameterTypes.toList()).isEmpty()

      val staticMethods = factoryClass.declaredMethods.filter { it.isStatic }

      val factoryInstance = staticMethods.single { it.name == "create" }
          .invoke(null)
      assertThat(factoryInstance::class.java).isEqualTo(factoryClass)

      val providedString = staticMethods.single { it.name == "provideString" }
          .invoke(null) as String

      assertThat(providedString).isEqualTo("abc")
      assertThat((factoryInstance as Factory<String>).get()).isEqualTo("abc")
    }
  }

  @Test fun `a factory class is generated for a provider method returning an inner class`() {
    compile(
        """
        package com.squareup.test
        
        import dagger.Module
        import dagger.Provides
        import com.squareup.anvil.compiler.dagger.OuterClass
        
        @Module
        object DaggerModule1 {
          @Provides fun provideInnerClass(): OuterClass.InnerClass = OuterClass.InnerClass()
        }
        """
    ) {
      val factoryClass = daggerModule1.moduleFactoryClass("provideInnerClass")

      val constructor = factoryClass.declaredConstructors.single()
      assertThat(constructor.parameterTypes.toList()).isEmpty()
    }
  }

  @Test
  fun `a factory class is generated for a provider method in a companion object in an inner module`() { // ktlint-disable max-line-length
    /*
package com.squareup.test;

import dagger.internal.Factory;
import dagger.internal.Preconditions;
import javax.annotation.Generated;

@Generated(
  value = "dagger.internal.codegen.ComponentProcessor",
  comments = "https://dagger.dev"
)
@SuppressWarnings({
  "unchecked",
  "rawtypes"
})
public final class ComponentInterface_InnerModule_Companion_ProvideStringFactory implements Factory<String> {
  @Override
  public String get() {
    return provideString();
  }

  public static ComponentInterface_InnerModule_Companion_ProvideStringFactory create() {
    return InstanceHolder.INSTANCE;
  }

  public static String provideString() {
    return Preconditions.checkNotNull(ComponentInterface.InnerModule.Companion.provideString(), "Cannot return null from a non-@Nullable @Provides method");
  }

  private static final class InstanceHolder {
    private static final ComponentInterface_InnerModule_Companion_ProvideStringFactory INSTANCE = new ComponentInterface_InnerModule_Companion_ProvideStringFactory();
  }
}
     */

    compile(
        """
        package com.squareup.test
        
        interface ComponentInterface {
          @dagger.Module
          abstract class InnerModule {
            @dagger.Binds abstract fun bindString(string: String): CharSequence
            
            companion object {
              @dagger.Provides fun provideString(): String = "abc"          
            }
          }
        }
        """
    ) {
      val factoryClass = innerModule.moduleFactoryClass("provideString", companion = true)

      val constructor = factoryClass.declaredConstructors.single()
      assertThat(constructor.parameterTypes.toList()).isEmpty()

      val staticMethods = factoryClass.declaredMethods.filter { it.isStatic }

      val factoryInstance = staticMethods.single { it.name == "create" }
          .invoke(null)
      assertThat(factoryInstance::class.java).isEqualTo(factoryClass)

      val providedString = staticMethods.single { it.name == "provideString" }
          .invoke(null) as String

      assertThat(providedString).isEqualTo("abc")
      assertThat((factoryInstance as Factory<String>).get()).isEqualTo("abc")
    }
  }

  @Test fun `no factory class is generated for multibindings`() {
    compile(
        """
        package com.squareup.test
        
        @dagger.Module
        abstract class DaggerModule1 {
          @dagger.Binds @dagger.multibindings.IntoSet abstract fun bindString(string: String): CharSequence
        }
        """
    ) {
      if (useDagger) {
        assertThat(sourcesGeneratedByAnnotationProcessor).isEmpty()
      }
    }
  }

  @Test fun `a factory class is generated for multibindings provider method`() {
    /*
package com.squareup.test;

import dagger.internal.Factory;
import dagger.internal.Preconditions;
import javax.annotation.Generated;

@Generated(
  value = "dagger.internal.codegen.ComponentProcessor",
  comments = "https://dagger.dev"
)
@SuppressWarnings({
  "unchecked",
  "rawtypes"
})
public final class DaggerModule1_ProvideStringFactory implements Factory<String> {
  @Override
  public String get() {
    return provideString();
  }

  public static DaggerModule1_ProvideStringFactory create() {
    return InstanceHolder.INSTANCE;
  }

  public static String provideString() {
    return Preconditions.checkNotNull(DaggerModule1.INSTANCE.provideString(), "Cannot return null from a non-@Nullable @Provides method");
  }

  private static final class InstanceHolder {
    private static final DaggerModule1_ProvideStringFactory INSTANCE = new DaggerModule1_ProvideStringFactory();
  }
}
     */

    compile(
        """
        package com.squareup.test
        
        @dagger.Module
        object DaggerModule1 {
          @dagger.Provides @dagger.multibindings.IntoSet fun provideString(): String = "abc"
        }
        """
    ) {
      val factoryClass = daggerModule1.moduleFactoryClass("provideString")

      val constructor = factoryClass.declaredConstructors.single()
      assertThat(constructor.parameterTypes.toList()).isEmpty()

      val staticMethods = factoryClass.declaredMethods.filter { it.isStatic }

      val factoryInstance = staticMethods.single { it.name == "create" }
          .invoke(null)
      assertThat(factoryInstance::class.java).isEqualTo(factoryClass)

      val providedString = staticMethods.single { it.name == "provideString" }
          .invoke(null) as String

      assertThat(providedString).isEqualTo("abc")
      assertThat((factoryInstance as Factory<String>).get()).isEqualTo("abc")
    }
  }

  @Test fun `a factory class is generated for multibindings provider method elements into set`() {
    /*
package com.squareup.test;

import dagger.internal.Factory;
import dagger.internal.Preconditions;
import java.util.Set;
import javax.annotation.Generated;

@Generated(
  value = "dagger.internal.codegen.ComponentProcessor",
  comments = "https://dagger.dev"
)
@SuppressWarnings({
  "unchecked",
  "rawtypes"
})
public final class DaggerModule1_ProvideStringFactory implements Factory<Set<String>> {
  @Override
  public Set<String> get() {
    return provideString();
  }

  public static DaggerModule1_ProvideStringFactory create() {
    return InstanceHolder.INSTANCE;
  }

  public static Set<String> provideString() {
    return Preconditions.checkNotNull(DaggerModule1.INSTANCE.provideString(), "Cannot return null from a non-@Nullable @Provides method");
  }

  private static final class InstanceHolder {
    private static final DaggerModule1_ProvideStringFactory INSTANCE = new DaggerModule1_ProvideStringFactory();
  }
}
     */

    compile(
        """
        package com.squareup.test
        
        @dagger.Module
        object DaggerModule1 {
          @dagger.Provides @dagger.multibindings.ElementsIntoSet fun provideString(): Set<String> = setOf("abc")
        }
        """
    ) {
      val factoryClass = daggerModule1.moduleFactoryClass("provideString")

      val constructor = factoryClass.declaredConstructors.single()
      assertThat(constructor.parameterTypes.toList()).isEmpty()

      val staticMethods = factoryClass.declaredMethods.filter { it.isStatic }

      val factoryInstance = staticMethods.single { it.name == "create" }
          .invoke(null)
      assertThat(factoryInstance::class.java).isEqualTo(factoryClass)

      val providedStringSet = staticMethods.single { it.name == "provideString" }
          .invoke(null) as Set<String>

      assertThat(providedStringSet).containsExactly("abc")
      assertThat((factoryInstance as Factory<Set<String>>).get()).containsExactly("abc")
    }
  }

  @Test fun `a factory class is generated for a provided function`() {
    /*
package com.squareup.test;

import dagger.internal.Factory;
import dagger.internal.Preconditions;
import javax.annotation.Generated;
import kotlin.jvm.functions.Function1;

@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes"
})
public final class DaggerModule1_ProvideFunctionFactory implements Factory<Function1<String, Integer>> {
  @Override
  public Function1<String, Integer> get() {
    return provideFunction();
  }

  public static DaggerModule1_ProvideFunctionFactory create() {
    return InstanceHolder.INSTANCE;
  }

  public static Function1<String, Integer> provideFunction() {
    return Preconditions.checkNotNull(DaggerModule1.INSTANCE.provideFunction(), "Cannot return null from a non-@Nullable @Provides method");
  }

  private static final class InstanceHolder {
    private static final DaggerModule1_ProvideFunctionFactory INSTANCE = new DaggerModule1_ProvideFunctionFactory();
  }
}
     */

    compile(
        """
        package com.squareup.test
        
        import dagger.Module
        import dagger.Provides
        
        @Module
        object DaggerModule1 {
          @Provides fun provideFunction(): (String) -> Int {
            return { string -> string.length }
          }
        }
        """
    ) {
      val factoryClass = daggerModule1.moduleFactoryClass("provideFunction")

      val constructor = factoryClass.declaredConstructors.single()
      assertThat(constructor.parameterTypes.toList()).isEmpty()

      val staticMethods = factoryClass.declaredMethods.filter { it.isStatic }

      val factoryInstance = staticMethods.single { it.name == "create" }
          .invoke(null)
          as Factory<(String) -> Int>
      assertThat(factoryInstance::class.java).isEqualTo(factoryClass)

      val providedInt = staticMethods.single { it.name == "provideFunction" }
          .invoke(null) as (String) -> Int

      assertThat(providedInt.invoke("abc")).isEqualTo(3)
      assertThat(factoryInstance.get().invoke("abcd")).isEqualTo(4)
    }
  }

  @Test
  fun `a factory class is generated for a multibindings provided function with a typealias`() {
    /*
package com.squareup.test;

import dagger.internal.Factory;
import dagger.internal.Preconditions;
import java.util.List;
import java.util.Set;
import javax.annotation.Generated;
import javax.inject.Provider;
import kotlin.jvm.functions.Function1;

@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes"
})
public final class DaggerModule1_ProvideFunctionFactory implements Factory<Set<Function1<List<String>, List<String>>>> {
  private final Provider<String> stringProvider;

  public DaggerModule1_ProvideFunctionFactory(Provider<String> stringProvider) {
    this.stringProvider = stringProvider;
  }

  @Override
  public Set<Function1<List<String>, List<String>>> get() {
    return provideFunction(stringProvider.get());
  }

  public static DaggerModule1_ProvideFunctionFactory create(Provider<String> stringProvider) {
    return new DaggerModule1_ProvideFunctionFactory(stringProvider);
  }

  public static Set<Function1<List<String>, List<String>>> provideFunction(String string) {
    return Preconditions.checkNotNull(DaggerModule1.INSTANCE.provideFunction(string), "Cannot return null from a non-@Nullable @Provides method");
  }
}
     */

    compile(
        """
        package com.squareup.test
        
        import dagger.Module
        import dagger.Provides
        import dagger.multibindings.ElementsIntoSet
        
        typealias StringList = List<String>
        
        @Module
        object DaggerModule1 {
          @Provides @ElementsIntoSet fun provideFunction(
            string: String
          ): @JvmSuppressWildcards Set<(StringList) -> StringList> {
            return setOf { list -> listOf(string) }
          }
        }
        """
    ) {
      val factoryClass = daggerModule1.moduleFactoryClass("provideFunction")

      val constructor = factoryClass.declaredConstructors.single()
      assertThat(constructor.parameterTypes.toList()).containsExactly(Provider::class.java)

      val staticMethods = factoryClass.declaredMethods.filter { it.isStatic }

      val factoryInstance = staticMethods.single { it.name == "create" }
          .invoke(null, Provider { "abc" })
          as Factory<Set<(List<String>) -> List<String>>>
      assertThat(factoryInstance::class.java).isEqualTo(factoryClass)

      val providedStringSet = staticMethods.single { it.name == "provideFunction" }
          .invoke(null, "abc") as Set<(List<String>) -> List<String>>

      assertThat(providedStringSet.single().invoke(emptyList())).containsExactly("abc")
      assertThat(factoryInstance.get().single().invoke(emptyList())).containsExactly("abc")
    }
  }

  @Test fun `a factory class is generated when Anvil generates the provider method`() {
    /*
package anvil.module.com.squareup.test;

import com.squareup.test.ParentInterface;
import dagger.internal.Factory;
import dagger.internal.Preconditions;
import javax.annotation.Generated;

@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes"
})
public final class ComponentInterfaceAnvilModule_ProvideComSquareupTestContributingObjectFactory implements Factory<ParentInterface> {
  @Override
  public ParentInterface get() {
    return provideComSquareupTestContributingObject();
  }

  public static ComponentInterfaceAnvilModule_ProvideComSquareupTestContributingObjectFactory create(
      ) {
    return InstanceHolder.INSTANCE;
  }

  public static ParentInterface provideComSquareupTestContributingObject() {
    return Preconditions.checkNotNull(ComponentInterfaceAnvilModule.INSTANCE.provideComSquareupTestContributingObject(), "Cannot return null from a non-@Nullable @Provides method");
  }

  private static final class InstanceHolder {
    private static final ComponentInterfaceAnvilModule_ProvideComSquareupTestContributingObjectFactory INSTANCE = new ComponentInterfaceAnvilModule_ProvideComSquareupTestContributingObjectFactory();
  }
}
     */

    /*
package com.squareup.test;

import anvil.module.com.squareup.test.ComponentInterfaceAnvilModule;
import dagger.internal.Preconditions;
import javax.annotation.Generated;

@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes"
})
public final class DaggerComponentInterface implements ComponentInterface {
  private DaggerComponentInterface() {

  }

  public static Builder builder() {
    return new Builder();
  }

  public static ComponentInterface create() {
    return new Builder().build();
  }

  public static final class Builder {
    private Builder() {
    }

    /**
     * @deprecated This module is declared, but an instance is not used in the component. This method is a no-op. For more, see https://dagger.dev/unused-modules.
     */
    @Deprecated
    public Builder componentInterfaceAnvilModule(
        ComponentInterfaceAnvilModule componentInterfaceAnvilModule) {
      Preconditions.checkNotNull(componentInterfaceAnvilModule);
      return this;
    }

    public ComponentInterface build() {
      return new DaggerComponentInterface();
    }
  }
}
     */

    compile(
        """
        package com.squareup.test
        
        import com.squareup.anvil.annotations.ContributesBinding
        import com.squareup.anvil.annotations.MergeComponent
        
        interface ParentInterface
        
        @ContributesBinding(Any::class)
        object ContributingObject : ParentInterface
        
        @MergeComponent(Any::class)
        interface ComponentInterface
        """
    ) {
      val factoryClass = componentInterfaceAnvilModule
          .moduleFactoryClass("provideComSquareupTestContributingObject")

      val constructor = factoryClass.declaredConstructors.single()
      assertThat(constructor.parameterTypes.toList()).isEmpty()

      val staticMethods = factoryClass.declaredMethods.filter { it.isStatic }

      val factoryInstance = staticMethods.single { it.name == "create" }
          .invoke(null) as Factory<Any>
      assertThat(factoryInstance::class.java).isEqualTo(factoryClass)

      val providedContributingObject = staticMethods
          .single { it.name == "provideComSquareupTestContributingObject" }
          .invoke(null)

      assertThat(providedContributingObject).isSameInstanceAs(factoryInstance.get())
    }
  }

  @Test fun `an error is thrown for overloaded provider methods`() {
    compile(
        """
        package com.squareup.test
        
        import dagger.Module
        import dagger.Provides
        import dagger.multibindings.ElementsIntoSet
        
        typealias StringList = List<String>
        
        @Module
        object DaggerModule1 {
            @Provides fun provideString(): String = ""
            @Provides fun provideString(s: String): Int = 1
        }
        """
    ) {
      assertThat(exitCode).isEqualTo(COMPILATION_ERROR)
      assertThat(messages).contains(
          "Cannot have more than one binding method with the same name in a single module"
      )
    }
  }

  @Test
  fun `a factory class is generated for a method returning a java class with a star import`() {
    compile(
        """
        package com.squareup.test
        
        import dagger.Module
        import dagger.Provides
        
        @Module
        object DaggerModule1 {
          @Provides fun provideClass(): Class<*> = java.lang.Runnable::class.java
        }
        """
    ) {
      val factoryClass = daggerModule1.moduleFactoryClass("provideClass")

      val constructor = factoryClass.declaredConstructors.single()
      assertThat(constructor.parameterTypes.toList()).isEmpty()
    }
  }

  @Test
  fun `a factory class is generated for a method returning a class with a named import`() {
    compile(
      """
        package com.squareup.test
        
        import dagger.Module
        import dagger.Provides
        import java.lang.Runnable as NamedRunnable
        
        @Module
        object DaggerModule1 {
          @Provides fun provideRunner(): NamedRunnable = NamedRunnable {}
        }
        """
    ) {
      val factoryClass = daggerModule1.moduleFactoryClass("provideRunner")

      val constructor = factoryClass.declaredConstructors.single()
      assertThat(constructor.parameterTypes.toList()).isEmpty()
    }
  }

  private fun compile(
    source: String,
    block: Result.() -> Unit = { }
  ): Result = com.squareup.anvil.compiler.compile(
      source,
      enableDaggerAnnotationProcessor = useDagger,
      generateDaggerFactories = !useDagger,
      block = block
  )
}
