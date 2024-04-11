package com.squareup.anvil.compiler.internal.reference

import com.rickbusarow.kase.Kase1
import com.rickbusarow.kase.kase
import com.rickbusarow.kase.stdlib.div
import com.rickbusarow.kase.wrap
import com.squareup.anvil.compiler.api.createGeneratedFile
import com.squareup.anvil.compiler.internal.containingFileAsJavaFile
import com.squareup.anvil.compiler.internal.reference.ReferencesTestEnvironment.ReferenceType.Psi
import com.squareup.anvil.compiler.internal.testing.simpleCodeGenerator
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.TestFactory
import java.io.File

class RealAnvilModuleDescriptorTest : ReferenceTests {

  @TestFactory
  fun `a file without type declarations only a typealias declaration is passed to a code generator`() =
    listOf<Kase1<(String) -> String>>(
      kase("top-level property") { "val $it = Unit" },
      kase("top-level function") { "fun $it() = Unit" },
      kase("typealias") { "typealias $it = Unit" },
      kase("just a comment") { "// this is a comment" },
      kase("only the package declaration") { "" },
    )
      .asTests(testEnvironmentFactory = testEnvironmentFactory.wrap(Psi)) { (declaration) ->
        val extraGenerator = simpleCodeGenerator { codeGenDir, _, files ->

          val singleSource = files.single()
          val sourceAsJava = singleSource.containingFileAsJavaFile()

          val newGenerated = sourceAsJava.resolveSibling("Generated.kt")

          // Don't generate the same file again.
          if (newGenerated == sourceAsJava) {
            return@simpleCodeGenerator emptyList()
          }

          listOf(
            createGeneratedFile(
              codeGenDir = codeGenDir,
              packageName = singleSource.packageFqName.asString(),
              fileName = newGenerated.nameWithoutExtension,
              content = """
                package com.squareup.test

                ${declaration("b")}
              """.trimIndent(),
              sourceFile = sourceAsJava,
            ),
          )
        }

        // Use regular Java files because the Psi objects are disposed when the compilation ends.
        val allProjectFiles = mutableListOf<File>()
        val allFilesFromModule = mutableSetOf<File>()

        compile(
          """
          package com.squareup.test
        
          ${declaration("a")}
          """.trimIndent(),
          codeGenerators = listOf(extraGenerator),
        ) {

          allProjectFiles.addAll(projectFiles.map { it.containingFileAsJavaFile() })
          allFilesFromModule.addAll(module.allFiles.map { it.containingFileAsJavaFile() })
        }

        // These are the files that are passed to the code generator.
        allProjectFiles shouldBe listOf(
          workingDir / "sources" / "src/main/java" / "com/squareup/test/Source0.kt",
          workingDir / "build/anvil" / "com/squareup/test/Generated.kt",
        )

        // These are the files returned by the module descriptor instance itself.
        allFilesFromModule shouldBe listOf(
          workingDir / "sources" / "src/main/java" / "com/squareup/test/Source0.kt",
          workingDir / "build/anvil" / "com/squareup/test/Generated.kt",
        )
      }
}
