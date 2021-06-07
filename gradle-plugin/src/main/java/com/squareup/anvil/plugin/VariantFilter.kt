package com.squareup.anvil.plugin

import com.android.build.gradle.api.BaseVariant
import org.gradle.api.Named

public interface VariantFilter : Named {

  /**
   * Indicate whether or not to ignore Anvil for this particular variant. Default is false.
   */
  public var ignore: Boolean
}

internal class CommonFilter(
  private val name: String
) : VariantFilter {
  override fun getName(): String = name
  override var ignore: Boolean = false
}

public class JvmVariantFilter internal constructor(
  commonFilter: CommonFilter
) : VariantFilter by commonFilter

public class AndroidVariantFilter internal constructor(
  commonFilter: CommonFilter,
  public val androidVariant: BaseVariant
) : VariantFilter by commonFilter
