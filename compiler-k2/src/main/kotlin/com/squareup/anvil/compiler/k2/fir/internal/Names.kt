package com.squareup.anvil.compiler.k2.fir.internal

import com.squareup.anvil.annotations.ExperimentalAnvilApi
import com.squareup.anvil.compiler.k2.fir.internal.tree.letIf
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import kotlin.properties.ReadOnlyProperty

@Suppress("UnusedReceiverParameter")
internal val Names.identifier: ReadOnlyProperty<Any?, Name>
  get() = ReadOnlyProperty { _, p -> Name.identifier(p.name) }

@Suppress("ClassName")
internal object Names {

  val inject get() = javax.inject.inject

  internal object identifiers {
    val scope by Names.identifier
    val replaces by Names.identifier
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
    private val annotations = "com.squareup.anvil.annotations".fqn()
    val contributesBinding by annotations.child()
    val contributesMultibinding by annotations.child()
    val contributesTo by annotations.child()
    val contributesSubcomponent by annotations.child()

    val contributesSubcomponentFactory = annotations.child("ContributesSubcomponent.Factory")

    val mergeComponent by annotations.child()
    val mergeSubcomponent by annotations.child()
    val mergeModules by annotations.child()
    val mergeInterfaces by annotations.child()

    val internalBindingMarker = annotations.child("internal.InternalBindingMarker")
  }

  object dagger {
    val binds = "dagger.Binds".fqn()
    val component = "dagger.Component".fqn()
    val factory = "dagger.internal.Factory".fqn()
    val lazy = "dagger.Lazy".fqn()
    val module = "dagger.Module".fqn()

    val subcomponent = "dagger.Subcomponent".fqn()
  }
}

internal fun FqName.child(capitalize: Boolean = true): ReadOnlyProperty<Any?, FqName> {
  return ReadOnlyProperty { _, p ->
    child(p.name.letIf(capitalize) { it.capitalize() })
  }
}

internal fun String.capitalize(): String = replaceFirstChar(Char::uppercaseChar)

public fun FqName.child(nameString: String): FqName = child(Name.identifier(nameString))

/**
 * Joins the simple names of a class with the given [separator], [prefix], and [suffix].
 *
 * ```
 * val normalName = ClassName("com.example", "Outer", "Middle", "Inner")
 * val joinedName = normalName.joinSimpleNames(separator = "_", suffix = "Factory")
 *
 * println(joinedName) // com.example.Outer_Middle_InnerFactory
 * ```
 * @throws IllegalArgumentException if the resulting class name is too long to be a valid file name.
 */
@ExperimentalAnvilApi
public fun ClassId.joinSimpleNames(
  separator: String = "_",
  prefix: String = "",
  suffix: String = "",
): ClassId = ClassId.topLevel(
  packageFqName.child(
    Name.identifier(
      relativeClassName.pathSegments()
        .joinToString(separator = separator, prefix = prefix, postfix = suffix),
    ),
  ),
)
