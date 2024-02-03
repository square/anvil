package com.squareup.anvil.plugin

import com.rickbusarow.kase.files.DirectoryBuilder

interface FileStubs {

  fun DirectoryBuilder.injectClass(packageName: String = "com.squareup.test") {
    kotlinFile(
      packageName.replace(".", "/") / "InjectClass.kt",
      """
      package $packageName
      
      import javax.inject.Inject
      
      class InjectClass @Inject constructor()
      """.trimIndent(),
    )
  }

  fun androidBlock(namespace: String = "com.squareup.anvil.android"): String {
    return """
    android {
      compileSdk = 33
      namespace = "$namespace"

      defaultConfig {
        minSdk = 24
        @Suppress("UnstableApiUsage")
        targetSdk = 33
      }

      compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
      }
    }
    """.trimIndent()
  }
}
