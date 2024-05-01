package com.squareup.anvil.compiler.testing

import com.rickbusarow.kase.DefaultTestEnvironment
import com.rickbusarow.kase.Kase1
import com.rickbusarow.kase.KaseTestFactory
import com.rickbusarow.kase.ParamTestEnvironmentFactory
import com.rickbusarow.kase.files.HasWorkingDir
import com.rickbusarow.kase.files.TestLocation
import com.rickbusarow.kase.kases
import com.squareup.anvil.compiler.isFullTestRun
import kotlin.reflect.KClass

abstract class AnvilAnnotationsTest(
  firstAnnotation: KClass<out Annotation>,
  vararg fullTestRunAnnotations: KClass<out Annotation>,
) : KaseTestFactory<
  Kase1<KClass<out Annotation>>,
  AnnotationTestEnvironment,
  ParamTestEnvironmentFactory<Kase1<KClass<out Annotation>>, AnnotationTestEnvironment>,
  > {

  override val params: List<Kase1<KClass<out Annotation>>> = kases(
    buildList {
      add(firstAnnotation)
      if (isFullTestRun()) {
        addAll(fullTestRunAnnotations)
      }
    },
    displayNameFactory = { "annotationClass: ${a1.simpleName!!}" },
  )

  override val testEnvironmentFactory:
    ParamTestEnvironmentFactory<Kase1<KClass<out Annotation>>, AnnotationTestEnvironment> =
    AnnotationTestEnvironment
}

class AnnotationTestEnvironment(
  val annotationClass: KClass<out Annotation>,
  hasWorkingDir: HasWorkingDir,
) : DefaultTestEnvironment(hasWorkingDir),
  CompilationEnvironment {

  val annotation = "@${annotationClass.simpleName}"
  val import = "import ${annotationClass.java.canonicalName}"

  companion object : ParamTestEnvironmentFactory<Kase1<KClass<out Annotation>>, AnnotationTestEnvironment> {
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
