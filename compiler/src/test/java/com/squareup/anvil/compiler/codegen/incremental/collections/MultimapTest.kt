package com.squareup.anvil.compiler.codegen.incremental.collections

import io.kotest.matchers.shouldBe
import org.junit.Test

class MultimapTest {

  @Test
  fun `add key value pair to empty multimap`() {
    val multimap = Multimap<String, Int>()

    val result = multimap.add("a", 1)

    result shouldBe true
    multimap["a"] shouldBe setOf(1)
  }

  @Test
  fun `add duplicate key value pair to multimap`() {
    val multimap = Multimap<String, Int>()
    multimap.add("a", 1)

    multimap.add("a", 1) shouldBe false

    multimap["a"] shouldBe setOf(1)
  }

  @Test
  fun `remove key from empty multimap`() {
    val multimap = Multimap<String, Int>()

    multimap.remove("a")

    multimap["a"] shouldBe emptySet()
  }

  @Test
  fun `remove non existing key from multimap`() {
    val multimap = Multimap<String, Int>()
    multimap.add("a", 1)

    multimap.remove("b")

    multimap["a"] shouldBe setOf(1)
    multimap["b"] shouldBe emptySet()
  }

  @Test
  fun `remove existing key value pair from multimap`() {
    val multimap = Multimap<String, Int>()
    multimap.add("a", 1)

    multimap.remove("a", 1)

    multimap["a"] shouldBe emptySet()
  }

  @Test
  fun `remove non existing key value pair from multimap`() {
    val multimap = Multimap<String, Int>()
    multimap.add("a", 1)

    multimap.remove("a", 2)

    multimap["a"] shouldBe setOf(1)
  }

  @Test
  fun `check contains on empty multimap`() {
    val multimap = Multimap<String, Int>()

    val result = multimap.contains("a")

    result shouldBe false
  }

  @Test
  fun `check contains on multimap with key`() {
    val multimap = Multimap<String, Int>()
    multimap.add("a", 1)

    val result = multimap.contains("a")

    result shouldBe true
  }

  @Test
  fun `check contains on multimap without key`() {
    val multimap = Multimap<String, Int>()
    multimap.add("a", 1)

    val result = multimap.contains("b")

    result shouldBe false
  }
}
