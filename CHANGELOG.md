# Change Log

**Unreleased**
--------------

0.4.0
-----

_2024-11-02_

**Note**: Up to this point, this library has largely attempted to preserve the pre-K2 Anvil impl and compatibility to ease adoption. This release marks a shift in that approach. New features will be implemented now (beyond just KSP support) and eventually K1 support will be dropped.

- **New**: Experimental support for jakarta.inject annotations. Note that Dagger itself appears to only partially support these at the moment. Generated code is identical, but jakarta `@Inject`/`@Qualifier`/`@Scope` annotations should be recognized now.
- **Enhancement**: The `annotations` and `annotations-optional` artifacts are now Kotlin multiplatform libraries. This allows for easier integration with multiplatform projects and/or adoption of kotlin-inject.
- **Enhancement**: `@SingleIn` and `@ForScope` can now be used with jakarta.inject and kotlin-inject.
- **Enhancement**: Improve error messaging for error types used as annotation arguments.
- Update Dagger to `2.52`.

Special thanks to [@mrmans0n](https://github.com/mrmans0n) and [@gabrielittner](https://github.com/gabrielittner) for contributing to this release!

0.3.3
-----

_2024-10-19_

- **Enhancement**: Move various KSP helper functions to `compiler-utils`.

Special thanks to [@WhosNickDoglio](https://github.com/WhosNickDoglio) for contributing to this release!

0.3.2
-----

_2024-10-11_

- **Enhancement**: Report more context for error types, such as the name of the parameter it came from.
- **Fix**: For generic types, check for error types in their type arguments as well.

Special thanks to [@jmartinesp](https://github.com/jmartinesp) for contributing to this release!

0.3.1
-----

_2024-09-02_

- **Enhancement**: Better handle error types and round deferral for generated types.

0.3.0
-----

_2024-08-31_

- **New**: Add option to disable contributes subcomponent handling. This can be useful if working in a codebase or project that doesn't use `@ContributeSubcomponent` and thus doesn't need to scan the classpath for them while merging. More details can be found in the `## Options` section of `FORK.md`.
- **Enhancement**: Improve hint caching during contribution merging. Hints from the classpath are now only searched for once rather than every round.
- **Enhancement**: Improve error messaging when class lookups fail.
- **Fix**: Don't use `ClassName.toString()` for `KSClassDeclaration` lookups.
- **Fix**: Ensure round processing is correctly reset if no `@ContributeSubcomponent` triggers are found in a given round. This was an edge case that affected projects with custom code generators that generated triggers in a later round.

0.2.6
-----

_2024-08-22_

- Significantly improve performance during component merging.
- Add a new `anvil-ksp-verbose` KSP option to enable verbose logging, such as timing information.
- **Fix:** Sort contributed interfaces when merging to ensure build cache idempotence. This also adds a few defensive stable sorts for other areas.
- **Fix:** Fix resolution of nested class generics in constructor injection.

0.2.5
-----

_2024-08-13_

- Source `AnvilKspExtension.supportedAnnotationTypes` in contribution merging when deciding when to defer.

0.2.4
-----

_2024-08-12_

- Significantly improve error messaging when encountering error types. All these cases _should_ now also show the location of the error type in source and ease debugging.

0.2.3
-----

_2024-08-08_

- **Fix:** Use more unique names for default parent component functions.
- **Fix:** Strip `ABSTRACT` modifiers if present when overriding component-returning functions.

0.2.2
-----

_2024-08-08_

- **Fix:** Correctly track inputs to KSP command line options so they don't result in incorrect task build cache hits.
- **Fix:** Don't set default AnvilExtension property values until after all properties are initialized.

0.2.1
-----

_2024-08-08_

- **Fix:** Don't cache symbols between processing rounds. This better supports KSP2.
- **Fix:** Workaround Kotlin plugin option parsing limitations. Contributing annotations should now be colon-delimited, and the underlying KSP argument is changed to a more consistent `anvil-ksp-extraContributingAnnotations`.

0.2.0
-----

_2024-08-07_

- **New:** Introduce an `AnvilKspExtension` API. See instructions [here](https://github.com/ZacSweers/anvil/blob/main/FORK.md#custom-code-generators).
- **Fix:** Allow contribution merging if factory generation is enabled.

0.1.0
-----

_2024-08-03_

Initial release of Anvil KSP. See [FORK.md](https://github.com/ZacSweers/anvil/blob/main/FORK.md) for more information and installation instructions.

---

For past Anvil release notes, see the original changelog: https://github.com/square/anvil/blob/main/CHANGELOG.md#250-beta09---2024-05-09
