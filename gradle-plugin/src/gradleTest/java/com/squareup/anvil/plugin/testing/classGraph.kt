package com.squareup.anvil.plugin.testing

import com.rickbusarow.kase.gradle.GradleProjectBuilder
import dagger.Component
import io.github.classgraph.ClassGraph
import io.github.classgraph.ClassInfo
import io.github.classgraph.HasName
import io.github.classgraph.MethodInfo
import io.github.classgraph.ScanResult
import java.io.File

/** A class that's annotated with `@Module` */
typealias ModuleClassInfo = ClassInfo

/** A class that implements `dagger.internal.Factory` */
typealias DaggerFactoryClassInfo = ClassInfo

/** A method that's annotated with `@Binds` */
typealias BindsMethodInfo = MethodInfo

/** A method that's annotated with `@Provides` */
typealias ProvidesMethodInfo = MethodInfo

typealias ReturnTypeName = String
typealias ParameterTypeName = String

/**
 * Returns all classes that are annotated with `@Module` in this scan result.
 * The classes are sorted by their name.
 *
 * ```
 * @TestFactory
 * fun `my test`() = testFactory {
 *   // ...
 *
 *   shouldSucceed("jar") // note the "jar" task
 *
 *   rootProject.classGraphResult()
 *     .allModuleClasses()
 *     .names() shouldBe listOf( /* ... */ )
 * }
 * ```
 */
fun ScanResult.allModuleClasses(): List<ModuleClassInfo> {
  return allClasses
    .filter { it.hasAnnotation("dagger.Module") }
    .sortedBy { it.name }
}

/**
 * Returns all classes that implement `dagger.internal.Factory` in this scan result.
 * The classes are sorted by their name.
 *
 * ```
 * @TestFactory
 * fun `my test`() = testFactory {
 *   // ...
 *
 *   shouldSucceed("jar") // note the "jar" task
 *
 *   rootProject.classGraphResult()
 *     .allDaggerFactoryClasses()
 *     .names() shouldBe listOf( /* ... */ )
 * }
 * ```
 */
fun ScanResult.allDaggerFactoryClasses(): List<DaggerFactoryClassInfo> {
  return allClasses
    .filter { it.implementsInterface("dagger.internal.Factory") }
    .sortedBy { it.name }
}

/**
 * Returns all classes that are arguments to the [modules][dagger.Component.modules] parameter
 * of a specific [@Component][dagger.Component] annotation applied to [componentInterfaceName].
 *
 * ```
 * @TestFactory
 * fun `my test`() = testFactory {
 *   // ...
 *
 *   shouldSucceed("jar") // note the "jar" task
 *
 *   rootProject.classGraphResult()
 *     .allMergedModulesForComponent("com.squareup.test.AppComponent")
 *     .names() shouldBe listOf(
 *       "com.squareup.test.ModuleA",
 *       "com.squareup.test.ModuleB",
 *     )
 * }
 * ```
 */
fun ScanResult.allMergedModulesForComponent(componentInterfaceName: String): List<ModuleClassInfo> {
  val componentClass = loadClass(componentInterfaceName, false)
  val annotation = componentClass.getAnnotation(Component::class.java)

  return annotation.modules.map { getClassInfo(it.java.name) as ModuleClassInfo }
}

/**
 * Returns all methods inside `@Module` types that are annotated with `@Binds` in this scan result.
 * The methods are sorted by their name.
 *
 * ```
 * @TestFactory
 * fun `my test`() = testFactory {
 *   // ...
 *
 *   shouldSucceed("jar") // note the "jar" task
 *
 *   rootProject.classGraphResult()
 *     .allBindsMethods()
 *     .names() shouldBe listOf( /* ... */ )
 * }
 * ```
 */
fun ScanResult.allBindsMethods(): List<BindsMethodInfo> {
  return allModuleClasses()
    .flatMap { it.allBindMethods() }
    .sortedBy { it.name }
}

/**
 * Returns pairs of types bound in `@Binds` methods,
 * where the first type is the parameter/contributed type
 * and the second type is the return/bound type.
 * The pairs are sorted by the parameter type.
 *
 * ```
 * @TestFactory
 * fun `my test`() = testFactory {
 *   // ...
 *
 *   shouldSucceed("jar") // note the "jar" task
 *
 *   rootProject.classGraphResult()
 *     .allBoundTypes() shouldBe listOf("com.example.FooImpl" to "com.example.Foo")
 * }
 * ```
 */
fun ScanResult.allBoundTypes(): List<Pair<ParameterTypeName, ReturnTypeName>> {
  return allModuleClasses()
    .flatMap { it.allBindMethods() }
    .map { it.boundTypes() }
    .sortedBy { it.first }
}

/**
 * Returns all binding methods declared in a particular `@Module` type.
 * The methods are sorted by their name.
 */
fun ModuleClassInfo.allBindMethods(): List<BindsMethodInfo> {
  return methodInfo.filter { it.hasAnnotation("dagger.Binds") }
}

/**
 * Returns all `provide___` methods declared in a particular `@Module` type.
 * The methods are sorted by their name.
 */
fun ModuleClassInfo.allProvidesMethods(): List<ProvidesMethodInfo> {
  return methodInfo.filter { it.hasAnnotation("dagger.Provides") }
}

fun BindsMethodInfo.boundTypes(): Pair<ParameterTypeName, ReturnTypeName> {
  val returnType = typeSignatureOrTypeDescriptor.resultType.toString()
  val parameterType = parameterInfo.single().typeSignatureOrTypeDescriptor.toString()
  return parameterType to returnType
}

fun Collection<HasName>.names(): List<String> = map { it.name }

fun GradleProjectBuilder.requireJarArtifact(buildDir: File = path.resolve("build")): File {

  fun Sequence<File>.requireSingle(libs: File): File {
    val size = count()
    require(size == 1) {
      """
      |Expected 1 jar file, but found $size in $libs:
      |  ${joinToString("\n  ")}
      |
      """.trimMargin()
    }
    return single()
  }

  // Android libs
  val intermediates = buildDir.resolve("intermediates/aar_main_jar/debug")
  val classesJar = intermediates
    .walkBottomUp()
    .filter { it.isFile && it.extension == "jar" }

  if (classesJar.any()) {
    return classesJar.requireSingle(intermediates)
  }

  // Java/Kotlin libs
  val libs = buildDir.resolve("libs")
  return libs.walkBottomUp()
    .filter { file ->
      when {
        !file.isFile -> false
        file.extension != "jar" -> false
        // We only care about the main jar file, not sources or javadoc jars.
        file.nameWithoutExtension.matches(".*?-(?:sources|javadoc)".toRegex()) -> false
        else -> true
      }
    }.requireSingle(libs)
}

/**
 * Fetches the generated `.jar` artifact for this project and optional dependencies,
 * then creates a ClassGraph scan result for inspection.
 *
 * ```
 * @TestFactory
 * fun `my test`() = testFactory {
 *   rootProject {
 *     gradleProject("a") { /* ... */ }
 *     gradleProject("b") {
 *       buildFile {
 *          // ...
 *          dependencies {
 *            implementation(project(":a"))
 *          }
 *       }
 *
 *       /* ... */
 *     }
 *   }
 *
 *   shouldSucceed(":b:jar")
 *
 *   val a by rootProject.subprojects
 *   val b by rootProject.subprojects
 *
 *   val scanResult = b.classGraphResult(a)
 * }
 * ```
 */
fun GradleProjectBuilder.classGraphResult(
  vararg dependencyProjects: GradleProjectBuilder,
  buildDir: File = path.resolve("build"),
): ScanResult {
  val jars = buildList {
    add(requireJarArtifact(buildDir = buildDir))
    addAll(dependencyProjects.map { it.requireJarArtifact() })
  }
  return ClassGraph().enableAllInfo()
    .overrideClasspath(jars)
    .scan()
}
