package com.squareup.anvil.conventions

import com.rickbusarow.kgx.pluginId
import com.squareup.anvil.conventions.utils.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.targets.js.dsl.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

class LibraryKmpPlugin : Plugin<Project> {

  @OptIn(ExperimentalWasmDsl::class)
  override fun apply(target: Project) = with(target) {
    pluginManager.apply("org.jetbrains.kotlin.multiplatform")

    configureKotlinJvm()

    kotlin {
      if (pluginManager.hasPlugin("com.android.library")) {
        androidTarget()
      }

      iosArm64()
      iosSimulatorArm64()
      iosX64()

      js {
        browser()
      }

      jvm()

      linuxArm64()
      linuxX64()

      macosArm64()
      macosX64()

      tvosArm64()
      tvosSimulatorArm64()
      tvosX64()

      wasmJs {
        browser()
      }

      watchosArm32()
      watchosArm64()
      watchosSimulatorArm64()
      watchosX64()

      // TODO: re-enable when kotlin-inject supports it
      // mingwX64()

      applyDefaultHierarchyTemplate()

      sourceSets.apply {
        val nonJvmMain = create("nonJvmMain")
        nonJvmMain.dependsOn(commonMain.get())

        nativeMain.get().dependsOn(nonJvmMain)
        jsMain.get().dependsOn(nonJvmMain)
        getByName("wasmJsMain").dependsOn(nonJvmMain)
      }
    }
    configureBinaryCompatibilityValidator()
    configureExplicitApi()
  }

  private fun Project.configureExplicitApi() {
    kotlin.explicitApi()
  }

  private fun Project.configureKotlinJvm() {
    configureJavaCompile()
    tasks.withType(KotlinCompile::class.java).configureEach {
      it.compilerOptions {
        jvmTarget.set(JvmTarget.fromTarget(libs.versions.jvm.target.minimal.get()))
      }
    }
  }

  private fun Project.kotlin(action: KotlinMultiplatformExtension.() -> Unit) {
    extensions.configure(KotlinMultiplatformExtension::class.java, action)
  }

  private val Project.kotlin: KotlinMultiplatformExtension
    get() = extensions.getByType(KotlinMultiplatformExtension::class.java)

  private fun Project.configureBinaryCompatibilityValidator() {
    plugins.apply(libs.plugins.kotlinx.binaryCompatibility.pluginId)
  }
}
