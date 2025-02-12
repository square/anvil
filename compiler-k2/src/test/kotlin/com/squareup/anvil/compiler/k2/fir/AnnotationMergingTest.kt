package com.squareup.anvil.compiler.k2.fir

import com.rickbusarow.kase.stdlib.div
import com.squareup.anvil.compiler.testing.CommonNames
import com.squareup.anvil.compiler.testing.CompilationMode
import com.squareup.anvil.compiler.testing.CompilationModeTest
import com.squareup.anvil.compiler.testing.child
import com.squareup.anvil.compiler.testing.classgraph.allMergedModulesForComponent
import com.squareup.anvil.compiler.testing.classgraph.fqNames
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.TestFactory

class AnnotationMergingTest : CompilationModeTest(
  // CompilationMode.K2(useKapt = false),
  CompilationMode.K2(useKapt = true),
) {

  @TestFactory
  fun `contributed modules from dependency compilations are merged`() = testFactory {

    val dep = compile2(
      """
        package com.squareup.test.dep

        import com.squareup.anvil.annotations.ContributesTo
        import dagger.Module
        import javax.inject.Inject

        class InjectClass @Inject constructor()

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

        fun referenceToOtherCompilation(module: com.squareup.test.dep.DaggerModule1) { }
      """.trimIndent(),
      configuration = {
        it.copy(
          compilationClasspath = it.compilationClasspath
            .plus(dep.jar)
            .plus(dep.classFilesDir),
        )
      },
      workingDir = workingDir / "consumer",
    ) {

      classGraph
        .allMergedModulesForComponent(CommonNames.componentInterface)
        .fqNames() shouldBe listOf(
        // CommonNames.daggerModule1,
        CommonNames.squareupTest.child("dep").child("DaggerModule1"),
        CommonNames.daggerModule2,
      )
    }
  }
}
