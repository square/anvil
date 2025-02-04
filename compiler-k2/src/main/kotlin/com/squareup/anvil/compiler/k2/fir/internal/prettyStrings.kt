package com.squareup.anvil.compiler.k2.fir.internal

public fun String.pretty(): String =
  replace("""(\S+:.+?) (?=\S+:)""".toRegex(), "$1\n  ")
    // .replace(":( ?(?:true|false)) ".toRegex(), ":$1\n  ")
    .replace("] (\\S+)".toRegex(), "]\n$1")
// .replace(":( ?\\d+) ".toRegex(), ":$1\n  ")
// .replace(":( ?\\[.*?]) ".toRegex(), ":$1\n  ")
// .replace(":( ?\\{.*?}) ".toRegex(), ":$1\n  ")

public fun String?.prettyToString(): String {
  return if (this == null) {
    toString()
  } else {
    replace(",", ",\n")
      .let { str ->
        listOf(
          '(' to ')',
          '[' to ']',
          '{' to '}',
        )
          .fold(str) { acc, (open, close) ->
            val o = Regex.escape(open.toString())
            val or = Regex.escapeReplacement(open.toString())
            val c = Regex.escape(close.toString())
            val cr = Regex.escapeReplacement(close.toString())
            acc
              .replace("""$o\s+$c""".toRegex(), "$or$cr")
              .replace("""$o(?!\s*$c)""".toRegex(), "$or\n")
              .replace("""(?<!$o\s?)$c""".toRegex(), "\n$cr")
          }
      }
      .indentByBrackets()
      .replace("""\n *\n""".toRegex(), "\n")
      .pretty()
  }
}

public fun Any?.toStringPretty(): String = when (this) {
  is Map<*, *> -> toList().joinToString("\n")
  else -> toString().prettyToString()
  // else -> (this?.renderAsDataClassToString() ?: toString()).prettyToString()
}

public fun String.indentByBrackets(tab: String = "  "): String {

  var tabCount = 0

  val open = setOf('{', '(', '[', '<')
  val close = setOf('}', ')', ']', '>')

  return lines()
    .map { it.trim() }
    .joinToString("\n") { line ->

      if (line.firstOrNull() in close) {
        tabCount--
      }

      "${tab.repeat(tabCount)}$line"
        .also {

          // Arrows aren't brackets
          val noSpecials = line.remove("<=", "->")

          tabCount += noSpecials.count { char -> char in open }
          // Skip the first char because if it's a closing bracket, it was already counted above.
          tabCount -= noSpecials.drop(1).count { char -> char in close }
        }
    }
}

public fun String.remove(vararg strings: String): String = strings.fold(this) { acc, string ->
  acc.replace(string, "")
}
public fun String.remove(vararg regexes: Regex): String = regexes.fold(this) { acc, regex ->
  acc.replace(regex, "")
}
