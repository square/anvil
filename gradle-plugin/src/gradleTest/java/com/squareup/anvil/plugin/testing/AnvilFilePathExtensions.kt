package com.squareup.anvil.plugin.testing

import java.io.File

interface AnvilFilePathExtensions {

  /** resolves `build/anvil/main/caches` */
  val File.anvilMainCaches: File
    get() = resolve("build/anvil/main/caches")

  /** resolves `build/anvil/main/generated` */
  val File.anvilMainGenerated: File
    get() = resolve("build/anvil/main/generated")

  /** resolves `anvil/hint/merge` */
  val File.anvilHintMerge: File
    get() = resolve("anvil/hint/merge")

  /** resolves `com/squareup/test/InjectClass_Factory.kt` */
  val File.injectClassFactory: File
    get() = resolve("com/squareup/test/InjectClass_Factory.kt")
}
