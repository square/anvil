package com.squareup.anvil.compiler.codegen

import com.google.auto.service.AutoService
import com.google.devtools.ksp.getVisibility
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.Visibility
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.compiler.HINT_BINDING_PACKAGE_PREFIX
import com.squareup.anvil.compiler.REFERENCE_SUFFIX
import com.squareup.anvil.compiler.SCOPE_SUFFIX
import com.squareup.anvil.compiler.api.AnvilContext
import com.squareup.anvil.compiler.api.CodeGenerator
import com.squareup.anvil.compiler.api.GeneratedFile
import com.squareup.anvil.compiler.api.createGeneratedFile
import com.squareup.anvil.compiler.codegen.ksp.AnvilSymbolProcessor
import com.squareup.anvil.compiler.codegen.ksp.AnvilSymbolProcessorProvider
import com.squareup.anvil.compiler.codegen.ksp.checkNoDuplicateScopeAndBoundType
import com.squareup.anvil.compiler.codegen.ksp.getKSAnnotationsByType
import com.squareup.anvil.compiler.codegen.ksp.scope
import com.squareup.anvil.compiler.contributesBindingFqName
import com.squareup.anvil.compiler.internal.createAnvilSpec
import com.squareup.anvil.compiler.internal.reference.asClassName
import com.squareup.anvil.compiler.internal.reference.classAndInnerClassReferences
import com.squareup.anvil.compiler.internal.reference.generateClassName
import com.squareup.anvil.compiler.internal.safePackageString
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.KModifier.PUBLIC
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.writeTo
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.psi.KtFile
import java.io.File
import kotlin.reflect.KClass

/**
 * Generates a hint for each contributed class in the `anvil.hint.bindings` package. This allows
 * the compiler plugin to find all contributed bindings a lot faster when merging modules and
 * component interfaces.
 */
internal object ContributesBindingCodeGen {
  fun generate(
    originClass: ClassName,
    scopes: Iterable<ClassName>,
  ): FileSpec {
    val fileName = originClass.generateClassName().simpleName
    val generatedPackage = HINT_BINDING_PACKAGE_PREFIX +
      originClass.packageName.safePackageString(dotPrefix = true)
    val propertyName = originClass.canonicalName.replace('.', '_')
    return FileSpec.createAnvilSpec(generatedPackage, fileName) {
      addProperty(
        PropertySpec
          .builder(
            name = propertyName + REFERENCE_SUFFIX,
            type = KClass::class.asClassName().parameterizedBy(originClass)
          )
          .initializer("%T::class", originClass)
          .addModifiers(PUBLIC)
          .build()
      )

      scopes.forEachIndexed { index, scope ->
        addProperty(
          PropertySpec
            .builder(
              name = propertyName + SCOPE_SUFFIX + index,
              type = KClass::class.asClassName().parameterizedBy(scope)
            )
            .initializer("%T::class", scope)
            .addModifiers(PUBLIC)
            .build()
        )
      }
    }
  }

  internal class KspGenerator(
    env: SymbolProcessorEnvironment,
    private val context: AnvilContext,
  ) : AnvilSymbolProcessor(env) {

    override fun processChecked(resolver: Resolver): List<KSAnnotated> {
      if (context.generateFactoriesOnly) return emptyList()

      resolver
        .getSymbolsWithAnnotation(contributesBindingFqName.asString())
        .mapNotNull { annotated ->
          when {
            annotated !is KSClassDeclaration -> {
              env.logger.error(
                "Only classes can be annotated with @ContributesBinding.", annotated
              )
              return@mapNotNull null
            }

            annotated.getVisibility() != Visibility.PUBLIC -> {
              env.logger.error(
                "${annotated.simpleName} is binding a type, but the class is not public. " +
                  "Only public types are supported.",
                annotated
              )
              return@mapNotNull null
            }
            // TODO
            // clazz.checkNotMoreThanOneQualifier(contributesBindingFqName)
            // clazz.checkSingleSuperType(contributesBindingFqName)
            // clazz.checkClassExtendsBoundType(contributesBindingFqName)
            else -> annotated
          }
        }
        .forEach { clazz ->
          val className = clazz.toClassName()

          val scopes = clazz.getKSAnnotationsByType(ContributesBinding::class)
            .toList()
            .also { it.checkNoDuplicateScopeAndBoundType(clazz) }
            .map { it.scope().toClassName() }
            .distinct()
            // Give it a stable sort.
            .sortedBy { it.canonicalName }

          val spec = generate(className, scopes)

          spec.writeTo(
            env.codeGenerator,
            aggregating = false,
            originatingKSFiles = listOf(clazz.containingFile!!)
          )
        }

      return emptyList()
    }

    @AutoService(SymbolProcessorProvider::class)
    class Provider : AnvilSymbolProcessorProvider(::KspGenerator)
  }

  @AutoService(CodeGenerator::class)
  internal class AnvilGenerator : CodeGenerator {

    override fun isApplicable(context: AnvilContext) = !context.generateFactoriesOnly

    override fun generateCode(
      codeGenDir: File,
      module: ModuleDescriptor,
      projectFiles: Collection<KtFile>
    ): Collection<GeneratedFile> {
      return projectFiles
        .classAndInnerClassReferences(module)
        .filter { it.isAnnotatedWith(contributesBindingFqName) }
        .onEach { clazz ->
          clazz.checkClassIsPublic {
            "${clazz.fqName} is binding a type, but the class is not public. " +
              "Only public types are supported."
          }
          clazz.checkNotMoreThanOneQualifier(contributesBindingFqName)
          clazz.checkSingleSuperType(contributesBindingFqName)
          clazz.checkClassExtendsBoundType(contributesBindingFqName)
        }
        .map { clazz ->
          val className = clazz.asClassName()

          val scopes = clazz.annotations
            .find(contributesBindingFqName)
            .also { it.checkNoDuplicateScopeAndBoundType() }
            .distinctBy { it.scope() }
            // Give it a stable sort.
            .sortedBy { it.scope() }
            .map { it.scope().asClassName() }

          val spec = generate(className, scopes)

          createGeneratedFile(
            codeGenDir = codeGenDir,
            packageName = spec.packageName,
            fileName = spec.name,
            content = spec.toString()
          )
        }
        .toList()
    }
  }
}
