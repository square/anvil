# Hephaestus

[![Maven Central](https://img.shields.io/maven-central/v/com.squareup.hephaestus/gradle-plugin.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:%22com.squareup.hephaestus%22)
[![CI](https://github.com/square/hephaestus/workflows/CI/badge.svg)](https://github.com/square/hephaestus/actions?query=branch%3Amain)

Hephaestus is a Kotlin compiler plugin to make dependency injection with [Dagger](https://dagger.dev/)
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
  id 'com.squareup.hephaestus' version "${latest_version}"
}
```

Or you can use the old way to apply a plugin:
```groovy
buildscript {
  repositories {
    mavenCentral()
  }
  dependencies {
    classpath "com.squareup.hephaestus:gradle-plugin:${latest_version}"
  }
}

apply plugin: 'com.squareup.hephaestus'
```

## Quick Start

There are three important annotations to work with Hephaestus.

`@ContributesTo` can be added to Dagger modules and component interfaces that should be included
in the Dagger component. Classes with this annotation are automatically merged by the compiler
plugin as long as they are on the compile classpath.

`@MergeComponent` is used instead of the Dagger annotation `@Component`. Hephaestus will generate
the Dagger annotation and automatically include all modules and component interfaces that were
contributed the same scope.

`@MergeSubcomponent` is similar to `@MergeComponent` and should be used for subcomponents instead.

## Scopes

Scope classes are only markers. The class `AppScope` from the sample could look like this:

```kotlin
abstract class AppScope private constructor()
```

These scope classes help Hephaestus make a connection between the Dagger component and which Dagger
modules and other component interfaces to include.

Scope classes are independent of the Dagger scopes. It's still necessary to set a scope for
the Dagger component, e.g.

```kotlin
@Singleton
@MergeComponent(AppScope::class)
interface AppComponent
```

## Exclusions

Dagger modules and component interfaces can be excluded in two different levels.

One class can always replace another one. This is especially helpful for modules that provide
different bindings for instrumentation tests, e.g.

```kotlin
@Module
@ContributesTo(
    scope = AppScope::class,
    replaces = DevelopmentApplicationModule::class
)
object DevelopmentApplicationTestModule {
  @Provides
  fun provideEndpointSelector(): EndpointSelector = TestingEndpointSelector
}
```

The compiler plugin will find both classes on the classpath. Adding both modules
`DevelopmentApplicationModule` and `DevelopmentApplicationTestModule` to the Dagger graph would
lead to duplicate bindings. Hephaestus sees that the test module wants to replace the other and
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

## Advantages

Adding Dagger modules to components in a large modularized codebase with many application targets
is overhead. You need to know where components are defined when creating a new Dagger module and
which modules to add when setting up a new application. This task involves many syncs in the IDE
after adding new module dependencies in the build graph. The process is tedious and cumbersome.
With Hephaestus you only add a dependency in your build graph and then you can immediately test
the build.

Aligning the build graph and Dagger's dependency graph brings a lot of consistency. If code is on
the compile classpath, then it's also included in the Dagger dependency graph.

Modules implicitly have a scope, if provided objects are tied to a scope. Now the scope of a module
is clear without looking at any binding.

With Hephaestus you don't need any composite Dagger module anymore, which only purpose is to
combine multiple modules to avoid repeating the setup for multiple applications. Composite modules
easily become hairballs. If one application wants to exclude a module, then it has to repeat the
setup. These forked graphs are painful and confusing. With Dagger you want to make the decision
which modules fulfill dependencies as late as possible, ideally in the application module.
Hephaestus makes this approach a lot easier by generating the code for included modules. Composite
modules are redundant. You make the decision which bindings to use by importing the desired module
in the application module.

## Performance

Hephaestus is a convenience tool. Similar to Dagger it doesn't improve build speed compared to
writing all code manually before running a build. The savings are in developer time.

The median overhead of Hephaestus is around 4%, which often means only a few hundred milliseconds
on top. The overhead is marginal, because Kotlin code is still compiled incrementally and Kotlin
compile tasks are skipped entirely, if nothing has changed. This doesn't change with Hephaestus.

![Benchmark](images/benchmark.png?raw=true "Benchmark")

## Kotlin compiler plugin

We investigated whether other alternatives like a bytecode transformer and an annotation processor
would be a better option, but ultimately decided against them. For what we tried to achieve a
bytecode transformer runs too late in the build process; after the Dagger components have been
generated. An annotation processor especially when using Kapt would be too slow. Even though the
Kotlin compiler plugin API isn't stable and contains bugs we decided to write a compiler plugin.

## Limitations

#### No Java support

Hephaestus is a Kotlin compiler plugin, thus Java isn’t supported. You can use Hephaestus in
modules with mixed Java and Kotlin code for Kotlin classes, though.

#### Incremental Kotlin compilation breaks compiler plugins

There are two bugs that affect the Hephaestus Kotlin compiler plugin:
* [Incremental compilation breaks compiler plugins](https://youtrack.jetbrains.com/issue/KT-38570)
* [AnalysisResult.RetryWithAdditionalRoots crashes during incremental compilation with java classes in classpath](https://youtrack.jetbrains.com/issue/KT-38576)

The Gradle plugin implements workarounds for these bugs, so you shouldn't notice them. Side effects
are that incremental Kotlin compilation is disabled for stub generating tasks (which don't run a
full compilation before KAPT anyways). The flag `usePreciseJavaTracking` is disabled, if the
module contains Java code.

## Hilt

[Hilt](https://dagger.dev/hilt/) is Google's opinionated guide how to dependency injection on
Android. It provides a similar feature with `@InstallIn` for entry points and modules as Hephaestus.
If you use Hilt, then you don't need to use Hephaestus.

Hilt includes many other features and comes with some restrictions. For us it was infeasible to
migrate a codebase to Hilt with thousands of modules and many Dagger components while we only
needed the feature to merge modules and component interfaces automatically. We also restrict the
usage of the Dagger annotation processor to only [specific modules](https://speakerdeck.com/vrallev/android-at-scale-at-square?slide=36)
for performance reasons. With Hilt we wouldn't be able to enforce this requirement anymore for
component interfaces. The development of Hephaestus started long before Hilt was announced and the
internal version is being used in production for a while.

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