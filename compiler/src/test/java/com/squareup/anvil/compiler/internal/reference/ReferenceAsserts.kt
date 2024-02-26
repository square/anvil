package com.squareup.anvil.compiler.internal.reference

import io.kotest.matchers.MatcherResult
import io.kotest.matchers.neverNullMatcher
import io.kotest.matchers.should
import io.kotest.matchers.shouldNot

interface ReferenceAsserts {

  infix fun TypeReference.shouldBeAssignableTo(other: TypeReference) {
    this should beAssignableTo(other)
  }

  infix fun TypeReference.shouldNotBeAssignableTo(other: TypeReference) {
    this shouldNot beAssignableTo(other)
  }

  infix fun TypeReference.beAssignableTo(other: TypeReference) =
    neverNullMatcher<TypeReference> { actual ->
      MatcherResult.invoke(
        passed = actual.isAssignableTo(other),
        failureMessageFn = {
          val supers = actual.allSuperTypeReferences().map { it.asTypeName() }
          """
          |${actual.asTypeName()} should be assignable to ${other.asTypeName()}.
          |Its supertypes are:
          |  ${supers.joinToString("\n  ")}
          """.trimMargin()
        },
        negatedFailureMessageFn = {
          "${actual.asTypeName()} should not be assignable to ${other.asTypeName()}"
        },
      )
    }
}
