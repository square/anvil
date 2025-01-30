package com.squareup.anvil.compiler.k2.ir

import dagger.internal.Factory
import dagger.internal.Provider

public class GoodClass_Factory(
  private val param0: Provider<String>,
) : Factory<GoodClass> {
  public var backFieldExample: String = ""
    get() = field
    set(value) {
      field = value
    }

  public override fun `get`(): GoodClass {
    return newInstance(param0.get())
  }

  public companion object {
    @JvmStatic
    public fun create(param0: Provider<String>): GoodClass_Factory = GoodClass_Factory(param0)

    @JvmStatic
    public fun newInstance(param0: String): GoodClass = GoodClass(param0)
  }
}

public class GoodClass(public val param0: String)
