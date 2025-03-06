package com.squareup.anvil.compiler.k2.fir

import com.rickbusarow.kase.stdlib.div
import com.squareup.anvil.compiler.testing.CompilationModeTest
import com.squareup.anvil.compiler.testing.TestNames
import com.squareup.anvil.compiler.testing.classgraph.allMergedModulesForComponent
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

        import com.squareup.anvil.annotations.MergeComponent
        import dagger.Binds
        import dagger.Component
        import dagger.Subcomponent
        import javax.inject.Inject

        @dagger.Module
        @com.squareup.anvil.annotations.ContributesTo(Unit::class)
        interface LocalProjectModule

        interface LocalA

        @com.squareup.anvil.annotations.ContributesBinding(Unit::class)
        class LocalAImpl @javax.inject.Inject constructor() : LocalA

        @MergeComponent(Unit::class)
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

      scanResult
        .allMergedModulesForComponent(TestNames.componentInterface.asFqNameString())
        .fqNames() shouldBe listOf(
        "com.squareup.test.dep1.DependencyModule1",
        "com.squareup.test.dep2.DependencyModule2",
        "com.squareup.test.dep2.Dep2AImpl_BindingModule",
        "com.squareup.test.LocalProjectModule",
        "com.squareup.test.LocalAImpl_BindingModule",
      ).map(::FqName)
    }
  }
}
