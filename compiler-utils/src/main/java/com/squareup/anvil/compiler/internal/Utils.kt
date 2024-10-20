package com.squareup.anvil.compiler.internal

import com.squareup.anvil.annotations.ExperimentalAnvilApi
import com.squareup.kotlinpoet.ClassName
import org.jetbrains.kotlin.name.FqName

@ExperimentalAnvilApi
public fun String.capitalize(): String = replaceFirstChar(Char::uppercaseChar)

@ExperimentalAnvilApi
public fun String.decapitalize(): String = replaceFirstChar(Char::lowercaseChar)

public val ClassName.fqName: FqName
  get() {
    return FqName(canonicalName)
  }
