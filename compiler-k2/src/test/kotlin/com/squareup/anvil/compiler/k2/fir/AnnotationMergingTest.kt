package com.squareup.anvil.compiler.k2.fir

import com.rickbusarow.kase.stdlib.div
import com.squareup.anvil.compiler.testing.CompilationMode
import com.squareup.anvil.compiler.testing.CompilationModeTest
import com.squareup.anvil.compiler.testing.TestNames
import com.squareup.anvil.compiler.testing.classgraph.allMergedModulesForComponent
import com.squareup.anvil.compiler.testing.classgraph.fqNames
import io.kotest.matchers.shouldBe
import org.jetbrains.kotlin.name.FqName
import org.junit.jupiter.api.TestFactory

class AnnotationMergingTest : CompilationModeTest(
  CompilationMode.K2(useKapt = false),
  // CompilationMode.K2(useKapt = true),
) {

  @TestFactory
  fun `contributed modules from dependency compilations are merged`() = testFactory {

    val dep1 = compile2(
      """
        package anvil.hint

        @javax.inject.Named("this.is.a.hint")
        private interface ContributedBindings_1
      """.trimIndent(),
      workingDir = workingDir / "dependency_1",
    )

    val dep2 = compile2(
      """
        package anvil.hint

        @javax.inject.Named("this.is.a.hint")
        private interface ContributedBindings_2
      """.trimIndent(),
      workingDir = workingDir / "dependency_2",
    )

    val dep = compile2(
      """
        package com.squareup.test.dep

        import com.squareup.anvil.annotations.ContributesTo
        import dagger.Module

        @Module
        @ContributesTo(Unit::class)
        interface DaggerModule1
      """.trimIndent(),
      workingDir = workingDir / "dependency",
    )

    compile2(
      """
        package com.squareup.test

        import com.squareup.anvil.annotations.MergeComponent
        import dagger.Binds
        import dagger.Component
        import dagger.Subcomponent
        import javax.inject.Inject

        @dagger.Module
        @com.squareup.anvil.annotations.ContributesTo(Unit::class)
        interface DaggerModule2

        @MergeComponent(Unit::class)
        interface ComponentInterface
      """.trimIndent(),
      configuration = {
        it.copy(
          compilationClasspath = it.compilationClasspath
            // .plus(dep.classFilesDir)
            .plus(dep1.classFilesDir)
            .plus(dep2.classFilesDir),
        )
      },
      workingDir = workingDir / "consumer",
    ) {

      scanResult
        .allMergedModulesForComponent(TestNames.componentInterface.asFqNameString())
        .fqNames() shouldBe listOf(
        // CommonNames.daggerModule1,
        FqName("com.squareup.test.dep.DaggerModule1"),
        TestNames.daggerModule2,
      )
    }
  }
}
