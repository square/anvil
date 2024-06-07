package com.squareup.anvil.plugin.testing

import com.rickbusarow.kase.files.DirectoryBuilder
import com.rickbusarow.kase.gradle.dsl.BuildFileSpec
import java.io.File

interface FileStubs {

  fun DirectoryBuilder.injectClass(packageName: String = "com.squareup.test"): File {
    return kotlinFile(
      packageName.replace(".", "/") / "InjectClass.kt",
      """
        package $packageName
        
        import javax.inject.Inject
        
        class InjectClass @Inject constructor()
      """.trimIndent(),
    )
  }

  fun DirectoryBuilder.boundClass(
    boundTypeFqName: String,
    scopeFqName: String = "kotlin.Any",
    packageName: String = "com.squareup.test",
  ): File {
    return kotlinFile(
      path = packageName.replace(".", "/") / "BoundClass.kt",
      content = """
        package $packageName

        import com.squareup.anvil.annotations.ContributesBinding
        import javax.inject.Inject
  
        @ContributesBinding($scopeFqName::class)
        class BoundClass @Inject constructor() : $boundTypeFqName
      """.trimIndent(),
    )
  }

  fun DirectoryBuilder.simpleInterface(
    simpleName: String,
  ): File = simpleInterface(packageName = "com.squareup.test", simpleName = simpleName)

  fun DirectoryBuilder.simpleInterface(packageName: String, simpleName: String): File {
    return kotlinFile(
      path = packageName.replace(".", "/") / "$simpleName.kt",
      content = """
        package $packageName

        interface $simpleName
      """.trimIndent(),
    )
  }

  fun DirectoryBuilder.consumerClass(
    dependencyFqName: String,
    packageName: String = "com.squareup.test",
  ): File {
    return kotlinFile(
      path = packageName.replace(".", "/") / "ConsumerClass.kt",
      content = """
        package $packageName
  
        import javax.inject.Inject
  
        class ConsumerClass @Inject constructor(
          private val dep: $dependencyFqName
        )
      """.trimIndent(),
    )
  }

  fun DirectoryBuilder.appComponent(
    scopeFqName: String = "kotlin.Any",
    packageName: String = "com.squareup.test",
  ): File {
    return kotlinFile(
      path = packageName.replace(".", "/") / "AppComponent.kt",
      content = """
        package $packageName
  
        import com.squareup.anvil.annotations.MergeComponent

        @MergeComponent($scopeFqName::class)
        interface AppComponent
      """.trimIndent(),
    )
  }

  fun BuildFileSpec.androidBlock(namespace: String = "com.squareup.anvil.android"): BuildFileSpec {
    return raw(androidBlockString(namespace))
  }

  fun androidBlockString(namespace: String = "com.squareup.anvil.android"): String {
    return """
    android {
      compileSdk = 33
      namespace = "$namespace"

      defaultConfig {
        minSdk = 24
      }

      compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
      }
    }
    """.trimIndent()
  }
}
