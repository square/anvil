package com.squareup.anvil.compiler.internal.testing

import com.tschuchort.compiletesting.KotlinCompilation
import org.jetbrains.kotlin.compiler.plugin.ComponentRegistrar

internal fun KotlinCompilation.setRegistrars(registrars: List<ComponentRegistrar>) {
  componentRegistrars = registrars
}
