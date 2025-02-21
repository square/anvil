package com.squareup.anvil.compiler.testing.reflect

import com.squareup.anvil.annotations.ExperimentalAnvilApi
import io.kotest.assertions.print.print
import io.kotest.matchers.Matcher
import io.kotest.matchers.MatcherResult
import io.kotest.matchers.neverNullMatcher
import io.kotest.matchers.should
import io.kotest.matchers.shouldNot
import org.jetbrains.kotlin.name.ClassId

public interface ClassAsserts {

  @ExperimentalAnvilApi
  public infix fun Class<*>.shouldExtend(other: Class<*>) {
    this should beSubtypeOf(other)
  }

  @ExperimentalAnvilApi
  public infix fun Class<*>.shouldExtend(other: ClassId) {
    this should beSubtypeOf(classLoader.loadClass(other))
  }

  @ExperimentalAnvilApi
  public infix fun Class<*>.shouldExtend(otherFqName: String) {
    this should beSubtypeOf(classLoader.loadClass(otherFqName))
  }

  @ExperimentalAnvilApi
  public infix fun Class<*>.shouldNotExtend(other: Class<*>) {
    this shouldNot beSubtypeOf(other)
  }

  @ExperimentalAnvilApi
  public infix fun Class<*>.shouldNotExtend(other: ClassId) {
    this shouldNot beSubtypeOf(classLoader.loadClass(other))
  }

  @ExperimentalAnvilApi
  public infix fun Class<*>.shouldNotExtend(otherFqName: String) {
    this shouldNot beSubtypeOf(classLoader.loadClass(otherFqName))
  }
}

private fun beSubtypeOf(expected: Class<*>): Matcher<Class<*>> = neverNullMatcher { value ->
  MatcherResult(
    expected.isAssignableFrom(value),
    { "$value is of type ${value.print().value} but expected ${expected.print().value}" },
    { "${value.print().value} should not be of type ${expected.print().value}" },
  )
}
