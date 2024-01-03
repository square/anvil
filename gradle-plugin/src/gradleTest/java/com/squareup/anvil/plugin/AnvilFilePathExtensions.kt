package com.squareup.anvil.plugin

import java.io.File

interface AnvilFilePathExtensions {

  /** resolves `build/anvil/main/generated` */
  val File.anvilMainGenerated: File
    get() = resolve("build/anvil/main/generated")

  /** resolves `anvil/hint/binding` */
  val File.anvilHintBinding: File
    get() = resolve("anvil/hint/binding")

  /** resolves `anvil/module` */
  val File.anvilModule: File
    get() = resolve("anvil/module")

  /** resolves `com/squareup/test/InjectClass_Factory.kt` */
  val File.injectClassFactory: File
    get() = resolve("com/squareup/test/InjectClass_Factory.kt")
}
