package com.squareup.anvil.compiler.testing.classgraph

import com.squareup.anvil.compiler.k2.utils.names.Names
import com.squareup.anvil.compiler.testing.asJavaNameString
import io.github.classgraph.AnnotationClassRef
import io.github.classgraph.AnnotationInfo
import io.github.classgraph.ClassInfo
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName

/** A class that's annotated with `@Module` */
public typealias ModuleClassInfo = ClassInfo

/** A class that implements `dagger.internal.Factory` */
public typealias DaggerFactoryClassInfo = ClassInfo

public val ClassInfo.classId: ClassId
  get() = ClassId(
    packageFqName = if (packageName.isNullOrBlank()) FqName.ROOT else FqName(packageName),
    relativeClassName = FqName(simpleName),
    isLocal = isAnonymousInnerClass,
  )

public fun Collection<ClassInfo>.fqNames(): List<FqName> = map { FqName(it.name) }

public fun Collection<ClassInfo>.classIds(): List<ClassId> = map { it.classId }

public fun ClassInfo.getAnnotationInfo(annotationClassId: ClassId): AnnotationInfo {
  return getAnnotationInfo(annotationClassId.asJavaNameString())
}

/**
 * Returns all Dagger modules specified by the Component as fully qualified names
 */
public val AnnotationInfo.moduleNames: List<String>
  get() {
    return parameterValues
      .getValue(Names.modules.identifier)
      .let { it as Array<*> }
      .map { (it as AnnotationClassRef).name }
      .sorted()
  }

/**
 * Returns all binding methods declared in a particular `@Module` type.
 * The methods are sorted by their name.
 */
public fun ModuleClassInfo.allBindMethods(): List<BindsMethodInfo> {
  return methodInfo.filter { it.hasAnnotation("dagger.Binds") }
}

/**
 * Returns all `provide___` methods declared in a particular `@Module` type.
 * The methods are sorted by their name.
 */
public fun ModuleClassInfo.allProvidesMethods(): List<ProvidesMethodInfo> {
  return methodInfo.filter { it.hasAnnotation("dagger.Provides") }
}
