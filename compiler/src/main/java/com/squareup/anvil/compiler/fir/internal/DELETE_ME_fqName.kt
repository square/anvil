package com.squareup.anvil.compiler.fir.internal

import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName

public fun String.fqn(): FqName = FqName(this)
public fun FqName.classId(): ClassId = ClassId.topLevel(this)
public fun String.classId(): ClassId = fqn().classId()
