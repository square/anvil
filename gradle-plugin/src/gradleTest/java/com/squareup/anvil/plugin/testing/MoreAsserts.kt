package com.squareup.anvil.plugin.testing

import io.kotest.assertions.print.print
import io.kotest.matchers.EqualityMatcherResult
import io.kotest.matchers.file.shouldExist
import io.kotest.matchers.neverNullMatcher
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import java.io.File

interface MoreAsserts {

  infix fun File.shouldExistWithText(expectedText: String) {
    shouldExist()
    readText() shouldBe expectedText
  }

  infix fun File.shouldExistWithTextContaining(substring: String) {
    shouldExist()
    readText() shouldContain substring
  }

  infix fun String?.shouldContain(substring: String): String? {
    this should include(substring)
    return this
  }

  /**
   * This overloads Kotest's `include` so that it can return a different `MatcherResult`.
   * The [EqualityMatcherResult] results in a different exception when it fails,
   * which enables the 'click to see difference' feature in IntelliJ.
   * That diff is much more legible.
   */
  private fun include(substring: String) = neverNullMatcher<String> { actual ->
    EqualityMatcherResult.invoke(
      passed = actual.contains(substring),
      actual = actual,
      expected = substring,
      failureMessageFn = {
        "${actual.print().value} should include substring ${substring.print().value}"
      },
      negatedFailureMessageFn = {
        "${actual.print().value} should not include substring ${substring.print().value}"
      },
    )
  }
}
