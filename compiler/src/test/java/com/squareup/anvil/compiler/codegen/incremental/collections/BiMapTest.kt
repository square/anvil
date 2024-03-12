package com.squareup.anvil.compiler.codegen.incremental.collections

import io.kotest.matchers.shouldBe
import org.junit.Test

class BiMapTest {
  @Test
  fun `get value from empty BiMap`() {
    val biMap = MutableBiMap<String, Int>()

    val result = biMap["a"]

    result shouldBe null
  }

  @Test
  fun `get value from BiMap with key`() {
    val biMap = MutableBiMap<String, Int>()
    biMap.put("a", 1)

    val result = biMap["a"]

    result shouldBe 1
  }

  @Test
  fun `get value from BiMap without key`() {
    val biMap = MutableBiMap<String, Int>()
    biMap.put("a", 1)

    val result = biMap["b"]

    result shouldBe null
  }

  @Test
  fun `put key value pair to empty BiMap`() {
    val biMap = MutableBiMap<String, Int>()

    biMap.put("a", 1)

    biMap["a"] shouldBe 1
  }

  @Test
  fun `force put key value pair to BiMap with existing key`() {
    val biMap = MutableBiMap<String, Int>()
    biMap.put("a", 1)

    biMap.forcePut("a", 2)

    biMap["a"] shouldBe 2
  }

  @Test
  fun `remove key from empty BiMap`() {
    val biMap = MutableBiMap<String, Int>()

    val result = biMap.remove("a")

    result shouldBe null
  }

  @Test
  fun `remove existing key from BiMap`() {
    val biMap = MutableBiMap<String, Int>()
    biMap.put("a", 1)

    val result = biMap.remove("a")

    result shouldBe 1
    biMap["a"] shouldBe null
  }

  @Test
  fun `remove non existing key from BiMap`() {
    val biMap = MutableBiMap<String, Int>()
    biMap.put("a", 1)

    val result = biMap.remove("b")

    result shouldBe null
    biMap["a"] shouldBe 1
  }

  @Test
  fun `check containsKey on empty BiMap`() {
    val biMap = MutableBiMap<String, Int>()

    val result = biMap.containsKey("a")

    result shouldBe false
  }

  @Test
  fun `check containsKey on BiMap with key`() {
    val biMap = MutableBiMap<String, Int>()
    biMap.put("a", 1)

    val result = biMap.containsKey("a")

    result shouldBe true
  }

  @Test
  fun `check containsKey on BiMap without key`() {
    val biMap = MutableBiMap<String, Int>()
    biMap.put("a", 1)

    val result = biMap.containsKey("b")

    result shouldBe false
  }

  @Test
  fun `check containsValue on empty BiMap`() {
    val biMap = MutableBiMap<String, Int>()

    val result = biMap.containsValue(1)

    result shouldBe false
  }

  @Test
  fun `check containsValue on BiMap with value`() {
    val biMap = MutableBiMap<String, Int>()
    biMap.put("a", 1)

    val result = biMap.containsValue(1)

    result shouldBe true
  }

  @Test
  fun `check containsValue on BiMap without value`() {
    val biMap = MutableBiMap<String, Int>()
    biMap.put("a", 1)

    val result = biMap.containsValue(2)

    result shouldBe false
  }
}
