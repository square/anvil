package com.squareup.anvil.compiler.dagger

import com.google.common.truth.Truth.assertThat
import com.squareup.anvil.compiler.testing.CompilationModeTest
import com.squareup.anvil.compiler.testing.reflect.createInstance
import com.squareup.anvil.compiler.testing.reflect.factoryClass
import com.squareup.anvil.compiler.testing.reflect.getFieldValue
import com.squareup.anvil.compiler.testing.reflect.injectClass_Factory
import com.squareup.anvil.compiler.testing.reflect.isStatic
import com.squareup.anvil.compiler.testing.skipWhen
import dagger.Lazy
import dagger.internal.Factory
import io.kotest.matchers.shouldBe
import org.jetbrains.kotlin.cli.common.ExitCode
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.TestFactory
import java.io.File
import javax.inject.Provider

class InjectConstructorFactoryGeneratorTest : CompilationModeTest() {

  @TestFactory
  fun `a factory class is generated for an inject constructor without arguments`() =
    testFactory {
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
  public final class InjectClass_Factory implements Factory<InjectClass> {
    @Override
    public InjectClass get() {
      return newInstance();
    }

    public static InjectClass_Factory create() {
      return InstanceHolder.INSTANCE;
    }

    public static InjectClass newInstance() {
      return new InjectClass();
    }

    private static final class InstanceHolder {
      private static final InjectClass_Factory INSTANCE = new InjectClass_Factory();
    }
  }
       */

      compile2(
        """
      package com.squareup.test
      
      import javax.inject.Inject
      
      class InjectClass @Inject constructor()
      """,
      ) {
        val factoryClass = classLoader.injectClass_Factory

        val constructor = factoryClass.declaredConstructors.single()
        assertThat(constructor.parameterTypes.toList()).isEmpty()

        val staticMethods = factoryClass.declaredMethods.filter { it.isStatic }

        val factoryInstance = staticMethods.single { it.name == "create" }
          .invoke(null)
        assertThat(factoryInstance::class.java).isEqualTo(factoryClass)

        val instance = staticMethods.single { it.name == "newInstance" }
          .invoke(null)

        assertThat(instance).isNotNull()
        assertThat((factoryInstance as Factory<*>).get()).isNotNull()
      }
    }

  /**
   * Covers a bug that previously led to conflicting imports in the generated code:
   * https://github.com/square/anvil/issues/738
   */
  @TestFactory
  fun `a factory class is generated without conflicting imports`() = testFactory {
    compile2(
      """
      package com.squareup.test
      
      import javax.inject.Inject
      
      class InjectClass @Inject constructor() {
        interface Factory {
          fun doSomething()
        }
      }

      class InjectClassFactory @Inject constructor(val factory: InjectClass.Factory)
      """,
    ) {
      // Loading one of the classes is all that's necessary to verify no conflicting imports were
      // generated
      classLoader.injectClass_Factory
    }
  }

  @TestFactory
  fun `a factory class is generated for an inject constructor with arguments`() =
    testFactory {
      /*
  package com.squareup.test;

  import dagger.internal.Factory;
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
  public final class InjectClass_Factory implements Factory<InjectClass> {
    private final Provider<String> stringProvider;

    private final Provider<Integer> p1_52215Provider;

    public InjectClass_Factory(Provider<String> stringProvider, Provider<Integer> p1_52215Provider) {
      this.stringProvider = stringProvider;
      this.p1_52215Provider = p1_52215Provider;
    }

    @Override
    public InjectClass get() {
      return newInstance(stringProvider.get(), p1_52215Provider.get());
    }

    public static InjectClass_Factory create(Provider<String> stringProvider,
        Provider<Integer> p1_52215Provider) {
      return new InjectClass_Factory(stringProvider, p1_52215Provider);
    }

    public static InjectClass newInstance(String string, int p1_52215) {
      return new InjectClass(string, p1_52215);
    }
  }
       */

      compile2(
        """
      package com.squareup.test
      
      import javax.inject.Inject
      
      data class InjectClass @Inject constructor(
        val string: String, 
        val int: Int
      )
      """,
      ) {
        val factoryClass = classLoader.injectClass_Factory

        val constructor = factoryClass.declaredConstructors.single()
        assertThat(constructor.parameterTypes.toList())
          .containsExactly(Provider::class.java, Provider::class.java)
          .inOrder()

        val staticMethods = factoryClass.declaredMethods.filter { it.isStatic }

        val factoryInstance = staticMethods.single { it.name == "create" }
          .invoke(null, Provider { "abc" }, Provider { 1 })
        assertThat(factoryInstance::class.java).isEqualTo(factoryClass)

        val newInstance = staticMethods.single { it.name == "newInstance" }
          .invoke(null, "abc", 1)
        val getInstance = (factoryInstance as Factory<*>).get()

        assertThat(newInstance).isNotNull()
        assertThat(getInstance).isNotNull()

        assertThat(newInstance).isEqualTo(getInstance)
        assertThat(newInstance).isNotSameInstanceAs(getInstance)
      }
    }

  @TestFactory
  fun `a factory class is generated for an inject constructor with Provider and Lazy arguments`() =
    testFactory {
      /*
  package com.squareup.test;

  import dagger.Lazy;
  import dagger.internal.DoubleCheck;
  import dagger.internal.Factory;
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
  public final class InjectClass_Factory implements Factory<InjectClass> {
    private final Provider<String> stringProvider;

    private final Provider<String> stringProvider2;

    private final Provider<List<String>> stringListProvider;

    private final Provider<String> stringProvider3;

    public InjectClass_Factory(Provider<String> stringProvider, Provider<String> stringProvider2,
        Provider<List<String>> stringListProvider, Provider<String> stringProvider3) {
      this.stringProvider = stringProvider;
      this.stringProvider2 = stringProvider2;
      this.stringListProvider = stringListProvider;
      this.stringProvider3 = stringProvider3;
    }

    @Override
    public InjectClass get() {
      return newInstance(stringProvider.get(), stringProvider2, stringListProvider, DoubleCheck.lazy(stringProvider3));
    }

    public static InjectClass_Factory create(Provider<String> stringProvider,
        Provider<String> stringProvider2, Provider<List<String>> stringListProvider,
        Provider<String> stringProvider3) {
      return new InjectClass_Factory(stringProvider, stringProvider2, stringListProvider, stringProvider3);
    }

    public static InjectClass newInstance(String string, Provider<String> stringProvider,
        Provider<List<String>> stringListProvider, Lazy<String> lazyString) {
      return new InjectClass(string, stringProvider, stringListProvider, lazyString);
    }
  }
       */

      skipWhen(mode.isK2) { "https://github.com/square/anvil/issues/1120" }

      @Suppress("EqualsOrHashCode")
      compile2(
        """
        package com.squareup.test
        
        import dagger.Lazy
        import javax.inject.Inject
        import javax.inject.Provider
        
        class InjectClass @Inject constructor(
          val string: String, 
          val stringProvider: Provider<String>,
          val stringListProvider: Provider<List<String>>,
          val lazyString: Lazy<String>
        ) {
          override fun equals(other: Any?): Boolean {
            return toString() == other.toString()
          }
          override fun toString(): String {
           return string + stringProvider.get() + 
               stringListProvider.get()[0] + lazyString.get()
          }
        }
        """,
      ) {
        val factoryClass = classLoader.injectClass_Factory

        val constructor = factoryClass.declaredConstructors.single()
        assertThat(constructor.parameterTypes.toList())
          .containsExactly(
            Provider::class.java,
            Provider::class.java,
            Provider::class.java,
            Provider::class.java,
          )
          .inOrder()

        val staticMethods = factoryClass.declaredMethods.filter { it.isStatic }

        val factoryInstance = staticMethods.single { it.name == "create" }
          .invoke(
            null,
            Provider { "a" },
            Provider { "b" },
            Provider { listOf("c") },
            Provider { "d" },
          )
        assertThat(factoryInstance::class.java).isEqualTo(factoryClass)

        val newInstance = staticMethods.single { it.name == "newInstance" }
          .invoke(null, "a", Provider { "b" }, Provider { listOf("c") }, Lazy { "d" })
        val getInstance = (factoryInstance as Factory<*>).get()

        assertThat(newInstance).isNotNull()
        assertThat(getInstance).isNotNull()

        assertThat(newInstance).isEqualTo(getInstance)
        assertThat(newInstance).isNotSameInstanceAs(getInstance)
      }
    }

  @TestFactory
  fun `a factory class is generated for an inject constructor with a lazy argument wrapped in a provider`() =
    testFactory {
      /*
  package com.squareup.test;

  import dagger.Lazy;
  import dagger.internal.DaggerGenerated;
  import dagger.internal.Factory;
  import dagger.internal.ProviderOfLazy;
  import javax.annotation.processing.Generated;
  import javax.inject.Provider;

  @DaggerGenerated
  @Generated(
      value = "dagger.internal.codegen.ComponentProcessor",
      comments = "https://dagger.dev"
  )
  @SuppressWarnings({
      "unchecked",
      "rawtypes"
  })
  public final class InjectClass_Factory implements Factory<InjectClass> {
    private final Provider<String> stringProvider;

    public InjectClass_Factory(Provider<String> stringProvider) {
      this.stringProvider = stringProvider;
    }

    @Override
    public InjectClass get() {
      return newInstance(ProviderOfLazy.create(stringProvider));
    }

    public static InjectClass_Factory create(Provider<String> stringProvider) {
      return new InjectClass_Factory(stringProvider);
    }

    public static InjectClass newInstance(Provider<Lazy<String>> string) {
      return new InjectClass(string);
    }
  }
       */

      skipWhen(mode.isK2) { "https://github.com/square/anvil/issues/1120" }

      @Suppress("EqualsOrHashCode")
      compile2(
        """
        package com.squareup.test
        
        import dagger.Lazy
        import javax.inject.Inject
        import javax.inject.Provider
        
        class InjectClass @Inject constructor(
          val string: Provider<Lazy<String>>
        ) {
          override fun equals(other: Any?): Boolean {
            return toString() == other.toString()
          }
          override fun toString(): String {
           return string.get().get() 
          }
        }
        """,
      ) {
        val factoryClass = classLoader.injectClass_Factory

        val constructor = factoryClass.declaredConstructors.single()
        assertThat(constructor.parameterTypes.toList())
          .containsExactly(Provider::class.java)

        val staticMethods = factoryClass.declaredMethods.filter { it.isStatic }

        val factoryInstance = staticMethods.single { it.name == "create" }
          .invoke(null, Provider { "a" })
        assertThat(factoryInstance::class.java).isEqualTo(factoryClass)

        val newInstance = staticMethods.single { it.name == "newInstance" }
          .invoke(null, Provider { dagger.Lazy { "a" } })
        val getInstance = (factoryInstance as Factory<*>).get()

        assertThat(newInstance).isNotNull()
        assertThat(getInstance).isNotNull()

        assertThat(newInstance).isEqualTo(getInstance)
        assertThat(newInstance).isNotSameInstanceAs(getInstance)
      }
    }

  // Notice in the get() function Dagger creates a Lazy of a Provider of Provider instead of a
  // Lazy of a Provider.
  @TestFactory
  @Disabled("This test is broken with Dagger as well.")
  fun `a factory class is generated for an inject constructor with a provider argument wrapped in a lazy`() =
    testFactory {
      /*
  package com.squareup.test;

  import dagger.Lazy;
  import dagger.internal.DaggerGenerated;
  import dagger.internal.DoubleCheck;
  import dagger.internal.Factory;
  import javax.annotation.processing.Generated;
  import javax.inject.Provider;

  @DaggerGenerated
  @Generated(
      value = "dagger.internal.codegen.ComponentProcessor",
      comments = "https://dagger.dev"
  )
  @SuppressWarnings({
      "unchecked",
      "rawtypes"
  })
  public final class InjectClass_Factory implements Factory<InjectClass> {
    private final Provider<Provider<String>> stringProvider;

    public InjectClass_Factory(Provider<Provider<String>> stringProvider) {
      this.stringProvider = stringProvider;
    }

    @Override
    public InjectClass get() {
      return newInstance(DoubleCheck.lazy(stringProvider));
    }

    public static InjectClass_Factory create(Provider<Provider<String>> stringProvider) {
      return new InjectClass_Factory(stringProvider);
    }

    public static InjectClass newInstance(Lazy<Provider<String>> string) {
      return new InjectClass(string);
    }
  }
       */

      @Suppress("EqualsOrHashCode")
      compile2(
        """
      package com.squareup.test
      
      import dagger.Lazy
      import javax.inject.Inject
      import javax.inject.Provider
      
      class InjectClass @Inject constructor(
        val string: Lazy<Provider<String>>
      ) {
        override fun equals(other: Any?): Boolean {
          return toString() == other.toString()
        }
        override fun toString(): String {
         return string.get().get() 
        }
      }
      """,
      ) {
        val factoryClass = classLoader.injectClass_Factory

        val constructor = factoryClass.declaredConstructors.single()
        assertThat(constructor.parameterTypes.toList())
          .containsExactly(Provider::class.java)

        val staticMethods = factoryClass.declaredMethods.filter { it.isStatic }

        val factoryInstance = staticMethods.single { it.name == "create" }
          .invoke(null, Provider { "a" })
        assertThat(factoryInstance::class.java).isEqualTo(factoryClass)

        val newInstance = staticMethods.single { it.name == "newInstance" }
          .invoke(null, dagger.Lazy { Provider { "a" } })
        val getInstance = (factoryInstance as Factory<*>).get()

        assertThat(newInstance).isNotNull()
        assertThat(getInstance).isNotNull()

        assertThat(newInstance).isEqualTo(getInstance)
        assertThat(newInstance).isNotSameInstanceAs(getInstance)
      }
    }

  @TestFactory
  fun `a factory class is generated for an inject constructor with star imports`() =
    testFactory {
      /*
  package com.squareup.test;

  import dagger.internal.DaggerGenerated;
  import dagger.internal.Factory;
  import java.io.File;
  import java.nio.file.Path;
  import java.util.Set;
  import javax.annotation.processing.Generated;
  import javax.inject.Provider;

  @DaggerGenerated
  @Generated(
      value = "dagger.internal.codegen.ComponentProcessor",
      comments = "https://dagger.dev"
  )
  @SuppressWarnings({
      "unchecked",
      "rawtypes"
  })
  public final class InjectClass_Factory implements Factory<InjectClass> {
    private final Provider<File> fileProvider;

    private final Provider<Path> pathProvider;

    private final Provider<Set<String>> setMultibindingProvider;

    public InjectClass_Factory(Provider<File> fileProvider, Provider<Path> pathProvider,
        Provider<Set<String>> setMultibindingProvider) {
      this.fileProvider = fileProvider;
      this.pathProvider = pathProvider;
      this.setMultibindingProvider = setMultibindingProvider;
    }

    @Override
    public InjectClass get() {
      return newInstance(fileProvider.get(), pathProvider.get(), setMultibindingProvider.get());
    }

    public static InjectClass_Factory create(Provider<File> fileProvider, Provider<Path> pathProvider,
        Provider<Set<String>> setMultibindingProvider) {
      return new InjectClass_Factory(fileProvider, pathProvider, setMultibindingProvider);
    }

    public static InjectClass newInstance(File file, Path path, Set<String> setMultibinding) {
      return new InjectClass(file, path, setMultibinding);
    }
  }
       */

      compile2(
        """
      package com.squareup.test
      
      import java.io.*
      import java.nio.file.*
      import java.util.*
      import javax.inject.*
      
      data class InjectClass @Inject constructor(
        val file: File, 
        val path: Path,
        val setMultibinding: Set<String>,
      )
      """,
      ) {
        val factoryClass = classLoader.injectClass_Factory

        val constructor = factoryClass.declaredConstructors.single()
        assertThat(constructor.parameterTypes.toList())
          .containsExactly(Provider::class.java, Provider::class.java, Provider::class.java)
          .inOrder()

        val staticMethods = factoryClass.declaredMethods.filter { it.isStatic }

        val factoryInstance = staticMethods.single { it.name == "create" }
          .invoke(
            null,
            Provider { File("") },
            Provider { File("").toPath() },
            Provider { emptySet<String>() },
          )
        assertThat(factoryInstance::class.java).isEqualTo(factoryClass)

        val newInstance = staticMethods.single { it.name == "newInstance" }
          .invoke(null, File(""), File("").toPath(), emptySet<String>())
        val getInstance = (factoryInstance as Factory<*>).get()

        assertThat(newInstance).isNotNull()
        assertThat(getInstance).isNotNull()

        assertThat(newInstance).isEqualTo(getInstance)
        assertThat(newInstance).isNotSameInstanceAs(getInstance)
      }
    }

  @TestFactory
  fun `a factory class is generated for an inject constructor with star imports - 2`() =
    testFactory {
      compile2(
        """
        package a
  
        class A {
          abstract class AA
        }
      """,
        """
        package b

        import a.A
        import a.A.*
        import javax.inject.Inject
        
        public class B @Inject constructor(): A.AA()
      """,
      ) {
        scanResult shouldContainClass "b.B_Factory"
      }
    }

  @TestFactory
  fun `a factory class is generated for an inject constructor with generic arguments`() =
    testFactory {
      /*
  package com.squareup.test;

  import dagger.internal.Factory;
  import java.util.List;
  import java.util.Set;
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
  public final class InjectClass_Factory implements Factory<InjectClass> {
    private final Provider<Pair<Pair<String, Integer>, ? extends List<String>>> pairProvider;

    private final Provider<Set<String>> setProvider;

    public InjectClass_Factory(
        Provider<Pair<Pair<String, Integer>, ? extends List<String>>> pairProvider,
        Provider<Set<String>> setProvider) {
      this.pairProvider = pairProvider;
      this.setProvider = setProvider;
    }

    @Override
    public InjectClass get() {
      return newInstance(pairProvider.get(), setProvider.get());
    }

    public static InjectClass_Factory create(
        Provider<Pair<Pair<String, Integer>, ? extends List<String>>> pairProvider,
        Provider<Set<String>> setProvider) {
      return new InjectClass_Factory(pairProvider, setProvider);
    }

    public static InjectClass newInstance(Pair<Pair<String, Integer>, ? extends List<String>> pair,
        Set<String> set) {
      return new InjectClass(pair, set);
    }
  }
       */

      compile2(
        """
      package com.squareup.test
      
      import javax.inject.Inject
      
      data class InjectClass @Inject constructor(
        val pair: Pair<Pair<String, Int>, List<String>>, 
        val set: Set<String>
      )
      """,
      ) {
        val factoryClass = classLoader.injectClass_Factory

        val constructor = factoryClass.declaredConstructors.single()
        assertThat(constructor.parameterTypes.toList())
          .containsExactly(Provider::class.java, Provider::class.java)
          .inOrder()

        val staticMethods = factoryClass.declaredMethods.filter { it.isStatic }

        val factoryInstance = staticMethods.single { it.name == "create" }
          .invoke(null, Provider { Pair(Pair("a", 1), listOf("b")) }, Provider { setOf("c") })
        assertThat(factoryInstance::class.java).isEqualTo(factoryClass)

        val newInstance = staticMethods.single { it.name == "newInstance" }
          .invoke(null, Pair(Pair("a", 1), listOf("b")), setOf("c"))
        val getInstance = (factoryInstance as Factory<*>).get()

        assertThat(newInstance).isNotNull()
        assertThat(getInstance).isNotNull()

        assertThat(newInstance).isEqualTo(getInstance)
        assertThat(newInstance).isNotSameInstanceAs(getInstance)
      }
    }

  @TestFactory
  fun `a factory class is generated for an inject constructor with a lambda as super type`() =
    testFactory {
      compile2(
        """
        package com.squareup.test

        import javax.inject.Inject
  
        class InjectClass @Inject constructor() : (Int) -> Unit {
          override fun invoke(integer: Int) = Unit
        }
      """,
      ) {
        assertThat(classLoader.injectClass_Factory).isNotNull()
      }
    }

  @TestFactory
  fun `a factory class is generated for an inject constructor inner class`() =
    testFactory {
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
  public final class OuterClass_InjectClass_Factory implements Factory<OuterClass.InjectClass> {
    @Override
    public OuterClass.InjectClass get() {
      return newInstance();
    }

    public static OuterClass_InjectClass_Factory create() {
      return InstanceHolder.INSTANCE;
    }

    public static OuterClass.InjectClass newInstance() {
      return new OuterClass.InjectClass();
    }

    private static final class InstanceHolder {
      private static final OuterClass_InjectClass_Factory INSTANCE = new OuterClass_InjectClass_Factory();
    }
  }
       */

      compile2(
        """
      package com.squareup.test
      
      import javax.inject.Inject
      
      class OuterClass {
        class InjectClass @Inject constructor()
      }
      """,
      ) {
        val injectClass = classLoader.loadClass("com.squareup.test.OuterClass\$InjectClass")
        val factoryClass = injectClass.factoryClass()

        val constructor = factoryClass.declaredConstructors.single()
        assertThat(constructor.parameterTypes.toList()).isEmpty()

        val staticMethods = factoryClass.declaredMethods.filter { it.isStatic }

        val factoryInstance = staticMethods.single { it.name == "create" }
          .invoke(null)
        assertThat(factoryInstance::class.java).isEqualTo(factoryClass)

        val instance = staticMethods.single { it.name == "newInstance" }
          .invoke(null)

        assertThat(instance).isNotNull()
        assertThat((factoryInstance as Factory<*>).get()).isNotNull()
      }
    }

  @TestFactory
  fun `a factory class is generated for an inject constructor with function arguments`() =
    testFactory {
      /*
  package com.squareup.test;

  import dagger.internal.Factory;
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
  public final class InjectClass_Factory implements Factory<InjectClass> {
    private final Provider<String> stringProvider;

    private final Provider<Set<Function1<List<String>, List<String>>>> setProvider;

    public InjectClass_Factory(Provider<String> stringProvider,
        Provider<Set<Function1<List<String>, List<String>>>> setProvider) {
      this.stringProvider = stringProvider;
      this.setProvider = setProvider;
    }

    @Override
    public InjectClass get() {
      return newInstance(stringProvider.get(), setProvider.get());
    }

    public static InjectClass_Factory create(Provider<String> stringProvider,
        Provider<Set<Function1<List<String>, List<String>>>> setProvider) {
      return new InjectClass_Factory(stringProvider, setProvider);
    }

    public static InjectClass newInstance(String string,
        Set<Function1<List<String>, List<String>>> set) {
      return new InjectClass(string, set);
    }
  }
       */

      compile2(
        """
      package com.squareup.test
      
      import javax.inject.Inject
      
      typealias StringList = List<String>
      
      data class InjectClass @Inject constructor(
        val string: String, 
        val set: @JvmSuppressWildcards Set<(StringList) -> StringList>
      )
      """,
      ) {
        val factoryClass = classLoader.injectClass_Factory

        val constructor = factoryClass.declaredConstructors.single()
        assertThat(constructor.parameterTypes.toList())
          .containsExactly(Provider::class.java, Provider::class.java)
          .inOrder()

        val staticMethods = factoryClass.declaredMethods.filter { it.isStatic }

        val factoryInstance = staticMethods.single { it.name == "create" }
          .invoke(null, Provider { "a" }, Provider { setOf { listOf("b") } })
        assertThat(factoryInstance::class.java).isEqualTo(factoryClass)

        val newInstance = staticMethods.single { it.name == "newInstance" }
          .invoke(null, "a", setOf { _: List<String> -> listOf("b") })
        val getInstance = (factoryInstance as Factory<*>).get()

        assertThat(newInstance).isNotNull()
        assertThat(getInstance).isNotNull()

        assertThat(newInstance).isNotSameInstanceAs(getInstance)
      }
    }

  @TestFactory
  fun `a factory class is generated for a class starting with a lowercase character`() =
    testFactory {
      /*
  package com.squareup.test;

  import dagger.internal.Factory;
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
  public final class InjectClass_Factory implements Factory<InjectClass> {
    private final Provider<String> stringProvider;

    private final Provider<Set<Function1<List<String>, List<String>>>> setProvider;

    public InjectClass_Factory(Provider<String> stringProvider,
        Provider<Set<Function1<List<String>, List<String>>>> setProvider) {
      this.stringProvider = stringProvider;
      this.setProvider = setProvider;
    }

    @Override
    public InjectClass get() {
      return newInstance(stringProvider.get(), setProvider.get());
    }

    public static InjectClass_Factory create(Provider<String> stringProvider,
        Provider<Set<Function1<List<String>, List<String>>>> setProvider) {
      return new InjectClass_Factory(stringProvider, setProvider);
    }

    public static InjectClass newInstance(String string,
        Set<Function1<List<String>, List<String>>> set) {
      return new InjectClass(string, set);
    }
  }
       */

      @Suppress("ClassName")
      compile2(
        """
      package com.squareup.test
      
      import javax.inject.Inject
      
      data class injectClass @Inject constructor(val string: String)
      """,
      ) {
        val factoryClass = classLoader.loadClass("com.squareup.test.injectClass").factoryClass()

        val constructor = factoryClass.declaredConstructors.single()
        assertThat(constructor.parameterTypes.toList())
          .containsExactly(Provider::class.java)
          .inOrder()

        val staticMethods = factoryClass.declaredMethods.filter { it.isStatic }

        val factoryInstance = staticMethods.single { it.name == "create" }
          .invoke(null, Provider { "a" })
        assertThat(factoryInstance::class.java).isEqualTo(factoryClass)

        val newInstance = staticMethods.single { it.name == "newInstance" }
          .invoke(null, "a")
        val getInstance = (factoryInstance as Factory<*>).get()

        assertThat(newInstance).isNotNull()
        assertThat(getInstance).isNotNull()

        assertThat(newInstance).isNotSameInstanceAs(getInstance)
      }
    }

  @TestFactory
  fun `a factory class is generated for a class with constructor and member injection`() =
    testFactory {
      /*
  import dagger.`internal`.Factory
  import javax.inject.Provider
  import kotlin.String
  import kotlin.Suppress
  import kotlin.jvm.JvmStatic
  import kotlin.jvm.JvmSuppressWildcards

  public class InjectClass_Factory(
    private val param0: Provider<String>,
    private val param1: Provider<@JvmSuppressWildcards Int>
  ) : Factory<InjectClass> {
    public override fun `get`(): InjectClass {
      val instance = newInstance(param0.get())
      InjectClass_MembersInjector.injectIntProvider(instance, param1)
      return instance
    }

    public companion object {
      @JvmStatic
      public fun create(param0: Provider<String>, param1: Provider<@JvmSuppressWildcards Int>):
          InjectClass_Factory = InjectClass_Factory(param0, param1)

      @JvmStatic
      public fun newInstance(param0: String): InjectClass = InjectClass(param0)
    }
  }
       */

      skipWhen(mode.generateDaggerFactories && mode.isK2) {
        "https://github.com/square/anvil/issues/1117"
      }

      compile2(
        """
      package com.squareup.test
      
      import javax.inject.Inject
      import javax.inject.Provider

      class InjectClass @Inject constructor(
        val string: String
      ) {
      
        @Inject lateinit var intProvider: Provider<Int>
        
        override fun equals(other: Any?): Boolean {
          if (this === other) return true
          if (other !is InjectClass) return false
      
          if (string != other.string) return false
          if (intProvider.get() != other.intProvider.get()) return false
      
          return true
        }
      
        override fun hashCode(): Int {
          var result = string.hashCode()
          result = 31 * result + intProvider.get().hashCode()
          return result
        }
      }
      """,
      ) {

        val factoryClass = classLoader.injectClass_Factory

        val constructor = factoryClass.declaredConstructors.single()
        assertThat(constructor.parameterTypes.toList())
          .containsExactly(Provider::class.java, Provider::class.java)

        val staticMethods = factoryClass.declaredMethods.filter { it.isStatic }

        val factoryInstance = staticMethods.single { it.name == "create" }
          .invoke(null, Provider { "a" }, Provider { 1 })
        assertThat(factoryInstance::class.java).isEqualTo(factoryClass)

        val newInstance = staticMethods.single { it.name == "newInstance" }
          .invoke(null, "a")
        val getInstance = (factoryInstance as Factory<*>).get()

        assertThat(newInstance).isNotNull()
        assertThat(getInstance).isNotNull()

        assertThat(newInstance).isNotSameInstanceAs(getInstance)
      }
    }

  @TestFactory
  fun `a factory class performs member injection on super classes`() = testFactory {

    skipWhen(mode.generateDaggerFactories && mode.isK2) {
      "https://github.com/square/anvil/issues/1117"
    }

    compile2(
      """
      package com.squareup.test

      import javax.inject.Inject

      class InjectClass @Inject constructor() : Middle() {

        @Inject
        lateinit var name: String
      }

      abstract class Middle : Base() {

        @Inject
        lateinit var middle1: Set<Int>

        @Inject
        lateinit var middle2: Set<String>
      }
      
      abstract class Base {

        @Inject
        lateinit var base1: List<Int>

        @Inject
        lateinit var base2: List<String>
      }
      """,
    ) {

      val factoryClass = classLoader.injectClass_Factory

      val constructor = factoryClass.declaredConstructors.single()
      assertThat(constructor.parameterTypes.toList())
        .containsExactly(
          Provider::class.java,
          Provider::class.java,
          Provider::class.java,
          Provider::class.java,
          Provider::class.java,
        )

      val name = "name"
      val middle1 = setOf(1)
      val middle2 = setOf("middle2")
      val base1 = listOf(3)
      val base2 = listOf("base2")

      val staticMethods = factoryClass.declaredMethods.filter { it.isStatic }

      val factoryInstance = factoryClass.createInstance(
        Provider { base1 },
        Provider { base2 },
        Provider { middle1 },
        Provider { middle2 },
        Provider { name },
      )
      assertThat(factoryInstance::class.java).isEqualTo(factoryClass)

      val newInstance = staticMethods.single { it.name == "newInstance" }
        .invoke(null)
      assertThat(newInstance).isNotNull()

      val getInstance = (factoryInstance as Factory<*>).get()

      assertThat(getInstance.getFieldValue("name")).isEqualTo(name)
      assertThat(getInstance.getFieldValue("middle1")).isEqualTo(middle1)
      assertThat(getInstance.getFieldValue("middle2")).isEqualTo(middle2)
      assertThat(getInstance.getFieldValue("base1")).isEqualTo(base1)
      assertThat(getInstance.getFieldValue("base2")).isEqualTo(base2)
    }
  }

  @TestFactory
  fun `a factory class performs member injection when the only fields are in a super class`() =
    testFactory {

      skipWhen(mode.generateDaggerFactories && mode.isK2) {
        "https://github.com/square/anvil/issues/1117"
      }

      compile2(
        """
      package com.squareup.test

      import javax.inject.Inject

      class InjectClass @Inject constructor() : Base() 

      abstract class Base {

        @Inject
        lateinit var base1: List<Int>

        @Inject
        lateinit var base2: List<String>
      }
      """,
      ) {

        val factoryClass = classLoader.injectClass_Factory

        val constructor = factoryClass.declaredConstructors.single()
        assertThat(constructor.parameterTypes.toList())
          .containsExactly(
            Provider::class.java,
            Provider::class.java,
          )

        val base1 = listOf(3)
        val base2 = listOf("base2")

        val staticMethods = factoryClass.declaredMethods.filter { it.isStatic }

        val factoryInstance = factoryClass.createInstance(
          Provider { base1 },
          Provider { base2 },
        )
        assertThat(factoryInstance::class.java).isEqualTo(factoryClass)

        val newInstance = staticMethods.single { it.name == "newInstance" }
          .invoke(null)
        assertThat(newInstance).isNotNull()

        val getInstance = (factoryInstance as Factory<*>).get()

        assertThat(getInstance.getFieldValue("base1")).isEqualTo(base1)
        assertThat(getInstance.getFieldValue("base2")).isEqualTo(base2)
      }
    }

  @TestFactory
  fun `a factory class performs member injection on a super class from another module`() =
    testFactory {

      skipWhen(mode.generateDaggerFactories && mode.isK2) {
        "https://github.com/square/anvil/issues/1117"
      }

      val otherModuleResult = compile2(
        """
      package com.squareup.test

      import javax.inject.Inject

      abstract class Base {

        @Inject
        lateinit var base1: List<Int>

        @Inject
        lateinit var base2: List<String>
      }
      """,
      ) {
        assertThat(exitCode).isEqualTo(ExitCode.OK)
      }

      compile2(
        """
      package com.squareup.test

      import javax.inject.Inject

      class InjectClass @Inject constructor() : Base() {

        @Inject
        lateinit var name: String
      }
      """,
        previousCompilation = otherModuleResult,
      ) {

        val factoryClass = classLoader.injectClass_Factory

        val constructor = factoryClass.declaredConstructors.single()
        assertThat(constructor.parameterTypes.toList())
          .containsExactly(
            Provider::class.java,
            Provider::class.java,
            Provider::class.java,
          )

        val name = "name"
        val base1 = listOf(3)
        val base2 = listOf("base2")

        val staticMethods = factoryClass.declaredMethods.filter { it.isStatic }

        val factoryInstance = factoryClass.createInstance(
          Provider { base1 },
          Provider { base2 },
          Provider { name },
        )
        assertThat(factoryInstance::class.java).isEqualTo(factoryClass)

        val newInstance = staticMethods.single { it.name == "newInstance" }
          .invoke(null)
        assertThat(newInstance).isNotNull()

        val getInstance = (factoryInstance as Factory<*>).get()

        assertThat(getInstance.getFieldValue("name")).isEqualTo(name)
        assertThat(getInstance.getFieldValue("base1")).isEqualTo(base1)
        assertThat(getInstance.getFieldValue("base2")).isEqualTo(base2)
      }
    }

  @TestFactory
  fun `a factory class performs member injection on a grand-super class from another module`() =
    testFactory {

      skipWhen(mode.generateDaggerFactories && mode.isK2) {
        "https://github.com/square/anvil/issues/1117"
      }

      val otherModuleResult = compile2(
        """
      package com.squareup.test

      import javax.inject.Inject

      abstract class Base {

        @Inject
        lateinit var base1: List<Int>

        @Inject
        lateinit var base2: List<String>
      }
      """,
      ) {
        exitCode shouldBe ExitCode.OK
      }

      compile2(
        """
      package com.squareup.test

      import javax.inject.Inject

      class InjectClass @Inject constructor() : Middle() {

        @Inject
        lateinit var name: String
      }

      abstract class Middle : Base() {

        @Inject
        lateinit var middle1: Set<Int>

        @Inject
        lateinit var middle2: Set<String>
      }
      """,
        previousCompilation = otherModuleResult,
      ) {

        val factoryClass = classLoader.injectClass_Factory

        val constructor = factoryClass.declaredConstructors.single()
        assertThat(constructor.parameterTypes.toList())
          .containsExactly(
            Provider::class.java,
            Provider::class.java,
            Provider::class.java,
            Provider::class.java,
            Provider::class.java,
          )

        val name = "name"
        val middle1 = setOf(1)
        val middle2 = setOf("middle2")
        val base1 = listOf(3)
        val base2 = listOf("base2")

        val staticMethods = factoryClass.declaredMethods.filter { it.isStatic }

        val factoryInstance = factoryClass.createInstance(
          Provider { base1 },
          Provider { base2 },
          Provider { middle1 },
          Provider { middle2 },
          Provider { name },
        )
        assertThat(factoryInstance::class.java).isEqualTo(factoryClass)

        val newInstance = staticMethods.single { it.name == "newInstance" }
          .invoke(null)
        assertThat(newInstance).isNotNull()

        val getInstance = (factoryInstance as Factory<*>).get()

        assertThat(getInstance.getFieldValue("name")).isEqualTo(name)
        assertThat(getInstance.getFieldValue("middle1")).isEqualTo(middle1)
        assertThat(getInstance.getFieldValue("middle2")).isEqualTo(middle2)
        assertThat(getInstance.getFieldValue("base1")).isEqualTo(base1)
        assertThat(getInstance.getFieldValue("base2")).isEqualTo(base2)
      }
    }

  @TestFactory
  fun `a factory class is generated which injects members when calling get`() = testFactory {
    /*
import dagger.`internal`.Factory
import javax.inject.Provider
import kotlin.Int
import kotlin.String
import kotlin.Suppress
import kotlin.jvm.JvmStatic
import kotlin.jvm.JvmSuppressWildcards

public class InjectClass_Factory(
  private val param0: Provider<String>,
  private val param1: Provider<@JvmSuppressWildcards Int>,
  private val param2: Provider<String>,
  private val param3: Provider<String>
) : Factory<InjectClass> {
  public override fun `get`(): InjectClass {
    val instance = newInstance(param0.get())
    InjectClass_MembersInjector.injectIntProvider(instance, param1)
    InjectClass_MembersInjector.injectStringLazy(instance, dagger.internal.DoubleCheck.lazy(param2))
    InjectClass_MembersInjector.injectOtherString(instance, param3.get())
    return instance
  }

  public companion object {
    @JvmStatic
    public fun create(
      param0: Provider<String>,
      param1: Provider<@JvmSuppressWildcards Int>,
      param2: Provider<String>,
      param3: Provider<String>
    ): InjectClass_Factory = InjectClass_Factory(param0, param1, param2, param3)

    @JvmStatic
    public fun newInstance(param0: String): InjectClass = InjectClass(param0)
  }
}
     */

    skipWhen(mode.generateDaggerFactories && mode.isK2) {
      """
        https://github.com/square/anvil/issues/1118
        https://github.com/square/anvil/issues/1117
      """.trimIndent()
    }

    compile2(
      """
      package com.squareup.test
      
      import javax.inject.Inject
      import javax.inject.Provider
      
      class InjectClass @Inject constructor(
        val string: String
      ) {
      
        @Inject lateinit var intProvider: Provider<Int>
        @Inject lateinit var stringLazy: dagger.Lazy<String>
        @Inject lateinit var otherString: String
        
        override fun equals(other: Any?): Boolean {
          if (this === other) return true
          if (other !is InjectClass) return false
      
          if (string != other.string) return false
          if (intProvider.get() != other.intProvider.get()) return false
          if (stringLazy.get() != other.stringLazy.get()) return false
          if (otherString != other.otherString) return false
      
          return true
        }
      
        override fun hashCode(): Int {
          var result = string.hashCode()
          result = 31 * result + intProvider.get().hashCode()
          result = 31 * result + stringLazy.get().hashCode()
          result = 31 * result + otherString.hashCode()
          return result
        }
      }
      """,
    ) {

      val factoryClass = classLoader.injectClass_Factory

      val constructor = factoryClass.declaredConstructors.single()
      constructor.parameterTypes shouldBe listOf(
        Provider::class.java,
        Provider::class.java,
        Provider::class.java,
        Provider::class.java,
      )

      val staticMethods = factoryClass.declaredMethods.filter { it.isStatic }

      val factoryInstance = staticMethods.single { it.name == "create" }
        .invoke(
          null,
          Provider { "a" },
          Provider { 1 },
          Provider { "stringLazy" },
          Provider { "otherString" },
        )
      assertThat(factoryInstance::class.java).isEqualTo(factoryClass)

      val newInstance = staticMethods.single { it.name == "newInstance" }
        .invoke(null, "a")
      val getInstance = (factoryInstance as Factory<*>).get()

      assertThat(newInstance).isNotNull()
      assertThat(getInstance).isNotNull()

      assertThat(newInstance).isNotSameInstanceAs(getInstance)
    }
  }

  @TestFactory
  fun `a factory class is generated for a class injecting a class starting with a lowercase character`() =
    testFactory {
      /*
  package com.squareup.test;

  import dagger.internal.Factory;
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
  public final class InjectClass_Factory implements Factory<InjectClass> {
    private final Provider<otherClass.inner> innerProvider;

    public InjectClass_Factory(Provider<otherClass.inner> innerProvider) {
      this.innerProvider = innerProvider;
    }

    @Override
    public InjectClass get() {
      return newInstance(innerProvider.get());
    }

    public static InjectClass_Factory create(Provider<otherClass.inner> innerProvider) {
      return new InjectClass_Factory(innerProvider);
    }

    public static InjectClass newInstance(otherClass.inner inner) {
      return new InjectClass(inner);
    }
  }
       */

      @Suppress("ClassName")
      compile2(
        """
      package com.squareup.test
      
      import javax.inject.Inject
      
      class InjectClass @Inject constructor(inner: otherClass.inner)
      
      class otherClass {
        class inner @Inject constructor()
      }
      """,
      ) {
        val constructor = classLoader.injectClass_Factory.declaredConstructors.single()
        assertThat(constructor.parameterTypes.toList()).containsExactly(Provider::class.java)
      }
    }

  @TestFactory
  fun `a factory class is generated for a class injecting an inner class with a generic type parameter`() =
    testFactory {
      /*
  package com.squareup.test

  import com.squareup.test.Other.Inner
  import dagger.`internal`.Factory
  import javax.inject.Provider
  import kotlin.String
  import kotlin.Suppress
  import kotlin.jvm.JvmStatic

  public class InjectClass_Factory(
    private val param0: Provider<Inner<String>>
  ) : Factory<InjectClass> {
    public override fun `get`(): InjectClass = newInstance(param0.get())

    public companion object {
      @JvmStatic
      public fun create(param0: Provider<Inner<String>>): InjectClass_Factory =
          InjectClass_Factory(param0)

      @JvmStatic
      public fun newInstance(param0: Inner<String>): InjectClass = InjectClass(param0)
    }
  }
       */

      compile2(
        """
      package com.squareup.test
      
      import javax.inject.Inject
      
      class Other {
        class Inner<T>
      }
      
      class InjectClass @Inject constructor(
        val inner: Other.Inner<String>
      )
      """,
      ) {
        val constructor = classLoader.injectClass_Factory.declaredConstructors.single()
        assertThat(constructor.parameterTypes.toList()).containsExactly(Provider::class.java)
      }
    }

  @TestFactory
  fun `a factory class is generated for a class injecting a deeply nested inner class with a generic type parameter`() =
    testFactory {
      /*
  package com.squareup.test

  import com.squareup.test.Other.Middle.Inner
  import dagger.`internal`.Factory
  import javax.inject.Provider
  import kotlin.String
  import kotlin.Suppress
  import kotlin.jvm.JvmStatic

  public class InjectClass_Factory(
    private val param0: Provider<Inner<String>>
  ) : Factory<InjectClass> {
    public override fun `get`(): InjectClass = newInstance(param0.get())

    public companion object {
      @JvmStatic
      public fun create(param0: Provider<Inner<String>>): InjectClass_Factory =
          InjectClass_Factory(param0)

      @JvmStatic
      public fun newInstance(param0: Inner<String>): InjectClass = InjectClass(param0)
    }
  }
       */

      compile2(
        """
      package com.squareup.test
      
      import javax.inject.Inject
      
      class Other {
        class Middle {
          class Inner<T>
        }
      }
      
      class InjectClass @Inject constructor(
        val inner: Other.Middle.Inner<String>
      )
      """,
      ) {
        val constructor = classLoader.injectClass_Factory.declaredConstructors.single()
        assertThat(constructor.parameterTypes.toList()).containsExactly(Provider::class.java)
      }
    }

  @TestFactory
  fun `a factory class is generated for an inner class starting with a lowercase character`() =
    testFactory {
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
  public final class MyClass_innerClass_Factory implements Factory<MyClass.innerClass> {
    @Override
    public MyClass.innerClass get() {
      return newInstance();
    }

    public static MyClass_innerClass_Factory create() {
      return InstanceHolder.INSTANCE;
    }

    public static MyClass.innerClass newInstance() {
      return new MyClass.innerClass();
    }

    private static final class InstanceHolder {
      private static final MyClass_innerClass_Factory INSTANCE = new MyClass_innerClass_Factory();
    }
  }
       */

      /*
  package com.squareup.test;

  import dagger.internal.Factory;
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
  public final class MyClass_Factory implements Factory<MyClass> {
    private final Provider<MyClass.innerClass> argProvider;

    public MyClass_Factory(Provider<MyClass.innerClass> argProvider) {
      this.argProvider = argProvider;
    }

    @Override
    public MyClass get() {
      return newInstance(argProvider.get());
    }

    public static MyClass_Factory create(Provider<MyClass.innerClass> argProvider) {
      return new MyClass_Factory(argProvider);
    }

    public static MyClass newInstance(MyClass.innerClass arg) {
      return new MyClass(arg);
    }
  }
       */

      @Suppress("ClassName")
      compile2(
        """
      package com.squareup.test
      
      import javax.inject.Inject
      
      class MyClass @Inject constructor(arg: innerClass) {
        class innerClass @Inject constructor()
      }
      """,
      ) {
        val factoryClass =
          classLoader.loadClass("com.squareup.test.MyClass\$innerClass").factoryClass()

        val constructor = factoryClass.declaredConstructors.single()
        assertThat(constructor.parameterTypes.toList()).isEmpty()

        val staticMethods = factoryClass.declaredMethods.filter { it.isStatic }

        val factoryInstance = staticMethods.single { it.name == "create" }.invoke(null)
        assertThat(factoryInstance::class.java).isEqualTo(factoryClass)

        val newInstance = staticMethods.single { it.name == "newInstance" }.invoke(null)
        val getInstance = (factoryInstance as Factory<*>).get()

        assertThat(newInstance).isNotNull()
        assertThat(getInstance).isNotNull()

        assertThat(newInstance).isNotSameInstanceAs(getInstance)
      }
    }

  @TestFactory
  fun `inner classes are imported correctly`() = testFactory {
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
public final class InjectClass_Dependencies_Factory implements Factory<InjectClass.Dependencies> {
  @Override
  public InjectClass.Dependencies get() {
    return newInstance();
  }

  public static InjectClass_Dependencies_Factory create() {
    return InstanceHolder.INSTANCE;
  }

  public static InjectClass.Dependencies newInstance() {
    return new InjectClass.Dependencies();
  }

  private static final class InstanceHolder {
    private static final InjectClass_Dependencies_Factory INSTANCE = new InjectClass_Dependencies_Factory();
  }
}
     */

    /*
package com.squareup.test;

import dagger.internal.Factory;
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
public final class InjectClass_Factory implements Factory<InjectClass> {
  private final Provider<InjectClass.Dependencies> dependenciesProvider;

  public InjectClass_Factory(Provider<InjectClass.Dependencies> dependenciesProvider) {
    this.dependenciesProvider = dependenciesProvider;
  }

  @Override
  public InjectClass get() {
    return newInstance(dependenciesProvider.get());
  }

  public static InjectClass_Factory create(
      Provider<InjectClass.Dependencies> dependenciesProvider) {
    return new InjectClass_Factory(dependenciesProvider);
  }

  public static InjectClass newInstance(InjectClass.Dependencies dependencies) {
    return new InjectClass(dependencies);
  }
}
     */

    compile2(
      """
      package com.squareup.test
      
      import javax.inject.Inject
      
      class InjectClass @Inject constructor(dependencies: Dependencies) {
        class Dependencies @Inject constructor()     
      }
      """,
    ) {
      val constructor = classLoader.injectClass_Factory.declaredConstructors.single()
      assertThat(constructor.parameterTypes.toList()).containsExactly(Provider::class.java)

      val constructorDependencies = classLoader
        .loadClass("com.squareup.test.InjectClass\$Dependencies")
        .factoryClass()
        .declaredConstructors
        .single()
      assertThat(constructorDependencies.parameterTypes.toList()).isEmpty()
    }
  }

  @TestFactory
  fun `inner classes are imported correctly from super type`() = testFactory {
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
public final class ParentOne_Dependencies_Factory implements Factory<ParentOne.Dependencies> {
  @Override
  public ParentOne.Dependencies get() {
    return newInstance();
  }

  public static ParentOne_Dependencies_Factory create() {
    return InstanceHolder.INSTANCE;
  }

  public static ParentOne.Dependencies newInstance() {
    return new ParentOne.Dependencies();
  }

  private static final class InstanceHolder {
    private static final ParentOne_Dependencies_Factory INSTANCE = new ParentOne_Dependencies_Factory();
  }
}
     */

    /*
package com.squareup.test;

import dagger.internal.Factory;
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
public final class InjectClass_Factory implements Factory<InjectClass> {
  private final Provider<ParentOne.Dependencies> dProvider;

  public InjectClass_Factory(Provider<ParentOne.Dependencies> dProvider) {
    this.dProvider = dProvider;
  }

  @Override
  public InjectClass get() {
    return newInstance(dProvider.get());
  }

  public static InjectClass_Factory create(Provider<ParentOne.Dependencies> dProvider) {
    return new InjectClass_Factory(dProvider);
  }

  public static InjectClass newInstance(ParentOne.Dependencies d) {
    return new InjectClass(d);
  }
}
     */

    compile2(
      """
      package com.squareup.test
      
      import javax.inject.Inject
      
      open class ParentOne(d: Dependencies) {
        class Dependencies @Inject constructor()
      }
      
      open class ParentTwo(d: Dependencies) : ParentOne(d)
      
      class InjectClass @Inject constructor(d: Dependencies) : ParentTwo(d)
      """,
    ) {
      val constructor = classLoader.injectClass_Factory.declaredConstructors.single()
      assertThat(constructor.parameterTypes.toList()).containsExactly(Provider::class.java)

      val constructorDependencies = classLoader
        .loadClass("com.squareup.test.ParentOne\$Dependencies")
        .factoryClass()
        .declaredConstructors
        .single()
      assertThat(constructorDependencies.parameterTypes.toList()).isEmpty()
    }
  }

  @TestFactory
  fun `inner classes in generics are imported correctly`() = testFactory {
    /*
package com.squareup.test;

import dagger.internal.Factory;
import java.util.Set;
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
public final class InjectClass_Factory implements Factory<InjectClass> {
  private final Provider<Set<InjectClass.Interceptor>> interceptorsProvider;

  public InjectClass_Factory(Provider<Set<InjectClass.Interceptor>> interceptorsProvider) {
    this.interceptorsProvider = interceptorsProvider;
  }

  @Override
  public InjectClass get() {
    return newInstance(interceptorsProvider.get());
  }

  public static InjectClass_Factory create(
      Provider<Set<InjectClass.Interceptor>> interceptorsProvider) {
    return new InjectClass_Factory(interceptorsProvider);
  }

  public static InjectClass newInstance(Set<InjectClass.Interceptor> interceptors) {
    return new InjectClass(interceptors);
  }
}
     */

    compile2(
      """
      package com.squareup.test
      
      import javax.inject.Inject
      
      class InjectClass @Inject constructor(
        interceptors: Set<@JvmSuppressWildcards Interceptor>
      ) {
        interface Interceptor
      }
      """,
    ) {
      val constructor = classLoader.injectClass_Factory.declaredConstructors.single()
      assertThat(constructor.parameterTypes.toList()).containsExactly(Provider::class.java)
    }
  }

  @TestFactory
  fun `inner classes from outer type are imported correctly`() = testFactory {
    /*
package com.squareup.test;

import com.squareup.anvil.compiler.dagger.OuterClass;
import dagger.internal.Factory;
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
public final class InjectClass_Inner_Factory implements Factory<InjectClass.Inner> {
  private final Provider<OuterClass.InnerClass> innerClassProvider;

  public InjectClass_Inner_Factory(Provider<OuterClass.InnerClass> innerClassProvider) {
    this.innerClassProvider = innerClassProvider;
  }

  @Override
  public InjectClass.Inner get() {
    return newInstance(innerClassProvider.get());
  }

  public static InjectClass_Inner_Factory create(
      Provider<OuterClass.InnerClass> innerClassProvider) {
    return new InjectClass_Inner_Factory(innerClassProvider);
  }

  public static InjectClass.Inner newInstance(OuterClass.InnerClass innerClass) {
    return new InjectClass.Inner(innerClass);
  }
}
     */

    compile2(
      """
      package com.squareup.test
      
      import com.squareup.anvil.compiler.dagger.OuterClass
      import javax.inject.Inject
      
      class InjectClass(innerClass: InnerClass): OuterClass(innerClass) {
        class Inner @Inject constructor(val innerClass: InnerClass)
      }
      """,
      """
      package com.squareup.anvil.compiler.dagger
      
      abstract class OuterClass(
        @Suppress("UNUSED_PARAMETER") innerClass: InnerClass,
      ) {
        class InnerClass
      }
      """,
    ) {
      val constructor = classLoader.loadClass("com.squareup.test.InjectClass\$Inner")
        .factoryClass().declaredConstructors.single()
      assertThat(constructor.parameterTypes.toList()).containsExactly(Provider::class.java)
    }
  }

  @TestFactory
  fun `a factory class is generated for a class with a type parameter`() =
    testFactory {
      /*
  package com.squareup.test;

  import dagger.internal.Factory;
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
  public final class InjectClass_Factory<T> implements Factory<InjectClass<T>> {
    private final Provider<T> creatorProvider;
    public InjectClass_Factory(Provider<T> creatorProvider) {
      this.creatorProvider = creatorProvider;
    }
    @Override
    public BaseViewModelFactory<T> get() {
      return newInstance(creatorProvider);
    }
    public static <T> InjectClass_Factory<T> create(Provider<T> creatorProvider) {
      return new InjectClass_Factory<T>(creatorProvider);
    }
    public static <T> InjectClass<T> newInstance(Provider<T> creator) {
      return new InjectClass<T>(creator);
    }
  }
       */

      compile2(
        """
      package com.squareup.test
      
      import javax.inject.Inject
      import javax.inject.Provider
      
      class InjectClass<T> @Inject constructor(prov: Provider<T>)
      """,
      ) {
        val constructor = classLoader.loadClass("com.squareup.test.InjectClass")
          .factoryClass().declaredConstructors.single()
        assertThat(constructor.parameterTypes.toList()).containsExactly(Provider::class.java)
      }
    }

  @TestFactory
  fun `a factory class is generated for a class with a type parameter no constructor argument`() =
    testFactory {
      /*
  package com.squareup.test;

  import dagger.internal.DaggerGenerated;
  import dagger.internal.Factory;
  import javax.annotation.processing.Generated;

  @DaggerGenerated
  @Generated(
      value = "dagger.internal.codegen.ComponentProcessor",
      comments = "https://dagger.dev"
  )
  @SuppressWarnings({
      "unchecked",
      "rawtypes"
  })
  public final class InjectClass_Factory<T> implements Factory<InjectClass<T>> {
    @Override
    public InjectClass<T> get() {
      return newInstance();
    }

    @SuppressWarnings("unchecked")
    public static <T> InjectClass_Factory<T> create() {
      return InstanceHolder.INSTANCE;
    }

    public static <T> InjectClass<T> newInstance() {
      return new InjectClass<T>();
    }

    private static final class InstanceHolder {
      @SuppressWarnings("rawtypes")
      private static final InjectClass_Factory INSTANCE = new InjectClass_Factory();
    }
  }
       */

      compile2(
        """
      package com.squareup.test
      
      import javax.inject.Inject
      
      class InjectClass<T> @Inject constructor()
      """,
      ) {
        val constructor = classLoader.loadClass("com.squareup.test.InjectClass")
          .factoryClass().declaredConstructors.single()
        assertThat(constructor.parameterTypes.toList()).isEmpty()
      }
    }

  @TestFactory
  fun `a factory class is generated for generics without modifier`() = testFactory {
    /*
package com.squareup.test;

import dagger.internal.Factory;
import java.util.Map;
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
public final class InjectClass_Factory implements Factory<InjectClass> {
  private final Provider<Map<Class<? extends CharSequence>, Provider<String>>> delegatesProvider;

  public InjectClass_Factory(
      Provider<Map<Class<? extends CharSequence>, Provider<String>>> delegatesProvider) {
    this.delegatesProvider = delegatesProvider;
  }

  @Override
  public InjectClass get() {
    return newInstance(delegatesProvider.get());
  }

  public static InjectClass_Factory create(
      Provider<Map<Class<? extends CharSequence>, Provider<String>>> delegatesProvider) {
    return new InjectClass_Factory(delegatesProvider);
  }

  public static InjectClass newInstance(
      Map<Class<? extends CharSequence>, Provider<String>> delegates) {
    return new InjectClass(delegates);
  }
}
     */

    compile2(
      """
      package com.squareup.test
      
      import javax.inject.Inject
      import javax.inject.Provider
      
      class InjectClass @Inject constructor(
        map: Map<Class<out CharSequence>, @JvmSuppressWildcards Provider<String>>
      )
      """,
    ) {
      val constructor = classLoader.loadClass("com.squareup.test.InjectClass")
        .factoryClass().declaredConstructors.single()
      assertThat(constructor.parameterTypes.toList()).containsExactly(Provider::class.java)
    }
  }

  @TestFactory
  fun `a factory class is generated for type parameter which extends class`() =
    testFactory {
      /*
  package com.squareup.test;

  import dagger.internal.Factory;
  import javax.inject.Provider;

  public final class InjectClass_Factory<T extends CharSequence> implements Factory<InjectClass<T>> {
      private final Provider<T> elementProvider;

      public InjectClass_Factory(Provider<T> var1) {
          this.elementProvider = var1;
      }

      public InjectClass<T> get() {
          return newInstance((CharSequence)this.elementProvider.get());
      }

      public static <T extends CharSequence> InjectClass_Factory<T> create(Provider<T> var0) {
          return new InjectClass_Factory(var0);
      }

      public static <T extends CharSequence> InjectClass<T> newInstance(T var0) {
          return new InjectClass(var0);
      }
  }
       */

      compile2(
        """
      package com.squareup.test
      
      import javax.inject.Inject
      import javax.inject.Provider
      
      class InjectClass<T : CharSequence> @Inject constructor(element: Provider<T>)
      """,
      ) {
        val constructor = classLoader.loadClass("com.squareup.test.InjectClass")
          .factoryClass().declaredConstructors.single()
        assertThat(constructor.parameterTypes.toList()).containsExactly(Provider::class.java)
      }
    }

  @TestFactory
  fun `a factory class is generated for a generic class with a where clause`() =
    testFactory {
      /*
  package com.squareup.test

  import dagger.`internal`.Factory
  import java.lang.Appendable
  import javax.inject.Provider
  import kotlin.CharSequence
  import kotlin.Suppress
  import kotlin.jvm.JvmStatic

  public class InjectClass_Factory<T>(
    private val param0: Provider<T>
  ) : Factory<InjectClass<T>> where T : Appendable, T : CharSequence {
    public override fun `get`(): InjectClass<T> = newInstance(param0.get())

    public companion object {
      @JvmStatic
      public fun <T> create(param0: Provider<T>): InjectClass_Factory<T> where T : Appendable, T :
          CharSequence = InjectClass_Factory<T>(param0)

      @JvmStatic
      public fun <T> newInstance(param0: T): InjectClass<T> where T : Appendable, T : CharSequence =
          InjectClass<T>(param0)
    }
  }
       */

      skipWhen(mode.isK2) { "https://github.com/square/anvil/issues/1122" }

      compile2(
        """
        package com.squareup.test
        
        import javax.inject.Inject
        import javax.inject.Provider
        
        class InjectClass<T> @Inject constructor(
          private val t: T
        ) where T : Appendable, T : CharSequence
        """,
      ) {

        val factoryClass = classLoader.injectClass_Factory

        val typeParams = factoryClass.typeParameters
          .associate { param ->
            param.name to param.bounds.map { it.typeName }
          }

        assertThat(typeParams)
          .containsExactly("T", listOf("java.lang.Appendable", "java.lang.CharSequence"))

        val constructor = factoryClass.declaredConstructors.single()
        assertThat(constructor.parameterTypes.toList()).containsExactly(Provider::class.java)

        val staticMethods = factoryClass.declaredMethods.filter { it.isStatic }

        val factoryInstance = staticMethods.single { it.name == "create" }
          .invoke(null, Provider { StringBuilder() })
        assertThat(factoryInstance::class.java).isEqualTo(factoryClass)

        val instance = staticMethods.single { it.name == "newInstance" }
          .invoke(null, StringBuilder())

        assertThat(instance).isNotNull()
        assertThat((factoryInstance as Factory<*>).get()).isNotNull()
      }
    }

  @TestFactory
  fun `a factory class is generated for a generic class with two type parameters and a where clause`() =
    testFactory {
      /*
  package com.squareup.test

  import dagger.Lazy
  import dagger.`internal`.Factory
  import java.lang.Appendable
  import javax.inject.Provider
  import kotlin.CharSequence
  import kotlin.String
  import kotlin.Suppress
  import kotlin.collections.Set
  import kotlin.jvm.JvmStatic
  import kotlin.jvm.JvmSuppressWildcards

  public class InjectClass_Factory<T, R : Set<String>>(
    private val param0: Provider<T>,
    private val param1: Provider<@JvmSuppressWildcards R>
  ) : Factory<InjectClass<T, R>> where T : Appendable, T : CharSequence {
    public override fun `get`(): InjectClass<T, R> = newInstance(param0.get(),
        dagger.internal.DoubleCheck.lazy(param1))

    public companion object {
      @JvmStatic
      public fun <T, R : Set<String>> create(param0: Provider<T>,
          param1: Provider<@JvmSuppressWildcards R>): InjectClass_Factory<T, R> where T : Appendable,
          T : CharSequence = InjectClass_Factory<T, R>(param0, param1)

      @JvmStatic
      public fun <T, R : Set<String>> newInstance(param0: T, param1: Lazy<@JvmSuppressWildcards R>):
          InjectClass<T, R> where T : Appendable, T : CharSequence = InjectClass<T, R>(param0, param1)
    }
  }
       */

      skipWhen(mode.isK2) { "https://github.com/square/anvil/issues/1122" }

      compile2(
        """
        package com.squareup.test
        
        import dagger.Lazy
        import javax.inject.Inject
        import javax.inject.Provider
        
        class InjectClass<T, R : Set<String>> @Inject constructor(
          private val t: T,
          private val r: Lazy<R>
        ) where T : Appendable, T : CharSequence
        """,
      ) {

        val factoryClass = classLoader.injectClass_Factory

        val typeParams = factoryClass.typeParameters
          .associate { param ->
            param.name to param.bounds.map { it.typeName }
          }

        assertThat(typeParams)
          .containsExactly(
            "T",
            listOf("java.lang.Appendable", "java.lang.CharSequence"),
            "R",
            listOf("java.util.Set<? extends java.lang.String>"),
          )

        val constructor = factoryClass.declaredConstructors.single()
        assertThat(constructor.parameterTypes.toList())
          .containsExactly(
            Provider::class.java,
            Provider::class.java,
          )

        val staticMethods = factoryClass.declaredMethods.filter { it.isStatic }

        val factoryInstance = staticMethods.single { it.name == "create" }
          .invoke(null, Provider { StringBuilder() }, Provider { mutableSetOf<String>() })

        assertThat(factoryInstance::class.java).isEqualTo(factoryClass)

        val instance = staticMethods.single { it.name == "newInstance" }
          .invoke(null, StringBuilder(), Lazy { mutableSetOf<String>() })

        assertThat(instance).isNotNull()
        assertThat((factoryInstance as Factory<*>).get()).isNotNull()
      }
    }

  @TestFactory
  fun `a factory class is generated for a generic class with a where clause with a class bound`() =
    testFactory {
      /*
  package com.squareup.test

  import com.squareup.test.Other
  import dagger.`internal`.Factory
  import java.lang.Appendable
  import javax.inject.Provider
  import kotlin.Suppress
  import kotlin.jvm.JvmStatic

  public class InjectClass_Factory<T>(
    private val param0: Provider<T>
  ) : Factory<InjectClass<T>> where T : Appendable, T : Other {
    public override fun `get`(): InjectClass<T> = newInstance(param0.get())

    public companion object {
      @JvmStatic
      public fun <T> create(param0: Provider<T>): InjectClass_Factory<T> where T : Appendable, T :
          Other = InjectClass_Factory<T>(param0)

      @JvmStatic
      public fun <T> newInstance(param0: T): InjectClass<T> where T : Appendable, T : Other =
          InjectClass<T>(param0)
    }
  }
       */

      skipWhen(mode.isK2) { "https://github.com/square/anvil/issues/1120" }

      // KotlinPoet will automatically add a bound of `Any?` if creating a `TypeVariableName` with an
      // empty list.  So, improperly handling `TypeVariableName` can result in a constraint like:
      // `where T : Any?, T : Appendable, T : Other`
      // This won't compile since a type can only have one bound which is a class.
      compile2(
        """
      package com.squareup.test
      
      import javax.inject.Inject
      import javax.inject.Provider
      
      abstract class Other
      
      class InjectClass<T> @Inject constructor(
        private val t: T
      ) where T : Appendable, T : Other
      """,
      ) {

        val factoryClass = classLoader.injectClass_Factory

        val typeParams = factoryClass.typeParameters
          .associate { param ->
            param.name to param.bounds.map { it.typeName }
          }

        assertThat(typeParams)
          .containsExactly("T", listOf("com.squareup.test.Other", "java.lang.Appendable"))

        val constructor = factoryClass.declaredConstructors.single()
        assertThat(constructor.parameterTypes.toList()).containsExactly(Provider::class.java)
      }
    }

  @TestFactory
  fun `a factory class is generated for a type parameter which extends a generic`() =
    testFactory {
      /*
  package com.squareup.test

  import dagger.`internal`.Factory
  import javax.inject.Provider
  import kotlin.String
  import kotlin.Suppress
  import kotlin.collections.List
  import kotlin.jvm.JvmStatic

  public class InjectClass_Factory<T : List<String>>(
    private val param0: Provider<T>
  ) : Factory<InjectClass<T>> {
    public override fun `get`(): InjectClass<T> = newInstance(param0.get())

    public companion object {
      @JvmStatic
      public fun <T : List<String>> create(param0: Provider<T>): InjectClass_Factory<T> =
          InjectClass_Factory<T>(param0)

      @JvmStatic
      public fun <T : List<String>> newInstance(param0: T): InjectClass<T> = InjectClass<T>(param0)
    }
  }
       */

      skipWhen(mode.isK2) { "https://github.com/square/anvil/issues/1122" }

      compile2(
        """
        package com.squareup.test
        
        import javax.inject.Inject
        import javax.inject.Provider
  
        class InjectClass<T : List<String>> @Inject constructor(
          private val t: T
        )
        """,
      ) {

        val factoryClass = classLoader.injectClass_Factory

        val typeParams = factoryClass.typeParameters
          .associate { param ->
            param.name to param.bounds.map { it.typeName }
          }

        assertThat(typeParams)
          .containsExactly("T", listOf("java.util.List<? extends java.lang.String>"))

        val constructor = factoryClass.declaredConstructors.single()
        assertThat(constructor.parameterTypes.toList())
          .containsExactly(Provider::class.java)

        val staticMethods = factoryClass.declaredMethods.filter { it.isStatic }

        val factoryInstance = staticMethods.single { it.name == "create" }
          .invoke(null, Provider { listOf<String>() })

        assertThat(factoryInstance::class.java).isEqualTo(factoryClass)

        val instance = staticMethods.single { it.name == "newInstance" }
          .invoke(null, listOf<String>())

        assertThat(instance).isNotNull()
        assertThat((factoryInstance as Factory<*>).get()).isNotNull()
      }
    }

  @TestFactory
  fun `a factory class is generated for an inject constructor without a package`() =
    testFactory {
      compile2(
        """
      import javax.inject.Inject
      
      class InjectClass @Inject constructor()
      """,
      ) {
        val factoryClass = classLoader.loadClass("InjectClass").factoryClass()

        val constructor = factoryClass.declaredConstructors.single()
        assertThat(constructor.parameterTypes.toList()).isEmpty()

        val staticMethods = factoryClass.declaredMethods.filter { it.isStatic }

        val factoryInstance = staticMethods.single { it.name == "create" }
          .invoke(null)
        assertThat(factoryInstance::class.java).isEqualTo(factoryClass)

        val instance = staticMethods.single { it.name == "newInstance" }
          .invoke(null)

        assertThat(instance).isNotNull()
        assertThat((factoryInstance as Factory<*>).get()).isNotNull()
      }
    }

  @TestFactory
  fun `two inject constructors aren't supported`() = testFactory {
    compile2(
      """
      package com.squareup.test
      
      import javax.inject.Inject
      
      class InjectClass @Inject constructor() {
        @Inject constructor(string: String)
      }
      """,
      expectedExitCode = ExitCode.COMPILATION_ERROR,
    ) {
      // assertThat(
      //   compilationErrorLine()
      //     .removeParametersAndSort(),
      // ).contains(
      //   "Type com.squareup.test.InjectClass may only contain one injected constructor. " +
      //     "Found: [@Inject com.squareup.test.InjectClass, @Inject com.squareup.test.InjectClass]",
      // )
    }
  }

  @TestFactory
  fun `verify the right state class is imported`() = testFactory {
    // That was reported here https://github.com/square/anvil/issues/358
    compile2(
      """
      package com.squareup.test
      
      interface Contract {
        data class State(
          val loading: Boolean = false
        )
      }
      """,
      """
      package com.squareup.test
        
      import com.squareup.test.Contract.*
      import javax.inject.Inject
      
      class SomeClass @Inject constructor(
        state: State
      )  
      """,
    ) {
      assertThat(exitCode).isEqualTo(ExitCode.OK)
    }
  }

  @TestFactory
  fun `verify qualifiers are supported`() = testFactory {
    /*
package com.squareup.test;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@DaggerGenerated
@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes"
})
public final class InjectClass_Factory implements Factory<InjectClass> {
  private final Provider<String> classArrayStringProvider;

  private final Provider<String> classStringProvider;

  private final Provider<String> enumArrayStringProvider;

  private final Provider<String> enumStringProvider;

  private final Provider<String> intStringProvider;

  private final Provider<String> string1Provider;

  private final Provider<String> string2Provider;

  private final Provider<String> string3Provider;

  public InjectClass_Factory(Provider<String> classArrayStringProvider,
      Provider<String> classStringProvider, Provider<String> enumArrayStringProvider,
      Provider<String> enumStringProvider, Provider<String> intStringProvider,
      Provider<String> string1Provider, Provider<String> string2Provider,
      Provider<String> string3Provider) {
    this.classArrayStringProvider = classArrayStringProvider;
    this.classStringProvider = classStringProvider;
    this.enumArrayStringProvider = enumArrayStringProvider;
    this.enumStringProvider = enumStringProvider;
    this.intStringProvider = intStringProvider;
    this.string1Provider = string1Provider;
    this.string2Provider = string2Provider;
    this.string3Provider = string3Provider;
  }

  @Override
  public InjectClass get() {
    return newInstance(classArrayStringProvider.get(), classStringProvider.get(), enumArrayStringProvider.get(), enumStringProvider.get(), intStringProvider.get(), string1Provider.get(), string2Provider.get(), string3Provider.get());
  }

  public static InjectClass_Factory create(Provider<String> classArrayStringProvider,
      Provider<String> classStringProvider, Provider<String> enumArrayStringProvider,
      Provider<String> enumStringProvider, Provider<String> intStringProvider,
      Provider<String> string1Provider, Provider<String> string2Provider,
      Provider<String> string3Provider) {
    return new InjectClass_Factory(classArrayStringProvider, classStringProvider, enumArrayStringProvider, enumStringProvider, intStringProvider, string1Provider, string2Provider, string3Provider);
  }

  public static InjectClass newInstance(String classArrayString, String classString,
      String enumArrayString, String enumString, String intString, String string1, String string2,
      String string3) {
    return new InjectClass(classArrayString, classString, enumArrayString, enumString, intString, string1, string2, string3);
  }
}
     */
    compile2(
      """
      package com.squareup.test

      import kotlin.LazyThreadSafetyMode.NONE
      import kotlin.LazyThreadSafetyMode.SYNCHRONIZED 
      import kotlin.reflect.KClass      
      import javax.inject.Inject
      import javax.inject.Named
      import javax.inject.Qualifier
      
      const val CONSTANT = "def"

      class InjectClass @Inject constructor(
        @ClassArrayQualifier([String::class, Int::class]) val classArrayString: String,
        @ClassQualifier(String::class) val classString: String,
        @EnumArrayQualifier([NONE, SYNCHRONIZED]) val enumArrayString: String,
        @EnumQualifier(SYNCHRONIZED) val enumString: String,
        @IntQualifier(3) val intString: String,
        @StringQualifier("abc") val string1: String,
        @StringQualifier(CONSTANT) val string2: String,
        @Named(CONSTANT) val string3: String
      )
      
      @Qualifier
      annotation class ClassArrayQualifier(val value: Array<KClass<*>>)
      
      @Qualifier
      annotation class ClassQualifier(val value: KClass<*>)
      
      @Qualifier
      annotation class EnumArrayQualifier(val value: Array<LazyThreadSafetyMode>)
      
      @Qualifier
      annotation class EnumQualifier(val value: LazyThreadSafetyMode)
      
      @Qualifier
      annotation class IntQualifier(val value: Int)
      
      @Qualifier
      annotation class StringQualifier(val value: String)
      """,
    ) {
      val constructor = classLoader.injectClass_Factory.declaredConstructors.single()
      assertThat(constructor.parameterTypes.toList()).hasSize(8)
    }
  }

  @TestFactory
  fun `nested inner classes are supported`() = testFactory {
    compile2(
      """
      package com.squareup.test

      import kotlin.LazyThreadSafetyMode.NONE
      import kotlin.LazyThreadSafetyMode.SYNCHRONIZED 
      import kotlin.reflect.KClass      
      import javax.inject.Inject
      import javax.inject.Named
      import javax.inject.Qualifier
      
      class A {
        class Factory @Inject constructor(
          private val innerClassFactory: Achild.Factory
        )
      
        class Achild {
          class Factory
        }
      }
      """,
    ) {
      assertThat(classLoader.loadClass("com.squareup.test.A\$Achild\$Factory")).isNotNull()
    }
  }
}
