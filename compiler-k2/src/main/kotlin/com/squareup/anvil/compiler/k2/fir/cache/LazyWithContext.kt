package com.squareup.anvil.compiler.k2.fir.cache

import org.jetbrains.kotlin.fir.caches.FirLazyValue
import java.io.Serializable

public fun <V, C> lazyWithContext(initializer: (C) -> V): LazyWithContext<V, C> =
  LazyWithContextImpl(initializer)

public abstract class LazyWithContext<out V, in C> : FirLazyValue<V>(), Serializable {
  public abstract fun getValue(context: C): V
  public abstract fun isInitialized(): Boolean
}

@Suppress("ClassName")
private object UNINITIALIZED_VALUE

internal class LazyWithContextImpl<out V, in C>(initializer: (C) -> V) :
  LazyWithContext<V, C>() {

  private var initializer: ((C) -> V)? = initializer
  private var value: Any? = UNINITIALIZED_VALUE

  override fun getValue(): V {
    check(isInitialized()) { "Value not initialized" }
    @Suppress("UNCHECKED_CAST")
    return value as V
  }

  override fun getValue(context: C): V {
    if (isInitialized()) {
      @Suppress("UNCHECKED_CAST")
      return value as V
    }
    return synchronized(this) {
      val v1 = value
      if (v1 !== UNINITIALIZED_VALUE) {
        @Suppress("UNCHECKED_CAST")
        v1 as V
      } else {
        val v2 = initializer!!(context)
        value = v2
        initializer = null
        v2
      }
    }
  }

  override fun isInitialized(): Boolean = value !== UNINITIALIZED_VALUE
  override fun toString(): String =
    if (isInitialized()) value.toString() else "Lazy value not initialized yet."
}
