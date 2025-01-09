package com.squareup.anvil.compiler.k2.internal

import com.squareup.anvil.compiler.internal.asClassName
import com.squareup.anvil.compiler.internal.joinSimpleNames
import com.squareup.anvil.compiler.internal.reference.asClassId
import org.jetbrains.kotlin.fir.lightTree.converter.nameAsSafeName
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName

internal object Names {

  object foo {
    val packageFqName = "foo".fqn()
    val bBindingModule = "foo.BBindingModule".fqn()
    val componentBase = "foo.ComponentBase".fqn()
    val testComponent = "foo.TestComponent".fqn()
  }

  val inject get() = javax.inject
  object javax {
    val inject = "javax.inject.Inject".fqn()
  }

  object anvil {
    val contributesBinding = "com.squareup.anvil.annotations.ContributesBinding".fqn()
    val contributesTo = "com.squareup.anvil.annotations.ContributesTo".fqn()
    val mergeComponent = "com.squareup.anvil.annotations.MergeComponent".fqn()
  }

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
