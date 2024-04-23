package com.squareup.anvil.compiler.codegen.incremental

import com.rickbusarow.kase.stdlib.createSafely
import com.rickbusarow.kase.stdlib.div
import com.squareup.anvil.compiler.api.AnvilCompilationException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.equality.shouldBeEqualUsingFields
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldNotBeSameInstanceAs
import org.junit.Test

internal class GeneratedFileCacheTest : CacheTests {

  @Test
  fun `add a new mapping`() = test {

    cache.addGeneratedFile(gen1.withSources(source1))

    cache.getGeneratedFiles(source1) shouldBe setOf(gen1.absolute)
  }

  @Test
  fun `remove source with single generated file`() = test {

    cache.addGeneratedFile(gen1.withSources(source1))

    cache.removeSource(source1)

    cache.getGeneratedFiles(source1) shouldBe emptySet()
  }

  @Test
  fun `add multiple generated files with a single source`() = test {

    cache.addAll(
      gen1.withSources(source1),
      gen2.withSources(source1),
    )

    cache.getGeneratedFiles(source1) shouldBe setOf(gen1.absolute, gen2.absolute)
  }

  @Test
  fun `remove source with multiple generated files`() = test {

    cache.addAll(
      gen1.withSources(source1),
      gen2.withSources(source1),
    )

    cache.removeSource(source1)

    cache.getGeneratedFiles(source1) shouldBe emptySet()
  }

  @Test
  fun `remove source not in map`() = test {

    cache.removeSource(source1)

    cache.getGeneratedFiles(source1) shouldBe emptySet()
  }

  @Test
  fun `add generated file as source for another generated file`() = test {

    cache.addAll(
      gen1.withSources(source1),
      gen2.withSources(gen1.absolute),
    )

    cache.getGeneratedFiles(source1) shouldBe setOf(gen1)
    cache.getGeneratedFiles(gen1.absolute) shouldBe setOf(gen2)
  }

  @Test
  fun `add multiple rounds of generated files with non-linear dependencies`() = test {

    cache.addAll(
      gen1.withSources(source1, source2),
      gen2.withSources(gen1.absolute),
    )

    cache.getGeneratedFiles(source1) shouldBe setOf(gen1)
    cache.getGeneratedFiles(source2) shouldBe setOf(gen1)
    cache.getGeneratedFiles(gen1.absolute) shouldBe setOf(gen2)
  }

  @Test
  fun `remove source with generated file that is also a source`() = test {

    cache.addAll(
      gen1.withSources(source1),
      gen2.withSources(gen1.absolute),
    )

    cache.removeSource(source1)

    cache.getGeneratedFiles(source1) shouldBe emptySet()
    cache.getGeneratedFiles(gen1.absolute) shouldBe emptySet()
  }

  @Test
  fun `remove source with multiple generations of files`() = test {

    cache.addAll(
      gen1.withSources(source1),
      gen2.withSources(source1),
    )

    cache.removeSource(source1)

    cache.getGeneratedFiles(source1) shouldBe emptySet()
  }

  @Test
  fun `remove source when generated file has two sources`() = test {

    cache.addAll(
      gen1.withSources(source1),
      gen2.withSources(source1, source2),
    )

    cache.removeSource(source2)

    cache.getGeneratedFiles(source1) shouldBe setOf(gen1)

    cache.getGeneratedFiles(source2) shouldBe emptySet()
  }

  @Test
  fun `adding a dependency cycle throws an exception`() = test {

    cache.addGeneratedFile(gen2.withSources(gen1.absolute))

    val exception = shouldThrow<AnvilCompilationException> {
      cache.addGeneratedFile(gen1.withSources(gen2.absolute))
    }

    exception.message shouldContain """
      Adding this mapping would create a cycle.
         source path: ${gen2.file}
      generated path: ${gen1.file}
    """.trimIndent()
  }

  @Test
  fun `adding a transitive dependency cycle throws an exception`() = test {

    cache.addGeneratedFile(gen2.withSources(gen1.absolute))
    cache.addGeneratedFile(gen3.withSources(gen2.absolute))

    val exception = shouldThrow<AnvilCompilationException> {
      cache.addGeneratedFile(gen1.withSources(gen3.absolute))
    }

    exception.message shouldContain """
      Adding this mapping would create a cycle.
         source path: ${gen3.file}
      generated path: ${gen1.file}
    """.trimIndent()
  }

  @Test
  fun `getAllRecursive returns transitive generated files`() = test {

    cache.addAll(
      gen1.withSources(source1),
      gen3.withSources(source1),
      gen2.withSources(source2),
      gen3.withSources(gen1.absolute),
      gen4.withSources(gen3.absolute),
      gen5.withSources(gen4.absolute),
      gen6.withSources(gen5.absolute),
      gen7.withSources(gen6.absolute),
      gen9.withSources(gen7.absolute),
    )

    cache.getGeneratedFilesRecursive(source1) shouldBe setOf(
      gen1,
      gen3,
      gen4,
      gen5,
      gen6,
      gen7,
      gen9,
    )
  }

  @Test
  fun `serialization and deserialization work`() = test {

    // use `use { }` to ensure the file is closed and the cache is written to disk
    cache.use {
      it.addAll(
        gen1.withSources(source1),
        gen2.withSources(source1, source2),
        gen3.withSources(source2),
        gen4.withSources(gen3.absolute),
        gen5.withSources(gen4.absolute, source3),
      )
    }

    val deserializedCache = GeneratedFileCache.fromFile(
      binaryFile = binaryFile,
      projectDir = projectDir,
      buildDir = buildDir,
    )

    deserializedCache shouldBe cache
    deserializedCache shouldBeEqualUsingFields cache
    deserializedCache shouldNotBeSameInstanceAs cache
  }

  @Test
  fun `hasChanged is false for an unchanged file`() = test {

    source1.writeText("content 1")

    cache.addGeneratedFile(gen1.withSources(source1))

    cache.hasChanged(source1) shouldBe false
  }

  @Test
  fun `hasChanged is true for a file that isn't in the cache`() = test {

    source1.writeText("content 1")

    cache.hasChanged(source1) shouldBe true
  }

  @Test
  fun `hasChanged is true for a modified file`() = test {

    source1.writeText("content 1")

    cache.addGeneratedFile(gen1.withSources(source1))

    workingDir.resolve(source1.file).writeText("content 2")

    cache.hasChanged(source1) shouldBe true
  }

  @Test
  fun `hasChanged is false for identical files with different parent directories`() = test {

    val projectA = BaseDir.ProjectDir(workingDir / "projectA")
    val buildA = BaseDir.BuildDir(projectA.file / "build")

    val fileA = (projectA.file / "common.kt").createSafely("content")

    GeneratedFileCache.fromFile(
      binaryFile = binaryFile,
      projectDir = projectA,
      buildDir = buildA,
    )
      .use { cacheA ->
        cacheA.hasChanged(AbsoluteFile(fileA)) shouldBe true
      }

    val projectB = BaseDir.ProjectDir(workingDir / "projectB")
    val buildB = BaseDir.BuildDir(projectB.file / "build")
    val fileB = (projectB.file / "common.kt").createSafely("content")

    GeneratedFileCache.fromFile(
      binaryFile = binaryFile,
      projectDir = projectB,
      buildDir = buildB,
    ).use { cacheB ->
      cacheB.hasChanged(AbsoluteFile(fileB)) shouldBe false
    }
  }
}
