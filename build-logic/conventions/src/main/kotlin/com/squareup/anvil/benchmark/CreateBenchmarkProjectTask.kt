package com.squareup.anvil.benchmark

import com.squareup.anvil.benchmark.Module.AppModule
import com.squareup.anvil.benchmark.Module.LibraryModule
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier.ABSTRACT
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.asClassName
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import java.io.File

private const val MODULE_COUNT = 20

/**
 * Generates 1000 library modules and 1 application module to benchmark Anvil. The first 10
 * generated library modules are special, because they define the interfaces that are bound by
 * other implementation classes in the other library modules. These 10 library modules also
 * contribute one subcomponent each.
 *
 * All library modules contribute a binding and Dagger module. The contributed binding replaces
 * the contributed binding from the module with the index - 10 to avoid duplicate bindings. The
 * Dagger module has one provider method for another interface. We replace all Dagger modules
 * except for the last 10 in the application module to avoid duplicate bindings.
 *
 * The app module contains 10 Dagger components with the same scope and runs the Dagger
 * annotation processor with KAPT. Each of the Dagger components merges the 10 contributed
 * subcomponents, so in total we have 100 subcomponents. For each of them + the 10 components
 * we run Anvil's slowest operations of merging all contributions.
 *
 * All generated modules are included in the main Gradle project for Anvil. This way we get
 * Gradle's dependency substitution feature for free and we always rebuild Anvil from source.
 *
 * To generate the project and run the benchmarks run:
 * ```
 * > ./gradlew :createBenchmarkProject
 * > gradle-profiler --benchmark --measure-config-time --scenario-file benchmark/benchmark.scenarios
 * ```
 *
 * There are two benchmark scenarios. The first only downloads all dependencies, because the second
 * runs in offline mode. In the real benchmark we only exercise the Kotlin compilation task of the
 * app module and clean the output of this task alone between iterations. This gives almost the
 * time Anvil needs (the Kotlin compiler does other things too, but Anvil is certainly the longest
 * running operation during compilation in this module).
 */
open class CreateBenchmarkProjectTask : DefaultTask() {

  private val rootDir: File get() = File(project.rootDir, "benchmark")

  @TaskAction fun createProject() {
    val libraryModules = List(MODULE_COUNT) { index ->
      val name = "lib$index"
      val path = ":$name"

      LibraryModule(
        name = name,
        path = path,
        index = index,
      )
    }.associateBy { it.index }

    val appModule = AppModule(
      name = "app",
      path = ":${rootDir.name}:app",
    )

    createSettingsGradleFile(libraryModules.values + appModule)
    createGradleDirectory()
    createGradlePropertiesFile()
    createScenariosFile()
    createAppModule(appModule, libraryModules)

    libraryModules.values.forEach {
      createLibraryModule(it, libraryModules)
    }
  }

  private fun createSettingsGradleFile(modules: Collection<Module>) {
    val content = buildString {

      appendLine(
        """
        pluginManagement {
          repositories {
            gradlePluginPortal()
            mavenCentral()
          }
          
          includeBuild '../build-logic/settings'
        }
     
        plugins {
          id 'com.squareup.anvil.gradle-settings'
        }
        
        includeBuild '..'
        
        """.trimIndent(),
      )
      modules.sortedBy { it.path }
        .forEach {
          appendLine("include '${it.name}'")
        }
    }

    File(rootDir, "settings.gradle").writeTextSafely(content)
  }

  // The benchmark build is invoked as its own top-level build,
  // from the 'benchmark' directory, without involving the 'anvil' build.
  // We copy the 'gradle' directory from the 'anvil' build so
  // that the scenarios use the same Gradle version and library catalog.
  private fun createGradleDirectory() {
    rootDir.parentFile.resolve("gradle")
      .copyRecursively(rootDir.resolve("gradle"), overwrite = true)
  }

  private fun createGradlePropertiesFile() {
    rootDir.parentFile.resolve("gradle.properties")
      .copyTo(rootDir.resolve("gradle.properties"), overwrite = true)
  }

  private fun createScenariosFile() {
    val content = """
      # Prefix with aa_ to run this scenario first. The Gradle Profiler uses an alphabetical sort
      # for all scenarios. 
      aa_fill_cache {
        tasks = [":app:assemble"]
        warm-ups = 1
        iterations = 0
      }
      
      anvil_benchmark {
        tasks = [":app:compileKotlin"]
        cleanup-tasks = [":app:cleanCompileKotlin"]

        warm-ups = 6
        iterations = 10
        
        gradle-args = ["--offline", "--no-build-cache"]
      }
    """.trimIndent()

    File(rootDir, "benchmark.scenarios").writeTextSafely(content)
  }

  private fun createLibraryModule(
    module: LibraryModule,
    allLibraryModules: Map<Int, LibraryModule>,
  ) {
    val moduleDir = File(rootDir, module.name)
    createLibraryBuildGradleFile(module, allLibraryModules, moduleDir)
    createLibraryModuleSourceFiles(module, moduleDir)
  }

  private fun createLibraryBuildGradleFile(
    module: LibraryModule,
    allLibraryModules: Map<Int, LibraryModule>,
    moduleDir: File,
  ) {
    val libDependency = allLibraryModules[module.index - 10]
      ?.let {
        """
          |dependencies {
          |  api project('${it.path}')
          |}
        """
      }

    val content = """
      |plugins {
      |  alias libs.plugins.kotlin.jvm
      |  id 'com.squareup.anvil'
      |}
      |
      |anvil {
      |  generateDaggerFactories = true
      |}
      |
      |dependencies {
      |  api libs.dagger2
      |}
      ${libDependency ?: "|"}
    """.trimMargin()

    File(moduleDir, "build.gradle").writeTextSafely(content)
  }

  private fun createLibraryModuleSourceFiles(
    module: LibraryModule,
    moduleDir: File,
  ) {
    val index = module.index
    val group = index % 10
    val isFirstTenModule = index / 10 == 0
    val packageBase = "com.squareup.anvil.benchmark"

    val scopeComponent = Unit::class.asClassName()
    val scopeSubcomponent = Any::class.asClassName()
    val boundInterface = ClassName("$packageBase$group", "BoundInterface$group")
    val injectedInterface = ClassName("$packageBase$group", "InjectedInterface$group")

    val injectedInterfaceImpl = ClassName("$packageBase$index", "InjectedInterfaceImpl$index")

    val contributesBinding = ClassName("com.squareup.anvil.annotations", "ContributesBinding")
    val contributesSubcomponent =
      ClassName("com.squareup.anvil.annotations", "ContributesSubcomponent")
    val contributesTo = ClassName("com.squareup.anvil.annotations", "ContributesTo")
    val injectAnnotation = ClassName("javax.inject", "Inject")
    val moduleAnnotation = ClassName("dagger", "Module")
    val providesAnnotation = ClassName("dagger", "Provides")

    val content = FileSpec.builder("$packageBase$index", "Source.kt")
      .apply {
        if (isFirstTenModule) {
          addType(TypeSpec.interfaceBuilder(boundInterface).build())
          addType(
            TypeSpec
              .interfaceBuilder("ContributedInterfaceComponent$index")
              .addAnnotation(
                AnnotationSpec
                  .builder(contributesTo)
                  .addMember("scope = %T::class", scopeComponent)
                  .build(),
              )
              .addFunction(
                FunSpec
                  .builder("boundInterface$index")
                  .addModifiers(ABSTRACT)
                  .returns(boundInterface)
                  .build(),
              )
              .build(),
          )

          addType(TypeSpec.interfaceBuilder(injectedInterface).build())
          addType(
            TypeSpec
              .interfaceBuilder("ContributedSubcomponent$index")
              .addAnnotation(
                AnnotationSpec
                  .builder(contributesSubcomponent)
                  .addMember("scope = %T::class", scopeSubcomponent)
                  .addMember("parentScope = %T::class", scopeComponent)
                  .build(),
              )
              .build(),
          )
          addType(
            TypeSpec
              .interfaceBuilder("ContributedInterfaceSubcomponent$index")
              .addAnnotation(
                AnnotationSpec
                  .builder(contributesTo)
                  .addMember("scope = %T::class", scopeSubcomponent)
                  .build(),
              )
              .addFunction(
                FunSpec
                  .builder("injectedInterface$index")
                  .addModifiers(ABSTRACT)
                  .returns(injectedInterface)
                  .build(),
              )
              .build(),
          )
        }
      }
      .addType(
        TypeSpec
          .objectBuilder("BoundInterfaceImpl$index")
          .addAnnotation(
            AnnotationSpec
              .builder(contributesBinding)
              .addMember("scope = %T::class", scopeComponent)
              .apply {
                if (!isFirstTenModule) {
                  val count = index / 10
                  val template = (1..count).joinToString(separator = ", ") { "%T::class" }

                  val replacedImpls = (0 until count).map {
                    val replacedIndex = it * 10 + group
                    ClassName("$packageBase$replacedIndex", "BoundInterfaceImpl$replacedIndex")
                  }
                  addMember("replaces = [$template]", *replacedImpls.toTypedArray())
                }
              }
              .build(),
          )
          .addSuperinterface(boundInterface)
          .build(),
      )
      .addType(
        TypeSpec
          .classBuilder(injectedInterfaceImpl)
          .addSuperinterface(injectedInterface)
          .primaryConstructor(
            FunSpec.constructorBuilder()
              .addAnnotation(injectAnnotation)
              .addParameter(
                ParameterSpec
                  .builder("boundInterface", boundInterface)
                  .addAnnotation(
                    AnnotationSpec
                      .builder(Suppress::class)
                      .addMember("\"UNUSED_PARAMETER\"")
                      .build(),
                  )
                  .build(),
              )
              .build(),
          )
          .build(),
      )
      .addType(
        TypeSpec
          .objectBuilder("DaggerModule$index")
          .addAnnotation(moduleAnnotation)
          .addAnnotation(
            AnnotationSpec
              .builder(contributesTo)
              .addMember("scope = %T::class", scopeSubcomponent)
              .build(),
          )
          .addFunction(
            FunSpec
              .builder("provideInjectedInterface")
              .addAnnotation(providesAnnotation)
              .addParameter("injectedInterfaceImpl", injectedInterfaceImpl)
              .addStatement("return injectedInterfaceImpl")
              .returns(injectedInterface)
              .build(),
          )
          .build(),
      )
      .build()
      .let { fileSpec ->
        StringBuilder().also { fileSpec.writeTo(it) }.toString()
      }

    val dir = File(moduleDir, "src/main/kotlin/${packageBase.replace('.', '/')}$index")
    File(dir, "Source.kt").writeTextSafely(content)
  }

  private fun createAppModule(
    module: AppModule,
    allLibraryModules: Map<Int, LibraryModule>,
  ) {
    val moduleDir = File(rootDir, module.name)
    createAppBuildGradleFile(allLibraryModules, moduleDir)
    createAppModuleSourceFiles(allLibraryModules, moduleDir)
  }

  private fun createAppBuildGradleFile(
    allLibraryModules: Map<Int, LibraryModule>,
    moduleDir: File,
  ) {
    val libDependencies = allLibraryModules.keys.sorted()
      .let { it.subList(it.size - 10, it.size) }
      .map { allLibraryModules.getValue(it) }
      .joinToString(separator = "\n") { "|  api project('${it.path}')" }

    val content = """
      |plugins {
      |  alias libs.plugins.kotlin.jvm
      |  alias libs.plugins.kotlin.kapt
      |  id 'com.squareup.anvil'
      |}
      |
      |kapt {
      |  // Explicitly use Java 8 for benchmarks until we raise our min target and/or add Java 17
      |  // support. This is needed because our benchmarks machine now primarily uses JDK 17 and 
      |  // auto-service/auto-value picks it up and uses a different @Generated annotation than
      |  // we're expecting (due to it being moved to a new package). Related (old) ticket:
      |  // https://github.com/google/dagger/issues/1449
      |  javacOptions {
      |    option("-source", "8")
      |    option("-target", "8")
      |  }
      |}
      |
      |dependencies {
      |  api libs.dagger2
      |  kapt libs.dagger2.compiler
      $libDependencies 
      |}
    """.trimMargin()

    File(moduleDir, "build.gradle").writeTextSafely(content)
  }

  private fun createAppModuleSourceFiles(
    allLibraryModules: Map<Int, LibraryModule>,
    moduleDir: File,
  ) {
    val packageName = "com.squareup.anvil.benchmark.app"

    val scopeComponent = Unit::class.asClassName()
    val scopeSubcomponent = Any::class.asClassName()

    val mergeComponent = ClassName("com.squareup.anvil.annotations", "MergeComponent")
    val contributesTo = ClassName("com.squareup.anvil.annotations", "ContributesTo")
    val moduleAnnotation = ClassName("dagger", "Module")

    val content = FileSpec.builder(packageName, "Source.kt")
      .apply {
        (1..10).forEach { componentIndex ->
          addType(
            TypeSpec
              .interfaceBuilder("Component$componentIndex")
              .addAnnotation(
                AnnotationSpec
                  .builder(mergeComponent)
                  .addMember("scope = %T::class", scopeComponent)
                  .build(),
              )
              .build(),
          )
        }
      }
      .addType(
        TypeSpec
          .objectBuilder("ReplacingDaggerModule")
          .addAnnotation(moduleAnnotation)
          .addAnnotation(
            AnnotationSpec
              .builder(contributesTo)
              .addMember("scope = %T::class", scopeSubcomponent)
              .apply {
                val range = 0 until (allLibraryModules.size - 10)
                val template = range.joinToString(separator = ", ") { "%T::class" }

                val replacedModules = range.map { index ->
                  ClassName("com.squareup.anvil.benchmark$index", "DaggerModule$index")
                }
                addMember("replaces = [$template]", *replacedModules.toTypedArray())
              }
              .build(),
          )
          .build(),
      )
      .build()
      .let { fileSpec ->
        StringBuilder().also { fileSpec.writeTo(it) }.toString()
      }

    val dir = File(moduleDir, "src/main/kotlin/${packageName.replace('.', '/')}")
    File(dir, "Source.kt").writeTextSafely(content)
  }

  private fun File.writeTextSafely(text: String) {
    check((parentFile.exists() && parentFile.isDirectory) || parentFile.mkdirs())
    check((exists() && isFile) || createNewFile())

    writeText(text)
  }
}
