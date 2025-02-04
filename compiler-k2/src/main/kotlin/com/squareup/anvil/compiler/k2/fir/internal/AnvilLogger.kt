package com.squareup.anvil.compiler.k2.fir.internal

import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity.ERROR
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity.INFO
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity.STRONG_WARNING
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity.WARNING
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.common.messages.MessageRenderer.PLAIN_FULL_PATHS
import org.jetbrains.kotlin.cli.common.messages.PrintingMessageCollector
import java.io.PrintWriter
import java.io.StringWriter

public interface AnvilLogger {
  public val isVerbose: Boolean

  public val infoWriter: PrintWriter
  public val warnWriter: PrintWriter
  public val errorWriter: PrintWriter

  public fun info(message: String)
  public fun warn(message: String)
  public fun error(message: String)

  public fun exception(e: Throwable)
}

public inline fun AnvilLogger.info(message: () -> String) {
  if (isVerbose) {
    info(message())
  }
}

public class MessageCollectorBackedAnvilLogger(
  override val isVerbose: Boolean,
  isInfoAsWarnings: Boolean,
  public val messageCollector: MessageCollector = defaultMessageCollector(isVerbose),
) : AnvilLogger {

  private companion object {
    const val PREFIX = "[anvil] "
    fun defaultMessageCollector(isVerbose: Boolean) =
      PrintingMessageCollector(System.err, PLAIN_FULL_PATHS, isVerbose)
  }

  override val errorWriter: PrintWriter = makeWriter(ERROR)
  override val warnWriter: PrintWriter = makeWriter(STRONG_WARNING)
  override val infoWriter: PrintWriter = makeWriter(if (isInfoAsWarnings) WARNING else INFO)

  override fun info(message: String) {
    if (isVerbose) {
      messageCollector.report(INFO, PREFIX + message)
    }
  }

  override fun warn(message: String) {
    messageCollector.report(WARNING, PREFIX + message)
  }

  override fun error(message: String) {
    messageCollector.report(ERROR, PREFIX + message)
  }

  override fun exception(e: Throwable) {
    val stacktrace = run {
      val writer = StringWriter()
      e.printStackTrace(PrintWriter(writer))
      writer.toString()
    }
    messageCollector.report(ERROR, PREFIX + "An exception occurred: " + stacktrace)
  }

  private fun makeWriter(severity: CompilerMessageSeverity): PrintWriter {
    return PrintWriter(MessageCollectorBackedWriter(messageCollector, severity))
  }
}
