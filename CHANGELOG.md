# Changelog

## Next Version

* Ensure that types cannot be included and excluded at the same time, which leads to unexpected results.

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