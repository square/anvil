package com.squareup.anvil.compiler.k2.fir

import com.rickbusarow.kase.stdlib.div
import com.squareup.anvil.compiler.testing.CompilationModeTest
import com.squareup.anvil.compiler.testing.TestNames
import com.squareup.anvil.compiler.testing.classgraph.allMergedModulesForComponent
import com.squareup.anvil.compiler.testing.classgraph.componentInterface
import com.squareup.anvil.compiler.testing.classgraph.fqNames
import io.kotest.matchers.shouldBe
import org.jetbrains.kotlin.name.FqName
import org.junit.jupiter.api.TestFactory

class AnnotationMergingTest : CompilationModeTest(MODE_DEFAULTS.filter { it.isK2 && !it.useKapt }) {

  @TestFactory
  fun `contributed modules from dependency compilations are merged`() = testFactory {

    val dep1 = compile2(
      """
        package com.squareup.test.dep1 

        @dagger.Module
        @com.squareup.anvil.annotations.ContributesTo(Unit::class)
        interface DependencyModule1
      """.trimIndent(),
      """
        package anvil.hint.dep1

        val myHint = Unit

        fun things() {
          println("hello world")
        }

        interface Dep1HintInterface
      """.trimIndent(),
      workingDir = workingDir / "dep1",
    )

    val dep2 = compile2(
      """
        package com.squareup.test.dep2

        interface Dep2A

        @com.squareup.anvil.annotations.ContributesBinding(Unit::class)
        class Dep2AImpl @javax.inject.Inject constructor() : Dep2A 

        @dagger.Module
        @com.squareup.anvil.annotations.ContributesTo(Unit::class)
        interface DependencyModule2
      """.trimIndent(),
      workingDir = workingDir / "dep2",
    )

    compile2(
      """
        package com.squareup.test

        fun foo(d2a: com.squareup.test.dep2.Dep2A) {}

        @dagger.Module
        @com.squareup.anvil.annotations.ContributesTo(Unit::class)
        interface LocalProjectModule

        interface LocalA

        @com.squareup.anvil.annotations.ContributesBinding(Unit::class)
        class LocalAImpl @javax.inject.Inject constructor() : LocalA

        @com.squareup.anvil.annotations.MergeComponent(Unit::class)
        interface ComponentInterface
      """.trimIndent(),
      configuration = {
        it.copy(
          compilationClasspath = it.compilationClasspath
            .plus(dep1.jar)
            .plus(dep2.jar),
        )
      },
      workingDir = workingDir / "consumer",
    ) {

      val hintPackage = scanResult.getPackageInfo("anvil.hint")

      // hintPackage.classInfo.names shouldBe listOf(
      //   "anvil.hint.AnvilModuleHints_7395f7a5",
      //   "anvil.hint.AnvilModuleHints_b9fd254f",
      //   "anvil.hint.AnvilModuleHints_c8e109e7",
      //   "anvil.hint.AnvilModuleHints_db380a0a",
      //   "anvil.hint.AnvilModuleHints_ee672a89",
      // )

      scanResult
        .allMergedModulesForComponent(TestNames.componentInterface.asFqNameString())
        .fqNames() shouldBe listOf(
        "com.squareup.test.LocalAImpl_BindingModule",
        "com.squareup.test.LocalProjectModule",
        "com.squareup.test.dep1.DependencyModule1",
        "com.squareup.test.dep2.Dep2AImpl_BindingModule",
        "com.squareup.test.dep2.DependencyModule2",
      ).map(::FqName)
    }
  }

  @TestFactory
  fun `contributed supertypes from dependency compilations are merged`() = testFactory {

    val dep1 = compile2(
      """
        package com.squareup.test.dep1 

        @com.squareup.anvil.annotations.ContributesTo(Unit::class)
        interface DependencySuper1
      """.trimIndent(),
      workingDir = workingDir / "dep1",
    )

    val dep2 = compile2(
      """
        package com.squareup.test.dep2

        @com.squareup.anvil.annotations.ContributesTo(Unit::class)
        interface DependencySuper2
      """.trimIndent(),
      workingDir = workingDir / "dep2",
    )

    compile2(
      """
        package com.squareup.test

        @com.squareup.anvil.annotations.ContributesTo(Unit::class)
        interface LocalSuper

        @com.squareup.anvil.annotations.MergeComponent(Unit::class)
        interface ComponentInterface
      """.trimIndent(),
      configuration = {
        it.copy(
          compilationClasspath = it.compilationClasspath
            .plus(dep1.jar)
            .plus(dep2.jar),
        )
      },
      workingDir = workingDir / "consumer",
    ) {

      scanResult.componentInterface.interfaces.names.sorted() shouldBe listOf(
        "com.squareup.test.LocalSuper",
        "com.squareup.test.dep1.DependencySuper1",
        "com.squareup.test.dep2.DependencySuper2",
      )
    }
  }
}
