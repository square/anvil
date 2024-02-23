package com.squareup.anvil.plugin

import com.rickbusarow.kase.Kase1
import com.rickbusarow.kase.gradle.dsl.buildFile
import com.rickbusarow.kase.gradle.rootProject
import com.rickbusarow.kase.kases
import com.rickbusarow.kase.stdlib.createSafely
import com.rickbusarow.kase.stdlib.div
import com.rickbusarow.kase.wrap
import io.kotest.matchers.file.shouldExist
import io.kotest.matchers.file.shouldNotExist
import io.kotest.matchers.shouldBe
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.DynamicNode
import org.junit.jupiter.api.TestFactory
import java.io.File
import java.util.stream.Stream

class IncrementalTest : BaseGradleTest() {

  @TestFactory
  fun `compileKotlin should execute again if anvil-generated output is deleted`() = testFactory {

    rootProject {
      dir("src/main/java") {
        injectClass()
        kotlinFile(
          "com/squareup/test/OtherClass.kt",
          """
          package com.squareup.test
      
          import javax.inject.Inject
          
          class OtherClass @Inject constructor()
          """.trimIndent(),
        )
      }
      gradlePropertiesFile(
        """
        com.squareup.anvil.trackSourceFiles=true
        """.trimIndent(),
      )
    }

    shouldSucceed("compileJava")

    val injectClassFactory = rootAnvilMainGenerated.injectClassFactory

    injectClassFactory.shouldExist()
    injectClassFactory.delete()
    injectClassFactory.shouldNotExist()

    shouldSucceed("compileJava") {
      task(":compileKotlin")?.outcome shouldBe TaskOutcome.SUCCESS
    }

    injectClassFactory.shouldExist()
  }

  @TestFactory
  fun `a generated factory is deleted if the source file is deleted`() = testFactory {

    val injectClassPath = "com/squareup/test/InjectClass.kt"

    rootProject {
      dir("src/main/java") {

        injectClass()

        kotlinFile(
          "com.squareup/test/OtherClass.kt",
          """
          package com.squareup.test
      
          import javax.inject.Inject
          
          class OtherClass @Inject constructor()
          """.trimIndent(),
        )
      }
      gradlePropertiesFile(
        """
        com.squareup.anvil.trackSourceFiles=true
        """.trimIndent(),
      )
    }

    shouldSucceed("compileJava")
    val otherClassFactory = rootAnvilMainGenerated
      .resolve("com/squareup/test/OtherClass_Factory.kt")

    rootAnvilMainGenerated.injectClassFactory.shouldExist()
    otherClassFactory.shouldExist()

    workingDir.resolve("src/main/java")
      .resolve(injectClassPath)
      .delete()

    shouldSucceed("compileJava") {
      task(":compileKotlin")?.outcome shouldBe TaskOutcome.SUCCESS
    }

    rootAnvilMainGenerated.injectClassFactory.shouldNotExist()
    otherClassFactory.shouldExist()
  }

  @TestFactory
  fun `a generated factory is deleted if the @Inject annotation is removed`() =
    withTrackSourceFiles { (trackSourceFiles) ->

      lateinit var injectClassPath: File

      rootProject {
        dir("src/main/java") {

          injectClassPath = injectClass()
        }
        gradlePropertiesFile(
          """
          com.squareup.anvil.trackSourceFiles=$trackSourceFiles
          """.trimIndent(),
        )
      }

      shouldSucceed("compileJava")

      rootAnvilMainGenerated.injectClassFactory.shouldExist()

      injectClassPath.kotlin(
        """
        package com.squareup.test

        class InjectClass
        """.trimIndent(),
      )

      shouldSucceed("compileJava") {
        task(":compileKotlin")?.outcome shouldBe TaskOutcome.SUCCESS
      }

      rootAnvilMainGenerated.injectClassFactory.shouldNotExist()
    }

  @TestFactory
  fun `a generated factory is updated if the source class constructor is changed`() =
    testFactory {

      lateinit var injectClassPath: File

      rootProject {
        dir("src/main/java") {
          injectClassPath = injectClass()
        }
        gradlePropertiesFile(
          """
            com.squareup.anvil.trackSourceFiles=true
          """.trimIndent(),
        )
      }

      shouldSucceed("compileJava")

      // no constructor parameters
      rootAnvilMainGenerated.injectClassFactory shouldExistWithTextContaining """
        public object InjectClass_Factory : Factory<InjectClass>
      """.trimIndent()

      injectClassPath.kotlin(
        """
        package com.squareup.test

        import javax.inject.Inject

        class InjectClass @Inject constructor(val name: String)
        """.trimIndent(),
      )

      shouldSucceed("compileJava") {
        task(":compileKotlin")?.outcome shouldBe TaskOutcome.SUCCESS
      }

      // now we have constructor parameters
      rootAnvilMainGenerated.injectClassFactory shouldExistWithTextContaining """
        public class InjectClass_Factory(
          private val name: Provider<String>,
        ) : Factory<InjectClass>
      """.trimIndent()
    }

  @TestFactory
  fun `generated files are retained after an unrelated incremental change`() = testFactory {

    val otherClassPath = "com/squareup/test/OtherClass.kt"

    rootProject {
      dir("src/main/java") {

        kotlinFile(
          otherClassPath,
          """
          package com.squareup.test
          
          import javax.inject.Inject
          
          class OtherClass @Inject constructor()
          """.trimIndent(),
        )
        injectClass()
      }
      gradlePropertiesFile(
        """
        com.squareup.anvil.trackSourceFiles=true
        """.trimIndent(),
      )
    }

    shouldSucceed("compileJava")

    val otherClassFactory = rootAnvilMainGenerated
      .resolve("com/squareup/test/OtherClass_Factory.kt")

    rootAnvilMainGenerated.injectClassFactory.shouldExist()
    otherClassFactory.shouldExist()

    rootProject {
      dir("src/main/java") {
        kotlinFile(
          otherClassPath,
          """
           package com.squareup.test
     
           class OtherClass
          """.trimIndent(),
        )
      }
    }

    shouldSucceed("compileJava") {
      task(":compileKotlin")?.outcome shouldBe TaskOutcome.SUCCESS
    }

    rootAnvilMainGenerated.injectClassFactory.shouldExist()
    otherClassFactory.shouldNotExist()
  }

  @TestFactory
  fun `compilation re-runs when a @ContributesBinding type supertype changes`() = testFactory {

    val otherClassPath = "com/squareup/test/OtherClass.kt"

    rootProject {
      dir("src/main/java") {

        kotlinFile(
          otherClassPath,
          """
          package com.squareup.test

          import com.squareup.anvil.annotations.ContributesBinding
          import com.squareup.anvil.annotations.MergeComponent
          import javax.inject.Inject

          @ContributesBinding(Any::class)
          class OtherClass @Inject constructor() : TypeA

          interface TypeA
          interface TypeB

          class Consumer @Inject constructor(
            private val dep: TypeA
          )

          @MergeComponent(Any::class)
          interface AppComponent
          """.trimIndent(),
        )
        injectClass()
      }
      gradlePropertiesFile(
        """
        com.squareup.anvil.trackSourceFiles=true
        """.trimIndent(),
      )
    }

    shouldSucceed("compileJava")

    val otherClassFactory = rootAnvilMainGenerated
      .resolve("com/squareup/test/OtherClass_Factory.kt")
    val otherClassHint = rootAnvilMainGenerated
      .anvilHintBinding
      .resolve("com/squareup/test/OtherClass.kt")

    val componentModuleFile = rootAnvilMainGenerated
      .anvilModule
      .resolve("com/squareup/test/AppComponent.kt")

    rootAnvilMainGenerated.injectClassFactory.shouldExist()
    otherClassFactory.shouldExist()
    otherClassHint.shouldExist()

    componentModuleFile shouldExistWithTextContaining """
        @Module
        @ContributesTo(Any::class)
        public abstract class AppComponentAnvilModule {
          @Binds
          public abstract fun bindTypeA(otherClass: OtherClass): TypeA
        }
    """.trimIndent()

    rootProject.path
      .resolve("src/main/java")
      .resolve(otherClassPath)
      .kotlin(
        """
        package com.squareup.test
  
        import com.squareup.anvil.annotations.ContributesBinding
        import com.squareup.anvil.annotations.MergeComponent
        import javax.inject.Inject
  
        @ContributesBinding(Any::class)
        class OtherClass @Inject constructor() : TypeB
  
        interface TypeA
        interface TypeB
  
        class Consumer @Inject constructor(
          private val dep: TypeA
        )
  
        @MergeComponent(Any::class)
        interface AppComponent
        """.trimIndent(),
      )

    shouldSucceed("compileJava") {
      task(":compileKotlin")?.outcome shouldBe TaskOutcome.SUCCESS
    }

    rootAnvilMainGenerated.injectClassFactory.shouldExist()
    otherClassFactory.shouldExist()
    otherClassHint.shouldExist()

    componentModuleFile shouldExistWithTextContaining """
        @Module
        @ContributesTo(Any::class)
        public abstract class AppComponentAnvilModule {
          @Binds
          public abstract fun bindTypeB(otherClass: OtherClass): TypeB
        }
    """.trimIndent()
  }

  @TestFactory
  fun `compilation re-runs when a dependency module's @ContributesBinding type supertype changes`() =
    testFactory {

      val otherClassPath = rootProject.path.resolve("lib")
        .resolve("src/main/java")
        .resolve("com/squareup/test/lib/OtherClass.kt")

      fun otherClassContent(superType: String) =
        //language=kotlin
        """
          package com.squareup.test.lib

          import com.squareup.anvil.annotations.ContributesBinding
          import javax.inject.Inject

          @ContributesBinding(Any::class)
          class OtherClass @Inject constructor() : $superType

          interface TypeA
          interface TypeB

          class Consumer @Inject constructor(
            private val dep: $superType
          )
        """.trimIndent()

      rootProject {
        gradlePropertiesFile(
          """
          com.squareup.anvil.trackSourceFiles=true
          com.squareup.anvil.addOptionalAnnotations=true
          """.trimIndent(),
        )

        settingsFileAsFile.appendText(
          """

          include(":lib")
          include(":app")
          """.trimIndent(),
        )

        project("lib") {
          buildFile {
            plugins {
              kotlin("jvm")
              id("com.squareup.anvil")
            }
            anvil {
              generateDaggerFactories.set(true)
            }
            dependencies {
              implementation(libs.dagger2.annotations)
            }
          }
          otherClassPath.createSafely(otherClassContent("TypeA"))
        }

        project("app") {

          buildFile {
            plugins {
              kotlin("jvm")
              kotlin("kapt")
              id("com.squareup.anvil")
            }
            dependencies {
              compileOnly(libs.dagger2.annotations)
              implementation(project(":lib"))
              kapt(libs.dagger2.compiler)
            }
          }

          dir("src/main/java") {
            kotlinFile(
              "com/squareup/test/app/AppComponent.kt",
              """
              package com.squareup.test.app

              import com.squareup.anvil.annotations.MergeComponent
              import javax.inject.Singleton
              import com.squareup.anvil.annotations.optional.SingleIn

              @SingleIn(Any::class)
              @MergeComponent(Any::class)
              interface AppComponent
              """.trimIndent(),
            )
          }
        }
      }

      shouldSucceed(":app:compileJava")

      val componentModuleFile = rootProject.path.resolve("app")
        .anvilMainGenerated
        .anvilModule
        .resolve("com/squareup/test/app/AppComponent.kt")

      componentModuleFile shouldExistWithTextContaining """
        @Module
        @ContributesTo(Any::class)
        public abstract class AppComponentAnvilModule {
          @Binds
          public abstract fun bindTypeA(otherClass: OtherClass): TypeA
        }
      """.trimIndent()

      otherClassPath.writeText(otherClassContent("TypeB"))

      shouldSucceed(":app:compileJava") {
        task(":app:compileKotlin")?.outcome shouldBe TaskOutcome.SUCCESS
      }

      componentModuleFile shouldExistWithTextContaining """
        @Module
        @ContributesTo(Any::class)
        public abstract class AppComponentAnvilModule {
          @Binds
          public abstract fun bindTypeB(otherClass: OtherClass): TypeB
        }
      """.trimIndent()
    }

  @TestFactory
  fun `build cache restores generated source files in a different project location`() =
    testFactory {

      // The testKit directory has the daemon and build cache.
      // We'll use the same testKit directory for both projects
      // to simulate having a shared remote build cache.
      val runner = gradleRunner
        .withTestKitDir(workingDir / "testKit")
        .withArguments("compileKotlin", "--stacktrace")

      val rootA = workingDir.resolve("a/root-a")
      val rootB = workingDir.resolve("b/root-b")

      for (root in listOf(rootA, rootB)) {
        rootProject(path = root) {
          // Copy the normal root project's build and settings files to the new projects.
          rootProject.buildFileAsFile.copyTo(buildFileAsFile)
          rootProject.settingsFileAsFile.copyTo(settingsFileAsFile)

          dir("src/main/java") {
            injectClass()
          }
          gradlePropertiesFile(
            """
            org.gradle.caching=true
            com.squareup.anvil.trackSourceFiles=true
            """.trimIndent(),
          )
        }
      }

      // Delete the normal auto-generated "root" Gradle files
      // since we created other projects to work with.
      rootProject.buildFileAsFile.delete()
      rootProject.settingsFileAsFile.delete()

      with(runner.withProjectDir(rootA).build()) {
        task(":compileKotlin")?.outcome shouldBe TaskOutcome.SUCCESS
      }

      rootA.deleteRecursively()
      rootA.shouldNotExist()

      with(runner.withProjectDir(rootB).build()) {
        task(":compileKotlin")?.outcome shouldBe TaskOutcome.FROM_CACHE

        // This file wasn't generated in the `root-b` project,
        // but it was cached and restored even though it isn't part of the normal 'classes' output.
        rootB.anvilMainGenerated.injectClassFactory.shouldExist()
      }
    }

  private fun withTrackSourceFiles(
    testAction: suspend AnvilGradleTestEnvironment.(Kase1<Boolean>) -> Unit,
  ): Stream<out DynamicNode> = params.asContainers { versions ->

    kases(listOf(true, false)) { "trackSourceFiles: $a1" }
      .asTests(AnvilGradleTestEnvironment.Factory().wrap(versions)) { testAction(it) }
  }
}
