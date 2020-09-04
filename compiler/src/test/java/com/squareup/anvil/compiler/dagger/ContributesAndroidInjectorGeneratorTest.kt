package com.squareup.anvil.compiler.dagger

import com.google.common.truth.Truth.assertThat
import com.squareup.anvil.compiler.contributesAndroidInjector
import com.squareup.anvil.compiler.daggerModule1
import com.squareup.anvil.compiler.extends
import com.squareup.anvil.compiler.isAbstract
import com.tschuchort.compiletesting.KotlinCompilation.Result
import dagger.Binds
import dagger.Module
import dagger.Subcomponent
import dagger.android.AndroidInjector
import dagger.multibindings.ClassKey
import dagger.multibindings.IntoMap
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters

@Suppress("UNCHECKED_CAST")
@RunWith(Parameterized::class)
class ContributesAndroidInjectorGeneratorTest(
  private val useDagger: Boolean
) {

  companion object {
    @Parameters(name = "Use Dagger: {0}")
    @JvmStatic
    fun useDagger(): Collection<Any> {
      return listOf(true, false)
    }
  }

  @Test
  fun `a bind class is generated for a @ContributesAndroidInjector method`() {
    /*
package com.squareup.test;

@Module(
  subcomponents =
      DaggerModule1_BindMyFragment.MyFragmentSubcomponent.class
)
public abstract class DaggerModule1_BindMyFragment {
  private DaggerModule1_BindMyFragment() {}

  @Binds
  @IntoMap
  @ClassKey(MyFragment.class)
  abstract AndroidInjector.Factory<?> bindAndroidInjectorFactory(
      MyFragmentSubcomponent.Factory builder);

  @Subcomponent
  public interface MyFragmentSubcomponent
      extends AndroidInjector<MyFragment> {
    @Subcomponent.Factory
    interface Factory extends AndroidInjector.Factory<MyFragment> {}
  }
}
     */

    compile(
      """
        package com.squareup.test
        
        class MyFragment
        
        @dagger.Module
        abstract class DaggerModule1 {
          @dagger.android.ContributesAndroidInjector abstract fun bindMyFragment(): MyFragment
        }
        """
    ) {
      val subcomponentClass = daggerModule1.contributesAndroidInjector("MyFragment")
      assertThat(subcomponentClass.declaredConstructors).hasLength(1)

      subcomponentClass.isAnnotationPresent(Module::class.java)
      val moduleAnnotation = subcomponentClass.getAnnotation(Module::class.java)
      assertThat(moduleAnnotation.subcomponents.single().java.name).isEqualTo(
        "com.squareup.test.DaggerModule1_BindMyFragment\$MyFragmentSubcomponent"
      )

      val bindMethod = subcomponentClass.declaredMethods.single()
      assertThat(bindMethod.isAbstract).isTrue()
      assertThat(bindMethod.isAnnotationPresent(Binds::class.java))
      assertThat(bindMethod.isAnnotationPresent(IntoMap::class.java))
      assertThat(bindMethod.isAnnotationPresent(ClassKey::class.java))
      val classKeyAnnotation = bindMethod.getAnnotation(ClassKey::class.java)
      assertThat(classKeyAnnotation.value.simpleName).isEqualTo("MyFragment")
      assertThat(bindMethod.returnType).isEqualTo(AndroidInjector.Factory::class.java)
      val parameter = bindMethod.parameters.single()
      assertThat(parameter.type.name).isEqualTo(
        "com.squareup.test.DaggerModule1_BindMyFragment\$MyFragmentSubcomponent\$Factory"
      )

      val subcomponentInterface = classLoader.loadClass(
        "com.squareup.test.DaggerModule1_BindMyFragment\$MyFragmentSubcomponent"
      )
      val subcomponentFactoryInterface = classLoader.loadClass(
        "com.squareup.test.DaggerModule1_BindMyFragment\$MyFragmentSubcomponent\$Factory"
      )
      assertThat(subcomponentInterface.isAnnotationPresent(Subcomponent::class.java))
      assertThat(subcomponentInterface.extends(AndroidInjector::class.java)).isTrue()
      assertThat(subcomponentFactoryInterface.isAnnotationPresent(Subcomponent.Factory::class.java))
      assertThat(subcomponentFactoryInterface.extends(AndroidInjector.Factory::class.java)).isTrue()
    }
  }

  private fun compile(
    source: String,
    block: Result.() -> Unit = { }
  ): Result = com.squareup.anvil.compiler.compile(
    source,
    enableDaggerAnnotationProcessor = useDagger,
    enableDaggerAndroidAnnotationProcessor = useDagger,
    generateDaggerFactories = !useDagger,
    block = block
  )
}
