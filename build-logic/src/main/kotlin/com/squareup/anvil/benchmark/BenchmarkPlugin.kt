package com.squareup.anvil.benchmark

import org.gradle.api.Plugin
import org.gradle.api.Project

open class BenchmarkPlugin : Plugin<Project> {
  override fun apply(target: Project) {
    target.tasks.register("createBenchmarkProject", CreateBenchmarkProjectTask::class.java)
  }
}
