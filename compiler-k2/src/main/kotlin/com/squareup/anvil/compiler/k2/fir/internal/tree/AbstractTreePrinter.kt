package com.squareup.anvil.compiler.k2.fir.internal.tree

import com.squareup.anvil.compiler.k2.fir.internal.remove
import com.squareup.anvil.compiler.k2.fir.internal.tree.AbstractTreePrinter.Color.Companion.colorized
import kotlin.experimental.ExperimentalTypeInference

/**
 * Conditionally applies the provided transform function to the receiver
 * object if the predicate is true, then returns the result of that transform.
 * If the predicate is false, the receiver object itself is returned.
 *
 * @param predicate The predicate to determine whether to apply the transform function.
 * @param transform The transform function to apply to the receiver object.
 * @return The result of the transform function if the
 *   predicate is true, or the receiver object itself otherwise.
 */
@OptIn(ExperimentalTypeInference::class)
internal inline fun <T : R, R> T.letIf(
  predicate: Boolean,
  @BuilderInference transform: (T) -> R,
): R {
  return if (predicate) transform(this) else this
}

/** Removes ANSI controls like `\u001B[]33m` */
internal fun String.noAnsi(): String = remove("""\u001B\[[;\d]*m""".toRegex())

/**
 * Base class for printing a tree structure of objects of type [T].
 *
 * @param whitespaceChar the character to use for replacing
 *   whitespaces in the node text when printing. Default is ' '.
 */
internal abstract class AbstractTreePrinter<T : Any>(
  private val whitespaceChar: Char? = null,
) {
  private val elementSimpleNameMap = mutableMapOf<T, String>()
  private val elementTypeNameMap = mutableMapOf<T, String>()

  private var currentColorIndex = 0

  /** Returns the simple class name of an object of type [T]. */
  abstract fun T.simpleClassName(): String

  /** Returns the parent of an object of type [T]. */
  abstract fun T.parent(): T?

  /** Returns the type name of an object of type [T]. */
  abstract fun T.typeName(): String

  /** Returns the text representation of an object of type [T]. */
  abstract fun T.text(): String

  /** Returns the children of an object of type [T] as a [Sequence]. */
  abstract fun T.children(): Sequence<T>

  /**
   * Prints the tree structure of an object of type [T] to the console.
   *
   * @param [rootNode] the root node of the tree.
   */
  fun printTreeString(rootNode: T) {
    println(treeString(rootNode))
  }

  /**
   * Returns the tree structure of an object of type [T] as a string.
   *
   * @param [rootNode] the root node of the tree.
   * @return the tree structure as a string.
   */
  fun treeString(rootNode: T): String {
    return buildTreeString(rootNode, 0)
  }

  private fun buildTreeString(rootNode: T, indentLevel: Int): String {
    val indent = "╎  ".repeat(indentLevel)

    val thisName = rootNode.uniqueSimpleName()

    val color = getCurrentColor()

    fun String.colorized(): String = colorized(color)

    val parentName = rootNode.parent()?.uniqueSimpleName() ?: "null"
    val parentType = rootNode.parent()?.typeName() ?: "null"

    val childrenText = rootNode.children()
      .joinToString("\n") { child ->
        buildTreeString(child, indentLevel + 1)
      }

    val typeName = rootNode.typeName()

    @Suppress("MagicNumber")
    return buildString {

      val chars = BoxChars.LIGHT

      val header =
        "$thisName [type: $typeName] [parent: $parentName] [parent type: $parentType]"

      val text = rootNode.text()
        .letIf(whitespaceChar != null) {
          it.replace(" ", "$whitespaceChar")
        }

      val headerLength = header.countVisibleChars()

      val longestTextLine = text.lines().maxOf { it.countVisibleChars() }

      val len = maxOf(headerLength + 4, longestTextLine)

      val headerBoxStart = "${chars.topLeft}${chars.dash}".colorized()

      val headerBoxEnd =
        ("${chars.dash}".repeat((len - 3) - headerLength) + chars.topRight).colorized()

      append("$indent$headerBoxStart $header $headerBoxEnd")

      append('\n')
      append(indent)
      append("${chars.midLeft}${"${chars.dash}".repeat(len)}${chars.bottomRight}".colorized())
      append('\n')

      val pipe = "${chars.pipe}".colorized()

      val prependedText = text.prependIndent("$indent$pipe")

      append(prependedText)

      append('\n')
      append(indent)
      append("${chars.bottomLeft}${"${chars.dash}".repeat(len)}${chars.dash}".colorized())
      // append("${chars.bottomLeft}${"${chars.dash}".repeat(longestTextLine)}".colorized())

      if (childrenText.isNotEmpty()) {
        append("\n")
        append(childrenText)
      }
    }
  }

  private data class BoxChars(
    val dash: Char,
    val pipe: Char,
    val topLeft: Char,
    val midLeft: Char,
    val bottomLeft: Char,
    val midBottom: Char,
    val midTop: Char,
    val topRight: Char,
    val midRight: Char,
    val bottomRight: Char,
  ) {
    companion object {
      val HEAVY = BoxChars(
        dash = '━',
        pipe = '┃',
        topLeft = '┏',
        midLeft = '┣',
        bottomLeft = '┗',
        midBottom = '┻',
        midTop = '┳',
        topRight = '┓',
        midRight = '┫',
        bottomRight = '┛',
      )
      val LIGHT = BoxChars(
        dash = '─',
        pipe = '│',
        topLeft = '┌',
        midLeft = '├',
        bottomLeft = '└',
        midBottom = '┴',
        midTop = '┬',
        topRight = '┐',
        midRight = '┤',
        bottomRight = '┘',
      )
    }
  }

  private fun T.uniqueSimpleName(): String = uniqueName(NameType.SIMPLE)

  private fun T.uniqueName(nameType: NameType): String {
    val map = when (nameType) {
      NameType.SIMPLE -> elementSimpleNameMap
      NameType.TYPE -> elementTypeNameMap
    }

    return map.getOrPut(this@uniqueName) {
      val count = map.keys.count { key ->
        if (nameType == NameType.SIMPLE) {
          key.simpleClassName() == simpleClassName()
        } else {
          key.typeName() == typeName()
        }
      }

      val name = if (nameType == NameType.SIMPLE) simpleClassName() else typeName()

      val unique = if (count == 0) {
        name
      } else {
        "$name (${count + 1})"
      }

      unique.colorized(getNextColor())
    }
  }

  private fun getCurrentColor(): Color = Color.entries[currentColorIndex]

  private fun getNextColor(): Color {
    currentColorIndex = (currentColorIndex + 1) % Color.entries.size
    return getCurrentColor()
  }

  private fun String.countVisibleChars(): Int = noAnsi().length

  private enum class NameType {
    SIMPLE,
    TYPE,
  }

  @Suppress("MagicNumber")
  internal enum class Color(val code: Int) {
    LIGHT_RED(91),
    LIGHT_YELLOW(93),
    LIGHT_BLUE(94),
    LIGHT_GREEN(92),
    LIGHT_MAGENTA(95),
    RED(31),
    YELLOW(33),
    BLUE(34),
    GREEN(32),
    MAGENTA(35),
    CYAN(36),
    LIGHT_CYAN(96),
    ORANGE_DARK(38),
    ORANGE_BRIGHT(48),
    PURPLE_DARK(53),
    PURPLE_BRIGHT(93),
    PINK_BRIGHT(198),
    BROWN_DARK(94),
    BROWN_BRIGHT(178),
    LIGHT_GRAY(37),
    DARK_GRAY(90),
    BLACK(30),
    WHITE(97),
    ;

    companion object {

      private val supported = "win" !in System.getProperty("os.name").lowercase()

      /** returns a string in the given color */
      fun String.colorized(color: Color): String {

        return if (supported) {
          "\u001B[${color.code}m$this\u001B[0m"
        } else {
          this
        }
      }
    }
  }
}
