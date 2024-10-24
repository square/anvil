package com.squareup.anvil.compiler.codegen.incremental.collections

import java.io.Serializable

internal interface Multimap<K, V> : Serializable {
  val keys: Set<K>

  operator fun get(key: K): Set<V>

  operator fun contains(key: K): Boolean
  fun add(key: K, value: V): Boolean
  fun remove(key: K)
  fun remove(key: K, value: V)

  companion object {
    operator fun <K, V> invoke(): Multimap<K, V> = MultimapImpl()
  }
}

internal open class MultimapImpl<K, V> : Multimap<K, V> {
  private val map: MutableMap<K, MutableSet<V>> = HashMap()

  override val keys: Set<K> get() = map.keys

  override operator fun get(key: K): Set<V> = map[key].orEmpty()
  override operator fun contains(key: K): Boolean = map.containsKey(key)

  override fun add(key: K, value: V): Boolean = map.getOrPut(key) { mutableSetOf() }.add(value)

  override fun remove(key: K) {
    map.remove(key)?.toSet()
  }

  override fun remove(key: K, value: V) {
    val values = map[key] ?: return

    values.remove(value)
    if (values.isEmpty()) {
      map.remove(key)
    }
  }

  override fun toString(): String = map.entries.joinToString("\n") { "${it.key} : ${it.value}" }
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is MultimapImpl<*, *>) return false
    if (map != other.map) return false
    return true
  }

  override fun hashCode(): Int = map.hashCode()
}
