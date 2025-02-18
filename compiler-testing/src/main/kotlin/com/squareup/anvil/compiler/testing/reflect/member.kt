package com.squareup.anvil.compiler.testing.reflect

import com.squareup.anvil.annotations.ExperimentalAnvilApi
import java.lang.reflect.Member
import java.lang.reflect.Modifier

@ExperimentalAnvilApi
public val Member.isStatic: Boolean get() = Modifier.isStatic(modifiers)

@ExperimentalAnvilApi
public val Member.isAbstract: Boolean get() = Modifier.isAbstract(modifiers)
