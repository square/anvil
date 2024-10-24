package com.squareup.anvil.compiler.codegen.incremental.collections

import java.io.Serializable

internal interface BiMap<K : Any, V : Any> : Serializable {
  val keys: Set<K>
  val values: Set<V>

  operator fun get(key: K): V?
  fun getValue(key: K): V

  fun containsKey(key: K): Boolean
  fun containsValue(value: V): Boolean
}

internal interface MutableBiMap<K : Any, V : Any> : BiMap<K, V>, Serializable {
  val inverse: BiMap<V, K>

  fun getOrPut(key: K, defaultValue: () -> V): V
  fun put(key: K, value: V)
  fun forcePut(key: K, value: V)

  operator fun set(key: K, value: V) {
    put(key, value)
  }

  fun remove(key: K): V?

  companion object {
    operator fun <K : Any, V : Any> invoke(): MutableBiMap<K, V> = MutableBiMapImpl(
      mutableMapOf(),
      mutableMapOf(),
    )
  }
}

internal abstract class AbstractBiMap<K : Any, V : Any>(
  protected open val direct: Map<K, V>,
  protected open val reverse: Map<V, K>,
) : BiMap<K, V>, Map<K, V> by direct {
  override val keys: Set<K> get() = direct.keys
  override val values: Set<V> get() = reverse.keys

  override fun get(key: K): V? = direct[key]
  override fun getValue(key: K): V = direct.getValue(key)
  override fun containsValue(value: V): Boolean = value in reverse
  override fun containsKey(key: K): Boolean = get(key) != null
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is AbstractBiMap<*, *>) return false

    if (direct != other.direct) return false
    if (reverse != other.reverse) return false

    return true
  }

  override fun hashCode(): Int {
    var result = direct.hashCode()
    result = 31 * result + reverse.hashCode()
    return result
  }
}

internal class InverseBiMap<K : Any, V : Any>(
  direct: MutableMap<K, V>,
  reverse: MutableMap<V, K>,
) : AbstractBiMap<K, V>(direct, reverse)

internal class MutableBiMapImpl<K : Any, V : Any>(
  override val direct: MutableMap<K, V>,
  override val reverse: MutableMap<V, K>,
) : AbstractBiMap<K, V>(direct, reverse),
  MutableBiMap<K, V> {

  override val inverse: BiMap<V, K> by lazy {
    InverseBiMap(
      this@MutableBiMapImpl.reverse,
      this@MutableBiMapImpl.direct,
    )
  }

  override fun getOrPut(key: K, defaultValue: () -> V): V {
    return direct.getOrPut(key, defaultValue).also { reverse[it] = key }
  }

  override fun forcePut(key: K, value: V) {
    reverse[value] = key
    direct[key] = value
  }

  override fun put(key: K, value: V) {
    forcePut(key, value)
  }

  override fun remove(key: K): V? = direct.remove(key)?.also { reverse.remove(it) }
}
