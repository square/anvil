package com.squareup.anvil.compiler.internal.reference

import com.squareup.kotlinpoet.TypeName

public data class ClassReferenceWithGenericParameters(
    public val classReference: ClassReference,
    public val typeArguments: List<TypeName>
)
