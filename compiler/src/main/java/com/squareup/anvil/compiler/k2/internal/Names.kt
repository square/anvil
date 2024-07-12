package com.squareup.anvil.compiler.k2.internal

import com.squareup.anvil.compiler.internal.asClassName
import com.squareup.anvil.compiler.internal.joinSimpleNames
import com.squareup.anvil.compiler.internal.reference.asClassId
import org.jetbrains.kotlin.fir.lightTree.converter.nameAsSafeName
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName

internal object Names {
  val foo = "foo".fqn()
  val ball = "foo.Ball".fqn()
  val emptyModule = "foo.EmptyModule".fqn()
  val freddy = "foo.Freddy".fqn()
  val componentBase = "foo.ComponentBase".fqn()
  val mergeComponentFir = "foo.MergeComponentFir".fqn()
  val testComponent = "foo.TestComponent".fqn()
  val inject = "javax.inject.Inject".fqn()
  val componentKotlin = "foo.ComponentKotlin".fqn()

  object dagger {
    val binds = "dagger.Binds".fqn()
    val module = "dagger.Module".fqn()
    val component = "dagger.Component".fqn()
    val subcomponent = "dagger.Subcomponent".fqn()
  }
}

internal fun ClassId.factoryDelegate(): ClassId {
  return asClassName().joinSimpleNames(suffix = "_FactoryDelegate").asClassId()
}

internal fun ClassId.isFactoryDelegate() = shortClassName.asString().endsWith("_FactoryDelegate")

internal fun ClassId.hasAnvilPrefix() = shortClassName.asString().startsWith("Anvil_")

internal fun ClassId.anvilPrefix(): ClassId {
  return asClassName().joinSimpleNames(prefix = "Anvil_").asClassId()
}

internal fun ClassId.removeAnvilPrefix(): ClassId {
  return ClassId(
    packageFqName = packageFqName,
    topLevelName = relativeClassName.pathSegments()
      .joinToString(".", "Anvil_")
      .nameAsSafeName(),
  )
}

internal fun FqName.anvilPrefix(): FqName = parent().child("Anvil_${shortName()}".nameAsSafeName())
