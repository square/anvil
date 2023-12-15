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
}
