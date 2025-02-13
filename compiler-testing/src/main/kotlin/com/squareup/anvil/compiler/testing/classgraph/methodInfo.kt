package com.squareup.anvil.compiler.testing.classgraph

import io.github.classgraph.MethodInfo

/** A method that's annotated with `@Binds` */
public typealias BindsMethodInfo = MethodInfo

/** A method that's annotated with `@Provides` */
public typealias ProvidesMethodInfo = MethodInfo

public typealias ReturnTypeName = String
public typealias ParameterTypeName = String

public fun BindsMethodInfo.boundTypes(): Pair<ParameterTypeName, ReturnTypeName> {
  val returnType = typeSignatureOrTypeDescriptor.resultType.toString()
  val parameterType = parameterInfo.single().typeSignatureOrTypeDescriptor.toString()
  return parameterType to returnType
}
