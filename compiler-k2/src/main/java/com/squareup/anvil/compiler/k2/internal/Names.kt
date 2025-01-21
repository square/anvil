package com.squareup.anvil.compiler.k2.internal

import com.squareup.anvil.compiler.internal.asClassName
import com.squareup.anvil.compiler.internal.joinSimpleNames
import com.squareup.anvil.compiler.internal.reference.asClassId
import org.jetbrains.kotlin.fir.lightTree.converter.nameAsSafeName
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import kotlin.properties.ReadOnlyProperty

@Suppress("UnusedReceiverParameter")
internal val Names.identifier: ReadOnlyProperty<Any?, Name>
  get() = ReadOnlyProperty { _, p -> Name.identifier(p.name) }

internal object Names {

  val inject get() = javax.inject.inject

  internal object identifiers {
    val scope by Names.identifier
    val modules by Names.identifier
    val dependencies by Names.identifier
  }

  object javax {
    object inject {

      val inject = "javax.inject.Inject".fqn()
      val provider = "javax.inject.Provider".fqn()
    }
  }

  object anvil {
    val contributesBinding = "com.squareup.anvil.annotations.ContributesBinding".fqn()
    val contributesTo = "com.squareup.anvil.annotations.ContributesTo".fqn()
    val mergeComponent = "com.squareup.anvil.annotations.MergeComponent".fqn()
  }

  object dagger {
    val binds = "dagger.Binds".fqn()
    val component = "dagger.Component".fqn()
    val factory = "dagger.internal.Factory".fqn()
    val lazy = "dagger.Lazy".fqn()
    val module = "dagger.Module".fqn()

    val subcomponent = "dagger.Subcomponent".fqn()
  }

  object foo {
    val packageFqName = "foo".fqn()
    val testComponent = "foo.TestComponent".fqn()
    val injectClass = "foo.InjectClass".fqn()
  }
}

internal fun ClassId.factory(): ClassId {
  return asClassName().joinSimpleNames(suffix = "_Factory").asClassId()
}

internal fun ClassId.factoryDelegate(): ClassId {
  return asClassName().joinSimpleNames(suffix = "_FactoryDelegate").asClassId()
}

internal fun ClassId.isFactory() = shortClassName.asString().endsWith("_Factory")
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
