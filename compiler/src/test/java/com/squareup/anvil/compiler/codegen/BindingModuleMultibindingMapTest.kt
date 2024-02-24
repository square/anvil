package com.squareup.anvil.compiler.codegen

import com.google.common.truth.Truth.assertThat
import com.squareup.anvil.annotations.MergeComponent
import com.squareup.anvil.annotations.MergeSubcomponent
import com.squareup.anvil.annotations.compat.MergeModules
import com.squareup.anvil.compiler.bindingKey
import com.squareup.anvil.compiler.compile
import com.squareup.anvil.compiler.componentInterface
import com.squareup.anvil.compiler.contributingInterface
import com.squareup.anvil.compiler.generatedMultiBindingModule
import com.squareup.anvil.compiler.internal.testing.AnyDaggerComponent
import com.squareup.anvil.compiler.internal.testing.anyDaggerComponent
import com.squareup.anvil.compiler.internal.testing.getValue
import com.squareup.anvil.compiler.internal.testing.isAbstract
import com.squareup.anvil.compiler.isError
import com.squareup.anvil.compiler.isFullTestRun
import com.squareup.anvil.compiler.mergedModules
import com.squareup.anvil.compiler.parentInterface
import dagger.Binds
import dagger.Provides
import dagger.multibindings.IntoMap
import dagger.multibindings.IntoSet
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters
import kotlin.reflect.KClass

@RunWith(Parameterized::class)
class BindingModuleMultibindingMapTest(
  private val annotationClass: KClass<out Annotation>,
) {

  private val annotation = "@${annotationClass.simpleName}"
  private val import = "import ${annotationClass.java.canonicalName}"

  companion object {
    @Parameters(name = "{0}")
    @JvmStatic
    fun annotationClasses(): Collection<Any> {
      return buildList {
        add(MergeComponent::class)
        if (isFullTestRun()) {
          add(MergeSubcomponent::class)
          add(MergeModules::class)
        }
      }
    }
  }

  @Test fun `the Dagger multibinding method is generated for non-objects`() {
    compile(
      """
      package com.squareup.test

      import com.squareup.anvil.annotations.ContributesMultibinding
      import dagger.MapKey
      $import
      
      @MapKey
      annotation class BindingKey(val value: String)

      interface ParentInterface

      interface Middle : ParentInterface

      @ContributesMultibinding(Any::class, ParentInterface::class)
      @BindingKey("abc")
      interface ContributingInterface : Middle

      $annotation(Any::class)
      interface ComponentInterface
      """,
    ) {
      val modules = componentInterface.mergedModules(annotationClass)

      val methods = modules.single().java.declaredMethods
      assertThat(methods).hasLength(1)

      with(methods[0]) {
        assertThat(returnType).isEqualTo(parentInterface)
        assertThat(parameterTypes.toList()).containsExactly(contributingInterface)
        assertThat(isAbstract).isTrue()
        assertThat(isAnnotationPresent(Binds::class.java)).isTrue()
        assertThat(isAnnotationPresent(IntoMap::class.java)).isTrue()
        assertThat(isAnnotationPresent(IntoSet::class.java)).isFalse()
        assertThat(isAnnotationPresent(bindingKey)).isTrue()
        assertThat(getAnnotation(bindingKey).getValue<String>()).isEqualTo("abc")
      }
    }
  }

  @Test fun `the Dagger provider method is generated for objects`() {
    compile(
      """
      package com.squareup.test

      import com.squareup.anvil.annotations.ContributesMultibinding
      import dagger.MapKey
      $import
      
      @MapKey
      annotation class BindingKey(val value: String)

      interface ParentInterface

      @ContributesMultibinding(Any::class)
      @BindingKey("abc")
      object ContributingInterface : ParentInterface

      $annotation(Any::class)
      interface ComponentInterface
      """,
    ) {
      val modules = componentInterface.mergedModules(annotationClass).toList()
      assertThat(modules).containsExactly(contributingInterface.generatedMultiBindingModule.kotlin)

      val methods = modules.single().java.declaredMethods
      assertThat(methods).hasLength(1)

      with(methods[0]) {
        assertThat(returnType).isEqualTo(parentInterface)
        assertThat(parameterTypes.toList()).isEmpty()
        assertThat(isAbstract).isFalse()
        assertThat(isAnnotationPresent(Provides::class.java)).isTrue()
        assertThat(isAnnotationPresent(IntoMap::class.java)).isTrue()
        assertThat(isAnnotationPresent(bindingKey)).isTrue()
        assertThat(getAnnotation(bindingKey).getValue<String>()).isEqualTo("abc")

        val moduleInstance = modules.first().java.declaredFields
          .first { it.name == "INSTANCE" }
          .get(null)

        assertThat(invoke(moduleInstance)::class.java.canonicalName)
          .isEqualTo("com.squareup.test.ContributingInterface")
      }
    }
  }

  @Test fun `an enum map key is supported`() {
    compile(
      """
      package com.squareup.test

      import com.squareup.anvil.annotations.ContributesMultibinding
      import dagger.MapKey
      $import
      
      enum class EnumClass { ABC }
      
      @MapKey
      annotation class BindingKey(val value: EnumClass)

      interface ParentInterface

      @ContributesMultibinding(Any::class)
      @BindingKey(EnumClass.ABC)
      interface ContributingInterface : ParentInterface

      $annotation(Any::class)
      interface ComponentInterface
      """,
    ) {
      val modules = componentInterface.mergedModules(annotationClass)

      val methods = modules.single().java.declaredMethods
      assertThat(methods).hasLength(1)

      assertThat(methods[0].getAnnotation(bindingKey).getValue<Any>().toString()).isEqualTo("ABC")
    }
  }

  @Test fun `a class map key is supported`() {
    compile(
      """
      package com.squareup.test

      import com.squareup.anvil.annotations.ContributesMultibinding
      import dagger.MapKey
      import kotlin.reflect.KClass
      $import
      
      @MapKey
      annotation class BindingKey(val value: KClass<*>)

      interface ParentInterface

      @ContributesMultibinding(Any::class)
      @BindingKey(Unit::class)
      interface ContributingInterface : ParentInterface

      $annotation(Any::class)
      interface ComponentInterface
      """,
    ) {
      val modules = componentInterface.mergedModules(annotationClass)

      val methods = modules.single().java.declaredMethods
      assertThat(methods).hasLength(1)

      assertThat(methods[0].getAnnotation(bindingKey).getValue<KClass<*>>())
        .isEqualTo(Unit::class.java)
    }
  }

  @Test fun `a complex map key is supported`() {
    compile(
      """
      package com.squareup.test

      import com.squareup.anvil.annotations.ContributesMultibinding
      import dagger.MapKey
      import kotlin.reflect.KClass
      $import
      
      @MapKey(unwrapValue = false)
      annotation class BindingKey(
        val name: String,
        val implementingClass: KClass<*>,
        val thresholds: IntArray
      )

      interface ParentInterface

      @ContributesMultibinding(Any::class)
      @BindingKey("abc", Unit::class, [1, 2, 3])
      interface ContributingInterface : ParentInterface

      $annotation(Any::class)
      interface ComponentInterface
      """,
    ) {
      val modules = componentInterface.mergedModules(annotationClass)

      val annotation = modules.single().java.declaredMethods.single().getAnnotation(bindingKey)
      val methods = annotation::class.java.declaredMethods

      assertThat(methods.single { it.name == "name" }.invoke(annotation)).isEqualTo("abc")
      assertThat(methods.single { it.name == "implementingClass" }.invoke(annotation))
        .isEqualTo(Unit::class.java)
      assertThat(methods.single { it.name == "thresholds" }.invoke(annotation) as IntArray)
        .asList().containsExactly(1, 2, 3).inOrder()
    }
  }

  @Test fun `contributed multibindings aren't allowed to have more than one map key`() {
    compile(
      """
      package com.squareup.test

      import com.squareup.anvil.annotations.ContributesMultibinding
      import dagger.MapKey
      import kotlin.reflect.KClass
      $import
      
      @MapKey
      annotation class BindingKey1(val value: KClass<*>)
      
      @MapKey
      annotation class BindingKey2(val value: KClass<*>)

      interface ParentInterface

      @ContributesMultibinding(Any::class)
      @BindingKey1(Unit::class)
      @BindingKey2(Unit::class)
      interface ContributingInterface : ParentInterface

      $annotation(Any::class)
      interface ComponentInterface
      """,
    ) {
      assertThat(exitCode).isError()
      assertThat(messages).contains(
        "Classes annotated with @ContributesMultibinding may not use more than one @MapKey.",
      )
    }
  }

  private val Class<*>.anyDaggerComponent: AnyDaggerComponent
    get() = anyDaggerComponent(annotationClass)
}
