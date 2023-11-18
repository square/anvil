package com.squareup.anvil.benchmark

sealed interface Module {
  val name: String
  val path: String

  data class LibraryModule(
    override val name: String,
    override val path: String,
    val index: Int,
  ) : Module

  data class AppModule(
    override val name: String,
    override val path: String,
  ) : Module
}
