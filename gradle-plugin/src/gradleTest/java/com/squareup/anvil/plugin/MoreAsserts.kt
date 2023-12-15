package com.squareup.anvil.plugin

import io.kotest.matchers.file.shouldExist
import io.kotest.matchers.shouldBe
import java.io.File

interface MoreAsserts {

  infix fun File.shouldExistWithText(expectedText: String) {
    shouldExist()
    readText() shouldBe expectedText
  }
}
