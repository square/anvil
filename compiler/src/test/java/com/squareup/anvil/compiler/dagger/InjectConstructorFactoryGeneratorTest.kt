package com.squareup.anvil.compiler.dagger

import com.google.common.truth.Truth.assertThat
import com.squareup.anvil.compiler.factoryClass
import com.squareup.anvil.compiler.injectClass
import com.squareup.anvil.compiler.isStatic
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
class InjectConstructorFactoryGeneratorTest(
  private val useDagger: Boolean
) {

  companion object {
    @Parameters(name = "Use Dagger: {0}")
    @JvmStatic fun useDagger(): Collection<Any> {
      return listOf(true, false)
    }
  }

  @Test fun `a factory class is generated for an inject constructor without arguments`() {
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

    compile(
      """
      package com.squareup.test
      
      import javax.inject.Inject
      
      class InjectClass @Inject constructor()
      """
    ) {
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

  @Test fun `a factory class is generated for an inject constructor with arguments`() {
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

    compile(
      """
      package com.squareup.test
      
      import javax.inject.Inject
      
      data class InjectClass @Inject constructor(
        val string: String, 
        val int: Int
      )
      """
    ) {
      val factoryClass = injectClass.factoryClass()

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

  @Test
  fun `a factory class is generated for an inject constructor with Provider and Lazy arguments`() {
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

    compile(
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
      """
    ) {
      val factoryClass = injectClass.factoryClass()

      val constructor = factoryClass.declaredConstructors.single()
      assertThat(constructor.parameterTypes.toList())
        .containsExactly(
          Provider::class.java,
          Provider::class.java,
          Provider::class.java,
          Provider::class.java
        )
        .inOrder()

      val staticMethods = factoryClass.declaredMethods.filter { it.isStatic }

      val factoryInstance = staticMethods.single { it.name == "create" }
        .invoke(
          null,
          Provider { "a" },
          Provider { "b" },
          Provider { listOf("c") },
          Provider { "d" }
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

  @Test fun `a factory class is generated for an inject constructor with star imports`() {
    /*
package com.squareup.test;

import dagger.internal.Factory;
import error.NonExistentClass;
import java.io.File;
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
  private final Provider<File> fileProvider;

  private final Provider<NonExistentClass> pathProvider;

  public InjectClass_Factory(Provider<File> fileProvider, Provider<NonExistentClass> pathProvider) {
    this.fileProvider = fileProvider;
    this.pathProvider = pathProvider;
  }

  @Override
  public InjectClass get() {
    return newInstance(fileProvider.get(), pathProvider.get());
  }

  public static InjectClass_Factory create(Provider<File> fileProvider,
      Provider<NonExistentClass> pathProvider) {
    return new InjectClass_Factory(fileProvider, pathProvider);
  }

  public static InjectClass newInstance(File file, NonExistentClass path) {
    return new InjectClass(file, path);
  }
}
     */

    compile(
      """
      package com.squareup.test
      
      import java.io.*
      import java.nio.file.*
      import javax.inject.*
      
      data class InjectClass @Inject constructor(
        val file: File, 
        val path: Path
      )
      """
    ) {
      val factoryClass = injectClass.factoryClass()

      val constructor = factoryClass.declaredConstructors.single()
      assertThat(constructor.parameterTypes.toList())
        .containsExactly(Provider::class.java, Provider::class.java)
        .inOrder()

      val staticMethods = factoryClass.declaredMethods.filter { it.isStatic }

      val factoryInstance = staticMethods.single { it.name == "create" }
        .invoke(null, Provider { File("") }, Provider { File("").toPath() })
      assertThat(factoryInstance::class.java).isEqualTo(factoryClass)

      val newInstance = staticMethods.single { it.name == "newInstance" }
        .invoke(null, File(""), File("").toPath())
      val getInstance = (factoryInstance as Factory<*>).get()

      assertThat(newInstance).isNotNull()
      assertThat(getInstance).isNotNull()

      assertThat(newInstance).isEqualTo(getInstance)
      assertThat(newInstance).isNotSameInstanceAs(getInstance)
    }
  }

  @Test fun `a factory class is generated for an inject constructor with generic arguments`() {
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

    compile(
      """
      package com.squareup.test
      
      import javax.inject.Inject
      
      data class InjectClass @Inject constructor(
        val pair: Pair<Pair<String, Int>, List<String>>, 
        val set: Set<String>
      )
      """
    ) {
      val factoryClass = injectClass.factoryClass()

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

  @Test fun `a factory class is generated for an inject constructor inner class`() {
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

    compile(
      """
      package com.squareup.test
      
      import javax.inject.Inject
      
      class OuterClass {
        class InjectClass @Inject constructor()
      }
      """
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

  @Test fun `a factory class is generated for an inject constructor with function arguments`() {
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

    compile(
      """
      package com.squareup.test
      
      import javax.inject.Inject
      
      typealias StringList = List<String>
      
      data class InjectClass @Inject constructor(
        val string: String, 
        val set: @JvmSuppressWildcards Set<(StringList) -> StringList>
      )
      """
    ) {
      val factoryClass = injectClass.factoryClass()

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

  @Test fun `a factory class is generated for a class starting with a lowercase character`() {
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

    compile(
      """
      package com.squareup.test
      
      import javax.inject.Inject
      
      data class injectClass @Inject constructor(val string: String)
      """
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

  @Test
  fun `a factory class is generated for a class injecting a class starting with a lowercase character`() {
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

    compile(
      """
      package com.squareup.test
      
      import javax.inject.Inject
      
      class InjectClass @Inject constructor(inner: otherClass.inner)
      
      class otherClass {
        class inner @Inject constructor()
      }
      """
    ) {
      val constructor = injectClass.factoryClass().declaredConstructors.single()
      assertThat(constructor.parameterTypes.toList()).containsExactly(Provider::class.java)
    }
  }

  @Test
  fun `a factory class is generated for a class injecting an inner class with a generic type parameter`() {
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

    compile(
      """
      package com.squareup.test
      
      import javax.inject.Inject
      
      class Other {
        class Inner<T>
      }
      
      class InjectClass @Inject constructor(
        val inner: Other.Inner<String>
      )
      """
    ) {
      val constructor = injectClass.factoryClass().declaredConstructors.single()
      assertThat(constructor.parameterTypes.toList()).containsExactly(Provider::class.java)
    }
  }

  @Test
  fun `a factory class is generated for a class injecting a deeply nested inner class with a generic type parameter`() {
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

    compile(
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
      """
    ) {
      val constructor = injectClass.factoryClass().declaredConstructors.single()
      assertThat(constructor.parameterTypes.toList()).containsExactly(Provider::class.java)
    }
  }

  @Test
  fun `a factory class is generated for an inner class starting with a lowercase character`() {
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

    compile(
      """
      package com.squareup.test
      
      import javax.inject.Inject
      
      class MyClass @Inject constructor(arg: innerClass) {
        class innerClass @Inject constructor()
      }
      """
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

  @Test fun `inner classes are imported correctly`() {
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

    compile(
      """
      package com.squareup.test
      
      import javax.inject.Inject
      
      class InjectClass @Inject constructor(dependencies: Dependencies) {
        class Dependencies @Inject constructor()     
      }
      """
    ) {
      val constructor = injectClass.factoryClass().declaredConstructors.single()
      assertThat(constructor.parameterTypes.toList()).containsExactly(Provider::class.java)

      val constructorDependencies = classLoader
        .loadClass("com.squareup.test.InjectClass\$Dependencies")
        .factoryClass()
        .declaredConstructors
        .single()
      assertThat(constructorDependencies.parameterTypes.toList()).isEmpty()
    }
  }

  @Test fun `inner classes are imported correctly from super type`() {
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

    compile(
      """
      package com.squareup.test
      
      import javax.inject.Inject
      
      open class ParentOne(d: Dependencies) {
        class Dependencies @Inject constructor()
      }
      
      open class ParentTwo(d: Dependencies) : ParentOne(d)
      
      class InjectClass @Inject constructor(d: Dependencies) : ParentTwo(d)
      """
    ) {
      val constructor = injectClass.factoryClass().declaredConstructors.single()
      assertThat(constructor.parameterTypes.toList()).containsExactly(Provider::class.java)

      val constructorDependencies = classLoader
        .loadClass("com.squareup.test.ParentOne\$Dependencies")
        .factoryClass()
        .declaredConstructors
        .single()
      assertThat(constructorDependencies.parameterTypes.toList()).isEmpty()
    }
  }

  @Test fun `inner classes in generics are imported correctly`() {
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

    compile(
      """
      package com.squareup.test
      
      import javax.inject.Inject
      
      class InjectClass @Inject constructor(
        interceptors: Set<@JvmSuppressWildcards Interceptor>
      ) {
        interface Interceptor
      }
      """
    ) {
      val constructor = injectClass.factoryClass().declaredConstructors.single()
      assertThat(constructor.parameterTypes.toList()).containsExactly(Provider::class.java)
    }
  }

  @Test fun `inner classes from outer type are imported correctly`() {
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

    compile(
      """
      package com.squareup.test
      
      import com.squareup.anvil.compiler.dagger.OuterClass
      import javax.inject.Inject
      
      class InjectClass(innerClass: InnerClass): OuterClass(innerClass) {
        class Inner @Inject constructor(innerClass: InnerClass)
      }
      """
    ) {
      val constructor = classLoader.loadClass("com.squareup.test.InjectClass\$Inner")
        .factoryClass().declaredConstructors.single()
      assertThat(constructor.parameterTypes.toList()).containsExactly(Provider::class.java)
    }
  }

  @Test fun `a factory class is generated for a class with a type parameter`() {
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

    compile(
      """
      package com.squareup.test
      
      import javax.inject.Inject
      import javax.inject.Provider;
      
      class InjectClass<T> @Inject constructor(prov: Provider<T>)
      """
    ) {
      val constructor = classLoader.loadClass("com.squareup.test.InjectClass")
        .factoryClass().declaredConstructors.single()
      assertThat(constructor.parameterTypes.toList()).containsExactly(Provider::class.java)
    }
  }

  @Test fun `a factory class is generated for generics without modifier`() {
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

    compile(
      """
      package com.squareup.test
      
      import javax.inject.Inject
      import javax.inject.Provider;
      
      class InjectClass @Inject constructor(
        map: Map<Class<out CharSequence>, @JvmSuppressWildcards Provider<String>>
      )
      """
    ) {
      val constructor = classLoader.loadClass("com.squareup.test.InjectClass")
        .factoryClass().declaredConstructors.single()
      assertThat(constructor.parameterTypes.toList()).containsExactly(Provider::class.java)
    }
  }

  @Test fun `a factory class is generated for type parameter which extends class`() {
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

    compile(
      """
      package com.squareup.test
      
      import javax.inject.Inject
      import javax.inject.Provider
      
      class InjectClass<T : CharSequence> @Inject constructor(element: Provider<T>)
      """
    ) {
      val constructor = classLoader.loadClass("com.squareup.test.InjectClass")
        .factoryClass().declaredConstructors.single()
      assertThat(constructor.parameterTypes.toList()).containsExactly(Provider::class.java)
    }
  }

  @Test fun `a factory class is generated for an inject constructor without a package`() {
    compile(
      """
      import javax.inject.Inject
      
      class InjectClass @Inject constructor()
      """
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

  @Test fun `two inject constructors aren't supported`() {
    compile(
      """
      package com.squareup.test
      
      import javax.inject.Inject
      
      class InjectClass @Inject constructor() {
        @Inject constructor(string: String)
      }
      """
    ) {
      assertThat(exitCode).isEqualTo(COMPILATION_ERROR)
      assertThat(messages).contains("Types may only contain one injected constructor")
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
    // Many constructor parameters are unused.
    allWarningsAsErrors = false,
    block = block
  )
}
