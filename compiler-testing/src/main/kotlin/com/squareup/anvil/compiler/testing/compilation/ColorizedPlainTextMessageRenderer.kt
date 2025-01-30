package com.squareup.anvil.compiler.testing.compilation

import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSourceLocation
import org.jetbrains.kotlin.cli.common.messages.PlainTextMessageRenderer
import java.io.File

internal class ColorizedPlainTextMessageRenderer : PlainTextMessageRenderer(true) {
  private val cwd by lazy(LazyThreadSafetyMode.NONE) { File(".").absoluteFile }

  private val _errors = mutableListOf<String>()
  val errors: List<String> get() = _errors

  override fun getName(): String = "ColorizedRelativePath"

  override fun render(
    severity: CompilerMessageSeverity,
    message: String,
    location: CompilerMessageSourceLocation?,
  ): String = super.render(severity, message, location)
    .also {
      if (severity == CompilerMessageSeverity.ERROR) {
        _errors += it
      }
    }

  override fun getPath(location: CompilerMessageSourceLocation): String {
    return File(location.path).toRelativeString(cwd)
  }
}
