package com.squareup.anvil.plugin.integration

import com.rickbusarow.kase.gradle.GradleRootProjectBuilder
import com.rickbusarow.kase.gradle.dsl.buildFile
import com.squareup.anvil.plugin.testing.Libs

object IntegrationTestProjects {

  fun codeGeneratorProject(rootProjectBuilder: GradleRootProjectBuilder, libs: Libs) {

    rootProjectBuilder.buildFile {
      this.plugins {
        this.kotlin("jvm")
        this.kotlin("kapt")
      }

      this.dependencies {
        this.api(libs.anvil.compilerApi)
        this.implementation(libs.anvil.compilerUtils)

        this.compileOnly(libs.auto.service.annotations)
        this.kapt(libs.auto.service.processor)
      }
    }

    rootProjectBuilder.dir("src/main/java") {
      this.kotlinFile(
        "com/squareup/anvil/test/TestCodeGenerator.kt",
        """
        package com.squareup.anvil.test
        
        import com.google.auto.service.AutoService
        import com.squareup.anvil.compiler.api.AnvilContext
        import com.squareup.anvil.compiler.api.CodeGenerator
        import com.squareup.anvil.compiler.api.GeneratedFileWithSources
        import com.squareup.anvil.compiler.api.createGeneratedFile
        import com.squareup.anvil.compiler.internal.containingFileAsJavaFile
        import com.squareup.anvil.compiler.internal.reference.classAndInnerClassReferences
        import com.squareup.anvil.compiler.internal.reference.topLevelFunctionReferences
        import com.squareup.anvil.compiler.internal.reference.topLevelPropertyReferences
        import com.squareup.anvil.compiler.internal.safePackageString
        import org.intellij.lang.annotations.Language
        import org.jetbrains.kotlin.descriptors.ModuleDescriptor
        import org.jetbrains.kotlin.name.FqName
        import org.jetbrains.kotlin.psi.KtFile
        import java.io.File
        
        /**
         * This generator creates code which can go in a source set which uses Anvil's factory generation.
         *
         * This is necessary because files with @Inject or @AssistedInject will generate duplicate classes
         * if they trigger both Anvil and Dagger generation.
         */
        @Suppress("unused")
        @AutoService(CodeGenerator::class)
        class TestCodeGenerator : CodeGenerator {
          override fun isApplicable(context: AnvilContext): Boolean = true
        
          override fun generateCode(
            codeGenDir: File,
            module: ModuleDescriptor,
            projectFiles: Collection<KtFile>,
          ): Collection<GeneratedFileWithSources> {
            return projectFiles
              .classAndInnerClassReferences(module)
              .filter { it.isAnnotatedWith(FqName("com.squareup.anvil.test.Trigger")) }
              .flatMap { clazz ->
                val generatedPackage = "generated.test" + clazz.packageFqName
                  .safePackageString(dotPrefix = true, dotSuffix = false)
        
                @Language("kotlin")
                val generatedClass = ""${'"'}
                  package ${'$'}generatedPackage
                  
                  class GeneratedClass 
                ""${'"'}.trimIndent()
        
                @Language("kotlin")
                val contributedInterface = ""${'"'}
                  package ${'$'}generatedPackage
                  
                  import com.squareup.anvil.annotations.ContributesTo
        
                  @ContributesTo(Unit::class)
                  interface ContributedInterface 
                ""${'"'}.trimIndent()
        
                @Language("kotlin")
                val contributedModule = ""${'"'}
                  package ${'$'}generatedPackage
                  
                  import com.squareup.anvil.annotations.ContributesTo
                  import dagger.Module 
        
                  @ContributesTo(Unit::class)
                  @Module
                  interface ContributedModule 
                ""${'"'}.trimIndent()
        
                // This snippet is helpful for testing #310.
                @Language("kotlin")
                val contributedBinding = ""${'"'}
                  package ${'$'}generatedPackage
                  
                  import com.squareup.anvil.annotations.ContributesBinding
                  import javax.inject.Inject
        
                  interface Binding
        
                  @ContributesBinding(Unit::class)
                  class ContributedBinding @Inject constructor() : Binding
                ""${'"'}.trimIndent()
        
                @Language("kotlin")
                val injectClass = ""${'"'}
                  package ${'$'}generatedPackage
                  
                  import javax.inject.Inject
            
                  class InjectClass @Inject constructor() 
                ""${'"'}.trimIndent()
        
                // This snippet is helpful for testing #326.
                @Language("kotlin")
                val assistedInject = ""${'"'}
                  package ${'$'}generatedPackage
                  
                  import dagger.assisted.Assisted
                  import dagger.assisted.AssistedFactory
                  import dagger.assisted.AssistedInject
        
                  data class AssistedService @AssistedInject constructor(
                    @Assisted val string: String
                  )
        
                  interface ParentAssistedFactory : Function1<String, AssistedService>
            
                  @AssistedFactory
                  interface SampleAssistedFactory : ParentAssistedFactory
                ""${'"'}.trimIndent()
        
                @Language("kotlin")
                val contributedSubcomponent = ""${'"'}
                  package ${'$'}generatedPackage
                  
                  import com.squareup.anvil.annotations.ContributesSubcomponent
                  import com.squareup.anvil.annotations.ContributesTo
                  import dagger.Module
                  import dagger.Provides
        
                  @ContributesTo(Int::class)
                  @Module
                  object DaggerModule {
                    @Provides fun provideInteger(): Int = 7
                  }
        
                  @ContributesSubcomponent(Int::class, parentScope = Unit::class)
                  interface ContributedSubcomponent {
                    fun integer(): Int
                  }
                ""${'"'}.trimIndent()
        
                sequenceOf(
                  createGeneratedFile(
                    codeGenDir = codeGenDir,
                    packageName = generatedPackage,
                    fileName = "GeneratedClass",
                    content = generatedClass,
                    sourceFile = clazz.containingFileAsJavaFile,
                  ),
                  createGeneratedFile(
                    codeGenDir = codeGenDir,
                    packageName = generatedPackage,
                    fileName = "ContributedInterface",
                    content = contributedInterface,
                    sourceFile = clazz.containingFileAsJavaFile,
                  ),
                  createGeneratedFile(
                    codeGenDir = codeGenDir,
                    packageName = generatedPackage,
                    fileName = "ContributedModule",
                    content = contributedModule,
                    sourceFile = clazz.containingFileAsJavaFile,
                  ),
                  createGeneratedFile(
                    codeGenDir = codeGenDir,
                    packageName = generatedPackage,
                    fileName = "ContributedBinding",
                    content = contributedBinding,
                    sourceFile = clazz.containingFileAsJavaFile,
                  ),
                  createGeneratedFile(
                    codeGenDir = codeGenDir,
                    packageName = generatedPackage,
                    fileName = "InjectClass",
                    content = injectClass,
                    sourceFile = clazz.containingFileAsJavaFile,
                  ),
                  createGeneratedFile(
                    codeGenDir = codeGenDir,
                    packageName = generatedPackage,
                    fileName = "AssistedInject",
                    content = assistedInject,
                    sourceFile = clazz.containingFileAsJavaFile,
                  ),
                  createGeneratedFile(
                    codeGenDir = codeGenDir,
                    packageName = generatedPackage,
                    fileName = "ContributedSubcomponent",
                    content = contributedSubcomponent,
                    sourceFile = clazz.containingFileAsJavaFile,
                  ),
                )
              }
              .plus(
                projectFiles
                  .topLevelFunctionReferences(module)
                  .filter { it.isAnnotatedWith(FqName("com.squareup.anvil.test.Trigger")) }
                  .flatMap {
                    val generatedPackage = "generated.test" + it.fqName.parent()
                      .safePackageString(dotPrefix = true, dotSuffix = false)
        
                    @Language("kotlin")
                    val generatedClass = ""${'"'}
                      package ${'$'}generatedPackage
                      
                      class GeneratedFunctionClass 
                    ""${'"'}.trimIndent()
        
                    listOf(
                      createGeneratedFile(
                        codeGenDir = codeGenDir,
                        packageName = generatedPackage,
                        fileName = "GeneratedFunctionClass",
                        content = generatedClass,
                        sourceFile = it.function.containingFileAsJavaFile(),
                      ),
                    )
                  },
              )
              .plus(
                projectFiles
                  .topLevelPropertyReferences(module)
                  .filter { it.isAnnotatedWith(FqName("com.squareup.anvil.test.Trigger")) }
                  .flatMap {
                    val generatedPackage = "generated.test" + it.fqName.parent()
                      .safePackageString(dotPrefix = true, dotSuffix = false)
        
                    @Language("kotlin")
                    val generatedClass = ""${'"'}
                      package ${'$'}generatedPackage
                      
                      class GeneratedPropertyClass 
                    ""${'"'}.trimIndent()
        
                    listOf(
                      createGeneratedFile(
                        codeGenDir = codeGenDir,
                        packageName = generatedPackage,
                        fileName = "GeneratedPropertyClass",
                        content = generatedClass,
                        sourceFile = it.property.containingFileAsJavaFile(),
                      ),
                    )
                  },
              )
              .toList()
          }
        }
        """.trimIndent(),
      )
      this.kotlinFile(
        "com/squareup/anvil/test/TestWithKaptCodeGenerator.kt",
        """
        package com.squareup.anvil.test
        
        import com.google.auto.service.AutoService
        import com.squareup.anvil.compiler.api.AnvilContext
        import com.squareup.anvil.compiler.api.CodeGenerator
        import com.squareup.anvil.compiler.api.GeneratedFileWithSources
        import com.squareup.anvil.compiler.api.createGeneratedFile
        import com.squareup.anvil.compiler.internal.reference.classAndInnerClassReferences
        import com.squareup.anvil.compiler.internal.safePackageString
        import org.intellij.lang.annotations.Language
        import org.jetbrains.kotlin.descriptors.ModuleDescriptor
        import org.jetbrains.kotlin.name.FqName
        import org.jetbrains.kotlin.psi.KtFile
        import java.io.File
        
        /**
         * This generator creates code which needs Dagger kapt to run on its source set,
         * such as interfaces which are annotated with @MergeComponent or @MergeSubcomponent.
         */
        @Suppress("unused")
        @AutoService(CodeGenerator::class)
        class TestWithKaptCodeGenerator : CodeGenerator {
          override fun isApplicable(context: AnvilContext): Boolean = true
        
          override fun generateCode(
            codeGenDir: File,
            module: ModuleDescriptor,
            projectFiles: Collection<KtFile>,
          ): Collection<GeneratedFileWithSources> {
            return projectFiles
              .classAndInnerClassReferences(module)
              .filter { it.isAnnotatedWith(FqName("com.squareup.anvil.test.TriggerWithKapt")) }
              .flatMap { clazz ->
                val generatedPackage = "generated.test" + clazz.packageFqName
                  .safePackageString(dotPrefix = true, dotSuffix = false)
        
                @Language("kotlin")
                val mergedComponent = ""${'"'}
                  package ${'$'}generatedPackage
                  
                  import com.squareup.anvil.annotations.MergeComponent
        
                  @MergeComponent(Unit::class)
                  interface MergedComponent 
                ""${'"'}.trimIndent()
        
                sequenceOf(
                  createGeneratedFile(
                    codeGenDir = codeGenDir,
                    packageName = generatedPackage,
                    fileName = "mergedComponent",
                    content = mergedComponent,
                    sourceFile = clazz.containingFileAsJavaFile,
                  ),
                )
              }
              .toList()
          }
        }
          """,
      )
    }
  }
}
