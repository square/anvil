package com.squareup.anvil.compiler.internal

import com.squareup.anvil.annotations.ExperimentalAnvilApi

@ExperimentalAnvilApi
public fun String.capitalize(): String = replaceFirstChar(Char::uppercaseChar)

@ExperimentalAnvilApi
public fun String.decapitalize(): String = replaceFirstChar(Char::lowercaseChar)
