package com.squareup.anvil.compiler.dagger

import com.google.common.truth.Truth.assertThat
import com.squareup.anvil.compiler.internal.testing.AnvilCompilationMode
import com.squareup.anvil.compiler.internal.testing.compileAnvil
import com.squareup.anvil.compiler.internal.testing.isStatic
import com.squareup.anvil.compiler.testParams
import com.tschuchort.compiletesting.JvmCompilationResult
import org.intellij.lang.annotations.Language
import org.jetbrains.kotlin.descriptors.runtime.components.tryLoadClass
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.junit.runners.Parameterized.Parameters

@RunWith(Parameterized::class)
class MapKeyCreatorGeneratorTest(
  private val useDagger: Boolean,
  private val mode: AnvilCompilationMode,
) {

  companion object {
    @Parameters(name = "Use Dagger: {0}, mode: {1}")
    @JvmStatic
    fun params() = testParams()
  }

  @Test fun `a creator class is generated`() {
    compile(
      """
      package com.squareup.test
      
      import dagger.MapKey
      import kotlin.reflect.KClass
      import com.squareup.anvil.compiler.dagger.TestEnum
      import com.squareup.anvil.compiler.dagger.TestAnnotation

      @MapKey(unwrapValue = false)
      annotation class ExampleMapKey(
        val stringValue: String,
        val stringArrayValue: Array<String>,
        val annotationValue: TestAnnotation,
        val annotationArrayValue: Array<TestAnnotation>,
        val enumValue: TestEnum,
        val enumArrayValue: Array<TestEnum>,
        val kClassValue: KClass<out String>,
        val kClassStarValue: KClass<*>,
        val kClassArrayValue: Array<KClass<*>>,
        val booleanValue: Boolean,
        val byteValue: Byte,
        val charValue: Char,
        val shortValue: Short,
        val intValue: Int,
        val longValue: Long,
        val floatValue: Float,
        val doubleValue: Double,
        val booleanArrayValue: BooleanArray,
        val byteArrayValue: ByteArray,
        val charArrayValue: CharArray,
        val shortArrayValue: ShortArray,
        val intArrayValue: IntArray,
        val longArrayValue: LongArray,
        val floatArrayValue: FloatArray,
        val doubleArrayValue: DoubleArray,
      )
      """,
    ) {
      val mapKeyClass = classLoader.loadClass("com.squareup.test.ExampleMapKey")
      val creatorClass = classLoader.loadClass("com.squareup.test.ExampleMapKeyCreator")

      val staticMethods = creatorClass.declaredMethods.filter { it.isStatic }
      assertThat(staticMethods).hasSize(2)

      // Assert that any used annotations also have creators
      val testAnnotationInstance = staticMethods.single { it.name == "createTestAnnotation" }
        .invoke(null, "test")

      assertThat(testAnnotationInstance).isEqualTo(TestAnnotation("test"))

      // Assert the primary creator is working
      val mapKeyInstance = staticMethods.single { it.name == "createExampleMapKey" }
        .invoke(
          null,
          /* stringValue*/
          "stringValue",
          /* stringArrayValue */
          arrayOf("stringArrayValue"),
          /* annotationValue */
          TestAnnotation("annotationValue"),
          /* annotationArrayValue */
          arrayOf(TestAnnotation("annotationValue")),
          /* enumValue */
          TestEnum.A,
          /* enumArrayValue */
          arrayOf(TestEnum.A),
          /* kClassValue */
          String::class.java,
          /* kClassStarValue */
          String::class.java,
          /* kClassArrayValue */
          arrayOf(String::class.java),
          /* booleanValue */
          true,
          /* byteValue */
          1.toByte(),
          /* charValue */
          3.toChar(),
          /* shortValue */
          2.toShort(),
          /* intValue */
          4,
          /* longValue */
          5L,
          /* floatValue */
          6.0f,
          /* doubleValue */
          7.0,
          /* booleanArrayValue */
          booleanArrayOf(true),
          /* byteArrayValue */
          byteArrayOf(1.toByte()),
          /* charArrayValue */
          charArrayOf(3.toChar()),
          /* shortArrayValue */
          shortArrayOf(2.toShort()),
          /* intArrayValue */
          intArrayOf(4),
          /* longArrayValue */
          longArrayOf(5L),
          /* floatArrayValue */
          floatArrayOf(6.0f),
          /* doubleArrayValue */
          doubleArrayOf(7.0),
        )

      assertThat(mapKeyInstance::class.java).isAssignableTo(mapKeyClass)
    }
  }

  @Test fun `a recursive annotation still works`() {
    compile(
      """
      package com.squareup.test
      
      import dagger.MapKey

      annotation class RecursiveAnnotation(
        val value: Array<RecursiveAnnotation> = []
      )

      @MapKey(unwrapValue = false)
      annotation class ExampleMapKey(
        val value: RecursiveAnnotation,
      )
      """,
    ) {
      val recursiveClass = classLoader.loadClass("com.squareup.test.RecursiveAnnotation")
      val mapKeyClass = classLoader.loadClass("com.squareup.test.ExampleMapKey")
      val creatorClass = classLoader.loadClass("com.squareup.test.ExampleMapKeyCreator")

      val staticMethods = creatorClass.declaredMethods.filter { it.isStatic }
      assertThat(staticMethods).hasSize(2)

      // Assert that any used annotations also have creators
      val recursiveAnnotationInstance = staticMethods
        .single { it.name == "createRecursiveAnnotation" }
        .invoke(null, java.lang.reflect.Array.newInstance(recursiveClass, 0))

      assertThat(recursiveAnnotationInstance).isInstanceOf(recursiveClass)

      // Assert the primary creator is working
      val mapKeyInstance = staticMethods.single { it.name == "createExampleMapKey" }
        .invoke(
          null,
          /* stringValue*/
          recursiveAnnotationInstance,
        )

      assertThat(mapKeyInstance::class.java).isAssignableTo(mapKeyClass)
    }
  }

  @Test fun `do nothing if unwrapValue is not set to false`() {
    compile(
      """
      package com.squareup.test
      
      import dagger.MapKey

      @MapKey(unwrapValue = true)
      annotation class ExampleMapKey(
        val value: String,
      )

      @MapKey // Use default value
      annotation class ExampleMapKey2(
        val value: String,
      )
      """,
    ) {
      classLoader.loadClass("com.squareup.test.ExampleMapKey")
      val creatorClass = classLoader.tryLoadClass("com.squareup.test.ExampleMapKeyCreator")
      assertThat(creatorClass).isNull()
      classLoader.loadClass("com.squareup.test.ExampleMapKey2")
      val creatorClass2 = classLoader.tryLoadClass("com.squareup.test.ExampleMapKeyCreator2")
      assertThat(creatorClass2).isNull()
    }
  }

  @Ignore("https://youtrack.jetbrains.com/issue/KT-54931")
  @Test
  fun `a nested creator class is generated`() {
    compile(
      """
      package com.squareup.test
      
      import dagger.MapKey

      class Container {
        @MapKey(unwrapValue = false)
        annotation class ExampleMapKey(
          val stringValue: String,
        )
      }
      """,
    ) {
      val mapKeyClass = classLoader.loadClass("com.squareup.test.Container\$ExampleMapKey")
      val creatorClass = classLoader.loadClass("com.squareup.test.Container_ExampleMapKeyCreator")

      val staticMethods = creatorClass.declaredMethods.filter { it.isStatic }
      assertThat(staticMethods).hasSize(1)

      val mapKeyInstance = staticMethods.single { it.name == "createExampleMapKey" }
        .invoke(
          null,
          /* stringValue*/
          "stringValue",
        )

      assertThat(mapKeyInstance::class.java).isAssignableTo(mapKeyClass)
    }
  }

  private fun compile(
    @Language("kotlin") vararg sources: String,
    block: JvmCompilationResult.() -> Unit = { },
  ): JvmCompilationResult = compileAnvil(
    sources = sources,
    enableDaggerAnnotationProcessor = useDagger,
    generateDaggerFactories = !useDagger,
    mode = mode,
    block = block,
  )
}
