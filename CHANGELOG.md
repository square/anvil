# Changelog

## Next Version

## 1.0.6 (2020-07-29)

* Add the annotation artifact as an `implementation` dependency instead of `api` #40.
* Remove the strong dependency on the Android Gradle Plugin and allow Anvil to be used in pure JVM modules #39.

**Note:** This version is compatible with Kotlin `1.3.72` and `1.4.0-rc`. The [bug](https://youtrack.jetbrains.com/issue/KT-40214) that required special builds for the 1.4 milestone releases was fixed.

## 1.0.5-1.4-M3 (2020-07-24)

* Renamed the project from Hephaestus to Anvil #12. **IMPORTANT:** Anvil is not compatible with Hephaestus and you must upgrade the plugin in all of your libraries. The artifact coordinates changed, too.
* Same as `1.0.5`, only built with Kotlin 1.4-M3.

## 1.0.5 (2020-07-24)

* Renamed the project from Hephaestus to Anvil #12. **IMPORTANT:** Anvil is not compatible with Hephaestus and you must upgrade the plugin in all of your libraries. The artifact coordinates changed, too.

## 1.0.4-1.4-M3 (2020-07-24)

* Ensure that types cannot be included and excluded at the same time, which leads to unexpected results.
* Fix a classpath issue in the Gradle plugin when using the new plugin syntax #31.
* Same as `1.0.4`, only built with Kotlin 1.4-M3.

## 1.0.4 (2020-07-24)

* Ensure that types cannot be included and excluded at the same time, which leads to unexpected results.
* Fix a classpath issue in the Gradle plugin when using the new plugin syntax #31.

## 1.0.3-1.4-M3 (2020-07-17)

* Bug fix for Gradle's configuration caching #29.
* Same as `1.0.3`, only built with Kotlin 1.4-M3.

## 1.0.3 (2020-07-17)

* Bug fix for Gradle's configuration caching #29.

## 1.0.2 (2020-07-17)

* Discarded

## 1.0.1 (2020-07-09)

* Added support for Gradle's configuration caching and made task configuration lazy #19.
* Fixed the implicit requirement to apply plugins in a specific order #8 #16.
* Fixed file path issues on Windows #13 #24.
* Allow Dagger modules that are interfaces #14 #24.
* Test Hephaestus with Kotlin `1.4` and `1.4.2`.
* Use `1.0.1-1.4-M3` to use Hephaestus in projects with Kotlin 1.4-M3.

## 1.0.0 (2020-06-16)

* Initial release.