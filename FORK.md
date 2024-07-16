Fork Instructions
=================

This repo is a fork of Anvil that seeks to complete KSP support. At the point of divergence,
Anvil supported KSP contributions and factories, but not yet merging contributions or 
contributing subcomponents.

## Blockers

Before trying to use KSP component merging, check this issue to see if any of the issues
listed there affect you: https://github.com/ZacSweers/anvil/issues/16.

## Installation

Right now, the easiest way to use this fork is to clone it and publish it to your local Maven.

```shell
git clone git@github.com:ZacSweers/anvil.git

cd anvil

# Change the VERSION_NAME in gradle.properties to a custom version, such as 2.6.0-local01

./gradlew publishToMavenLocal -x dokkaHtml --no-configuration-cache
```

Then consume this in your project.

```kotlin
// Change the anvil version in libs.versions.toml to the one you set in VERSION_NAME above

// In build.gradle/settings.gradle. Make sure you set this for both the buildscript and project repositories
repositories {
  // ...
  mavenLocal()
}
```

## Migration


### 1. KSP Contributions

It _should_ be possible to migrate to this fork with minimal source changes.

First, update to KSP contribution. It's better to do this first before proceeding to component 
merging as it allows for incremental adoption. However, you can do both at the same time if you'd 
rather.

```kotlin
// In build.gradle
anvil {
  useKsp(
    contributesAndFactoryGeneration = true,
    componentMerging = false,
  )
}
```

This will use KSP for contributions (i.e. `@Contributes*` annotations) and factory generation. 

Note that you may need to set up KAPT stub gen and KAPT to tasks to source KSP outputs for this. If 
you are doing both steps at the same time, you can skip this.

Note that you also need to ensure that your kapt tasks still target Kotlin language version 1.9 for 
this step as the original Anvil IR merger only works in Kotlin 1.9.

### 2. KSP Component Merging

Once you have KSP contributions working, you can enable component merging.

```kotlin
// In build.gradle
anvil {
  useKsp(
    contributesAndFactoryGeneration = true,
    componentMerging = true,
  )
}

```

Next - remove dagger-compiler from `kapt` configurations and move it to the `ksp` configuration instead.

If this was the only bit using kapt, you can now remove kapt entirely! You can also remove any `languageVersion` overrides forcing Kotlin 1.9.

This is where you will need to do some small source changes.

Replace any creator annotations with anvil equivalents.

| Before                  | After                        |
|-------------------------|------------------------------|
| `@Component.Factory`    | `@MergeComponent.Factory`    |
| `@Component.Builder`    | `@MergeComponent.Builder`    |
| `@Subcomponent.Factory` | `@MergeSubcomponent.Factory` |
| `@Subcomponent.Builder` | `@MergeSubcomponent.Builder` |

This is the only source change you should need to make! Anvil will generate source-compatible shims 
where necessary for everything else.

You _should_ be able to build now.

## Compatibility

### Using Anvil KSP without dagger-ksp

It _should_ be possible to use Anvil KSP and still run dagger in kapt, but you will need to ensure 
KSP's output sources are wired in as inputs to the appropriate kapt tasks. If you do this, you 
_should_ be able to safely use K2 KAPT now and remove `languageVersion` overrides, as Anvil will not 
longer be running a compiler plugin in kapt's stub gen task.

### Using Anvil KSP with KSP2

See the Blockers section for any issues here. At the time of writing, dagger-ksp does not work in 
KSP2 but you may be able use Anvil KSP with KSP2 IFF you use dagger with kapt. YMMV.

### Backward compatibility

This fork should be backward-compatible with code generated from Anvil 2.5.0-beta09. However, it's 
not well-tested and encouraged to recompile all Anvil-processed code after migrating to this fork. 
Especially if you use `@ContributesSubcomponent` or `@MergeComponent`, as the generated code for 
these scenarios has changed the most in the move to KSP.

## Future

In the medium term, I plan to tune the gradle plugin to be able to consume this in existing projects 
easily and have the gradle plugin automatically replace dependencies with the fork's.

Long term, this will ideally move back upstream to Anvil main.
