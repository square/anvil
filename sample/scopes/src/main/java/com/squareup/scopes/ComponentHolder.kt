package com.squareup.scopes

object ComponentHolder {
  val components = mutableSetOf<Any>()

  inline fun <reified T> component(): T = components
    .filterIsInstance<T>()
    .single()
}
