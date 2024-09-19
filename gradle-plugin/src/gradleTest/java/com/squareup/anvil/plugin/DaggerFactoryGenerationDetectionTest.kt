package com.squareup.anvil.plugin

import com.google.common.collect.Lists.cartesianProduct
import com.rickbusarow.kase.gradle.GradleKotlinAgpTestVersions
import com.rickbusarow.kase.gradle.GradleKotlinTestVersions
import com.rickbusarow.kase.gradle.dsl.BuildFileSpec
import com.rickbusarow.kase.gradle.dsl.PluginsSpec
import com.rickbusarow.kase.gradle.dsl.buildFile
import com.rickbusarow.kase.gradle.dsl.model.HasDependenciesBlock
import com.rickbusarow.kase.gradle.versions
import com.rickbusarow.kase.wrap
import com.squareup.anvil.plugin.testing.AnvilGradleTestEnvironment
import com.squareup.anvil.plugin.testing.BaseGradleTest
import com.squareup.anvil.plugin.testing.Libs
import com.squareup.anvil.plugin.testing.allDaggerFactoryClasses
import com.squareup.anvil.plugin.testing.allModuleClasses
import com.squareup.anvil.plugin.testing.allProvidesMethods
import com.squareup.anvil.plugin.testing.anvil
import com.squareup.anvil.plugin.testing.classGraphResult
import com.squareup.anvil.plugin.testing.names
import io.kotest.assertions.asClue
import io.kotest.matchers.collections.shouldBeSingleton
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.DynamicNode
import org.junit.jupiter.api.TestFactory
import java.util.stream.Stream

class DaggerFactoryGenerationDetectionTest : BaseGradleTest() {

  @TestFactory
  fun `a provider factory is always generated for a contributed object binding - jvm`() =
    params.withFactoryGen { _, factoryGen ->

      rootProject {

        buildFile {

          plugins {
            kotlin("jvm")
            id("com.squareup.anvil")
            pluginsExtras(factoryGen)
          }

          anvilBlock(factoryGen)

          dependenciesBlock(libs, factoryGen)
        }

        dir("src/main/java") {
          kotlinFile(
            "com/squareup/test/Bound.kt",
            """
            package com.squareup.test
            
            import com.squareup.anvil.annotations.ContributesBinding

            interface Bound

            @ContributesBinding(Unit::class)
            object Contributed : Bound
            """,
          )
        }
      }

      shouldSucceed("jar")

      val classGraph = rootProject.classGraphResult()

      val moduleClass = classGraph.allModuleClasses()
        .shouldBeSingleton().single()

      moduleClass.allProvidesMethods().names() shouldBe listOf("provideBound")

      classGraph.allDaggerFactoryClasses()
        .shouldBeSingleton().single()
        .asClue { factory ->

          factory.packageName shouldBe "com.squareup.test"

          factory.name shouldBe "${moduleClass.name}_ProvideBoundFactory"

          factory.typeDescriptor.superinterfaceSignatures
            .map { it.fullyQualifiedClassName } shouldBe listOf("dagger.internal.Factory")

          factory.getMethodInfo("provideBound")
            .single()
            .typeSignatureOrTypeDescriptor.resultType.toString() shouldBe "com.squareup.test.Bound"
        }
    }

  @TestFactory
  fun `a provider factory is always generated for a contributed object binding - android`() =
    kaseMatrix.versions(GradleKotlinAgpTestVersions)
      .withFactoryGen { _, factoryGen ->

        rootProject {

          buildFile {

            plugins {
              id("com.android.library")
              kotlin("android")
              id("com.squareup.anvil")
              pluginsExtras(factoryGen)
            }

            raw(
              """
              kotlin {
                jvmToolchain(11)
              }
              """.trimIndent(),
            )

            androidBlock("com.squareup.test")

            anvilBlock(factoryGen)

            dependenciesBlock(libs, factoryGen)
          }

          dir("src/main/java") {
            kotlinFile(
              "com/squareup/test/Bound.kt",
              """
              package com.squareup.test
              
              import com.squareup.anvil.annotations.ContributesBinding
  
              interface Bound
  
              @ContributesBinding(Unit::class)
              object Contributed : Bound
              """,
            )
          }

          // AGP 7.4.2 has memory problems with the default 512MB
          gradlePropertiesFile("org.gradle.jvmargs=-Xmx1g")
        }

        shouldSucceed("assembleDebug")

        val classGraph = rootProject.classGraphResult()

        val moduleClass = classGraph.allModuleClasses()
          .shouldBeSingleton().single()

        moduleClass.allProvidesMethods().names() shouldBe listOf("provideBound")

        classGraph.allDaggerFactoryClasses()
          .shouldBeSingleton().single()
          .asClue { factory ->

            factory.packageName shouldBe "com.squareup.test"

            factory.name shouldBe "${moduleClass.name}_ProvideBoundFactory"

            factory.typeDescriptor.superinterfaceSignatures
              .map { it.fullyQualifiedClassName } shouldBe listOf("dagger.internal.Factory")

            factory.getMethodInfo("provideBound")
              .single()
              .typeSignatureOrTypeDescriptor.resultType.toString() shouldBe "com.squareup.test.Bound"
          }
      }

  private fun PluginsSpec.pluginsExtras(factoryGen: FactoryGen) {
    if (factoryGen.addKsp) {
      id("com.google.devtools.ksp")
    }
    if (factoryGen.addKapt) {
      kotlin("kapt")
    }
  }

  private fun BuildFileSpec.anvilBlock(factoryGen: FactoryGen) {
    anvil {
      generateDaggerFactories.set(factoryGen.genFactories)

      if (factoryGen.addKsp) {
        useKsp(
          contributesAndFactoryGeneration = true,
          componentMerging = false,
        )
      }
    }
  }

  private fun HasDependenciesBlock<*>.dependenciesBlock(libs: Libs, factoryGen: FactoryGen) {
    dependencies {
      api(libs.dagger2.annotations)
      compileOnly(libs.inject)

      if (factoryGen.daggerCompiler) {
        when {
          factoryGen.addKapt -> {
            kapt(libs.dagger2.compiler)
          }
          factoryGen.addKsp -> {
            ksp(libs.dagger2.compiler)
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
    val addKsp: Boolean,
    val addKapt: Boolean,
    val daggerCompiler: Boolean,
  ) {
    override fun toString(): String = buildString {
      if (addKsp) {
        append("KSP plugin | ")
      }
      if (addKapt) {
        append("KAPT plugin | ")
      }
      if (!addKsp && !addKapt) {
        append("Anvil only ")
      }
      if (daggerCompiler) {
        append("with Dagger compiler | ")
      } else {
        append("no Dagger compiler | ")
      }
      append("genFactories: $genFactories")
    }
  }

  private inline fun <K : GradleKotlinTestVersions> List<K>.withFactoryGen(
    crossinline testAction: suspend AnvilGradleTestEnvironment.(
      versions: K,
      factoryGen: FactoryGen,
    ) -> Unit,
  ): Stream<out DynamicNode> = asContainers { versions ->
    cartesianProduct(
      listOf(true, false),
      listOf(false),
      listOf(true, false),
      listOf(true, false),
    )
      .map { (genFactories, addKsp, addKapt, daggerCompiler) ->
        FactoryGen(
          genFactories = genFactories,
          addKsp = addKsp,
          addKapt = addKapt,
          daggerCompiler = daggerCompiler,
        )
      }
      .filter {
        when {
          // We can't add a Dagger compiler dependency without a plugin to add it to
          it.daggerCompiler -> (it.addKsp || it.addKapt) && !it.genFactories
          else -> true
        }
      }
      .asTests(
        testEnvironmentFactory = AnvilGradleTestEnvironment.Factory().wrap(versions),
        testAction = { factoryGen -> testAction(versions, factoryGen) },
      )
  }
}
