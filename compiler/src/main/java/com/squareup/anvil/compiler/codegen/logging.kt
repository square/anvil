package com.squareup.anvil.compiler.codegen

import org.jetbrains.kotlin.util.suffixIfNot
import java.io.File
import kotlin.io.path.appendText
import kotlin.io.path.createFile
import kotlin.io.path.createParentDirectories
import kotlin.io.path.exists

internal fun log(file: File, msg: String) {
  // val stamp = "${LocalTime.now().truncatedTo(ChronoUnit.MILLIS)}  "
  val stamp = "${System.currentTimeMillis()}  "

  val indented = msg.prependIndent(" ".repeat(stamp.length)).trimStart()
  val text = "$stamp$indented".suffixIfNot("\n")

  file.toPath()
    .createParentDirectories()
    .let {
      if (it.exists()) {
        it
      } else {
        it.createFile()
      }
    }
    .appendText(text)
}
