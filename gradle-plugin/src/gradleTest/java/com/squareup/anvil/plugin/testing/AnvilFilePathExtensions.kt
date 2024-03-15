package com.squareup.anvil.plugin.testing

import java.io.File

interface AnvilFilePathExtensions {

  /** resolves `build/anvil/main/caches` */
  val File.buildAnvilMainCaches: File
    get() = resolve("build/anvil/main/caches")

  /** resolves `build/anvil/main/generated` */
  val File.buildAnvilMainGenerated: File
    get() = resolve("build/anvil/main/generated")

  /** resolves `build/generated/ksp/main/kotlin` */
  val File.buildGeneratedKspMainKotlin: File
    get() = resolve("build/generated/ksp/main/kotlin")

  /** resolves `anvil/hint/merge` */
  val File.anvilHintMerge: File
    get() = resolve("anvil/hint/merge")

  /** resolves `com/squareup/test/InjectClass_Factory.kt` */
  val File.injectClassFactory: File
    get() = resolve("com/squareup/test/InjectClass_Factory.kt")

  /** Resolves the main sourceset generated directory for Anvil or KSP. */
  fun File.generatedDir(useKsp: Boolean): File {
    return if (useKsp) {
      buildGeneratedKspMainKotlin
    } else {
      buildAnvilMainGenerated
    }
  }
}
