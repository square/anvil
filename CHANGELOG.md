# Change Log

## [Unreleased]

### Added

### Changed

- Upgrade to Dagger `2.46.1`
- Upgrade to kotlinx-metadata `0.6.0`

### Deprecated

### Removed

### Fixed

### Security

### Custom Code Generator

### Other Notes


## [2.4.6] - 2023-05-25

### Changed

- Upgrade KotlinPoet to `1.13.0` and fix bug uncovered by new TypeName#equals/hashCode changes, see #699.
- Upgrade Kotlin to `1.8.21`.


## [2.4.5] - 2023-04-06

### Changed

- Raise minimum Kotlin version to 1.8.20.
- Raise minimum AGP version to 7.1.0.
- The Kotlin Gradle Plugin (both the core plugin and the API artifact) are no longer a dependency of the Anvil Gradle Plugin. Instead, it's now a `compileOnly` dependency, allowing the plugin to defer to whatever version the user already has. If you were accidentally depending on KGP through Anvil, you'll need to explicitly add the plugin yourself now.

### Removed

- Support for the old compiler backend. The Java stub generating task uses the new backend by default since Kotlin 1.8.20.

### Fixed

- Fix duplicate generated binding method names. If a class contributes multiple bindings and the bound types have the same short name, then Anvil would generate methods with duplicate names that clash in the end.
- Support `Any` as bound type when using `@ContributesBinding` or `@ContributesMultibinding`, see #680.

### Custom Code Generator

- Add option to change the JVM target when testing code generators with the custom `AnvilCompilation` class, see #682.


# [2.4.4] - 2023-01-12

### Added

- Added support for Kotlin 1.8.

### Changed

- The [issue](https://youtrack.jetbrains.com/issue/KT-38576) that required disabling precise Java tracking is not needed anymore. The workaround has been removed.

### Removed

- Remove support for Kotlin 1.7. Anvil only supports Kotlin 1.8 moving forward.

### Custom Code Generator

- Add ability to query top-level functions and properties. The entry point is `projectFiles.topLevelFunctionReferences(module)` and `projectFiles.topLevelPropertyReferences(module)`. This allows you write code generators reacting to top-level functions and properties and not only classes, see #644.
- The `FunctionReference` type has been renamed to `MemberFunctionReference` and a new super type `FunctionReference` has been introduced for `TopLevelFunctionReference` and `MemberFunctionReference`.
- The `PropertyReference` type has been renamed to `MemberPropertyReference` and a new super type `PropertyReference` has been introduced for `TopLevelPropertyReference` and `MemberPropertyReference`.

### Other Notes

- We received a report from one project that there were issues with using Dagger 2.42 with this release. You may need to use Dagger 2.44+ when upgrading to Anvil 2.4.4.


## [2.4.3] - 2022-12-16

### Added
- Add support for generating MapKeyCreator classes when generating Dagger factories, see #651.
- `@Binds` methods are now validated for correctness when generating Dagger factories, see #649.

### Changed
- Upgrade Kotlin to `1.7.20` and Gradle to `7.5.1`, see #643.
- For Kotlin `1.8.x` releases, we now use a fork of `kotlin-compile-testing`: `dev.zacsweers.kctfork:core:0.1.0-1.8.0-Beta01`.
- Use Anvil version `2.4.3-1-8-0-RC` if you want to test Kotlin `1.8.0-RC`. Until Anvil has fully adopted Kotlin `1.8` we'll publish additional versions that are required due to compiler API incompatibilities.

### Fixed
- Fix resolving types whose packages are wrapped in backticks, see #665.
- Fix resolving types when paired with qualifiers, see #664.
- Fix inconsistency between Dagger and Anvil for generated factory names involving a dash-separated module name, see #653.
- Fix resolving types whose names are wrapped in backticks, see #641.
- Update outdated documentation on incremental compilation limitations, see #637.


## [2.4.2] - 2022-08-23

### Removed

- Remove support for Kotlin 1.6. Anvil only supports Kotlin 1.7 moving forward.

### Fixed

- Upgraded KotlinPoet to the latest version to fix potential conflicts with other libraries, see #613.
- When resolving `FqName`s check the inner class hierarchy for the right reference, see #617.
- An imported top level function should not be considered a class.
- Support star projections for wrapped type references, see #620.
- Support contributing types with `Any` as `boundType`, see #619.
- Improve the error message for contributed inner classes, see #616.
- Don't share the output directory for the `DisableIncrementalCompilationTask`, if there are multiple Kotlin compilation tasks for the same module, see #602.
- Unwrap types from type aliases for `TypeReference`, see #611.
- Remove incremental compilation workaround, see #627.
- Fix annotation arguments using string templates not being parsed correctly, see #631.
- Align Anvil with Dagger and don't support member injection for private fields, see #341.


## [2.4.1] - 2022-06-09

### Changed

- **Attention:** This version supports Kotlin `1.7.0` only. For Kotlin `1.6.*` support please use version `2.4.1-1-6` instead. Future Anvil versions will remove support for Kotlin 1.6.  

### Fixed

- Support wildcard imports for constants when resolving annotation arguments, see #592.
- Fix dagger factory member injection not handling generics, see #486.
- Correctly merge bindings from all scopes, if multiple `@Merge*` annotations are used, see #596.

### Custom Code Generator
 
- Change the method to get all super classes for `ClassReference` to return `TypeReference` instead.
- Avoid a stackoverflow when querying all super types, see #587.
- Create `PropertyReference.Psi` from primary constructor properties to have the same behavior as the descriptor implementation, see #609.


## [2.4.0] - 2022-03-28

### Added

- Anvil annotations are repeatable. Modules and bindings can now be contributed multiple times to different scopes. Multiple scopes can be merged in a single component, see #236.
- Rewrote many of the internals of Anvil and as a result Anvil is up to 41% faster due to heavy caching of already parsed code structures.
- Automatically publish snapshots for the `main` branch.
- Documented Anvil's internal, see [here](https://github.com/square/anvil/blob/main/docs/INTERNALS.md).

### Changed

- Many of the internals of Anvil were rewritten and the non-stable APIs of the `compiler-utils` artifact have changed. Some highlights:
  - Instead of working with PSI or Descriptor APIs directly, you should work with the common `ClassReference` API.
  - `ClassReference` is a sealed class and either implemented with PSI or Descriptors, so it's easy to fallback to a specific API and add your own extensions.
  - The entry point to iterate through all classes used be `classesAndInnerClass(module)`, use `classAndInnerClassReferences()` instead. 

### Removed

- Removed support for Kotlin `1.5`.
- Removed deprecated APIs from the `AnvilExtension` in the gradle plugin.

### Fixed

- Filter duplicate generated properties, see #565.
- Generate code for `@ContributedSubcomponent` when the trigger is created AFTER the contribution, see #478.
- Properly parse the `FqName` if the type reference is an inner class, see #479.


## [2.4.0-M2] - 2022-03-015

### Added

- Made annotations repeatable. Modules and bindings can now be contributed multiple times to different scopes. Multiple scopes can be merged in a single component.

### Fixed

- Filter duplicate generated properties, see #565.


## [2.4.0-M1] - 2022-03-03

### Added

- Rewrote many of the internals of Anvil and as a result Anvil is up to 41% faster.
- Automatically publish snapshots for the `main` branch.
- Documented Anvil's internal, see [here](https://github.com/square/anvil/blob/main/docs/INTERNALS.md).

### Removed

- Removed support for Kotlin `1.5`.
- Removed deprecated APIs from the `AnvilExtension` in the gradle plugin.

### Fixed

- Generate code for `@ContributedSubcomponent`s when the trigger is created AFTER the contribution, see #478.
- Properly parse the `FqName` if the type reference is an inner class, see #479.


## [2.3.11] - 2022-01-28

### Changed

- Promote `@ContributesSubcomponent` to stable, see #474.
- Use Anvil version `2.3.11-1-6-10` if you use Kotlin `1.6.10`. Until Anvil hasn't adopted Kotlin `1.6` I'll publish additional versions that are required due to compiler API incompatibilities.
  - **Attention:** This is the last release to simultaneously support Kotlin 1.5 and 1.6. The next release will only support Kotlin 1.6.

### Added

- Support replacing `@ContributesSubcomponent` through a new `replaces` attribute, see #466.
- Support member injection for super types from different modules, see #438, #439 and #442.
- Support custom `CodeGenerator`s for `AnvilCompilation`. This makes it easier to unit-test specific scenarios, see #470.
- Detect duplicated generated files (helpful for custom `CodeGenerator`s), see #467.

### Fixed

- Avoid duplicate bindings when a `@ContributesSubcomponent` uses a factory and is used in multiple parent components, see #459.
- Fix rare duplicate bindings error for the same type with incremental compilation, see #460.
- Fix the import resolver for wildcard imports and inner classes, see #468.


## [2.3.10] - 2021-11-24

### Changed

- New experimental annotation [`@ContributesSubcomponent`](https://github.com/square/anvil/blob/main/annotations/src/main/java/com/squareup/anvil/annotations/ContributesSubcomponent.kt) to delay merging contributions until the parent component is created, see #160.
- Add option to contribute class using `@MergeInterfaces` and `@MergeModules` to another scope with `@ContributesTo`.
- Add a workaround for a bug in the Kotlin 1.6.0 compiler, see [KT-49340](https://youtrack.jetbrains.com/issue/KT-49340).
- Use Anvil version `2.3.10-1-6-0` if you want to test Kotlin `1.6.0`. Until Anvil hasn't adopted Kotlin `1.6` I'll publish additional versions that are required due to compiler API incompatibilities.


## [2.3.9] - 2021-11-08

### Changed

- Add a workaround for AGP to sync generated sources with `syncGeneratedSources`, see #413.
- Ignore functions with defaults in assisted factories, see #415.
- Use Anvil version `2.3.9-1-6-0-RC2` if you want to test Kotlin `1.6.0-RC2`. Until Anvil hasn't adopted Kotlin `1.6` I'll publish additional versions that are required due to compiler API incompatibilities.
- Use Anvil version `2.3.9-1-6-0` if you want to test Kotlin `1.6.0`. Until Anvil hasn't adopted Kotlin `1.6` I'll publish additional versions that are required due to compiler API incompatibilities.


## [2.3.8] - 2021-11-04

### Changed

- Add an option in the Anvil DSL `syncGeneratedSources` to sync generated sources in the IDE, see #412.
- Fall back to PSI parsing for `BindingModuleGenerator`, see #310. (this allows you generated `@ContributesBinding` annotations in custom code generators)
- Support generic supers for assisted factories when the assisted factory interface is generated, see #407.
- Support generic type resolution in assisted factories, see #395. (regression in previous release)
- Align `TypeNames` for assisted lambda arguments between descriptors and PSI, see #400. (regression in previous release)
- Enable experimental Anvil APIs by default in the compiler testing utilities, see #398.
- Make it easy the feed a compilation result to another Anvil compilation in the testing utilities, see #404.
- Use Anvil version `2.3.8-1-6-0-RC2` if you want to test Kotlin `1.6.0-RC2`. Until Anvil hasn't adopted Kotlin `1.6` I'll publish additional versions that are required due to compiler API incompatibilities.


## [2.3.7] - 2021-10-19

### Changed

- Allow configuring the `KotlinCompilation` when using the utilities to test custom code generators, see #386.
- Support invariant and covariant type parameters properly, see #388.
- Use Psi parsing for assisted factory generation, see #326.
- Support assisted injection for deeply nested inner classes, see #394.
- Use Anvil version `2.3.7-1-6-0-RC` if you want to test Kotlin `1.6.0-RC`. Until Anvil hasn't adopted Kotlin `1.6` I'll publish additional versions that are required due to compiler API incompatibilities.


## [2.3.6] - 2021-10-12

- Support constant members in annotations properly, see #379.
- Use Anvil version `2.3.6-1-6-0-RC` if you want to test Kotlin `1.6.0-RC`. Until Anvil hasn't adopted Kotlin `1.6` I'll publish additional versions that are required due to compiler API incompatibilities.

## [2.3.5] - 2021-10-06

- Upgraded Anvil to Kotlin `1.5.31`.
- Use correct setter name for members in generated factories, see #362.
- Handle the special case of injecting a `Provider<Lazy<Type>>` properly for member injection, see #365.
- Make sure in JVM modules that the configuration `anvilMain` extends `anvil` so that code generators are picked up, see #368.
- Support member injection for super classes, see #343.
- Prefer Kotlin collection types when Java collections are imported through a star import, see #371.


## [2.3.4] - 2021-08-27

### Changed

- Upgraded Anvil to Kotlin `1.5.21`.
- Properly inject members when property setters are annotated, see #340.
- Properly inject members when using assisted injection, see #342.
- Don't generate a singleton factory (object), if the class with the inject constructor has any type parameter, see #348.
- Look for star imports before checking imports from the Kotlin package. Star imports have a higher priority, see #358.
- Handle the special case of injecting a `Provider<Lazy<Type>>` properly, see #344.


## [2.3.3] - 2021-06-23

### Changed

- Fix a bug in the Gradle plugin that accidentally realized all tasks in the module instead of evaluating them lazily, see #330.


## [2.3.2] - 2021-06-15

### Changed

- Remove an accidental required dependency on the Android Gradle Plugin, see #323.
- Ensure that excluded bindings and modules are only excluded for the specific component that merges a scope and not all components merging the same scope, see #321.
- Disable precise Java tracking for the stub generating Kotlin compilation task when needed, see #324.


## [2.3.1] - 2021-06-09

### Changed

- Ignore replaced bindings/modules from excluded bindings/modules, see #302.
- Create separate Anvil configurations for each build type, see #307.
- Introduce a new VariantFilter for the Gradle extension. This API allows you to enable and disable Anvil for specific variants. Allow to override `generateDaggerFactories`, `generateDaggerFactoriesOnly` and `disableComponentMerging` through the variant filter, see #100.


## [2.3.0] - 2021-06-02

### Changed

- Add option to extend Anvil with your own `CodeGenerator`, see [here](README.md#extending-anvil) and #265.
- Use Gradle Property APIs in the Anvil extension. This is a source-breaking change (but binary-compatible) for Kotlin and .kts consumers of the Anvil Gradle plugin, see #284.
- Upgrade Anvil to Kotlin `1.5.10`. The old legacy compiler backend is still supported and the IR backend not required yet.


## [2.2.3] - 2021-05-25

### Changed

- Support the JVM and Android targets for Kotlin Multiplatform projects, see #222.
- Add a generation only mode for Anvil in order to avoid performance penalties when still using KAPT in the same module, see #258.
- Respect qualifiers when checking whether there is a duplicate binding, see #270.


## [2.2.2] - 2021-05-16

### Changed

- Handle inner generic classes in factories for constructor injection properly, see #244.
- Generate a correct factory when a class has both constructor and member injection, see #246.
- Make generated assisted factories match interface function names, see #252.
- Fix a parsing error for inner class reference expressions, see #256.
- Verify that the qualifier is added to generated methods for member injection, see #264.


## [2.2.1] - 2021-04-09

### Changed

- Fix problematic check for Kotlin annotations, see #232.
- Handle Lazy assisted parameters properly in generated assisted factories.
- Build and test Anvil with Kotlin 1.5.0-M2 in CI.


## [2.2.0] - 2021-03-17

### Changed

- `@ContributesBinding` supports qualifiers now, see the README and documentation for examples.
- You can generate multibindings with `@ContributesMultibinding` now, see the README and documentation for examples, see #152.
- Upgrade Dagger to `2.32`. Generating factories for assisted injection is no longer compatible with older Dagger versions due to the behavior change in Dagger itself. Make sure to use Dagger version `2.32` or newer in your project, too.
- `@ContributesBinding` has a priority field now for cases where you don't have access to replaced bindings at compile time, see #161.
- Use the mangled function name to generate the factory for a provider method.
- Handle fully qualified names with type parameters for Dagger factories properly, see #198.
- Support classes in the root package and don't crash, see #227.


## [2.1.0] - 2021-02-05

### Changed

- This release upgrades Anvil to Kotlin `1.4.30`. Older Kotlin version are no longer supported moving forward.
- The IR extension is enabled by default. Anvil is compatible with the new IR and old compiler backend.


## [2.0.14] - 2021-02-04

### Changed

- Anvil falsely detected provider methods inside of companion objects of interfaces as abstracted, see #187.
- Support nullable parameters for assisted injection properly, see #189.


## [2.0.13] - 2021-02-04

### Changed

- This release accidentally used a Kotlin preview version.


## [2.0.12] - 2021-02-02

### Changed

- Support Dagger's assisted injection feature and generate necessary code, see #165.
- Throw an error if a provider method is abstract, see #183.


## [2.0.11] - 2020-12-28

### Changed

- Declare the Dagger Factory generation option as stable.
- Support a mode that only generates Dagger Factories through the `generateDaggerFactoriesOnly` flag, see #164.
- Suppress any deprecation warnings in generated code, see #169.


## [2.0.10] - 2020-11-20

### Changed

- Upgrade Kotlin to `1.4.20`. Note that this version **no longer works** with Kotlin `1.4.10` or older, see #159.


## [2.0.9] - 2020-11-20

### Changed

- Upgrade Kotlin to `1.4.10`. Note that this release is not compatible with Kotlin `1.4.20`.
- Remove the usage of the now deprecated `KotlinGradleSubplugin` class, see #30.
- Enable Kotlin's explicit API mode in the `:annotations` artifact.


## [2.0.8] - 2020-11-12

### Changed

- Support Kotlin's explicit API mode in generated code, see #144.
- Handle packages starting with an uppercase letter properly, see #150.
- Use the correct import if an uppercase function is imported with the same name as the type, see #154.
- Support properties as providers in Dagger modules, see #149.


## [2.0.7] - 2020-11-12 [YANKED]

### Changed

- **DO NOT USE!** This version was compiled with the wrong Kotlin version.


## [2.0.6] - 2020-10-06

### Changed

- Support constructor injection for classes with bounded types, see #126.
- Print a better error message for Dagger provider methods with implicit return types, see #124.
- Fix another instance of absolute paths as task inputs, see #65.
- Use lazy APIs in the Gradle plugin for task configuration avoidance.
- Handle named imports correctly, see #137.


## [2.0.5] - 2020-09-18

### Changed

- Support type parameters for @Inject constructors, see #111.
- Handle named imports properly, see #115.
- Fix a bug for Gradle's experimental configuration caching feature, see #113.
- Implement an extension for the new Kotlin IR backend. This extension will ship with Kotlin 1.4.20, see #11.
- Build the project and run tests in CI with JDK 11.
- Preserve variance keywords when generating factory classes, see #120.


## [2.0.4] - 2020-09-18 [YANKED]

### Changed

- Ignore, this release was built with Kotlin 1.4.20 accidentally.


## [2.0.3] - 2020-09-08

### Changed

- Support classes with multiple generic parameters for field injection, see #91.
- Fix missing Factory class when Anvil generates them, `@MergeModules` is used and a Kotlin object uses `@ContributesBinding` in the dependency graph.
- Fix absolute paths in Kapt tasks, see #65.
- Similar to Dagger throw an error if provider names clash, see #99.
- Verify that the replacement and exclusion mechanism is only used within the same scope, see #107.
- Rework how imports are resolved for generated code. That should fix problems around inner classes #97 and avoid unused imports #82.


## [2.0.2] - 2020-09-01

### Changed

- Support using `@ContributesBinding` for objects and generate a `@Provides` rather than a `@Binds` function.
- Allow using Anvil to generate Dagger factories in modules using `@Subcomponent`, see #74.
- Reduce the size of the generated bytecode in certain scenarios, see #76.
- Stop adding the `@Generated` annotation. This leads to issues on Android where this annotation doesn't exist, see #75.
- Support classes starting with a lowercase character, see #80.
- Support generic classes using field injection, see #91.
- Add missing import for inject constructor factories when the injected type is an inner class, see #79.


## [2.0.1] - 2020-08-27

### Changed

- Throw a compilation error when a `@ContributesBinding` annotation binds a generic type.
- Remove absolute paths in Kotlin compilation task inputs #65.
- Add new experimental feature to generate Dagger factories for faster build times.


## [2.0.0] - 2020-08-07

### Changed

- Change the `replaces` attribute from a single class to an array. This gives the API more flexibility and avoids redundant classes. E.g. one Dagger module with several binding and provider methods may wish to replace multiple other Dagger modules.
- Introduce the new `@ContributesBinding` annotation. This annotation allows you to contribute binding methods without actually writing a Dagger module.
    ```kotlin
    interface Authenticator
    
    @ContributesBinding(AppScope::class)
    class RealAuthenticator @Inject constructor() : Authenticator
    
    // The generated and automatically included Dagger module would look similar like this:
    @Module
    @ContributesTo(AppScope::class)
    abstract class AuthenticatorModule {
      @Binds abstract fun bindRealAuthenticator(authenticator: RealAuthenticator): Authenticator
    }
    ```
- Support nested classes for contributed Dagger modules and component interfaces if the outer class uses a different scope #45.


## [1.0.6] - 2020-07-29

### Changed

- Add the annotation artifact as an `implementation` dependency instead of `api` #40.
- Remove the strong dependency on the Android Gradle Plugin and allow Anvil to be used in pure JVM modules #39.
- **Note:** This version is compatible with Kotlin `1.3.72` and `1.4.0-rc`. The [bug](https://youtrack.jetbrains.com/issue/KT-40214) that required special builds for the 1.4 milestone releases was fixed.


## [1.0.5-1.4-M3] - 2020-07-24

### Changed

- Renamed the project from Hephaestus to Anvil #12. **IMPORTANT:** Anvil is not compatible with Hephaestus and you must upgrade the plugin in all of your libraries. The artifact coordinates changed, too.
- Same as `1.0.5`, only built with Kotlin 1.4-M3.


## [1.0.5] - 2020-07-24

### Changed

- Renamed the project from Hephaestus to Anvil #12. **IMPORTANT:** Anvil is not compatible with Hephaestus and you must upgrade the plugin in all of your libraries. The artifact coordinates changed, too.


## [1.0.4-1.4-M3] - 2020-07-24

### Changed

- Ensure that types cannot be included and excluded at the same time, which leads to unexpected results.
- Fix a classpath issue in the Gradle plugin when using the new plugin syntax #31.
- Same as `1.0.4`, only built with Kotlin 1.4-M3.


## [1.0.4] - 2020-07-24

### Changed

- Ensure that types cannot be included and excluded at the same time, which leads to unexpected results.
- Fix a classpath issue in the Gradle plugin when using the new plugin syntax #31.


## [1.0.3-1.4-M3] - 2020-07-17

### Changed

- Bug fix for Gradle's configuration caching #29.
- Same as `1.0.3`, only built with Kotlin 1.4-M3.


## [1.0.3] - 2020-07-17

### Changed

- Bug fix for Gradle's configuration caching #29.


## [1.0.2] - 2020-07-17 [YANKED]

### Changed

- Discarded


## [1.0.1] - 2020-07-09

### Changed

- Added support for Gradle's configuration caching and made task configuration lazy #19.
- Fixed the implicit requirement to apply plugins in a specific order #8 #16.
- Fixed file path issues on Windows #13 #24.
- Allow Dagger modules that are interfaces #14 #24.
- Test Hephaestus with Kotlin `1.4` and `1.4.2`.
- Use `1.0.1-1.4-M3` to use Hephaestus in projects with Kotlin 1.4-M3.


## [1.0.0] - 2020-06-16

- Initial release.



[Unreleased]: https://github.com/square/anvil/compare/v2.4.6...HEAD
[2.4.6]: https://github.com/square/anvil/releases/tag/v2.4.6
[2.4.5]: https://github.com/square/anvil/releases/tag/v2.4.5
[2.4.4]: https://github.com/square/anvil/releases/tag/v2.4.4
[2.4.3]: https://github.com/square/anvil/releases/tag/v2.4.3
[2.4.2]: https://github.com/square/anvil/releases/tag/v2.4.2
[2.4.1]: https://github.com/square/anvil/releases/tag/v2.4.1
[2.4.0]: https://github.com/square/anvil/releases/tag/v2.4.0
[2.4.0-M2]: https://github.com/square/anvil/releases/tag/v2.4.0-M2
[2.4.0-M1]: https://github.com/square/anvil/releases/tag/v2.4.0-M1
[2.3.11]: https://github.com/square/anvil/releases/tag/v2.3.11
[2.3.10]: https://github.com/square/anvil/releases/tag/v2.3.10
[2.3.9]: https://github.com/square/anvil/releases/tag/v2.3.9
[2.3.8]: https://github.com/square/anvil/releases/tag/v2.3.8
[2.3.7]: https://github.com/square/anvil/releases/tag/v2.3.7
[2.3.6]: https://github.com/square/anvil/releases/tag/v2.3.6
[2.3.5]: https://github.com/square/anvil/releases/tag/v2.3.5
[2.3.4]: https://github.com/square/anvil/releases/tag/v2.3.4
[2.3.3]: https://github.com/square/anvil/releases/tag/v2.3.3
[2.3.2]: https://github.com/square/anvil/releases/tag/v2.3.2
[2.3.1]: https://github.com/square/anvil/releases/tag/v2.3.1
[2.3.0]: https://github.com/square/anvil/releases/tag/v2.3.0
[2.2.3]: https://github.com/square/anvil/releases/tag/v2.2.3
[2.2.2]: https://github.com/square/anvil/releases/tag/v2.2.2
[2.2.1]: https://github.com/square/anvil/releases/tag/v2.2.1
[2.2.0]: https://github.com/square/anvil/releases/tag/v2.2.0
[2.1.0]: https://github.com/square/anvil/releases/tag/v2.1.0
[2.0.14]: https://github.com/square/anvil/releases/tag/v2.0.14
[2.0.13]: https://github.com/square/anvil/releases/tag/v2.0.13
[2.0.12]: https://github.com/square/anvil/releases/tag/v2.0.12
[2.0.11]: https://github.com/square/anvil/releases/tag/v2.0.11
[2.0.10]: https://github.com/square/anvil/releases/tag/v2.0.10
[2.0.9]: https://github.com/square/anvil/releases/tag/v2.0.9
[2.0.8]: https://github.com/square/anvil/releases/tag/v2.0.8
[2.0.7]: https://github.com/square/anvil/releases/tag/v2.0.7
[2.0.6]: https://github.com/square/anvil/releases/tag/v2.0.6
[2.0.5]: https://github.com/square/anvil/releases/tag/v2.0.5
[2.0.4]: https://github.com/square/anvil/releases/tag/v2.0.4
[2.0.3]: https://github.com/square/anvil/releases/tag/v2.0.3
[2.0.2]: https://github.com/square/anvil/releases/tag/v2.0.2
[2.0.1]: https://github.com/square/anvil/releases/tag/v2.0.1
[2.0.0]: https://github.com/square/anvil/releases/tag/v2.0.0
[1.0.6]: https://github.com/square/anvil/releases/tag/v1.0.6
[1.0.5-1.4-M3]: https://github.com/square/anvil/releases/tag/v1.0.5-1.4-M3
[1.0.5]: https://github.com/square/anvil/releases/tag/v1.0.5
[1.0.4-1.4-M3]: https://github.com/square/anvil/releases/tag/v1.0.4-1.4-M3
[1.0.4]: https://github.com/square/anvil/releases/tag/v1.0.4
[1.0.3-1.4-M3]: https://github.com/square/anvil/releases/tag/v1.0.3-1.4-M3
[1.0.3]: https://github.com/square/anvil/releases/tag/v1.0.3
[1.0.2]: https://github.com/square/anvil/releases/tag/v1.0.2
[1.0.1]: https://github.com/square/anvil/releases/tag/v1.0.1
[1.0.0]: https://github.com/square/anvil/releases/tag/v1.0.0
