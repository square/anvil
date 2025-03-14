package com.squareup.anvil.compiler.k2.utils.names

import org.jetbrains.kotlin.name.Name

public object Names {
  public val boundType: Name = Name.identifier("boundType")
  public val dependencies: Name = Name.identifier("dependencies")
  public val exclude: Name = Name.identifier("exclude")
  public val hints: Name = Name.identifier("hints")
  public val modules: Name = Name.identifier("modules")
  public val rank: Name = Name.identifier("rank")
  public val replaces: Name = Name.identifier("replaces")
  public val scope: Name = Name.identifier("scope")
}
