package com.squareup.anvil.compiler.internal

import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesMultibinding
import com.squareup.anvil.annotations.ContributesSubcomponent
import com.squareup.anvil.annotations.ContributesTo
import com.squareup.anvil.annotations.ExperimentalAnvilApi
import com.squareup.anvil.annotations.MergeComponent
import com.squareup.anvil.annotations.MergeSubcomponent
import com.squareup.anvil.annotations.compat.MergeInterfaces
import com.squareup.anvil.annotations.compat.MergeModules
import dagger.Lazy
import dagger.MapKey
import dagger.Provides
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.parentOrNull
import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Scope
import kotlin.reflect.KClass
import jakarta.inject.Inject as JakartaInject
import jakarta.inject.Qualifier as JakartaQualifier
import jakarta.inject.Scope as JakartaScope

internal val jvmSuppressWildcardsFqName = JvmSuppressWildcards::class.fqName
internal val publishedApiFqName = PublishedApi::class.fqName
internal val qualifierFqNames = setOf(
  Qualifier::class.fqName,
  JakartaQualifier::class.fqName,
)
internal val mapKeyFqName = MapKey::class.fqName
internal val daggerProvidesFqName = Provides::class.fqName
internal val daggerLazyFqName = Lazy::class.fqName
internal val scopeFqNames = setOf(
  Scope::class.fqName,
  JakartaScope::class.fqName,
)
internal val injectFqName = Inject::class.fqName
internal val injectFqNames = setOf(injectFqName, JakartaInject::class.fqName)

internal val contributesToFqName = ContributesTo::class.fqName
internal val contributesBindingFqName = ContributesBinding::class.fqName
internal val contributesMultibindingFqName = ContributesMultibinding::class.fqName
internal val contributesSubcomponentFqName = ContributesSubcomponent::class.fqName
internal val mergeComponentFqName = MergeComponent::class.fqName
internal val mergeSubcomponentFqName = MergeSubcomponent::class.fqName
internal val mergeInterfacesFqName = MergeInterfaces::class.fqName
internal val mergeModulesFqName = MergeModules::class.fqName

internal val anyFqName = Any::class.fqName

/**
 * Generates a sequence of [FqName] starting from the current FqName and including its parents
 * up to the root. The sequence will include the current FqName as well.
 */
@ExperimentalAnvilApi
public fun FqName.parentsWithSelf(): Sequence<FqName> {
  return generateSequence(this) { it.parentOrNull() }
    .map {
      it.toUnsafe()
      // The top-most parent is an FqName with the text "<root>",
      // whereas the actual FqName.ROOT is an empty string.  We want the empty string.
      if (parent().isRoot) FqName.ROOT else it
    }
}

/**
 * Generates a sequence of [FqName] starting from the current FqName and including its parents
 * up to the root. The sequence will not include the current FqName.
 */
@ExperimentalAnvilApi
public fun FqName.parents(): Sequence<FqName> = parentsWithSelf().drop(1)

@ExperimentalAnvilApi
public fun FqName.descendant(segments: String): FqName =
  if (isRoot) FqName(segments) else FqName("${asString()}.$segments")

/** Returns the computed [FqName] representation of this [KClass]. */
@ExperimentalAnvilApi
public val KClass<*>.fqName: FqName
  get() = FqName(
    requireNotNull(qualifiedName) {
      "An FqName cannot be created for a local class or class of an anonymous object."
    },
  )

/** @see String.safePackageString */
@ExperimentalAnvilApi
public fun FqName.safePackageString(
  dotPrefix: Boolean = false,
  dotSuffix: Boolean = true,
): String = toString().safePackageString(isRoot, dotPrefix, dotSuffix)

/**
 * This function should only be used for package names. If the FqName is the root (no package at
 * all), then this function returns an empty string whereas `toString()` would return "<root>". For
 * a more convenient string concatenation the returned result can be prefixed and suffixed with an
 * additional dot. The root package never will use a prefix or suffix.
 */
@ExperimentalAnvilApi
public fun String.safePackageString(
  isRoot: Boolean = isEmpty(),
  dotPrefix: Boolean = false,
  dotSuffix: Boolean = true,
): String =
  if (isRoot) {
    ""
  } else {
    val prefix = if (dotPrefix) "." else ""
    val suffix = if (dotSuffix) "." else ""
    "$prefix$this$suffix"
  }

@ExperimentalAnvilApi
public fun FqName.classIdBestGuess(): ClassId {
  val segments = pathSegments().map { it.asString() }
  val classNameIndex = segments.indexOfFirst { it[0].isUpperCase() }
  if (classNameIndex < 0) {
    return ClassId.topLevel(this)
  }

  val packageFqName = FqName.fromSegments(segments.subList(0, classNameIndex))
  val relativeClassName = FqName.fromSegments(segments.subList(classNameIndex, segments.size))
  return ClassId(packageFqName, relativeClassName, false)
}
