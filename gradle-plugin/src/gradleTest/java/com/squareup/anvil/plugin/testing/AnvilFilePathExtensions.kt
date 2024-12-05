package com.squareup.anvil.plugin.testing

import java.io.File

interface AnvilFilePathExtensions {

  /** resolves `build/anvil/main/caches` */
  val File.buildAnvilMainCaches: File
    get() = resolve("build/anvil/main/caches")

  /** resolves `build/anvil/main/generated` */
  val File.buildAnvilMainGenerated: File
    get() = resolve("build/anvil/main/generated")

  /** resolves `anvil/hint` */
  val File.anvilHint: File
    get() = resolve("anvil/hint")

  /** resolves `com/squareup/test/InjectClass_Factory.kt` */
  val File.injectClassFactory: File
    get() = resolve("com/squareup/test/InjectClass_Factory.kt")

  /** Resolves the main sourceset generated directory for Anvil. */
  fun File.generatedDir(): File = buildAnvilMainGenerated
}
