package com.squareup.anvil.compiler

import java.lang.reflect.Method
import java.lang.reflect.Modifier

val Method.isStatic: Boolean get() = Modifier.isStatic(modifiers)
