package com.squareup.anvil.plugin.testing

import com.google.common.collect.Lists.cartesianProduct
import com.rickbusarow.kase.KaseMatrix
import com.rickbusarow.kase.gradle.GradleKotlinTestVersions
import com.rickbusarow.kase.gradle.GradleTestEnvironment
import com.rickbusarow.kase.gradle.KaseGradleTest
import com.rickbusarow.kase.gradle.dsl.model.HasDependenciesBlock
import com.rickbusarow.kase.gradle.versions
import com.rickbusarow.kase.stdlib.letIf
import com.rickbusarow.kase.wrap
import com.squareup.anvil.plugin.buildProperties.fullTestRun
import com.squareup.anvil.plugin.testing.AnvilGradleTestEnvironment.Factory
import org.junit.jupiter.api.DynamicNode
import java.util.stream.Stream

abstract class BaseGradleTest(
  override val kaseMatrix: KaseMatrix = AnvilVersionMatrix(),
) : KaseGradleTest<GradleKotlinTestVersions, AnvilGradleTestEnvironment, Factory>,
  FileStubs,
  MoreAsserts,
  ClassGraphAsserts {

  override val testEnvironmentFactory = Factory()

  override val params: List<GradleKotlinTestVersions>
    get() = kaseMatrix.versions(GradleKotlinTestVersions)
      .letIf(!fullTestRun) { it.takeLast(1) }

  /** Forced overload so that tests don't reference the generated BuildProperties file directly */
  @Suppress("UnusedReceiverParameter")
  val GradleTestEnvironment.anvilVersion: String
    get() = com.squareup.anvil.plugin.buildProperties.anvilVersion
}

abstract class FactoryGenTest(
  kaseMatrix: KaseMatrix = AnvilVersionMatrix(),
) : BaseGradleTest(kaseMatrix) {

  inline fun <K : GradleKotlinTestVersions> List<K>.withFactoryGen(
    crossinline testAction: suspend AnvilGradleTestEnvironment.(
      versions: K,
      factoryGen: FactoryGen,
    ) -> Unit,
  ): Stream<out DynamicNode> = asContainers { versions ->
    cartesianProduct(
      listOf(true, false),
      listOf(true, false),
      listOf(true, false),
    )
      .map { (genFactories, addKapt, daggerCompiler) ->
        FactoryGen(
          genFactories = genFactories,
          addKapt = addKapt,
          daggerCompiler = daggerCompiler,
        )
      }
      .filter {
        when {
          // We can't add a Dagger compiler dependency without a plugin to add it to
          it.daggerCompiler -> it.addKapt && !it.genFactories
          else -> true
        }
      }
      .asTests(
        testEnvironmentFactory = AnvilGradleTestEnvironment.Factory().wrap(versions),
        testAction = { factoryGen -> testAction(versions, factoryGen) },
      )
  }

  fun HasDependenciesBlock<*>.dependenciesBlock(libs: Libs, factoryGen: FactoryGen) {
    dependencies {
      api(libs.dagger2.annotations)
      compileOnly(libs.inject)

      if (factoryGen.daggerCompiler) {
        when {
          factoryGen.addKapt -> {
            kapt(libs.dagger2.compiler)
          }
          else -> {
            error("No compiler plugin added")
          }
        }
      }
    }
  }

  data class FactoryGen(
    val genFactories: Boolean,
    val addKapt: Boolean,
    val daggerCompiler: Boolean,
  ) {
    override fun toString(): String = buildString {
      if (addKapt) {
        append("KAPT plugin | ")
      }
      if (daggerCompiler) {
        append("with Dagger compiler | ")
      } else {
        append("no Dagger compiler | ")
      }
      append("genFactories: $genFactories")
    }
  }
}
