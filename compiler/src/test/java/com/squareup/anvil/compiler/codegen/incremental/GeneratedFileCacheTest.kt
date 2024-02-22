package com.squareup.anvil.compiler.codegen.incremental

import com.rickbusarow.kase.stdlib.createSafely
import com.squareup.anvil.compiler.api.AnvilCompilationException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.Test

internal class GeneratedFileCacheTest : CacheTests {

  @Test
  fun `add a new mapping`() = test {

    cache.addGeneratedFile(gen1.withSources(source1))

    cache.getGeneratedFiles(source1) shouldBe setOf(gen1.relativeFile)
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

    cache.getGeneratedFiles(source1) shouldBe setOf(gen1.relativeFile, gen2.relativeFile)
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
      gen2.withSources(gen1.toSourceFile()),
    )

    cache.getGeneratedFiles(source1) shouldBe setOf(gen1.relativeFile)
    cache.getGeneratedFiles(gen1.toSourceFile()) shouldBe setOf(gen2.relativeFile)
  }

  @Test
  fun `add multiple rounds of generated files with non-linear dependencies`() = test {

    cache.addAll(
      gen1.withSources(source1, source2),
      gen2.withSources(gen1.toSourceFile()),
    )

    cache.getGeneratedFiles(source1) shouldBe setOf(gen1.relativeFile)
    cache.getGeneratedFiles(source2) shouldBe setOf(gen1.relativeFile)
    cache.getGeneratedFiles(gen1.toSourceFile()) shouldBe setOf(gen2.relativeFile)
  }

  @Test
  fun `remove source with generated file that is also a source`() = test {

    cache.addAll(
      gen1.withSources(source1),
      gen2.withSources(gen1.toSourceFile()),
    )

    cache.removeSource(source1)

    cache.getGeneratedFiles(source1) shouldBe emptySet()
    cache.getGeneratedFiles(gen1.toSourceFile()) shouldBe emptySet()
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

    cache.getGeneratedFiles(source1) shouldBe setOf(gen1.relativeFile)

    cache.getGeneratedFiles(source2) shouldBe emptySet()
  }

  @Test
  fun `adding a dependency cycle throws an exception`() = test {

    cache.addGeneratedFile(gen2.withSources(gen1.toSourceFile()))

    val exception = shouldThrow<AnvilCompilationException> {
      cache.addGeneratedFile(gen1.withSources(gen2.toSourceFile()))
    }

    exception.message shouldContain """
      Adding this mapping would create a cycle.
         source path: ${gen2.file}
      generated path: ${gen1.file}
    """.trimIndent()
  }

  @Test
  fun `adding a transitive dependency cycle throws an exception`() = test {

    cache.addGeneratedFile(gen2.withSources(gen1.toSourceFile()))
    cache.addGeneratedFile(gen3.withSources(gen2.toSourceFile()))

    val exception = shouldThrow<AnvilCompilationException> {
      cache.addGeneratedFile(gen1.withSources(gen3.toSourceFile()))
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
      gen3.withSources(gen1.toSourceFile()),
      gen4.withSources(gen3.toSourceFile()),
      gen5.withSources(gen4.toSourceFile()),
      gen6.withSources(gen5.toSourceFile()),
      gen7.withSources(gen6.toSourceFile()),
      gen9.withSources(gen7.toSourceFile()),
    )

    cache.getGeneratedFilesRecursive(source1) shouldBe setOf(
      gen1.relativeFile,
      gen3.relativeFile,
      gen4.relativeFile,
      gen5.relativeFile,
      gen6.relativeFile,
      gen7.relativeFile,
      gen9.relativeFile,
    )
  }

  @Test
  fun `serialization and deserialization work`() = test {

    cache.use {
      it.addAll(
        gen1.withSources(source1),
        gen2.withSources(source1, source2),
        gen3.withSources(source2),
        gen4.withSources(gen3.toSourceFile()),
        gen5.withSources(gen4.toSourceFile(), source3),
      )
    }

    val deserializedCache = GeneratedFileCache.fromFile(binaryFile, projectDir)

    deserializedCache shouldBe cache
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

    val projectA = ProjectDir(workingDir.resolve("projectA"))
    val fileA = projectA.file.resolve("common.kt").createSafely("content")

    GeneratedFileCache.fromFile(binaryFile, projectA).use { cacheA ->
      cacheA.hasChanged(AbsoluteFile(fileA)) shouldBe true
    }

    val projectB = ProjectDir(workingDir.resolve("projectB"))
    val fileB = projectB.file.resolve("common.kt").createSafely("content")

    GeneratedFileCache.fromFile(binaryFile, projectB).use { cacheB ->
      cacheB.hasChanged(AbsoluteFile(fileB)) shouldBe false
    }
  }
}
