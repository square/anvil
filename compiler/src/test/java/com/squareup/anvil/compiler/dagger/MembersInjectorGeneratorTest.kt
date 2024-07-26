package com.squareup.anvil.compiler.dagger

import com.google.common.truth.Truth.assertThat
import com.squareup.anvil.compiler.injectClass
import com.squareup.anvil.compiler.internal.capitalize
import com.squareup.anvil.compiler.internal.testing.AnvilCompilationMode
import com.squareup.anvil.compiler.internal.testing.compileAnvil
import com.squareup.anvil.compiler.internal.testing.createInstance
import com.squareup.anvil.compiler.internal.testing.getPropertyValue
import com.squareup.anvil.compiler.internal.testing.getValue
import com.squareup.anvil.compiler.internal.testing.isStatic
import com.squareup.anvil.compiler.internal.testing.membersInjector
import com.squareup.anvil.compiler.nestedInjectClass
import com.squareup.anvil.compiler.useDaggerAndKspParams
import com.tschuchort.compiletesting.JvmCompilationResult
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.KotlinCompilation.ExitCode.OK
import dagger.Lazy
import dagger.MembersInjector
import org.intellij.lang.annotations.Language
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters
import java.io.File
import java.lang.reflect.Method
import javax.inject.Named
import javax.inject.Provider
import kotlin.reflect.KClass
import kotlin.test.assertFailsWith

@Suppress("UNCHECKED_CAST")
@RunWith(Parameterized::class)
class MembersInjectorGeneratorTest(
  private val useDagger: Boolean,
  private val mode: AnvilCompilationMode,
) {

  companion object {
    @Parameters(name = "Use Dagger: {0}, mode: {1}")
    @JvmStatic
    fun params() = useDaggerAndKspParams()
  }

  @Test fun `a factory class is generated for a field injection`() {
    /*
package com.squareup.test;

import dagger.MembersInjector;
import dagger.internal.DaggerGenerated;
import dagger.internal.InjectedFieldSignature;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.Generated;
import javax.inject.Named;
import javax.inject.Provider;
import kotlin.Pair;
import kotlin.jvm.functions.Function1;

@DaggerGenerated
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

  private final Provider<Map<String, String>> p0Provider;

  private final Provider<Map<String, Boolean>> p0Provider2;

  public InjectClass_MembersInjector(Provider<String> stringProvider,
      Provider<String> qualifiedStringProvider, Provider<CharSequence> charSequenceProvider,
      Provider<List<String>> listProvider,
      Provider<Pair<Pair<String, Integer>, ? extends Set<String>>> pairProvider,
      Provider<Set<Function1<List<String>, List<String>>>> setProvider,
      Provider<Map<String, String>> p0Provider, Provider<Map<String, Boolean>> p0Provider2) {
    this.stringProvider = stringProvider;
    this.qualifiedStringProvider = qualifiedStringProvider;
    this.charSequenceProvider = charSequenceProvider;
    this.listProvider = listProvider;
    this.pairProvider = pairProvider;
    this.setProvider = setProvider;
    this.p0Provider = p0Provider;
    this.p0Provider2 = p0Provider2;
  }

  public static MembersInjector<InjectClass> create(Provider<String> stringProvider,
      Provider<String> qualifiedStringProvider, Provider<CharSequence> charSequenceProvider,
      Provider<List<String>> listProvider,
      Provider<Pair<Pair<String, Integer>, ? extends Set<String>>> pairProvider,
      Provider<Set<Function1<List<String>, List<String>>>> setProvider,
      Provider<Map<String, String>> p0Provider, Provider<Map<String, Boolean>> p0Provider2) {
    return new InjectClass_MembersInjector(stringProvider, qualifiedStringProvider, charSequenceProvider, listProvider, pairProvider, setProvider, p0Provider, p0Provider2);
  }

  @Override
  public void injectMembers(InjectClass instance) {
    injectString(instance, stringProvider.get());
    injectQualifiedString(instance, qualifiedStringProvider.get());
    injectCharSequence(instance, charSequenceProvider.get());
    injectList(instance, listProvider.get());
    injectPair(instance, pairProvider.get());
    injectSet(instance, setProvider.get());
    injectSetSetterAnnotated(instance, p0Provider.get());
    injectSetSetterAnnotated2(instance, p0Provider2.get());
  }

  @InjectedFieldSignature("com.squareup.test.InjectClass.string")
  public static void injectString(InjectClass instance, String string) {
    instance.string = string;
  }

  @InjectedFieldSignature("com.squareup.test.InjectClass.qualifiedString")
  @Named("qualified")
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

  public static void injectSetSetterAnnotated(InjectClass instance, Map<String, String> p0) {
    instance.setSetterAnnotated(p0);
  }

  public static void injectSetSetterAnnotated2(InjectClass instance, Map<String, Boolean> p0) {
    instance.setSetterAnnotated2(p0);
  }
}
*/

    compile(
      """
      package com.squareup.test
      
      import javax.inject.Inject
      import javax.inject.Named
      
      typealias StringList = List<String>
      
      // Generate a factory too to cover for https://github.com/square/anvil/issues/362
      class InjectClass @Inject constructor() {
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
      """,
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
          Provider::class.java,
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
      membersInjector.staticInjectMethod("setSetterAnnotated")
        .invoke(null, injectInstanceStatic, mapOf("Hello" to "World"))
      membersInjector.staticInjectMethod("setSetterAnnotated2")
        .invoke(null, injectInstanceStatic, mapOf("Hello" to false))

      assertThat(injectInstanceConstructor).isEqualTo(injectInstanceStatic)
      assertThat(injectInstanceConstructor).isNotSameInstanceAs(injectInstanceStatic)

      val namedAnnotation = membersInjector.staticInjectMethod("qualifiedString").annotations
        .single { it.annotationClass == Named::class }
      assertThat(namedAnnotation.getValue<String>()).isEqualTo("qualified")
    }
  }

  @Test fun `a factory class is generated for a qualifier with an enum`() {
    /*
package com.squareup.test;

import dagger.MembersInjector;
import dagger.internal.DaggerGenerated;
import dagger.internal.InjectedFieldSignature;
import javax.annotation.processing.Generated;
import javax.inject.Named;
import javax.inject.Provider;
import kotlin.LazyThreadSafetyMode;

@DaggerGenerated
@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes"
})
public final class InjectClass_MembersInjector implements MembersInjector<InjectClass> {
  private final Provider<String> classArrayStringProvider;

  private final Provider<String> classStringProvider;

  private final Provider<String> enumArrayStringProvider;

  private final Provider<String> enumStringProvider;

  private final Provider<String> intStringProvider;

  private final Provider<String> string1Provider;

  private final Provider<String> string2Provider;

  private final Provider<String> string3Provider;

  public InjectClass_MembersInjector(Provider<String> classArrayStringProvider,
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

  public static MembersInjector<InjectClass> create(Provider<String> classArrayStringProvider,
      Provider<String> classStringProvider, Provider<String> enumArrayStringProvider,
      Provider<String> enumStringProvider, Provider<String> intStringProvider,
      Provider<String> string1Provider, Provider<String> string2Provider,
      Provider<String> string3Provider) {
    return new InjectClass_MembersInjector(classArrayStringProvider, classStringProvider, enumArrayStringProvider, enumStringProvider, intStringProvider, string1Provider, string2Provider, string3Provider);
  }

  @Override
  public void injectMembers(InjectClass instance) {
    injectClassArrayString(instance, classArrayStringProvider.get());
    injectClassString(instance, classStringProvider.get());
    injectEnumArrayString(instance, enumArrayStringProvider.get());
    injectEnumString(instance, enumStringProvider.get());
    injectIntString(instance, intStringProvider.get());
    injectString1(instance, string1Provider.get());
    injectString2(instance, string2Provider.get());
    injectString3(instance, string3Provider.get());
  }

  @InjectedFieldSignature("com.squareup.test.InjectClass.classArrayString")
  @ClassArrayQualifier({
      String.class,
      int.class
  })
  public static void injectClassArrayString(InjectClass instance, String classArrayString) {
    instance.classArrayString = classArrayString;
  }

  @InjectedFieldSignature("com.squareup.test.InjectClass.classString")
  @ClassQualifier(String.class)
  public static void injectClassString(InjectClass instance, String classString) {
    instance.classString = classString;
  }

  @InjectedFieldSignature("com.squareup.test.InjectClass.enumArrayString")
  @EnumArrayQualifier({
      LazyThreadSafetyMode.NONE,
      LazyThreadSafetyMode.SYNCHRONIZED
  })
  public static void injectEnumArrayString(InjectClass instance, String enumArrayString) {
    instance.enumArrayString = enumArrayString;
  }

  @InjectedFieldSignature("com.squareup.test.InjectClass.enumString")
  @EnumQualifier(LazyThreadSafetyMode.SYNCHRONIZED)
  public static void injectEnumString(InjectClass instance, String enumString) {
    instance.enumString = enumString;
  }

  @InjectedFieldSignature("com.squareup.test.InjectClass.intString")
  @IntQualifier(3)
  public static void injectIntString(InjectClass instance, String intString) {
    instance.intString = intString;
  }

  @InjectedFieldSignature("com.squareup.test.InjectClass.string1")
  @StringQualifier("abc")
  public static void injectString1(InjectClass instance, String string1) {
    instance.string1 = string1;
  }

  @InjectedFieldSignature("com.squareup.test.InjectClass.string2")
  @StringQualifier("def")
  public static void injectString2(InjectClass instance, String string2) {
    instance.string2 = string2;
  }

  @InjectedFieldSignature("com.squareup.test.InjectClass.string3")
  @Named("def")
  public static void injectString3(InjectClass instance, String string3) {
    instance.string3 = string3;
  }
}
     */
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
    InjectClass instance = newInstance();
    InjectClass_MembersInjector.injectClassArrayString(instance, classArrayStringProvider.get());
    InjectClass_MembersInjector.injectClassString(instance, classStringProvider.get());
    InjectClass_MembersInjector.injectEnumArrayString(instance, enumArrayStringProvider.get());
    InjectClass_MembersInjector.injectEnumString(instance, enumStringProvider.get());
    InjectClass_MembersInjector.injectIntString(instance, intStringProvider.get());
    InjectClass_MembersInjector.injectString1(instance, string1Provider.get());
    InjectClass_MembersInjector.injectString2(instance, string2Provider.get());
    InjectClass_MembersInjector.injectString3(instance, string3Provider.get());
    return instance;
  }

  public static InjectClass_Factory create(Provider<String> classArrayStringProvider,
      Provider<String> classStringProvider, Provider<String> enumArrayStringProvider,
      Provider<String> enumStringProvider, Provider<String> intStringProvider,
      Provider<String> string1Provider, Provider<String> string2Provider,
      Provider<String> string3Provider) {
    return new InjectClass_Factory(classArrayStringProvider, classStringProvider, enumArrayStringProvider, enumStringProvider, intStringProvider, string1Provider, string2Provider, string3Provider);
  }

  public static InjectClass newInstance() {
    return new InjectClass();
  }
}
     */
    compile(
      """
      package com.squareup.test2
      
      const val CONST_IMPORTED = "yay"
      
      object Constants {
        const val CONST_NESTED = "yay2"
        const val CONST_NESTED_BUT_IMPORTED_DIRECTLY = "yay3"
      }
      
      class ClassWithCompanion {
        class NestedClassWithCompanion {
          companion object CustomCompanionName {
            const val CONST_NESTED_IN_COMPANION = "yay4"
          }
        }
      }
      """,
      """
      package com.squareup.test
      
      const val CONST_SAME_PACKAGE = "samePackageConst"
      """,
      """
      package com.squareup.test
      
      import com.squareup.test2.CONST_IMPORTED
      import com.squareup.test2.Constants
      import com.squareup.test2.Constants.CONST_NESTED_BUT_IMPORTED_DIRECTLY
      import com.squareup.test2.ClassWithCompanion.NestedClassWithCompanion
      import kotlin.LazyThreadSafetyMode.NONE
      import kotlin.LazyThreadSafetyMode.SYNCHRONIZED 
      import kotlin.reflect.KClass      
      import javax.inject.Inject
      import javax.inject.Named
      import javax.inject.Qualifier
      
      const val CONSTANT = "def"

      class InjectClass @Inject constructor() {
        @ClassArrayQualifier([String::class, Int::class]) @Inject lateinit var classArrayString: String
        @ClassQualifier(String::class) @Inject lateinit var classString: String
        @EnumArrayQualifier([NONE, SYNCHRONIZED]) @Inject lateinit var enumArrayString: String
        @EnumQualifier(SYNCHRONIZED) @Inject lateinit var enumString: String
        @IntQualifier(3) @Inject lateinit var intString: String
        @StringQualifier("abc") @Inject lateinit var string1: String
        @StringQualifier(CONSTANT) @Inject lateinit var string2: String
        @Named(CONSTANT) @Inject lateinit var string3: String
        @Named(NESTED_CONSTANT) @Inject lateinit var string4: String
        @Named(CONST_IMPORTED) @Inject lateinit var string5: String
        @Named(Constants.CONST_NESTED) @Inject lateinit var string6: String
        @Named(CONST_NESTED_BUT_IMPORTED_DIRECTLY) @Inject lateinit var string7: String
        @Named(NestedClassWithCompanion.CONST_NESTED_IN_COMPANION) @Inject lateinit var string8: String
        @Named(CONST_SAME_PACKAGE) @Inject lateinit var string9: String
        
        companion object {
          const val NESTED_CONSTANT = "def2"
        }
      }
      
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
      val membersInjector = injectClass.membersInjector()

      val classArrayAnnotation = membersInjector.staticInjectMethod("classArrayString")
        .annotations
        .single { it.annotationClass.simpleName == "ClassArrayQualifier" }

      assertThat(classArrayAnnotation.getValue<Array<KClass<*>>>().toList())
        .isEqualTo(arrayOf(String::class.java, Int::class.javaPrimitiveType).toList())

      val classAnnotation = membersInjector.staticInjectMethod("classString")
        .annotations
        .single { it.annotationClass.simpleName == "ClassQualifier" }

      assertThat(classAnnotation.getValue<KClass<*>>()).isEqualTo(String::class.java)

      val enumArrayAnnotation = membersInjector.staticInjectMethod("enumArrayString")
        .annotations
        .single { it.annotationClass.simpleName == "EnumArrayQualifier" }

      assertThat(enumArrayAnnotation.getValue<Array<LazyThreadSafetyMode>>())
        .isEqualTo(arrayOf(LazyThreadSafetyMode.NONE, LazyThreadSafetyMode.SYNCHRONIZED))

      val enumAnnotation = membersInjector.staticInjectMethod("enumString")
        .annotations
        .single { it.annotationClass.simpleName == "EnumQualifier" }

      assertThat(enumAnnotation.getValue<LazyThreadSafetyMode>())
        .isEqualTo(LazyThreadSafetyMode.SYNCHRONIZED)

      val intAnnotation = membersInjector.staticInjectMethod("intString")
        .annotations
        .single { it.annotationClass.simpleName == "IntQualifier" }

      assertThat(intAnnotation.getValue<Int>()).isEqualTo(3)

      val string9 = membersInjector.staticInjectMethod("string9")
        .annotations
        .single { it.annotationClass.simpleName == "Named" }

      assertThat(string9.getValue<String>()).isEqualTo("samePackageConst")
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
      """,
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

  @Test
  fun `a member injector is generated for a class which is nested inside a custom Error type`() {

    // https://github.com/square/anvil/issues/445#issuecomment-992952286
    // InjectedClass is nested so that there are no imports

    compile(
      """
      package com.squareup.test
 
      import javax.inject.Inject

      sealed class Base {
        sealed class Error : Base() {
          @Inject lateinit var string: String
          
          class InjectClass : Error() {
            @Inject lateinit var numbers: List<Int>

            
            override fun equals(other: Any?): Boolean {
              if (this === other) return true
              if (javaClass != other?.javaClass) return false
        
              other as InjectClass
        
              if (string != other.string) return false
              if (numbers != other.numbers) return false
        
              return true
            }

            override fun hashCode(): Int {
              var result = numbers.hashCode()
              result = 31 * result + string.hashCode()
              return result
            }
          }
        }
      }
      """,
    ) {

      val errorMembersInjector = classLoader.loadClass("com.squareup.test.Base\$Error")
        .membersInjector()

      val injectClass = classLoader
        .loadClass("com.squareup.test.Base\$Error\$InjectClass")

      val injectClassMembersInjector = injectClass.membersInjector()

      val constructor = injectClassMembersInjector.declaredConstructors.single()
      assertThat(constructor.parameterTypes.toList())
        .containsExactly(
          Provider::class.java,
          Provider::class.java,
        )

      val membersInjectorInstance = constructor
        .newInstance(
          Provider { "a" },
          Provider { listOf(1, 2) },
        ) as MembersInjector<Any>

      val injectInstanceConstructor = injectClass.createInstance()
      membersInjectorInstance.injectMembers(injectInstanceConstructor)

      val injectInstanceStatic = injectClass.createInstance()

      injectClassMembersInjector.staticInjectMethod("numbers")
        .invoke(null, injectInstanceStatic, listOf(1, 2))
      errorMembersInjector.staticInjectMethod("string")
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
      """,
    ) {
      val membersInjector = injectClass.membersInjector()

      val constructor = membersInjector.declaredConstructors.single()
      assertThat(constructor.parameterTypes.toList())
        .containsExactly(
          Provider::class.java,
          Provider::class.java,
          Provider::class.java,
          Provider::class.java,
        )

      val membersInjectorInstance = constructor
        .newInstance(
          Provider { "a" },
          Provider { "b" },
          Provider { listOf("c") },
          Provider { "d" },
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

  @Test fun `a factory class is generated for a field injection with Lazy wrapped in a Provider`() {
    /*
package com.squareup.test;

import dagger.Lazy;
import dagger.MembersInjector;
import dagger.internal.DaggerGenerated;
import dagger.internal.InjectedFieldSignature;
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
public final class InjectClass_MembersInjector implements MembersInjector<InjectClass> {
  private final Provider<String> stringProvider;

  public InjectClass_MembersInjector(Provider<String> stringProvider) {
    this.stringProvider = stringProvider;
  }

  public static MembersInjector<InjectClass> create(Provider<String> stringProvider) {
    return new InjectClass_MembersInjector(stringProvider);
  }

  @Override
  public void injectMembers(InjectClass instance) {
    injectLazyStringProvider(instance, ProviderOfLazy.create(stringProvider));
  }

  @InjectedFieldSignature("com.squareup.test.InjectClass.lazyStringProvider")
  public static void injectLazyStringProvider(InjectClass instance,
      Provider<Lazy<String>> lazyStringProvider) {
    instance.lazyStringProvider = lazyStringProvider;
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
        @Inject lateinit var lazyStringProvider: Provider<Lazy<String>>
        
        override fun equals(other: Any?): Boolean {
          return toString() == other.toString()
        }
        override fun toString(): String {
         return lazyStringProvider.get().get()
        }
      }
      """,
    ) {
      val membersInjector = injectClass.membersInjector()

      val constructor = membersInjector.declaredConstructors.single()
      assertThat(constructor.parameterTypes.toList())
        .containsExactly(Provider::class.java)

      val membersInjectorInstance = constructor
        .newInstance(Provider { "a" })
        as MembersInjector<Any>

      val injectInstanceConstructor = injectClass.createInstance()
      membersInjectorInstance.injectMembers(injectInstanceConstructor)

      val injectInstanceStatic = injectClass.createInstance()

      membersInjector.staticInjectMethod("lazyStringProvider")
        .invoke(null, injectInstanceStatic, Provider { dagger.Lazy { "a" } })

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
      """,
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
      """,
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

  @Test
  fun `a factory class is generated for a field injection on a super class`() {
    /*
    package com.squareup.test;

    import dagger.MembersInjector;
    import dagger.internal.DaggerGenerated;
    import dagger.internal.InjectedFieldSignature;
    import java.util.List;
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
    public final class InjectClass_MembersInjector implements MembersInjector<InjectClass> {
      private final Provider<List<Integer>> base1Provider;

      private final Provider<List<String>> base2Provider;

      private final Provider<Set<Integer>> middle1Provider;

      private final Provider<Set<String>> middle2Provider;

      private final Provider<String> nameProvider;

      public InjectClass_MembersInjector(Provider<List<Integer>> base1Provider,
          Provider<List<String>> base2Provider, Provider<Set<Integer>> middle1Provider,
          Provider<Set<String>> middle2Provider, Provider<String> nameProvider) {
        this.base1Provider = base1Provider;
        this.base2Provider = base2Provider;
        this.middle1Provider = middle1Provider;
        this.middle2Provider = middle2Provider;
        this.nameProvider = nameProvider;
      }

      public static MembersInjector<InjectClass> create(Provider<List<Integer>> base1Provider,
          Provider<List<String>> base2Provider, Provider<Set<Integer>> middle1Provider,
          Provider<Set<String>> middle2Provider, Provider<String> nameProvider) {
        return new InjectClass_MembersInjector(base1Provider, base2Provider, middle1Provider, middle2Provider, nameProvider);
      }

      @Override
      public void injectMembers(InjectClass instance) {
        Base_MembersInjector.injectBase1(instance, base1Provider.get());
        Base_MembersInjector.injectBase2(instance, base2Provider.get());
        Middle_MembersInjector.injectMiddle1(instance, middle1Provider.get());
        Middle_MembersInjector.injectMiddle2(instance, middle2Provider.get());
        injectName(instance, nameProvider.get());
      }

      @InjectedFieldSignature("com.squareup.test.InjectClass.name")
      public static void injectName(InjectClass instance, String name) {
        instance.name = name;
      }
    }

     */
    compile(
      """
      package com.squareup.test

      import javax.inject.Inject
      import javax.inject.Provider

      class InjectClass : Middle() {

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
      val membersInjector = injectClass.membersInjector()

      val injectorConstructor = membersInjector.declaredConstructors.single()
      assertThat(injectorConstructor.parameterTypes.toList())
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

      val injectorInstance = membersInjector.createInstance(
        Provider { base1 },
        Provider { base2 },
        Provider { middle1 },
        Provider { middle2 },
        Provider { name },
      )
        as MembersInjector<Any>

      val classInstanceConstructor = injectClass.createInstance()
      injectorInstance.injectMembers(classInstanceConstructor)

      assertThat(classInstanceConstructor.getPropertyValue("name")).isEqualTo(name)
      assertThat(classInstanceConstructor.getPropertyValue("middle1")).isEqualTo(middle1)
      assertThat(classInstanceConstructor.getPropertyValue("middle2")).isEqualTo(middle2)
      assertThat(classInstanceConstructor.getPropertyValue("base1")).isEqualTo(base1)
      assertThat(classInstanceConstructor.getPropertyValue("base2")).isEqualTo(base2)

      val classInstanceStatic = injectClass.createInstance()
      injectorInstance.injectMembers(classInstanceStatic)

      assertThat(classInstanceStatic.getPropertyValue("name")).isEqualTo(name)
      assertThat(classInstanceStatic.getPropertyValue("middle1")).isEqualTo(middle1)
      assertThat(classInstanceStatic.getPropertyValue("middle2")).isEqualTo(middle2)
      assertThat(classInstanceStatic.getPropertyValue("base1")).isEqualTo(base1)
      assertThat(classInstanceStatic.getPropertyValue("base2")).isEqualTo(base2)
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
      """,
    ) {
      val membersInjector = injectClass.membersInjector()

      val constructor = membersInjector.declaredConstructors.single()
      assertThat(constructor.parameterTypes.toList())
        .containsExactly(Provider::class.java)
    }
  }

  @Test fun `a factory class is generated for an injected generic field in an inherited generic class`() {
    compile(
      """
      package com.squareup.test
      
      import javax.inject.Inject

      open class Base<T : Any> {
        @Inject lateinit var value: T
      }
      
      class InjectClass : Base<Int>() {
        @Inject lateinit var string: String
        
        override fun equals(other: Any?): Boolean {
          return other is InjectClass && value == other.value && string == other.string 
        }
      }
      """,
    ) {
      val baseMembersInjector = classLoader.loadClass("com.squareup.test.Base")
        .membersInjector()
      val injectClassMembersInjector = injectClass.membersInjector()

      val constructor = injectClassMembersInjector.declaredConstructors.single()
      assertThat(constructor.parameterTypes.toList())
        .containsExactly(
          Provider::class.java,
          Provider::class.java,
        )

      val membersInjectorInstance = constructor.newInstance(
        Provider { 123 },
        Provider { "foo" },
      ) as MembersInjector<Any>

      val injectInstanceConstructor = injectClass.createInstance()
      membersInjectorInstance.injectMembers(injectInstanceConstructor)

      val injectInstanceStatic = injectClass.createInstance()

      baseMembersInjector.staticInjectMethod("value")
        .invoke(null, injectInstanceStatic, 123)
      injectClassMembersInjector.staticInjectMethod("string")
        .invoke(null, injectInstanceStatic, "foo")

      assertThat(injectInstanceConstructor).isEqualTo(injectInstanceStatic)
      assertThat(injectInstanceConstructor).isNotSameInstanceAs(injectInstanceStatic)
    }
  }

  @Test fun `a factory class is generated for a generic field injection with a generic class`() {
    compile(
      """
      package com.squareup.test
      
      import javax.inject.Inject

      class InjectClass<T, R> {
        @Inject lateinit var unknownItems: List<T>
      }
      """,
    ) {
      val membersInjector = injectClass.membersInjector()

      val constructor = membersInjector.declaredConstructors.single()
      assertThat(constructor.parameterTypes.toList())
        .containsExactly(Provider::class.java)
    }
  }

  @Test fun `a factory class is generated for a field injection in a class with a parent class with a generic field injection`() {
    compile(
      """
      package com.squareup.test
      
      import javax.inject.Inject

      abstract class Base<T> {
        @Inject lateinit var unknownItems: List<T>
      }

      class InjectClass : Base<String>() {
        @Inject lateinit var numbers: List<Int>
        
        override fun equals(other: Any?): Boolean {
          if (this === other) return true
          if (javaClass != other?.javaClass) return false
     
          other as InjectClass

          if (unknownItems != other.unknownItems) return false
          if (numbers != other.numbers) return false
     
          return true
        }
      }
      """,
    ) {
      val baseMembersInjector = classLoader.loadClass("com.squareup.test.Base")
        .membersInjector()
      val injectClassMembersInjector = injectClass.membersInjector()

      val constructor = injectClassMembersInjector.declaredConstructors.single()
      assertThat(constructor.parameterTypes.toList())
        .containsExactly(
          Provider::class.java,
          Provider::class.java,
        )

      val membersInjectorInstance = constructor
        .newInstance(
          Provider { listOf("a", "b") },
          Provider { listOf(1, 2) },
        ) as MembersInjector<Any>

      val injectInstanceConstructor = injectClass.createInstance()
      membersInjectorInstance.injectMembers(injectInstanceConstructor)

      val injectInstanceStatic = injectClass.createInstance()

      injectClassMembersInjector.staticInjectMethod("numbers")
        .invoke(null, injectInstanceStatic, listOf(1, 2))
      baseMembersInjector.staticInjectMethod("unknownItems")
        .invoke(null, injectInstanceStatic, listOf("a", "b"))

      assertThat(injectInstanceConstructor).isEqualTo(injectInstanceStatic)
      assertThat(injectInstanceConstructor).isNotSameInstanceAs(injectInstanceStatic)
    }
  }

  @Test fun `a factory class is generated for a field injection in a class with an ancestor class with a generic field injection`() {
    compile(
      """
      package com.squareup.test
      
      import javax.inject.Inject

      abstract class Base<T> {
        @Inject lateinit var unknownItems: List<T>
      }

      abstract class Middle<R> : Base<R>() {
        @Inject lateinit var numbers: List<Int>
        
        override fun equals(other: Any?): Boolean {
          if (this === other) return true
          if (javaClass != other?.javaClass) return false
     
          other as InjectClass

          if (unknownItems != other.unknownItems) return false
          if (numbers != other.numbers) return false
     
          return true
        }
      }

      class InjectClass : Middle<String>() {
        @Inject lateinit var bools: List<Boolean>
      }
      """,
    ) {
      val baseMembersInjector = classLoader.loadClass("com.squareup.test.Base")
        .membersInjector()
      val middleMembersInjector = classLoader.loadClass("com.squareup.test.Middle")
        .membersInjector()
      val injectClassMembersInjector = injectClass.membersInjector()

      val constructor = injectClassMembersInjector.declaredConstructors.single()
      assertThat(constructor.parameterTypes.toList())
        .containsExactly(
          Provider::class.java,
          Provider::class.java,
          Provider::class.java,
        )

      val membersInjectorInstance = constructor
        .newInstance(
          Provider { listOf("a", "b") },
          Provider { listOf(1, 2) },
          Provider { listOf(true) },
        ) as MembersInjector<Any>

      val injectInstanceConstructor = injectClass.createInstance()
      membersInjectorInstance.injectMembers(injectInstanceConstructor)

      val injectInstanceStatic = injectClass.createInstance()

      injectClassMembersInjector.staticInjectMethod("bools")
        .invoke(null, injectInstanceStatic, listOf(true))
      middleMembersInjector.staticInjectMethod("numbers")
        .invoke(null, injectInstanceStatic, listOf(1, 2))
      baseMembersInjector.staticInjectMethod("unknownItems")
        .invoke(null, injectInstanceStatic, listOf("a", "b"))

      assertThat(injectInstanceConstructor).isEqualTo(injectInstanceStatic)
      assertThat(injectInstanceConstructor).isNotSameInstanceAs(injectInstanceStatic)
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
      """,
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
      """,
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

  @Test fun `a member injector is generated for a class with a super class in another module`() {

    val otherModuleResult = compile(
      """
      package com.squareup.test

      import javax.inject.Inject

      abstract class Base {
        @Inject lateinit var string: String
      }
      """,
    ) {
      assertThat(exitCode).isEqualTo(OK)
    }

    compile(
      """
      package com.squareup.test

      import javax.inject.Inject

      class InjectClass : Base() {
        @Inject lateinit var numbers: List<Int>
     
        override fun equals(other: Any?): Boolean {
          if (this === other) return true
          if (javaClass != other?.javaClass) return false
     
          other as InjectClass
     
          if (numbers != other.numbers) return false
          if (string != other.string) return false
     
          return true
        }
      }
      """,
      previousCompilationResult = otherModuleResult,
    ) {
      val baseMembersInjector = classLoader.loadClass("com.squareup.test.Base")
        .membersInjector()

      val injectClassMembersInjector = injectClass.membersInjector()

      val constructor = injectClassMembersInjector.declaredConstructors.single()
      assertThat(constructor.parameterTypes.toList())
        .containsExactly(
          Provider::class.java,
          Provider::class.java,
        )

      val membersInjectorInstance = constructor
        .newInstance(
          Provider { "a" },
          Provider { listOf(1, 2) },
        ) as MembersInjector<Any>

      val injectInstanceConstructor = injectClass.createInstance()
      membersInjectorInstance.injectMembers(injectInstanceConstructor)

      val injectInstanceStatic = injectClass.createInstance()

      injectClassMembersInjector.staticInjectMethod("numbers")
        .invoke(null, injectInstanceStatic, listOf(1, 2))
      baseMembersInjector.staticInjectMethod("string")
        .invoke(null, injectInstanceStatic, "a")

      assertThat(injectInstanceConstructor).isEqualTo(injectInstanceStatic)
      assertThat(injectInstanceConstructor).isNotSameInstanceAs(injectInstanceStatic)
    }
  }

  @Test
  fun `a member injector is generated for a class with a grand-super class in another module`() {

    val otherModuleResult = compile(
      """
      package com.squareup.test

      import javax.inject.Inject

      abstract class Base {
        @Inject lateinit var string: String
      }
      """,
    ) {
      assertThat(exitCode).isEqualTo(OK)
    }

    compile(
      """
      package com.squareup.test

      import javax.inject.Inject

      abstract class Mid : Base() {
        @Inject lateinit var strings: List<String>
      }

      class InjectClass : Mid() {
        @Inject lateinit var numbers: List<Int>
     
        override fun equals(other: Any?): Boolean {
          if (this === other) return true
          if (javaClass != other?.javaClass) return false
     
          other as InjectClass
     
          if (numbers != other.numbers) return false
          if (strings != other.strings) return false
          if (string != other.string) return false
     
          return true
        }
      }
      """,
      previousCompilationResult = otherModuleResult,
    ) {
      val baseMembersInjector = classLoader.loadClass("com.squareup.test.Base")
        .membersInjector()
      val midMembersInjector = classLoader.loadClass("com.squareup.test.Mid")
        .membersInjector()

      val injectClassMembersInjector = injectClass.membersInjector()

      val constructor = injectClassMembersInjector.declaredConstructors.single()
      assertThat(constructor.parameterTypes.toList())
        .containsExactly(
          Provider::class.java,
          Provider::class.java,
          Provider::class.java,
        )

      val membersInjectorInstance = constructor
        .newInstance(
          Provider { "a" },
          Provider { listOf("b") },
          Provider { listOf(1, 2) },
        ) as MembersInjector<Any>

      val injectInstanceConstructor = injectClass.createInstance()
      membersInjectorInstance.injectMembers(injectInstanceConstructor)

      val injectInstanceStatic = injectClass.createInstance()

      injectClassMembersInjector.staticInjectMethod("numbers")
        .invoke(null, injectInstanceStatic, listOf(1, 2))
      midMembersInjector.staticInjectMethod("strings")
        .invoke(null, injectInstanceStatic, listOf("b"))
      baseMembersInjector.staticInjectMethod("string")
        .invoke(null, injectInstanceStatic, "a")

      assertThat(injectInstanceConstructor).isEqualTo(injectInstanceStatic)
      assertThat(injectInstanceConstructor).isNotSameInstanceAs(injectInstanceStatic)
    }
  }

  @Test
  fun `a member injector is generated for a class with two super classes in another module`() {

    val otherModuleResult = compile(
      """
      package com.squareup.test

      import javax.inject.Inject

      abstract class Base1 {
        @Inject lateinit var string: String
      }

      abstract class Base2 : Base1() {
        @Inject lateinit var chars: List<Char>
      }
      """,
    ) {
      assertThat(exitCode).isEqualTo(OK)
    }

    compile(
      """
      package com.squareup.test

      import javax.inject.Inject

      abstract class Mid : Base2() {
        @Inject lateinit var strings: List<String>
      }

      class InjectClass : Mid() {
        @Inject lateinit var numbers: List<Int>
     
        override fun equals(other: Any?): Boolean {
          if (this === other) return true
          if (javaClass != other?.javaClass) return false
     
          other as InjectClass
     
          if (numbers != other.numbers) return false
          if (strings != other.strings) return false
          if (string != other.string) return false
     
          return true
        }
      }
      """,
      previousCompilationResult = otherModuleResult,
    ) {

      val base1MembersInjector = classLoader.loadClass("com.squareup.test.Base1")
        .membersInjector()
      val base2MembersInjector = classLoader.loadClass("com.squareup.test.Base2")
        .membersInjector()
      val midMembersInjector = classLoader.loadClass("com.squareup.test.Mid")
        .membersInjector()

      val injectClassMembersInjector = injectClass.membersInjector()

      val constructor = injectClassMembersInjector.declaredConstructors.single()
      assertThat(constructor.parameterTypes.toList())
        .containsExactly(
          Provider::class.java,
          Provider::class.java,
          Provider::class.java,
          Provider::class.java,
        )

      val membersInjectorInstance = constructor
        .newInstance(
          Provider { "a" },
          Provider { listOf('b', 'c') },
          Provider { listOf("d") },
          Provider { listOf(1, 2) },
        ) as MembersInjector<Any>

      val injectInstanceConstructor = injectClass.createInstance()
      membersInjectorInstance.injectMembers(injectInstanceConstructor)

      val injectInstanceStatic = injectClass.createInstance()

      injectClassMembersInjector.staticInjectMethod("numbers")
        .invoke(null, injectInstanceStatic, listOf(1, 2))
      midMembersInjector.staticInjectMethod("strings")
        .invoke(null, injectInstanceStatic, listOf("d"))
      base1MembersInjector.staticInjectMethod("string")
        .invoke(null, injectInstanceStatic, "a")
      base2MembersInjector.staticInjectMethod("chars")
        .invoke(null, injectInstanceStatic, listOf('b', 'c'))

      assertThat(injectInstanceConstructor).isEqualTo(injectInstanceStatic)
      assertThat(injectInstanceConstructor).isNotSameInstanceAs(injectInstanceStatic)
    }
  }

  @Test
  fun `a member injector is generated for a class with two super classes with an overridden property in another module`() {

    val otherModuleResult = compile(
      """
      package com.squareup.test

      import javax.inject.Inject

      abstract class Base1 {
        @Inject open lateinit var string: String
      }

      abstract class Base2 : Base1() {
        @Inject override lateinit var string: String
      } 
      """,
    ) {
      assertThat(exitCode).isEqualTo(OK)
    }

    compile(
      """
      package com.squareup.test
 
      import javax.inject.Inject

      abstract class Mid : Base2() {
        @Inject lateinit var strings: List<String>
      }

      class InjectClass : Mid() {
        @Inject lateinit var numbers: List<Int>
     
        override fun equals(other: Any?): Boolean {
          if (this === other) return true
          if (javaClass != other?.javaClass) return false
     
          other as InjectClass
     
          if (numbers != other.numbers) return false
          if (strings != other.strings) return false
          if (string != other.string) return false
     
          return true
        }
      }
      """,
      previousCompilationResult = otherModuleResult,
    ) {

      val base1MembersInjector = classLoader.loadClass("com.squareup.test.Base1")
        .membersInjector()
      val base2MembersInjector = classLoader.loadClass("com.squareup.test.Base2")
        .membersInjector()
      val midMembersInjector = classLoader.loadClass("com.squareup.test.Mid")
        .membersInjector()

      val injectClassMembersInjector = injectClass.membersInjector()

      val constructor = injectClassMembersInjector.declaredConstructors.single()
      assertThat(constructor.parameterTypes.toList())
        .containsExactly(
          Provider::class.java,
          Provider::class.java,
          Provider::class.java,
          Provider::class.java,
        )

      val membersInjectorInstance = constructor
        .newInstance(
          Provider { "a" },
          Provider { "b" },
          Provider { listOf("b") },
          Provider { listOf(1, 2) },
        ) as MembersInjector<Any>

      val injectInstanceConstructor = injectClass.createInstance()
      membersInjectorInstance.injectMembers(injectInstanceConstructor)

      val injectInstanceStatic = injectClass.createInstance()

      injectClassMembersInjector.staticInjectMethod("numbers")
        .invoke(null, injectInstanceStatic, listOf(1, 2))
      midMembersInjector.staticInjectMethod("strings")
        .invoke(null, injectInstanceStatic, listOf("b"))
      base1MembersInjector.staticInjectMethod("string")
        .invoke(null, injectInstanceStatic, "a")
      base2MembersInjector.staticInjectMethod("string")
        .invoke(null, injectInstanceStatic, "b")

      assertThat(injectInstanceConstructor).isEqualTo(injectInstanceStatic)
      assertThat(injectInstanceConstructor).isNotSameInstanceAs(injectInstanceStatic)
    }
  }

  @Test
  fun `a member injector is generated for a class with an overridden property in the same module`() {

    compile(
      """
      package com.squareup.test

      import javax.inject.Inject

      abstract class Base {
        @Inject open lateinit var string: String
      }

      abstract class Mid : Base() {
        @Inject override lateinit var string: String
      }

      class InjectClass : Mid() {
        @Inject lateinit var numbers: List<Int>
     
        override fun equals(other: Any?): Boolean {
          if (this === other) return true
          if (javaClass != other?.javaClass) return false
     
          other as InjectClass

          if (string != other.string) return false
          if (numbers != other.numbers) return false
     
          return true
        }
      }
      """,
    ) {

      val baseMembersInjector = classLoader.loadClass("com.squareup.test.Base")
        .membersInjector()
      val midMembersInjector = classLoader.loadClass("com.squareup.test.Mid")
        .membersInjector()

      val injectClassMembersInjector = injectClass.membersInjector()

      val constructor = injectClassMembersInjector.declaredConstructors.single()
      assertThat(constructor.parameterTypes.toList())
        .containsExactly(
          Provider::class.java,
          Provider::class.java,
          Provider::class.java,
        )

      val membersInjectorInstance = constructor
        .newInstance(
          Provider { "a" },
          Provider { "b" },
          Provider { listOf(1, 2) },
        ) as MembersInjector<Any>

      val injectInstanceConstructor = injectClass.createInstance()
      membersInjectorInstance.injectMembers(injectInstanceConstructor)

      val injectInstanceStatic = injectClass.createInstance()

      baseMembersInjector.staticInjectMethod("string")
        .invoke(null, injectInstanceStatic, "a")
      midMembersInjector.staticInjectMethod("string")
        .invoke(null, injectInstanceStatic, "b")
      injectClassMembersInjector.staticInjectMethod("numbers")
        .invoke(null, injectInstanceStatic, listOf(1, 2))

      assertThat(injectInstanceConstructor).isEqualTo(injectInstanceStatic)
      assertThat(injectInstanceConstructor).isNotSameInstanceAs(injectInstanceStatic)
    }
  }

  @Test
  fun `a member injector is generated for a class with a typealias injected parameter`() {

    compile(
      """
      package com.squareup.test

      import javax.inject.Inject
      
      typealias StringList = List<String>

      class InjectClass {
        @Inject lateinit var stringList: StringList
     
        override fun equals(other: Any?): Boolean {
          if (this === other) return true
          if (javaClass != other?.javaClass) return false
     
          other as InjectClass
     
          if (stringList != other.stringList) return false
     
          return true
        }
        
        override fun hashCode() = stringList.hashCode()
      }
      """,
    ) {

      val injectClassMembersInjector = injectClass.membersInjector()

      val constructor = injectClassMembersInjector.declaredConstructors.single()
      assertThat(constructor.parameterTypes.toList())
        .containsExactly(Provider::class.java)

      val membersInjectorInstance = constructor
        .newInstance(Provider { listOf("a") }) as MembersInjector<Any>

      val injectInstanceConstructor = injectClass.createInstance()
      membersInjectorInstance.injectMembers(injectInstanceConstructor)

      val injectInstanceStatic = injectClass.createInstance()

      injectClassMembersInjector.staticInjectMethod("stringList")
        .invoke(null, injectInstanceStatic, listOf("a"))

      assertThat(injectInstanceConstructor).isEqualTo(injectInstanceStatic)
      assertThat(injectInstanceConstructor).isNotSameInstanceAs(injectInstanceStatic)
    }
  }

  @Test
  fun `a member injector is generated for a class with a typealias injected parameter and different type arguments`() {

    compile(
      """
      package com.squareup.test

      import javax.inject.Inject

      typealias AliasType<X> = Map<String, X>

      class InjectClass {
        @Inject lateinit var value: AliasType<Int>
     
        override fun equals(other: Any?): Boolean {
          if (this === other) return true
          if (javaClass != other?.javaClass) return false
     
          other as InjectClass
     
          if (value != other.value) return false
     
          return true
        }
        
        override fun hashCode() = value.hashCode()
      }
      """,
    ) {
      val injectClassMembersInjector = injectClass.membersInjector()

      val constructor = injectClassMembersInjector.declaredConstructors.single()
      assertThat(constructor.parameterTypes.toList())
        .containsExactly(Provider::class.java)

      val membersInjectorInstance = constructor
        .newInstance(Provider { mapOf("a" to 1) }) as MembersInjector<Any>

      val injectInstanceConstructor = injectClass.createInstance()
      membersInjectorInstance.injectMembers(injectInstanceConstructor)

      val injectInstanceStatic = injectClass.createInstance()

      injectClassMembersInjector.staticInjectMethod("value")
        .invoke(null, injectInstanceStatic, mapOf("a" to 1))

      assertThat(injectInstanceConstructor).isEqualTo(injectInstanceStatic)
      assertThat(injectInstanceConstructor).isNotSameInstanceAs(injectInstanceStatic)
    }
  }

  @Test
  fun `a member injector is generated for a class with a typealias superclass`() {
    compile(
      """
      package com.squareup.test

      import javax.inject.Inject

      abstract class ActualBase {
        @Inject lateinit var string: String
      }
      
      typealias Base = ActualBase

      class InjectClass : Base() {
        @Inject lateinit var numbers: List<Int>
     
        override fun equals(other: Any?): Boolean {
          if (this === other) return true
          if (javaClass != other?.javaClass) return false
     
          other as InjectClass
     
          if (numbers != other.numbers) return false
          if (string != other.string) return false
     
          return true
        }

        override fun hashCode(): Int {
          var result = numbers.hashCode()
          result = 31 * result + string.hashCode()
          return result
        }
      }
      """,
    ) {

      val actualBaseMembersInjector = classLoader.loadClass("com.squareup.test.ActualBase")
        .membersInjector()

      val injectClassMembersInjector = injectClass.membersInjector()

      val constructor = injectClassMembersInjector.declaredConstructors.single()
      assertThat(constructor.parameterTypes.toList())
        .containsExactly(
          Provider::class.java,
          Provider::class.java,
        )

      val membersInjectorInstance = constructor
        .newInstance(
          Provider { "a" },
          Provider { listOf(1, 2) },
        ) as MembersInjector<Any>

      val injectInstanceConstructor = injectClass.createInstance()
      membersInjectorInstance.injectMembers(injectInstanceConstructor)

      val injectInstanceStatic = injectClass.createInstance()

      injectClassMembersInjector.staticInjectMethod("numbers")
        .invoke(null, injectInstanceStatic, listOf(1, 2))
      actualBaseMembersInjector.staticInjectMethod("string")
        .invoke(null, injectInstanceStatic, "a")

      assertThat(injectInstanceConstructor).isEqualTo(injectInstanceStatic)
      assertThat(injectInstanceConstructor).isNotSameInstanceAs(injectInstanceStatic)
    }
  }

  @Test
  fun `a member injector is generated for a class with a typealias superclass in another module`() {
    val otherModuleResult = compile(
      """
      package com.squareup.test

      import javax.inject.Inject

      abstract class ActualBase {
        @Inject lateinit var string: String
      }
      
      typealias Base = ActualBase
      """,
    ) {
      assertThat(exitCode).isEqualTo(OK)
    }

    compile(
      """
      package com.squareup.test
 
      import javax.inject.Inject

      class InjectClass : Base() {
        @Inject lateinit var numbers: List<Int>
     
        override fun equals(other: Any?): Boolean {
          if (this === other) return true
          if (javaClass != other?.javaClass) return false
     
          other as InjectClass
     
          if (numbers != other.numbers) return false
          if (string != other.string) return false
     
          return true
        }

        override fun hashCode(): Int {
          var result = numbers.hashCode()
          result = 31 * result + string.hashCode()
          return result
        }
      }
      """,
      previousCompilationResult = otherModuleResult,
    ) {

      val actualBaseMembersInjector = classLoader.loadClass("com.squareup.test.ActualBase")
        .membersInjector()

      val injectClassMembersInjector = injectClass.membersInjector()

      val constructor = injectClassMembersInjector.declaredConstructors.single()
      assertThat(constructor.parameterTypes.toList())
        .containsExactly(
          Provider::class.java,
          Provider::class.java,
        )

      val membersInjectorInstance = constructor
        .newInstance(
          Provider { "a" },
          Provider { listOf(1, 2) },
        ) as MembersInjector<Any>

      val injectInstanceConstructor = injectClass.createInstance()
      membersInjectorInstance.injectMembers(injectInstanceConstructor)

      val injectInstanceStatic = injectClass.createInstance()

      injectClassMembersInjector.staticInjectMethod("numbers")
        .invoke(null, injectInstanceStatic, listOf(1, 2))
      actualBaseMembersInjector.staticInjectMethod("string")
        .invoke(null, injectInstanceStatic, "a")

      assertThat(injectInstanceConstructor).isEqualTo(injectInstanceStatic)
      assertThat(injectInstanceConstructor).isNotSameInstanceAs(injectInstanceStatic)
    }
  }

  @Test
  fun `a member injector is not generated when field injected properties are only in a super class`() {

    compile(
      """
      package com.squareup.test
 
      import javax.inject.Inject

      abstract class Base {
        @Inject lateinit var string: String
      }

      class InjectClass : Base() {
     
        override fun equals(other: Any?): Boolean {
          if (this === other) return true
          if (javaClass != other?.javaClass) return false
     
          other as InjectClass
     
          if (string != other.string) return false
     
          return true
        }

        override fun hashCode(): Int = string.hashCode()
      }
      """,
    ) {
      assertThat(exitCode).isEqualTo(OK)

      assertFailsWith<ClassNotFoundException> {
        injectClass.membersInjector()
      }
    }
  }

  @Test
  fun `a member injector is not generated when Dagger doesn't support it`() {
    compile(
      """
      package com.squareup.test
 
      import javax.inject.Inject

      class InjectClass {
        @Inject
        var injected: String? = null
      }
      """,
      expectExitCode = KotlinCompilation.ExitCode.COMPILATION_ERROR,
    ) {
      assertThat(messages).contains("Dagger does not support injection into private fields")

      assertFailsWith<ClassNotFoundException> {
        injectClass.membersInjector()
      }
    }

    compile(
      """
      package com.squareup.test
 
      import javax.inject.Inject

      class InjectClass {
        @Inject
        lateinit var injected1: String
        
        @Inject @JvmField
        var injected2: String? = null
        
        override fun equals(other: Any?): Boolean {
          if (this === other) return true
          if (javaClass != other?.javaClass) return false
      
          other as InjectClass
      
          if (injected1 != other.injected1) return false
          if (injected2 != other.injected2) return false
      
          return true
        }
      
        override fun hashCode(): Int {
          var result = injected1.hashCode()
          result = 31 * result + injected2.hashCode()
          return result
        }
      }
      """,
    ) {
      val membersInjector = injectClass.membersInjector()

      val constructor = membersInjector.declaredConstructors.single()
      assertThat(constructor.parameterTypes.toList())
        .containsExactly(Provider::class.java, Provider::class.java)

      val membersInjectorInstance = constructor
        .newInstance(Provider { "a" }, Provider { "b" }) as MembersInjector<Any>

      val injectInstanceConstructor = injectClass.createInstance()
      membersInjectorInstance.injectMembers(injectInstanceConstructor)

      val injectInstanceStatic = injectClass.createInstance()

      membersInjector.staticInjectMethod("injected1")
        .invoke(null, injectInstanceStatic, "a")

      membersInjector.staticInjectMethod("injected2")
        .invoke(null, injectInstanceStatic, "b")

      assertThat(injectInstanceConstructor).isEqualTo(injectInstanceStatic)
      assertThat(injectInstanceConstructor).isNotSameInstanceAs(injectInstanceStatic)
    }
  }

  @Test
  fun `a member injector is generated for a class only injected property setters`() {
    compile(
      """
      package com.squareup.test

      import javax.inject.Inject

      class InjectClass {
        @set:Inject
        var value: String = "initial"
          set(value) {
            field = value
          }
     
        override fun equals(other: Any?): Boolean {
          if (this === other) return true
          if (javaClass != other?.javaClass) return false
     
          other as InjectClass
     
          if (value != other.value) return false
     
          return true
        }
        
        override fun hashCode() = value.hashCode()
      }
      """,
    ) {
      val injectClassMembersInjector = injectClass.membersInjector()

      val constructor = injectClassMembersInjector.declaredConstructors.single()
      assertThat(constructor.parameterTypes.toList())
        .containsExactly(Provider::class.java)

      val membersInjectorInstance = constructor
        .newInstance(Provider { "a" }) as MembersInjector<Any>

      val injectInstanceConstructor = injectClass.createInstance()
      membersInjectorInstance.injectMembers(injectInstanceConstructor)

      val injectInstanceStatic = injectClass.createInstance()

      injectClassMembersInjector.staticInjectMethod("setValue")
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

  private fun compile(
    @Language("kotlin") vararg sources: String,
    previousCompilationResult: JvmCompilationResult? = null,
    expectExitCode: KotlinCompilation.ExitCode = KotlinCompilation.ExitCode.OK,
    block: JvmCompilationResult.() -> Unit = { },
  ): JvmCompilationResult = compileAnvil(
    sources = sources,
    enableDaggerAnnotationProcessor = useDagger,
    generateDaggerFactories = !useDagger,
    previousCompilationResult = previousCompilationResult,
    expectExitCode = expectExitCode,
    mode = mode,
    block = block,
  )
}
