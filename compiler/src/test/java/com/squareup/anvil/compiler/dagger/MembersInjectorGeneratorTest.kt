package com.squareup.anvil.compiler.dagger

import com.google.common.truth.Truth.assertThat
import com.squareup.anvil.compiler.USE_IR
import com.squareup.anvil.compiler.injectClass
import com.squareup.anvil.compiler.internal.capitalize
import com.squareup.anvil.compiler.internal.testing.compileAnvil
import com.squareup.anvil.compiler.internal.testing.createInstance
import com.squareup.anvil.compiler.internal.testing.getValue
import com.squareup.anvil.compiler.internal.testing.isStatic
import com.squareup.anvil.compiler.internal.testing.membersInjector
import com.squareup.anvil.compiler.nestedInjectClass
import com.tschuchort.compiletesting.KotlinCompilation.Result
import dagger.Lazy
import dagger.MembersInjector
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters
import java.io.File
import java.lang.reflect.Method
import javax.inject.Named
import javax.inject.Provider

@Suppress("UNCHECKED_CAST")
@RunWith(Parameterized::class)
class MembersInjectorGeneratorTest(
  private val useDagger: Boolean
) {

  companion object {
    @Parameters(name = "Use Dagger: {0}")
    @JvmStatic fun useDagger(): Collection<Any> {
      return listOf(true, false)
    }
  }

  @Test fun `a factory class is generated for a field injection`() {
    /*
package com.squareup.test;

import dagger.MembersInjector;
import dagger.internal.InjectedFieldSignature;
import java.util.List;
import java.util.Set;
import javax.annotation.Generated;
import javax.inject.Named;
import javax.inject.Provider;
import kotlin.Pair;
import kotlin.jvm.functions.Function1;

@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes"
})
public final class InjectClass_MembersInjector implements MembersInjector<InjectClass> {
  private final Provider<String> stringProvider;

  private final Provider<String> qualifiedStringProvider;

  private final Provider<CharSequence> charSequenceProvider;

  private final Provider<List<String>> listProvider;

  private final Provider<Pair<Pair<String, Integer>, ? extends Set<String>>> pairProvider;

  private final Provider<Set<Function1<List<String>, List<String>>>> setProvider;

  public InjectClass_MembersInjector(Provider<String> stringProvider,
      Provider<String> qualifiedStringProvider, Provider<CharSequence> charSequenceProvider,
      Provider<List<String>> listProvider, Provider<Pair<Pair<String, Integer>,
      ? extends Set<String>>> pairProvider, Provider<Set<Function1<List<String>, List<String>>>> setProvider) {
    this.stringProvider = stringProvider;
    this.qualifiedStringProvider = qualifiedStringProvider;
    this.charSequenceProvider = charSequenceProvider;
    this.listProvider = listProvider;
    this.pairProvider = pairProvider;
    this.setProvider = setProvider;
  }

  public static MembersInjector<InjectClass> create(Provider<String> stringProvider,
      Provider<String> qualifiedStringProvider, Provider<CharSequence> charSequenceProvider,
      Provider<List<String>> listProvider, Provider<Pair<Pair<String, Integer>,
      ? extends Set<String>>> pairProvider, Provider<Set<Function1<List<String>,
      List<String>>>> setProvider) {
    return new InjectClass_MembersInjector(stringProvider, qualifiedStringProvider, charSequenceProvider, listProvider, pairProvider, setProvider);}

  @Override
  public void injectMembers(InjectClass instance) {
    injectString(instance, stringProvider.get());
    injectQualifiedString(instance, qualifiedStringProvider.get());
    injectCharSequence(instance, charSequenceProvider.get());
    injectList(instance, listProvider.get());
    injectPair(instance, pairProvider.get());
    injectSet(instance, setProvider.get());
  }

  @InjectedFieldSignature("com.squareup.test.InjectClass.string")
  public static void injectString(InjectClass instance, String string) {
    instance.string = string;
  }

  @Named("qualified")
  @InjectedFieldSignature("com.squareup.test.InjectClass.qualifiedString")
  public static void injectQualifiedString(InjectClass instance, String qualifiedString) {
    instance.qualifiedString = qualifiedString;
  }

  @InjectedFieldSignature("com.squareup.test.InjectClass.charSequence")
  public static void injectCharSequence(InjectClass instance, CharSequence charSequence) {
    instance.charSequence = charSequence;
  }

  @InjectedFieldSignature("com.squareup.test.InjectClass.list")
  public static void injectList(InjectClass instance, List<String> list) {
    instance.list = list;
  }

  @InjectedFieldSignature("com.squareup.test.InjectClass.pair")
  public static void injectPair(InjectClass instance,
      Pair<Pair<String, Integer>, ? extends Set<String>> pair) {
    instance.pair = pair;
  }

  @InjectedFieldSignature("com.squareup.test.InjectClass.set")
  public static void injectSet(InjectClass instance,
      Set<Function1<List<String>, List<String>>> set) {
    instance.set = set;
  }
}
     */

    compile(
      """
      package com.squareup.test
      
      import javax.inject.Inject
      import javax.inject.Named
      
      typealias StringList = List<String>
      
      class InjectClass {
        @Inject lateinit var string: String
        @Named("qualified") @Inject lateinit var qualifiedString: String
        @Inject lateinit var charSequence: CharSequence
        @Inject lateinit var list: List<String>
        @Inject lateinit var pair: Pair<Pair<String, Int>, Set<String>>
        @Inject lateinit var set: @JvmSuppressWildcards Set<(StringList) -> StringList>
        var setterAnnotated: Map<String, String> = emptyMap()
          @Inject set
        @set:Inject var setterAnnotated2: Map<String, Boolean> = emptyMap()
        
        override fun equals(other: Any?): Boolean {
          if (this === other) return true
          if (javaClass != other?.javaClass) return false
      
          other as InjectClass
      
          if (string != other.string) return false
          if (qualifiedString != other.qualifiedString) return false
          if (charSequence != other.charSequence) return false
          if (list != other.list) return false
          if (pair != other.pair) return false
          if (set.single().invoke(emptyList())[0] != other.set.single().invoke(emptyList())[0]) return false
          if (setterAnnotated != other.setterAnnotated) return false
          if (setterAnnotated2 != other.setterAnnotated2) return false
      
          return true
        }
      
        override fun hashCode(): Int {
          var result = string.hashCode()
          result = 31 * result + qualifiedString.hashCode()
          result = 31 * result + charSequence.hashCode()
          result = 31 * result + list.hashCode()
          result = 31 * result + pair.hashCode()
          result = 31 * result + set.single().invoke(emptyList())[0].hashCode()
          result = 31 * result + setterAnnotated.hashCode()
          result = 31 * result + setterAnnotated2.hashCode()
          return result
        }
      }
      """
    ) {
      val membersInjector = injectClass.membersInjector()

      val constructor = membersInjector.declaredConstructors.single()
      assertThat(constructor.parameterTypes.toList())
        .containsExactly(
          Provider::class.java,
          Provider::class.java,
          Provider::class.java,
          Provider::class.java,
          Provider::class.java,
          Provider::class.java,
          Provider::class.java,
          Provider::class.java
        )

      @Suppress("RedundantLambdaArrow")
      val membersInjectorInstance = constructor
        .newInstance(
          Provider { "a" },
          Provider { "b" },
          Provider<CharSequence> { "c" },
          Provider { listOf("d") },
          Provider { Pair(Pair("e", 1), setOf("f")) },
          Provider { setOf { _: List<String> -> listOf("g") } },
          Provider { mapOf("Hello" to "World") },
          Provider { mapOf("Hello" to false) },
        )
        as MembersInjector<Any>

      val injectInstanceConstructor = injectClass.createInstance()
      membersInjectorInstance.injectMembers(injectInstanceConstructor)

      val injectInstanceStatic = injectClass.createInstance()

      membersInjector.staticInjectMethod("string")
        .invoke(null, injectInstanceStatic, "a")
      membersInjector.staticInjectMethod("qualifiedString")
        .invoke(null, injectInstanceStatic, "b")
      membersInjector.staticInjectMethod("charSequence")
        .invoke(null, injectInstanceStatic, "c" as CharSequence)
      membersInjector.staticInjectMethod("list")
        .invoke(null, injectInstanceStatic, listOf("d"))
      membersInjector.staticInjectMethod("pair")
        .invoke(null, injectInstanceStatic, Pair(Pair("e", 1), setOf("f")))
      membersInjector.staticInjectMethod("set")
        .invoke(null, injectInstanceStatic, setOf { _: List<String> -> listOf("g") })
      // From dagger, it will just see a plain old "set" method rather than a field
      val name = if (useDagger) "setSetterAnnotated" else "setterAnnotated"
      membersInjector.staticInjectMethod(name)
        .invoke(null, injectInstanceStatic, mapOf("Hello" to "World"))
      membersInjector.staticInjectMethod("${name}2")
        .invoke(null, injectInstanceStatic, mapOf("Hello" to false))

      assertThat(injectInstanceConstructor).isEqualTo(injectInstanceStatic)
      assertThat(injectInstanceConstructor).isNotSameInstanceAs(injectInstanceStatic)

      val namedAnnotation = membersInjector.staticInjectMethod("qualifiedString").annotations
        .single { it.annotationClass == Named::class }
      assertThat(namedAnnotation.getValue<String>()).isEqualTo("qualified")
    }
  }

  @Test fun `a factory class is generated for a field injection in a nested class`() {
    /*
package com.squareup.test;

import dagger.MembersInjector;
import dagger.internal.InjectedFieldSignature;
import java.util.List;
import java.util.Set;
import javax.annotation.Generated;
import javax.inject.Named;
import javax.inject.Provider;
import kotlin.Pair;
import kotlin.jvm.functions.Function1;

@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes"
})
public final class ParentClass_NestedInjectClass_MembersInjector implements MembersInjector<InjectClass> {
  private final Provider<String> stringProvider;

  public ParentClass_NestedInjectClass_MembersInjector(Provider<String> stringProvider) {
    this.stringProvider = stringProvider;
  }

  public static MembersInjector<InjectClass> create(Provider<String> stringProvider) {
    return new ParentClass_NestedInjectClass_MembersInjector(stringProvider);}

  @Override
  public void injectMembers(InjectClass instance) {
    injectString(instance, stringProvider.get());
  }

  @InjectedFieldSignature("com.squareup.test.ParentClass.NestedInjectClass.string")
  public static void injectString(InjectClass instance, String string) {
    instance.string = string;
  }
}
     */

    compile(
      """
      package com.squareup.test
      
      import javax.inject.Inject
      import javax.inject.Named
      
      typealias StringList = List<String>
      
      class ParentClass {
        class NestedInjectClass {
          @Inject lateinit var string: String
          
          override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
        
            other as NestedInjectClass
        
            if (string != other.string) return false
        
            return true
          }
        
          override fun hashCode(): Int {
            return string.hashCode()
          }
        }
      }
      """
    ) {
      val membersInjector = nestedInjectClass.membersInjector()

      val constructor = membersInjector.declaredConstructors.single()
      assertThat(constructor.parameterTypes.toList())
        .containsExactly(Provider::class.java)

      @Suppress("RedundantLambdaArrow")
      val membersInjectorInstance = constructor
        .newInstance(Provider { "a" }) as MembersInjector<Any>

      val injectInstanceConstructor = nestedInjectClass.createInstance()
      membersInjectorInstance.injectMembers(injectInstanceConstructor)

      val injectInstanceStatic = nestedInjectClass.createInstance()

      membersInjector.staticInjectMethod("string")
        .invoke(null, injectInstanceStatic, "a")

      assertThat(injectInstanceConstructor).isEqualTo(injectInstanceStatic)
      assertThat(injectInstanceConstructor).isNotSameInstanceAs(injectInstanceStatic)
    }
  }

  @Test fun `a factory class is generated for a field injection with Lazy and Provider`() {
    /*
package com.squareup.test;

import dagger.Lazy;
import dagger.MembersInjector;
import dagger.internal.DoubleCheck;
import dagger.internal.InjectedFieldSignature;
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
public final class InjectClass_MembersInjector implements MembersInjector<InjectClass> {
  private final Provider<String> stringProvider;

  private final Provider<String> stringProvider2;

  private final Provider<List<String>> stringListProvider;

  private final Provider<String> stringProvider3;

  public InjectClass_MembersInjector(Provider<String> stringProvider,
      Provider<String> stringProvider2, Provider<List<String>> stringListProvider,
      Provider<String> stringProvider3) {
    this.stringProvider = stringProvider;
    this.stringProvider2 = stringProvider2;
    this.stringListProvider = stringListProvider;
    this.stringProvider3 = stringProvider3;
  }

  public static MembersInjector<InjectClass> create(Provider<String> stringProvider,
      Provider<String> stringProvider2, Provider<List<String>> stringListProvider,
      Provider<String> stringProvider3) {
    return new InjectClass_MembersInjector(stringProvider, stringProvider2, stringListProvider, stringProvider3);}

  @Override
  public void injectMembers(InjectClass instance) {
    injectString(instance, stringProvider.get());
    injectStringProvider(instance, stringProvider2);
    injectStringListProvider(instance, stringListProvider);
    injectLazyString(instance, DoubleCheck.lazy(stringProvider3));
  }

  @InjectedFieldSignature("com.squareup.test.InjectClass.string")
  public static void injectString(InjectClass instance, String string) {
    instance.string = string;
  }

  @InjectedFieldSignature("com.squareup.test.InjectClass.stringProvider")
  public static void injectStringProvider(InjectClass instance, Provider<String> stringProvider) {
    instance.stringProvider = stringProvider;
  }

  @InjectedFieldSignature("com.squareup.test.InjectClass.stringListProvider")
  public static void injectStringListProvider(InjectClass instance,
      Provider<List<String>> stringListProvider) {
    instance.stringListProvider = stringListProvider;
  }

  @InjectedFieldSignature("com.squareup.test.InjectClass.lazyString")
  public static void injectLazyString(InjectClass instance, Lazy<String> lazyString) {
    instance.lazyString = lazyString;
  }
}
     */

    compile(
      """
      package com.squareup.test
      
      import dagger.Lazy
      import javax.inject.Inject
      import javax.inject.Provider
      
      class InjectClass {
        @Inject lateinit var string: String
        @Inject lateinit var stringProvider: Provider<String>
        @Inject lateinit var stringListProvider: Provider<List<String>>
        @Inject lateinit var lazyString: Lazy<String>
        
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
      val membersInjector = injectClass.membersInjector()

      val constructor = membersInjector.declaredConstructors.single()
      assertThat(constructor.parameterTypes.toList())
        .containsExactly(
          Provider::class.java,
          Provider::class.java,
          Provider::class.java,
          Provider::class.java
        )

      val membersInjectorInstance = constructor
        .newInstance(
          Provider { "a" },
          Provider { "b" },
          Provider { listOf("c") },
          Provider { "d" }
        )
        as MembersInjector<Any>

      val injectInstanceConstructor = injectClass.createInstance()
      membersInjectorInstance.injectMembers(injectInstanceConstructor)

      val injectInstanceStatic = injectClass.createInstance()

      membersInjector.staticInjectMethod("string")
        .invoke(null, injectInstanceStatic, "a")
      membersInjector.staticInjectMethod("stringProvider")
        .invoke(null, injectInstanceStatic, Provider { "b" })
      membersInjector.staticInjectMethod("stringListProvider")
        .invoke(null, injectInstanceStatic, Provider { listOf("c") })
      membersInjector.staticInjectMethod("lazyString")
        .invoke(null, injectInstanceStatic, Lazy { "d" })

      assertThat(injectInstanceConstructor).isEqualTo(injectInstanceStatic)
      assertThat(injectInstanceConstructor).isNotSameInstanceAs(injectInstanceStatic)
    }
  }

  @Test fun `a factory class is generated for a field injection with star imports`() {
    /*
package com.squareup.test;

import dagger.MembersInjector;
import dagger.internal.InjectedFieldSignature;
import error.NonExistentClass;
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
public final class InjectClass_MembersInjector implements MembersInjector<InjectClass> {
  private final Provider<NonExistentClass> fileProvider;

  private final Provider<NonExistentClass> pathProvider;

  public InjectClass_MembersInjector(Provider<NonExistentClass> fileProvider,
      Provider<NonExistentClass> pathProvider) {
    this.fileProvider = fileProvider;
    this.pathProvider = pathProvider;
  }

  public static MembersInjector<InjectClass> create(Provider<NonExistentClass> fileProvider,
      Provider<NonExistentClass> pathProvider) {
    return new InjectClass_MembersInjector(fileProvider, pathProvider);}

  @Override
  public void injectMembers(InjectClass instance) {
    injectFile(instance, fileProvider.get());
    injectPath(instance, pathProvider.get());
  }

  @InjectedFieldSignature("com.squareup.test.InjectClass.file")
  public static void injectFile(InjectClass instance, NonExistentClass file) {
    instance.file = file;
  }

  @InjectedFieldSignature("com.squareup.test.InjectClass.path")
  public static void injectPath(InjectClass instance, NonExistentClass path) {
    instance.path = path;
  }
}
     */

    compile(
      """
      package com.squareup.test
      
      import java.io.*
      import java.nio.file.*
      import javax.inject.*
      
      class InjectClass {
        @Inject lateinit var file: File
        @Inject lateinit var path: Path
        
        override fun equals(other: Any?): Boolean {
          if (this === other) return true
          if (javaClass != other?.javaClass) return false
      
          other as InjectClass
      
          if (file != other.file) return false
          if (path != other.path) return false
      
          return true
        }
      
        override fun hashCode(): Int {
          var result = file.hashCode()
          result = 31 * result + path.hashCode()
          return result
        }
      }
      """
    ) {
      val membersInjector = injectClass.membersInjector()

      val constructor = membersInjector.declaredConstructors.single()
      assertThat(constructor.parameterTypes.toList())
        .containsExactly(Provider::class.java, Provider::class.java)

      val membersInjectorInstance = constructor
        .newInstance(Provider { File("") }, Provider { File("").toPath() })
        as MembersInjector<Any>

      val injectInstanceConstructor = injectClass.createInstance()
      membersInjectorInstance.injectMembers(injectInstanceConstructor)

      val injectInstanceStatic = injectClass.createInstance()

      membersInjector.staticInjectMethod("file")
        .invoke(null, injectInstanceStatic, File(""))
      membersInjector.staticInjectMethod("path")
        .invoke(null, injectInstanceStatic, File("").toPath())

      assertThat(injectInstanceConstructor).isEqualTo(injectInstanceStatic)
      assertThat(injectInstanceConstructor).isNotSameInstanceAs(injectInstanceStatic)
    }
  }

  @Test fun `a factory class is generated for a field injection inner class`() {
    /*
package com.squareup.test;

import dagger.MembersInjector;
import dagger.internal.InjectedFieldSignature;
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
public final class OuterClass_InjectClass_MembersInjector implements MembersInjector<OuterClass.InjectClass> {
  private final Provider<String> stringProvider;

  private final Provider<CharSequence> charSequenceProvider;

  private final Provider<List<String>> listProvider;

  public OuterClass_InjectClass_MembersInjector(Provider<String> stringProvider,
      Provider<CharSequence> charSequenceProvider, Provider<List<String>> listProvider) {
    this.stringProvider = stringProvider;
    this.charSequenceProvider = charSequenceProvider;
    this.listProvider = listProvider;
  }

  public static MembersInjector<OuterClass.InjectClass> create(Provider<String> stringProvider,
      Provider<CharSequence> charSequenceProvider, Provider<List<String>> listProvider) {
    return new OuterClass_InjectClass_MembersInjector(stringProvider, charSequenceProvider, listProvider);}

  @Override
  public void injectMembers(OuterClass.InjectClass instance) {
    injectString(instance, stringProvider.get());
    injectCharSequence(instance, charSequenceProvider.get());
    injectList(instance, listProvider.get());
  }

  @InjectedFieldSignature("com.squareup.test.OuterClass.InjectClass.string")
  public static void injectString(OuterClass.InjectClass instance, String string) {
    instance.string = string;
  }

  @InjectedFieldSignature("com.squareup.test.OuterClass.InjectClass.charSequence")
  public static void injectCharSequence(OuterClass.InjectClass instance,
      CharSequence charSequence) {
    instance.charSequence = charSequence;
  }

  @InjectedFieldSignature("com.squareup.test.OuterClass.InjectClass.list")
  public static void injectList(OuterClass.InjectClass instance, List<String> list) {
    instance.list = list;
  }
}
     */

    compile(
      """
      package com.squareup.test
      
      import javax.inject.Inject
      
      class OuterClass {
        class InjectClass {
          @Inject lateinit var string: String
          @Inject lateinit var charSequence: CharSequence
          @Inject lateinit var list: List<String>
          
          override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
        
            other as InjectClass
        
            if (string != other.string) return false
            if (charSequence != other.charSequence) return false
            if (list != other.list) return false
        
            return true
          }
        
          override fun hashCode(): Int {
            var result = string.hashCode()
            result = 31 * result + charSequence.hashCode()
            result = 31 * result + list.hashCode()
            return result
          }
        }
      }
      """
    ) {
      val injectClass = classLoader.loadClass("com.squareup.test.OuterClass\$InjectClass")
      val membersInjector = injectClass.membersInjector()

      val constructor = membersInjector.declaredConstructors.single()
      assertThat(constructor.parameterTypes.toList())
        .containsExactly(Provider::class.java, Provider::class.java, Provider::class.java)

      val membersInjectorInstance = constructor
        .newInstance(Provider { "a" }, Provider<CharSequence> { "b" }, Provider { listOf("c") })
        as MembersInjector<Any>

      val injectInstanceConstructor = injectClass.createInstance()
      membersInjectorInstance.injectMembers(injectInstanceConstructor)

      val injectInstanceStatic = injectClass.createInstance()

      membersInjector.staticInjectMethod("string")
        .invoke(null, injectInstanceStatic, "a")
      membersInjector.staticInjectMethod("charSequence")
        .invoke(null, injectInstanceStatic, "b" as CharSequence)
      membersInjector.staticInjectMethod("list")
        .invoke(null, injectInstanceStatic, listOf("c"))

      assertThat(injectInstanceConstructor).isEqualTo(injectInstanceStatic)
      assertThat(injectInstanceConstructor).isNotSameInstanceAs(injectInstanceStatic)
    }
  }

  @Test fun `a factory class is generated for a field injection with a generic class`() {
    /*
package com.squareup.test;

import dagger.MembersInjector;
import dagger.internal.InjectedFieldSignature;
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
public final class InjectClass_MembersInjector<T> implements MembersInjector<InjectClass<T>> {
  private final Provider<String> stringProvider;

  public InjectClass_MembersInjector(Provider<String> stringProvider) {
    this.stringProvider = stringProvider;
  }

  public static <T> MembersInjector<InjectClass<T>> create(Provider<String> stringProvider) {
    return new InjectClass_MembersInjector<T>(stringProvider);}

  @Override
  public void injectMembers(InjectClass<T> instance) {
    injectString(instance, stringProvider.get());
  }

  @InjectedFieldSignature("com.squareup.test.InjectClass.string")
  public static <T> void injectString(InjectClass<T> instance, String string) {
    instance.string = string;
  }
}
     */

    compile(
      """
      package com.squareup.test
      
      import javax.inject.Inject
      
      abstract class InjectClass<T> {
        @Inject lateinit var string: String
      }
      """
    ) {
      val membersInjector = injectClass.membersInjector()

      val constructor = membersInjector.declaredConstructors.single()
      assertThat(constructor.parameterTypes.toList())
        .containsExactly(Provider::class.java)
    }
  }

  @Test fun `a factory class is generated for a field injection with three generic classes`() {
    /*
package com.squareup.test;

import dagger.MembersInjector;
import dagger.internal.InjectedFieldSignature;
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
public final class InjectClass_MembersInjector<T, U, V> implements MembersInjector<InjectClass<T, U, V>> {
  private final Provider<String> stringProvider;

  public InjectClass_MembersInjector(Provider<String> stringProvider) {
    this.stringProvider = stringProvider;
  }

  public static <T, U, V> MembersInjector<InjectClass<T>> create(Provider<String> stringProvider) {
    return new InjectClass_MembersInjector<T>(stringProvider);}

  @Override
  public void injectMembers(InjectClass<T, U, V> instance) {
    injectString(instance, stringProvider.get());
  }

  @InjectedFieldSignature("com.squareup.test.InjectClass.string")
  public static <T> void injectString(InjectClass<T, U, V> instance, String string) {
    instance.string = string;
  }
}
     */

    compile(
      """
      package com.squareup.test
      
      import javax.inject.Inject
      
      abstract class InjectClass<T, U, V> {
        @Inject lateinit var string: String
      }
      """
    ) {
      val membersInjector = injectClass.membersInjector()

      val constructor = membersInjector.declaredConstructors.single()
      assertThat(constructor.parameterTypes.toList())
        .containsExactly(Provider::class.java)
    }
  }

  @Test fun `a factory class is generated for a field injection without a package`() {
    compile(
      """
      import javax.inject.Inject
      
      class InjectClass {
        @Inject lateinit var string: String
        
        override fun equals(other: Any?): Boolean {
          if (this === other) return true
          if (javaClass != other?.javaClass) return false
      
          other as InjectClass
      
          if (string != other.string) return false
      
          return true
        }
      
        override fun hashCode(): Int {
          return string.hashCode()
        }
      }
      """
    ) {
      val injectClass = classLoader.loadClass("InjectClass")
      val membersInjector = injectClass.membersInjector()

      val constructor = membersInjector.declaredConstructors.single()
      assertThat(constructor.parameterTypes.toList())
        .containsExactly(Provider::class.java)

      @Suppress("RedundantLambdaArrow")
      val membersInjectorInstance = constructor
        .newInstance(Provider { "a" }) as MembersInjector<Any>

      val injectInstanceConstructor = injectClass.createInstance()
      membersInjectorInstance.injectMembers(injectInstanceConstructor)

      val injectInstanceStatic = injectClass.createInstance()

      membersInjector.staticInjectMethod("string")
        .invoke(null, injectInstanceStatic, "a")

      assertThat(injectInstanceConstructor).isEqualTo(injectInstanceStatic)
      assertThat(injectInstanceConstructor).isNotSameInstanceAs(injectInstanceStatic)
    }
  }

  private fun Class<*>.staticInjectMethod(memberName: String): Method {
    // We can't check the @InjectedFieldSignature annotation unfortunately, because it has class
    // retention.
    return declaredMethods
      .filter { it.isStatic }
      .single { it.name == "inject${memberName.capitalize()}" }
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
