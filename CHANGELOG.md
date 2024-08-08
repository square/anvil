# Change Log

**Unreleased**
--------------

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
