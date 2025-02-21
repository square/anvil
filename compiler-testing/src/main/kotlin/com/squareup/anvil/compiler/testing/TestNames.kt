package com.squareup.anvil.compiler.testing

import com.rickbusarow.kase.stdlib.letIf
import com.squareup.anvil.compiler.k2.utils.names.child
import com.squareup.anvil.compiler.k2.utils.names.factoryJoined
import com.squareup.anvil.compiler.k2.utils.names.nested
import io.kotest.matchers.nulls.shouldNotBeNull
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KClass

public object TestNames {
  /** `kotlin.Any` */
  public val any: ClassId = ClassId.fromString("kotlin.Any")

  /** `kotlin.Nothing` */
  public val nothing: ClassId = ClassId.fromString("kotlin.Nothing")

  /** `com.squareup.test` */
  public val squareupTest: FqName = FqName("com.squareup.test")

  /** `com.squareup.test.ContributingObject` */
  public val contributingObject: ClassId = classId(squareupTest, "ContributingObject")

  /** `com.squareup.test.ContributingInterface` */
  public val contributingInterface: ClassId = classId(squareupTest, "ContributingInterface")

  /** `com.squareup.test.SecondContributingInterface` */
  public val secondContributingInterface: ClassId =
    classId(squareupTest, "SecondContributingInterface")

  /** `com.squareup.test.SomeClass` */
  public val someClass: ClassId = classId(squareupTest, "SomeClass")

  /** `com.squareup.test.ParentInterface` */
  public val parentInterface: ClassId = classId(squareupTest, "ParentInterface")

  /** `com.squareup.test.ParentInterface1` */
  public val parentInterface1: ClassId = classId(squareupTest, "ParentInterface1")

  /** `com.squareup.test.ParentInterface2` */
  public val parentInterface2: ClassId = classId(squareupTest, "ParentInterface2")

  /** `com.squareup.test.ComponentInterface` */
  public val componentInterface: ClassId = classId(squareupTest, "ComponentInterface")

  /** `com.squareup.test.SubcomponentInterface` */
  public val subcomponentInterface: ClassId = classId(squareupTest, "SubcomponentInterface")

  /** `com.squareup.test.AssistedService` */
  public val assistedService: ClassId = classId(squareupTest, "AssistedService")

  /** `com.squareup.test.AssistedServiceFactory` */
  public val assistedServiceFactory: ClassId = classId(squareupTest, "AssistedServiceFactory")

  /** `com.squareup.test.DaggerModule1` */
  public val daggerModule1: ClassId = classId(squareupTest, "DaggerModule1")

  /** `com.squareup.test.DaggerModule2` */
  public val daggerModule2: ClassId = classId(squareupTest, "DaggerModule2")

  /** `com.squareup.test.DaggerModule3` */
  public val daggerModule3: ClassId = classId(squareupTest, "DaggerModule3")

  /** `com.squareup.test.DaggerModule4` */
  public val daggerModule4: ClassId = classId(squareupTest, "DaggerModule4")

  /** `com.squareup.test.InjectClass` */
  public val injectClass: ClassId = classId(squareupTest, "InjectClass")

  /** `com.squareup.test.InjectClass_Factory` */
  public val injectClass_Factory: ClassId get() = injectClass.factoryJoined

  /** `com.squareup.test.JavaClass` */
  public val javaClass: ClassId = classId(squareupTest, "JavaClass")

  /** `com.squareup.test.AnyQualifier` */
  public val anyQualifier: ClassId = classId(squareupTest, "AnyQualifier")
}

private fun classId(
  packageFqName: FqName,
  simpleName: String,
  vararg nestedSimpleNames: String,
): ClassId = nestedSimpleNames
  .fold(ClassId(packageFqName, Name.identifier(simpleName))) { acc, name ->
    acc.child(name)
  }

/**
 * Creates a new ClassId with a nested type named `InnerModule`.
 * ```
 * ClassId("com.example", "SomeClass").innerModule shouldBe
 *     ClassId("com.example", "SomeClass.InnerModule")
 * ```
 */
public val ClassId.innerModule: ClassId get() = child("InnerModule")

/**
 * Creates a new ClassId with a nested type named `NestedInterface`.
 * ```
 * ClassId("com.example", "SomeClass").nestedInterface shouldBe
 *     ClassId("com.example", "SomeClass.NestedInterface")
 * ```
 */
public val ClassId.nestedInterface: ClassId get() = child("NestedInterface")

/**
 * Creates a new ClassId with a nested type named `NestedClass`.
 * ```
 * ClassId("com.example", "SomeClass").nestedClass shouldBe
 *     ClassId("com.example", "SomeClass.NestedClass")
 * ```
 */
public val ClassId.nestedClass: ClassId get() = child("NestedClass")

/**
 * Creates a new ClassId with a nested type named `NestedObject`.
 * ```
 * ClassId("com.example", "SomeClass").nestedObject shouldBe
 *     ClassId("com.example", "SomeClass.NestedObject")
 * ```
 */
public val ClassId.nestedObject: ClassId get() = child("NestedObject")

internal fun String.capitalize(): String = replaceFirstChar(Char::uppercaseChar)

/** Returns the ClassId for this KClass. */
@Suppress("RecursivePropertyAccessor")
public val KClass<*>.classId: ClassId
  get() = this.java.enclosingClass?.kotlin?.classId?.createNestedClassId(Name.identifier(simpleName!!))
    ?: ClassId.topLevel(FqName(qualifiedName!!))

/**
 * Returns the fully qualified Java-style name (nested classes joined by `$`).
 *
 * |     Kotlin Class Name     |      Java Class Name      |
 * |---------------------------|---------------------------|
 * | `com.example.Foo`         | `com.example.Foo`         |
 * | `com.example.Foo.Bar`     | `com.example.Foo$Bar`     |
 * | `com.example.Foo.Bar.Baz` | `com.example.Foo$Bar$Baz` |
 * | (root package) `Foo.Bar`  | `Foo$Bar`                 |
 *
 * @see ClassId.asFqNameString for a version that returns an entirely dot-separated string
 */
public fun ClassId.asJavaNameString(): String {
  return relativeClassName.pathSegments()
    .joinToString(
      separator = "$",
      prefix = if (packageFqName.isRoot) "" else "${packageFqName.asString()}.",
    )
}

public fun ClassId.child(capitalize: Boolean = true): ReadOnlyProperty<Any?, ClassId> =
  nested(capitalize)

public fun ClassId.nested(capitalize: Boolean = true): ReadOnlyProperty<Any?, ClassId> {
  return ReadOnlyProperty { _, p ->
    nested(p.name.letIf(capitalize) { it.capitalize() })
  }
}

/**
 * ```
 *  given: `com.example.SomeClass
 * output: `com.example.SomeClass_BindingModule`
 * ```
 */
public val FqName.bindingModuleSibling: FqName
  get() = sibling("${shortName().asString()}_BindingModule")

/**
 * ```
 *  given: `com.example.SomeClass
 * output: `com.example.SomeClass_MembersInjector`
 * ```
 */
public val FqName.membersInjectorSibling: FqName
  get() = sibling("${shortName().asString()}_MembersInjector")

/**
 * ```
 *  given: `com.example.SomeClass
 * output: `com.example.SomeClass_Factory`
 * ```
 */
public val FqName.factorySibling: FqName
  get() = sibling("${shortName().asString()}_Factory")

public fun FqName.sibling(nameString: String): FqName = parent().shouldNotBeNull().child(nameString)

public val FqName.innerModule: FqName get() = child("InnerModule")
public val FqName.nestedInterface: FqName get() = child("NestedInterface")
public val FqName.nestedClass: FqName get() = child("NestedClass")
public val FqName.nestedObject: FqName get() = child("NestedObject")
public val FqName.companion: FqName get() = child("Companion")

/** Returns the FqName for this Class. Throws if the class is primitive or an array. */
public val Class<*>.fqName: FqName
  get() {
    require(!isPrimitive) { "Can't compute FqName for primitive type: $this" }
    require(!isArray) { "Can't compute FqName for array type: $this" }
    val outerClass = declaringClass
    @Suppress("RecursivePropertyAccessor")
    return when {
      outerClass == null -> FqName(canonicalName)
      else -> outerClass.fqName.child(Name.identifier(simpleName))
    }
  }

/** Returns the FqName for this KClass. */
@Suppress("RecursivePropertyAccessor")
public val KClass<*>.fqName: FqName
  get() = this.java.enclosingClass?.kotlin?.fqName?.child(Name.identifier(simpleName!!))
    ?: FqName(qualifiedName!!)

public fun FqName.child(capitalize: Boolean = true): ReadOnlyProperty<Any?, FqName> =
  nested(capitalize)

public fun FqName.nested(capitalize: Boolean = true): ReadOnlyProperty<Any?, FqName> {
  return ReadOnlyProperty { _, p ->
    nested(p.name.letIf(capitalize) { it.capitalize() })
  }
}

/**
 * alias for [com.squareup.anvil.compiler.testing.nested]
 *
 * ```
 * val kotlinMap = FqName("kotlin.collections.Map")
 *
 * val entry by kotlinMap.child()
 *
 * entry.asString() shouldBe "kotlin.collections.Map.Entry"
 * ```
 * @see FqName.child
 */
public fun FqName.child(nameString: String): FqName = nested(nameString)

/**
 * alias for [com.squareup.anvil.compiler.testing.nested]
 *
 * ```
 * val kotlinMap = FqName("kotlin.collections.Map")
 *
 * val entry by kotlinMap.nested()
 *
 * entry.asString() shouldBe "kotlin.collections.Map.Entry"
 * ```
 * @see FqName.child
 */
public fun FqName.nested(nameString: String): FqName = child(Name.identifier(nameString))
