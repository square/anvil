package com.squareup.anvil.compiler

import kotlin.math.roundToInt

internal class RecordingCache<K, V>(private val name: String) {
  private val cache: MutableMap<K, V> = mutableMapOf()
  private var hits = 0
  private var misses = 0

  operator fun contains(key: K): Boolean {
    return key in cache
  }

  operator fun get(key: K): V? {
    return cache[key]
  }

  operator fun set(key: K, value: V) {
    cache[key] = value
  }

  fun getValue(key: K): V {
    return cache.getValue(key)
  }

  fun hit() {
    hits++
  }

  fun miss() {
    misses++
  }

  fun clear() {
    cache.clear()
  }

  fun statsString(): String {
    val fidelity = if (hits + misses == 0) {
      0
    } else {
      ((hits.toDouble() / (hits + misses)) * 100).roundToInt()
    }
    return """
      $name Cache
        Size:     ${cache.size}
        Hits:     $hits
        Misses:   $misses
        Fidelity: $fidelity%
    """.trimIndent()
  }

  operator fun plusAssign(values: Map<K, V>) {
    cache += values
  }

  fun mutate(mutation: (MutableMap<K, V>) -> Unit) {
    mutation(cache)
  }
}
