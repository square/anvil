package com.squareup.anvil.plugin

import com.rickbusarow.kase.gradle.BaseGradleTest
import com.rickbusarow.kase.gradle.DependencyVersion
import com.rickbusarow.kase.gradle.GradleKotlinTestVersions
import com.rickbusarow.kase.gradle.VersionMatrix
import com.rickbusarow.kase.gradle.generation.dsl.BuildFileSpec
import com.rickbusarow.kase.gradle.generation.model.AbstractDslElementContainer
import com.rickbusarow.kase.gradle.generation.model.LambdaParameter
import com.rickbusarow.kase.gradle.generation.model.gradlePropertyReference
import org.gradle.util.GradleVersion
import java.io.File

abstract class BaseAnvilGradleTest(
  override val versionMatrix: VersionMatrix = VersionMatrix(
    DependencyVersion.Gradle(GradleVersion.current().version),
    DependencyVersion.Kotlin(KotlinVersion.CURRENT.toString())
  )
) : BaseGradleTest<GradleKotlinTestVersions> {

  override val localM2Path: File
    get() = BuildPropertiesIntegrationTest.localBuildM2Dir

  override val kases: List<GradleKotlinTestVersions>
    get() = GradleKotlinTestVersions.from(versionMatrix)

  val libs = Libs()

  fun BuildFileSpec.anvil(
    block: AnvilExtensionSpec.() -> Unit
  ) = functionCall("anvil", LambdaParameter(builder = block))
}

class AnvilExtensionSpec : AbstractDslElementContainer<AnvilExtensionSpec>() {

  val generateDaggerFactories by gradlePropertyReference()
  val generateDaggerFactoriesOnly by gradlePropertyReference()
  val disableComponentMerging by gradlePropertyReference()
  val syncGeneratedSources by gradlePropertyReference()
  val addOptionalAnnotations by gradlePropertyReference()
}
