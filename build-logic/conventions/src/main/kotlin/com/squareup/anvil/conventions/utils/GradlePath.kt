package com.squareup.anvil.conventions.utils

import org.gradle.StartParameter

typealias GradlePath = org.gradle.util.Path

// /**
//  * For fully qualified/absolute path like `:a:b:c`, this would be `:`.
//  *
//  * ```text
//  * receiver | returned
//  * ---------|---------
//  * :a:b:c   | :
//  * :        | :
//  * a:b:c    | a
//  * ```
//  *
//  */
// val GradlePath.root: GradlePath
//   @Suppress("UnstableApiUsage")
//   get() = this.takeFirstSegments(1)

/** For a path of `:a:b:c`, this would be `:a`. */
val GradlePath.firstSegment: GradlePath
  @Suppress("UnstableApiUsage")
  get() = this.takeFirstSegments(1)

/** For a path of `:a:b:c`, this would be `c`. */
val GradlePath.lastSegment: GradlePath
  get() = this.removeFirstSegments(this.segmentCount() - 1)

/**
 * Travels from the full path upwards.
 *
 * For a path of `:a:b:c`, this would be `[ ':a:b', ':a' ]`.
 */
fun GradlePath.parents(): Sequence<GradlePath> = generateSequence(parent) { it.parent }

/**
 * Travels from the full path upwards.
 *
 * For a path of `:a:b:c`, this would be `[ ':a:b:c', ':a:b', ':a' ]`.
 */
fun GradlePath.parentsWithSelf(): Sequence<GradlePath> = generateSequence(this) { it.parent }

fun StartParameter.taskPaths() = taskNames.map { GradlePath.path(it) }
