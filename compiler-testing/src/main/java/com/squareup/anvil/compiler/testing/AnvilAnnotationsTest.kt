package com.squareup.anvil.compiler.testing

import com.rickbusarow.kase.DefaultTestEnvironment
import com.rickbusarow.kase.Kase1
import com.rickbusarow.kase.KaseTestFactory
import com.rickbusarow.kase.ParamTestEnvironmentFactory
import com.rickbusarow.kase.files.HasWorkingDir
import com.rickbusarow.kase.files.TestLocation
import com.rickbusarow.kase.kases
import kotlin.reflect.KClass

/**
 * Creates test factories to run the same test against multiple annotations.
 *
 * example:
 * ```
 * class MyTestClass : AnvilAnnotationsTest(
 *   MergeComponent::class,
 *   MergeSubcomponent::class,
 *   MergeModules::class) {
 *
 *   @TestFactory fun `test something`() = testFactory {
 *     compile(
 *       """
 *       package com.squareup.test
 *
 *       $import
 *
 *       @annotation(Any::class)
 *       interface SomeInterface
 *       """
 *     ) {
 *       // ...
 *     }
 *   }
 * }
 * ```
 * @param requiredAnnotation A test case is always created for this annotation.
 * @param fullTestRunAnnotations Test cases are only created in a full test run.
 */
public abstract class AnvilAnnotationsTest(
  requiredAnnotation: KClass<out Annotation>,
  vararg fullTestRunAnnotations: KClass<out Annotation>,
) : KaseTestFactory<
  Kase1<KClass<out Annotation>>,
  AnnotationTestEnvironment,
  ParamTestEnvironmentFactory<Kase1<KClass<out Annotation>>, AnnotationTestEnvironment>,
  > {

  override val params: List<Kase1<KClass<out Annotation>>> = kases(
    buildList {
      add(requiredAnnotation)
      if (FULL_TEST_RUN) {
        addAll(fullTestRunAnnotations)
      }
    },
    displayNameFactory = { "annotationClass: ${a1.simpleName!!}" },
  )

  override val testEnvironmentFactory:
    ParamTestEnvironmentFactory<Kase1<KClass<out Annotation>>, AnnotationTestEnvironment> =
    AnnotationTestEnvironment
}

public class AnnotationTestEnvironment(
  public val annotationClass: KClass<out Annotation>,
  hasWorkingDir: HasWorkingDir,
) : DefaultTestEnvironment(hasWorkingDir),
  CompilationEnvironment {

  /**
   * Always an unqualified string, e.g. "@MergeComponent".
   */
  public val annotation: String = "@${annotationClass.simpleName}"

  /**
   * ex: `import com.squareup.anvil.annotations.MergeComponent`
   */
  public val import: String = "import ${annotationClass.java.canonicalName}"

  public companion object : ParamTestEnvironmentFactory<Kase1<KClass<out Annotation>>, AnnotationTestEnvironment> {
    override fun create(
      params: Kase1<KClass<out Annotation>>,
      names: List<String>,
      location: TestLocation,
    ): AnnotationTestEnvironment = AnnotationTestEnvironment(
      params.a1,
      HasWorkingDir(names, location),
    )
  }
}
