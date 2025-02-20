package com.squareup.anvil.compiler.k2.utils.names

import com.squareup.anvil.annotations.ExperimentalAnvilApi
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

/** Well-known class ids used by Anvil. */
public object ClassIds {

  /** `javax.inject.Inject` */
  public val javaxInject: ClassId = classId("javax.inject", "Inject")

  /** `javax.inject.Provider` */
  public val javaxProvider: ClassId = classId("javax.inject", "Provider")

  /** `javax.inject.Qualifier` */
  public val javaxQualifier: ClassId = classId("javax.inject", "Qualifier")

  /** `javax.inject.Scope` */
  public val javaxScope: ClassId = classId("javax.inject", "Scope")

  /** `kotlin.jvm.JvmStatic` */
  public val kotlinJvmStatic: ClassId = classId("kotlin.jvm", "JvmStatic")

  /** `kotlin.jvm.JvmSuppressWildcards` */
  public val kotlinJvmSuppressWildcards: ClassId = classId("kotlin.jvm", "JvmSuppressWildcards")

  /** `kotlin.PublishedApi` */
  public val kotlinPublishedApi: ClassId = classId("kotlin", "PublishedApi")

  /** `com.squareup.anvil.annotations.ContributesBinding` */
  public val anvilContributesBinding: ClassId =
    classId("com.squareup.anvil.annotations", "ContributesBinding")

  /** `com.squareup.anvil.annotations.ContributesMultibinding` */
  public val anvilContributesMultibinding: ClassId =
    classId("com.squareup.anvil.annotations", "ContributesMultibinding")

  /** `com.squareup.anvil.annotations.ContributesSubcomponent` */
  public val anvilContributesSubcomponent: ClassId =
    classId("com.squareup.anvil.annotations", "ContributesSubcomponent")

  /** `com.squareup.anvil.annotations.ContributesTo` */
  public val anvilContributesTo: ClassId =
    classId("com.squareup.anvil.annotations", "ContributesTo")

  /** `com.squareup.anvil.annotations.MergeComponent` */
  public val anvilMergeComponent: ClassId =
    classId("com.squareup.anvil.annotations", "MergeComponent")

  /** `com.squareup.anvil.annotations.MergeSubcomponent` */
  public val anvilMergeSubcomponent: ClassId =
    classId("com.squareup.anvil.annotations", "MergeSubcomponent")

  /** `com.squareup.anvil.annotations.compat.MergeInterfaces` */
  public val anvilMergeInterfaces: ClassId =
    classId("com.squareup.anvil.annotations.compat", "MergeInterfaces")

  /** `com.squareup.anvil.annotations.compat.MergeModules` */
  public val anvilMergeModules: ClassId =
    classId("com.squareup.anvil.annotations.compat", "MergeModules")

  /** `dagger.Binds` */
  public val daggerBinds: ClassId = classId("dagger", "Binds")

  /** `dagger.Component` */
  public val daggerComponent: ClassId = classId("dagger", "Component")

  /** `dagger.internal.Factory` */
  public val daggerFactory: ClassId = classId("dagger.internal", "Factory")

  /** `dagger.Module` */
  public val daggerModule: ClassId = classId("dagger", "Module")

  /** `dagger.internal.Provider` */
  public val daggerProvider: ClassId = classId("dagger.internal", "Provider")

  /** `dagger.Lazy` */
  public val daggerLazy: ClassId = classId("dagger", "Lazy")

  /** `dagger.Subcomponent` */
  public val daggerSubcomponent: ClassId = classId("dagger", "Subcomponent")
}

private fun classId(
  packageFqName: String,
  relativeName: String,
  isLocal: Boolean = false,
): ClassId = ClassId(
  packageFqName = FqName(packageFqName),
  relativeClassName = FqName(relativeName),
  isLocal = isLocal,
)

/**
 * ```
 *  given: `com.example.SomeClass
 * output: `com.example.SomeClass_BindingModule`
 * ```
 */
public val ClassId.bindingModuleSibling: ClassId
  get() = sibling("${shortClassName.asString()}_BindingModule")

/**
 * ```
 *  given: `com.example.SomeClass
 * output: `com.example.SomeClass_MembersInjector`
 * ```
 */
public val ClassId.membersInjectorSibling: ClassId
  get() = joinSimpleNames(separator = "_", suffix = "_MembersInjector")

/**
 * ```
 *  given: `com.example.SomeClass
 * output: `com.example.SomeClass_Factory`
 * ```
 */
public val ClassId.factorySibling: ClassId
  get() = sibling("${shortClassName.asString()}_Factory")

/**
 * ```
 *  given: `com.example.OuterClass.InnerClass
 * output: `com.example.OuterClass_InnerClass_Factory`
 * ```
 */
public val ClassId.factoryJoined: ClassId
  get() = joinSimpleNames(separator = "_", suffix = "_Factory")

public val ClassId.companion: ClassId get() = child("Companion")

/**
 * If the receiver ClassId is a top-level class, this function returns a new ClassId with the same
 * package and the given name.
 *
 * ```
 * val topLevel = ClassId.topLevel(FqName("com.example.SomeClass"))
 *
 * topLevel.sibling("SiblingClass").asFqNameString() shouldBe "com.example.SiblingClass"
 * ```
 *
 * If the receiver ClassId is a nested class, this function returns a new ClassId
 * with the same package and the given name, but nested inside the receiver ClassId.
 *
 * ```
 * val innerClass1 = ClassId(
 *   FqName("com.example"),
 *   Name.identifier("SomeClass.InnerClass1")
 * )
 *
 * innerClass1.sibling("InnerClass2").asFqNameString() shouldBe "com.example.SomeClass.InnerClass2"
 * ```
 */
public fun ClassId.sibling(nameString: String): ClassId {
  return parentClassId?.child(nameString)
    ?: ClassId(packageFqName, Name.identifier(nameString))
}

/**
 * alias for [com.squareup.anvil.compiler.k2.utils.names.nested]
 *
 * ```
 * val kotlinMap = ClassId.fromString("kotlin/collections.Map")
 *
 * val entry by kotlinMap.child()
 *
 * entry.asFqNameString() shouldBe "kotlin.collections.Map.Entry"
 * ```
 * @see ClassId.createNestedClassId
 */
public fun ClassId.child(nameString: String): ClassId = nested(nameString)

/**
 * alias for [com.squareup.anvil.compiler.k2.utils.names.child]
 *
 * ```
 * val kotlinMap = ClassId.fromString("kotlin/collections.Map")
 *
 * val entry by kotlinMap.nested()
 *
 * entry.asFqNameString() shouldBe "kotlin.collections.Map.Entry"
 * ```
 * @see ClassId.createNestedClassId
 */
public fun ClassId.nested(nameString: String): ClassId {
  return createNestedClassId(Name.identifier(nameString))
}

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
