package com.squareup.anvil.compiler.testing

import com.rickbusarow.kase.DefaultTestEnvironment
import com.rickbusarow.kase.Kase4
import com.rickbusarow.kase.KaseTestFactory
import com.rickbusarow.kase.ParamTestEnvironmentFactory
import com.rickbusarow.kase.files.HasWorkingDir
import com.rickbusarow.kase.files.TestLocation
import com.rickbusarow.kase.kase
import com.rickbusarow.kase.kases
import com.squareup.anvil.compiler.testing.classgraph.ClassGraphAsserts
import com.squareup.anvil.compiler.testing.classgraph.ClassInfoAsserts
import com.squareup.anvil.compiler.testing.reflect.ClassAsserts
import org.jetbrains.kotlin.config.LanguageVersion

public class CompilationModeTestEnvironment(
  override val mode: CompilationMode,
  hasWorkingDir: HasWorkingDir,
) : DefaultTestEnvironment(hasWorkingDir),
  CompilationEnvironment {

  public companion object : ParamTestEnvironmentFactory<
    CompilationMode,
    CompilationModeTestEnvironment,
    > {
    override fun create(
      params: CompilationMode,
      names: List<String>,
      location: TestLocation,
    ): CompilationModeTestEnvironment = CompilationModeTestEnvironment(
      mode = params,
      hasWorkingDir = HasWorkingDir(names, location),
    )
  }
}

public data class CompilationMode(
  public val languageVersion: LanguageVersion,
  public val useKapt: Boolean,
  public val generateDaggerFactories: Boolean = true,
  public val mergeComponents: Boolean = true,
) : Kase4<LanguageVersion, Boolean, Boolean, Boolean> by kase(
  a1 = languageVersion,
  a2 = useKapt,
  a3 = generateDaggerFactories,
  a4 = mergeComponents,
  displayNameFactory =
  { "languageVersion: ${a1.versionString} | kapt: $a2 | factory gen: $a3 | merging: $a4" },
) {
  val isK2: Boolean get() = languageVersion.usesK2
}

public abstract class CompilationModeTest(
  modes: List<CompilationMode> = MODE_DEFAULTS,
) : KaseTestFactory<
  CompilationMode,
  CompilationModeTestEnvironment,
  ParamTestEnvironmentFactory<CompilationMode, CompilationModeTestEnvironment>,
  >,
  ClassGraphAsserts,
  ClassInfoAsserts,
  ClassAsserts,
  MoreAsserts {

  public fun test(
    mode: CompilationMode,
    names: List<String> = emptyList(),
    testLocation: TestLocation = TestLocation.get(),
    testAction: suspend CompilationModeTestEnvironment.() -> Unit,
  ) {
    test(
      param = mode,
      testEnvironmentFactory = testEnvironmentFactory,
      names = names,
      testLocation = testLocation,
      testAction = testAction,
    )
  }

  public constructor(mode: CompilationMode, vararg additionalModes: CompilationMode) : this(
    listOf(mode, *additionalModes),
  )

  override val params: List<CompilationMode> = modes

  override val testEnvironmentFactory:
    ParamTestEnvironmentFactory<CompilationMode, CompilationModeTestEnvironment> =
    CompilationModeTestEnvironment

  public companion object {

    public val MODE_DEFAULTS: List<CompilationMode> = kases(
      listOf(
        LanguageVersion.KOTLIN_1_9,
        LanguageVersion.KOTLIN_2_0,
        LanguageVersion.KOTLIN_2_1,
      ),
      listOf(true, false),
    ).map { (languageVersion, useKapt) ->
      CompilationMode(
        languageVersion = languageVersion,
        useKapt = useKapt,
        generateDaggerFactories = true,
        mergeComponents = true,
      )
    }
  }
}
