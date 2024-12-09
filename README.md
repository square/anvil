# Anvil

[![Maven Central](https://img.shields.io/maven-central/v/com.squareup.anvil/gradle-plugin.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:%22com.squareup.anvil%22)
[![CI](https://github.com/square/anvil/workflows/CI/badge.svg)](https://github.com/square/anvil/actions?query=branch%3Amain)

> _"When all you have is an anvil, every problem looks like a hammer."_ - [Abraham Maslow](https://en.wikipedia.org/wiki/Law_of_the_instrument)

Anvil is a Kotlin compiler plugin to make dependency injection with [Dagger](https://dagger.dev/)
easier by automatically merging Dagger modules and component interfaces. In a nutshell, instead of
manually adding modules to a Dagger component and making the Dagger component extend all component
interfaces, these modules and interfaces can be included in a component automatically:

```kotlin
@Module
@ContributesTo(AppScope::class)
class DaggerModule { .. }

@ContributesTo(AppScope::class)
interface ComponentInterface {
  fun getSomething(): Something
  fun injectActivity(activity: MyActivity)
}

// The real Dagger component.
@MergeComponent(AppScope::class)
interface AppComponent
```

The generated `AppComponent` interface that Dagger sees looks like this:

```kotlin
@Component(modules = [DaggerModule::class])
interface AppComponent : ComponentInterface
```

Notice that `AppComponent` automatically includes `DaggerModule` and extends `ComponentInterface`.

## Setup

The plugin consists of a Gradle plugin and Kotlin compiler plugin. The Gradle plugin automatically
adds the Kotlin compiler plugin and annotation dependencies. It needs to be applied in all modules
that either contribute classes to the dependency graph or merge them:

```groovy
plugins {
  id 'com.squareup.anvil' version "${latest_version}"
}
```

Or you can use the old way to apply a plugin:
```groovy
buildscript {
  repositories {
    mavenCentral()
  }
  dependencies {
    classpath "com.squareup.anvil:gradle-plugin:${latest_version}"
  }
}

apply plugin: 'com.squareup.anvil'
```

## Quick Start

There are three important annotations to work with Anvil.

`@ContributesTo` can be added to Dagger modules and component interfaces that should be included
in the Dagger component. Classes with this annotation are automatically merged by the compiler
plugin as long as they are on the compile classpath.

`@MergeComponent` is used instead of the Dagger annotation `@Component`. Anvil will generate
the Dagger annotation and automatically include all modules and component interfaces that were
contributed the same scope.

`@MergeSubcomponent` is similar to `@MergeComponent` and should be used for subcomponents instead.

## Scopes

Scope classes are only markers. The class `AppScope` from the sample could look like this:

```kotlin
abstract class AppScope private constructor()
```

These scope classes help Anvil make a connection between the Dagger component and which Dagger
modules and other component interfaces to include.

Scope classes are independent of the Dagger scopes. It's still necessary to set a scope for
the Dagger component, e.g.

```kotlin
@Singleton
@MergeComponent(AppScope::class)
interface AppComponent
```

## Contributed bindings

The `@ContributesBinding` annotation generates a Dagger binding method for an annotated class and 
contributes this binding method to the given scope. Imagine this example:
```kotlin
interface Authenticator

class RealAuthenticator @Inject constructor() : Authenticator

@Module
@ContributesTo(AppScope::class)
abstract class AuthenticatorModule {
  @Binds abstract fun bindRealAuthenticator(authenticator: RealAuthenticator): Authenticator
}
```
This is a lot of boilerplate if you always want to use `RealAuthenticator` when injecting
`Authenticator`. You can replace this entire Dagger module with the `@ContributesBinding` 
annotation. The equivalent would be:
```kotlin
interface Authenticator

@ContributesBinding(AppScope::class)
class RealAuthenticator @Inject constructor() : Authenticator
```

`@ContributesBinding` also supports qualifiers. You can annotate the class with any qualifier 
and the generated binding method will preserve the qualifier, e.g.
```kotlin
@ContributesBinding(AppScope::class)
@Named("Prod")
class RealAuthenticator @Inject constructor() : Authenticator

// Will generate:
@Binds @Named("Prod") 
abstract fun bindRealAuthenticator(authenticator: RealAuthenticator): Authenticator
```

## Contributed multibindings

Similar to contributed bindings, `@ContributesMultibinding` will generate a multibindings method 
for (all/an) annotated class(es). Qualifiers are supported the same way as normal bindings.
```kotlin
@ContributesMultibinding(AppScope::class)
@Named("Prod")
class MainListener @Inject constructor() : Listener

// Will generate this binding method.
@Binds @IntoSet @Named("Prod")
abstract fun bindMainListener(listener: MainListener): Listener
```
If the class is annotated with a map key annotation, then Anvil will generate a maps multibindings 
method instead of adding the element to a set:
```kotlin
@MapKey
annotation class BindingKey(val value: String)

@ContributesMultibinding(AppScope::class)
@BindingKey("abc")
class MainListener @Inject constructor() : Listener

// Will generate this binding method.
@Binds @IntoMap @BindingKey("abc")
abstract fun bindMainListener(listener: MainListener): Listener
```

## Exclusions

Dagger modules and component interfaces can be excluded in two different levels.

One class can always replace another one. This is especially helpful for modules that provide
different bindings for instrumentation tests, e.g.

```kotlin
@Module
@ContributesTo(
    scope = AppScope::class,
    replaces = [DevelopmentApplicationModule::class]
)
object DevelopmentApplicationTestModule {
  @Provides
  fun provideEndpointSelector(): EndpointSelector = TestingEndpointSelector
}
```

The compiler plugin will find both classes on the classpath. Adding both modules
`DevelopmentApplicationModule` and `DevelopmentApplicationTestModule` to the Dagger graph would
lead to duplicate bindings. Anvil sees that the test module wants to replace the other and
ignores it. This replacement rule has a global effect for all applications which are including the
classes on the classpath.

Applications can exclude Dagger modules and component interfaces individually without affecting
other applications.

```kotlin
@MergeComponent(
  scope = AppScope::class,
  exclude = [
    DaggerModule::class
  ]
)
interface AppComponent
```

In a perfect build graph it’s unlikely that this feature is needed. However, due to legacy modules,
wrong imports and deeply nested dependency chains applications might need to make use of it. The
exclusion rule does what it implies. In this specific example `DaggerModule` wishes to be
contributed to this scope, but it has been excluded for this component and thus is not added.

## Dagger Factory Generation

Anvil allows you to generate Factory classes that usually the Dagger annotation processor would
generate for `@Provides` methods, `@Inject` constructors and `@Inject` fields. The benefit of this
feature is that you don't need to enable the Dagger annotation processor in this module. That often
means you can skip KAPT and the stub generating task. In addition Anvil generates Kotlin instead
of Java code, which allows Gradle to skip the Java compilation task. The result is faster
builds.

<details open>
<summary>Gradle DSL</summary>

```groovy
// build.gradle
anvil {
  generateDaggerFactories = true // default is false
}
```
</details>
<details>
<summary>Gradle Properties</summary>

```properties
# gradle.properties
com.squareup.anvil.generateDaggerFactories=true # default is false
```
</details>

In our codebase we measured that modules using Dagger build 65% faster with this new Anvil feature
compared to using the Dagger annotation processor:

|| Stub generation | Kapt | Javac | Kotlinc | Sum
:--- | ---: | ---: | ---: | ---: | ---:
Dagger | 12.976 | 40.377 | 8.571 | 10.241 | 72.165
Anvil | 0 | 0 | 6.965 | 17.748 | 24.713

For full builds of applications we measured savings of 16% on average.

![Benchmark Dagger Factories](images/benchmark_dagger_factories.png?raw=true "Benchmark Dagger Factories")

This feature can only be enabled in Gradle modules that don't compile any Dagger component. Since
Anvil only processes Kotlin code, you shouldn't enable it in modules with mixed Kotlin / Java
sources either.

When you enable this feature, don't forget to remove the Dagger annotation processor. You should
keep all other dependencies.

## Extending Anvil

Every codebase has its own dependency injection patterns where certain code structures need to be
repeated over and over again. Here Anvil comes to the rescue and you can extend the compiler 
plugin with your own `CodeGenerator`. For usage please take a look at the 
[`compiler-api` artifact](compiler-api/README.md)

## Advantages of Anvil

Adding Dagger modules to components in a large modularized codebase with many application targets
is overhead. You need to know where components are defined when creating a new Dagger module and
which modules to add when setting up a new application. This task involves many syncs in the IDE
after adding new module dependencies in the build graph. The process is tedious and cumbersome.
With Anvil you only add a dependency in your build graph and then you can immediately test
the build.

Aligning the build graph and Dagger's dependency graph brings a lot of consistency. If code is on
the compile classpath, then it's also included in the Dagger dependency graph.

Modules implicitly have a scope, if provided objects are tied to a scope. Now the scope of a module
is clear without looking at any binding.

With Anvil you don't need any composite Dagger module anymore, which only purpose is to
combine multiple modules to avoid repeating the setup for multiple applications. Composite modules
easily become hairballs. If one application wants to exclude a module, then it has to repeat the
setup. These forked graphs are painful and confusing. With Dagger you want to make the decision
which modules fulfill dependencies as late as possible, ideally in the application module.
Anvil makes this approach a lot easier by generating the code for included modules. Composite
modules are redundant. You make the decision which bindings to use by importing the desired module
in the application module.

## Performance

Anvil is a convenience tool. Similar to Dagger it doesn't improve build speed compared to
writing all code manually before running a build. The savings are in developer time.

The median overhead of Anvil is around 4%, which often means only a few hundred milliseconds
on top. The overhead is marginal, because Kotlin code is still compiled incrementally and Kotlin
compile tasks are skipped entirely, if nothing has changed. This doesn't change with Anvil.

![Benchmark](images/benchmark.png?raw=true "Benchmark")

On top of that, Anvil provides actual build time improvements by replacing the Dagger annotation
processor in many modules if you enable [Dagger Factory generation](#dagger-factory-generation).

## Kotlin compiler plugin

We investigated whether other alternatives like a bytecode transformer and an annotation processor
would be a better option, but ultimately decided against them. For what we tried to achieve a
bytecode transformer runs too late in the build process; after the Dagger components have been
generated. An annotation processor especially when using KAPT would be too slow. Even though the
Kotlin compiler plugin API isn't stable and contains bugs we decided to write a compiler plugin.

## Limitations

#### No Java support

Anvil is a Kotlin compiler plugin, thus Java isn’t supported. You can use Anvil in
modules with mixed Java and Kotlin code for Kotlin classes, though.

#### Correct error types disabled

KAPT has the option to
[correct non-existent types](https://kotlinlang.org/docs/kapt.html#non-existent-type-correction).
This option however changes order of how compiler plugins and KAPT itself are invoked. The result
is that Anvil cannot merge supertypes before the Dagger annotation processor runs and abstract
functions won't be implemented properly in the final Dagger component.

Anvil will automatically set `correctErrorTypes` to false to avoid this issue.

#### Incremental Kotlin compilation breaks Anvil's feature to merge contributions

> [!TIP]
> Anvil now supports incremental compilation and Gradle's build caching,
> as of [v2.5.0](https://github.com/square/anvil/releases/tag/v2.5.0-beta01).
>
> This feature is enabled by default.
> It can be disabled via a Gradle property or the Gradle DSL:
> <details open>
> <summary>Gradle Properties</summary>
> 
> ```properties
> # gradle.properties
> com.squareup.anvil.trackSourceFiles=false # default is true
> ```
> 
> </details>
> <details>
> <summary>Gradle DSL</summary>
> 
> ```groovy
> // build.gradle
> anvil {
>   trackSourceFiles = false // default is true
> }
> ```
> </details>

Anvil merges Dagger component interfaces and Dagger modules during the stub generating task
when `@MergeComponent` is used. This requires scanning the compile classpath for any contributions.
Assume the scenario that a contributed type in a module dependency has changed, but the module
using `@MergeComponent` itself didn't change. With Kotlin incremental compilation enabled the
compiler will notice that the module using `@MergeComponent` doesn't need to be recompiled and
therefore doesn't invoke compiler plugins. Anvil will miss the new contributed type from the module
dependency.

To avoid this issue, Anvil must disable incremental compilation for the stub generating task, which
runs right before Dagger processes annotations. Normal Kotlin compilation isn't impacted by this
workaround. The issue is captured in
[KT-54850 Provide mechanism for compiler plugins to add custom information into binaries](https://youtrack.jetbrains.com/issue/KT-54850/Provide-mechanism-for-compiler-plugins-to-add-custom-information-into-binaries).

Disabling incremental compilation for the stub generating task could have a negative impact on
compile times, if you heavily rely on KAPT. While Anvil can
[significantly help to improve build times](#dagger-factory-generation), the wrong configuration
and using KAPT in most modules could make things worse. The
suggestion is to extract and isolate annotation processors in separate modules and avoid using Anvil
in the same modules, e.g. a common practice is to move the Dagger component using `@MergeComponent`
into the final application module with little to no other code in the app module.

## Hilt

[Hilt](https://dagger.dev/hilt/) is Google's opinionated guide how to dependency injection on
Android. It provides a similar feature with `@InstallIn` for entry points and modules as Anvil.
If you use Hilt, then you don't need to use Anvil.

Hilt includes many other features and comes with some restrictions. For us it was infeasible to
migrate a codebase to Hilt with thousands of modules and many Dagger components while we only
needed the feature to merge modules and component interfaces automatically. We also restrict the
usage of the Dagger annotation processor to only [specific modules](https://speakerdeck.com/vrallev/android-at-scale-at-square?slide=36)
for performance reasons. With Hilt we wouldn't be able to enforce this requirement anymore for
component interfaces. The development of Anvil started long before Hilt was announced and the
internal version is being used in production for a while.

## Roadmap
See [here](https://github.com/square/anvil/blob/main/docs/ROADMAP.md)

## License

    Copyright 2020 Square, Inc.

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
