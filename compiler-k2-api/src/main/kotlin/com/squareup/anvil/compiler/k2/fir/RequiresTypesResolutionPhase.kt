package com.squareup.anvil.compiler.k2.fir

@RequiresOptIn("Cannot be called before supertype resolution", RequiresOptIn.Level.ERROR)
@Retention(AnnotationRetention.BINARY)
public annotation class RequiresTypesResolutionPhase
