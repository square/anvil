package com.squareup.anvil.compiler.codegen

import com.squareup.anvil.annotations.ContributesTo
import com.squareup.anvil.compiler.ANVIL_MODULE_SUFFIX
import com.squareup.anvil.compiler.ClassScanner
import com.squareup.anvil.compiler.MODULE_PACKAGE_PREFIX
import com.squareup.anvil.compiler.api.AnvilCompilationException
import com.squareup.anvil.compiler.api.AnvilContext
import com.squareup.anvil.compiler.api.GeneratedFile
import com.squareup.anvil.compiler.codegen.GeneratedMethod.BindingMethod
import com.squareup.anvil.compiler.codegen.GeneratedMethod.ProviderMethod
import com.squareup.anvil.compiler.contributesBindingFqName
import com.squareup.anvil.compiler.contributesMultibindingFqName
import com.squareup.anvil.compiler.contributesToFqName
import com.squareup.anvil.compiler.daggerModuleFqName
import com.squareup.anvil.compiler.internal.buildFile
import com.squareup.anvil.compiler.internal.capitalize
import com.squareup.anvil.compiler.internal.decapitalize
import com.squareup.anvil.compiler.internal.reference.ClassReference
import com.squareup.anvil.compiler.internal.reference.asClassName
import com.squareup.anvil.compiler.internal.reference.classAndInnerClassReferences
import com.squareup.anvil.compiler.internal.reference.generateClassName
import com.squareup.anvil.compiler.internal.safePackageString
import com.squareup.anvil.compiler.mergeComponentFqName
import com.squareup.anvil.compiler.mergeModulesFqName
import com.squareup.anvil.compiler.mergeSubcomponentFqName
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier.ABSTRACT
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.STAR
import com.squareup.kotlinpoet.TypeSpec
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoMap
import dagger.multibindings.IntoSet
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtFile
import java.io.File

private val supportedFqNames = listOf(
  mergeComponentFqName,
  mergeSubcomponentFqName,
  mergeModulesFqName
)

internal class BindingModuleGenerator(
  private val classScanner: ClassScanner
) : FlushingCodeGenerator {

  override fun isApplicable(context: AnvilContext): Boolean = !context.generateFactoriesOnly

  // Keeps track of for which scopes which files were generated. Usually there is only one file,
  // but technically there can be multiple.
  private val mergedClasses = mutableMapOf<ClassReference, File>()

  private val contributedBindingClasses = mutableListOf<ClassReference>()
  private val contributedMultibindingClasses = mutableListOf<ClassReference>()
  private val contributedModuleClasses = mutableListOf<ClassReference>()

  override fun generateCode(
    codeGenDir: File,
    module: ModuleDescriptor,
    projectFiles: Collection<KtFile>
  ): Collection<GeneratedFile> {
    val classes = projectFiles.classAndInnerClassReferences(module).toList()

    // Even though we support multiple rounds the Kotlin compiler let's us generate code only once.
    // It's possible that we use the @ContributesBinding annotation in the module in which we
    // merge components. Remember for which classes a hint was generated and generate a @Binds
    // method for them later.
    contributedBindingClasses += classes.filter {
      it.isAnnotatedWith(contributesBindingFqName)
    }
    contributedMultibindingClasses += classes.filter {
      it.isAnnotatedWith(contributesMultibindingFqName)
    }

    // Similar to the explanation above, we must track contributed modules.
    contributedModuleClasses += classes.filter {
      it.isAnnotatedWith(contributesToFqName) && it.isAnnotatedWith(daggerModuleFqName)
    }

    // Generate a Dagger module for each @MergeComponent and friends.
    return classes
      .mapNotNull { clazz ->
        val mergeAnnotations = clazz.annotations
          .filter { it.fqName in supportedFqNames }
          .ifEmpty { return@mapNotNull null }

        val packageName = generatePackageName(clazz)
        val className = clazz.generateClassName().relativeClassName.asString()

        val directory = File(codeGenDir, packageName.replace('.', File.separatorChar))
        val file = File(directory, "$className.kt")
        check(file.parentFile.exists() || file.parentFile.mkdirs()) {
          "Could not generate package directory: ${file.parentFile}"
        }

        // We cheat and don't actually write the content to the file. We write the content in the
        // flush() method when we collected all binding methods to avoid two writes.
        check(file.exists() || file.createNewFile()) {
          "Could not create new file: $file"
        }

        val content = daggerModuleContent(
          scopes = mergeAnnotations.map { it.scope() },
          clazz = clazz,
          generatedMethods = emptyList()
        )

        mergedClasses[clazz] = file

        GeneratedFile(file, content)
      }
      .toList()
  }

  override fun flush(
    codeGenDir: File,
    module: ModuleDescriptor
  ): Collection<GeneratedFile> {
    return mergedClasses.map { (mergedClass, daggerModuleFile) ->
      val annotations = mergedClass.annotations.filter { it.fqName in supportedFqNames }
      val scopes = annotations.map { it.scope() }
      val excludedClasses = annotations.flatMap { it.exclude() }

      // Contributed Dagger modules can replace other Dagger modules but also contributed bindings.
      // If a binding is replaced, then we must not generate the binding method.
      //
      // We precompute this list here and share the result in the methods below. Resolving classes
      // and types can be an expensive operation, so avoid doing it twice.
      val bindingsReplacedInDaggerModules = contributedModuleClasses
        .asSequence()
        .plus(
          scopes.flatMap { scope ->
            classScanner
              .findContributedClasses(
                module = module,
                annotation = contributesToFqName,
                scope = scope.fqName
              )
              .filter { it.isAnnotatedWith(daggerModuleFqName) }
          }
        )
        // Ignore replaced bindings specified by excluded modules for this scope.
        .filter { clazz -> clazz !in excludedClasses }
        .flatMap { clazz ->
          scopes.flatMap { scope ->
            clazz.annotations
              .find(annotationName = contributesToFqName, scope = scope)
              .flatMap { it.replaces() }
          }
        }
        .toList()

      // Note that this is an inner function to share some of the parameters. It computes all
      // generated functions for contributed bindings and multibindings. The generated functions
      // are so similar that it makes sense to share the code between normal binding and
      // multibinding methods.
      fun getContributedBindingClasses(
        collectedClasses: List<ClassReference>,
        annotationFqName: FqName,
        isMultibinding: Boolean
      ): List<GeneratedMethod> {

        val contributedBindingsDependencies = scopes.flatMap { scope ->
          classScanner.findContributedClasses(
            module,
            annotation = annotationFqName,
            scope = scope.fqName
          )
        }

        val allContributedClasses = collectedClasses
          .plus(contributedBindingsDependencies)

        val replacedBindings = allContributedClasses
          .flatMap { classReference ->
            scopes.flatMap { scope ->
              classReference.annotations
                .find(annotationName = annotationFqName, scope = scope)
                .flatMap { it.replaces() }
            }
          }

        return allContributedClasses
          .asSequence()
          .filterNot { it in replacedBindings }
          .filterNot { it in bindingsReplacedInDaggerModules }
          .filterNot { it in excludedClasses }
          .flatMap { clazz ->
            scopes.flatMap { scope ->
              clazz.annotations
                .find(annotationName = annotationFqName, scope = scope)
                .map { it.toContributedBinding(isMultibinding, module) }
            }
          }
          .let { bindings ->
            if (isMultibinding) {
              bindings
            } else {
              bindings.groupBy { it.qualifiersKeyLazy.value }
                .map { it.value.findHighestPriorityBinding() }
                .asSequence()
            }
          }
          .map { binding -> binding.toGeneratedMethod(isMultibinding) }
          .toList()
      }

      val generatedMethods =
        getContributedBindingClasses(
          collectedClasses = contributedBindingClasses,
          annotationFqName = contributesBindingFqName,
          isMultibinding = false
        )
          .plus(
            getContributedBindingClasses(
              collectedClasses = contributedMultibindingClasses,
              annotationFqName = contributesMultibindingFqName,
              isMultibinding = true
            )
          )
          .renameDuplicateFunctions()
          .sorted()

      val content = daggerModuleContent(
        scopes = scopes,
        clazz = mergedClass,
        generatedMethods = generatedMethods
      )

      daggerModuleFile.writeText(content)

      GeneratedFile(daggerModuleFile, content)
    }
  }

  private fun daggerModuleContent(
    scopes: List<ClassReference>,
    clazz: ClassReference,
    generatedMethods: List<GeneratedMethod>
  ): String {
    val className = clazz.generateClassName(separator = "", suffix = ANVIL_MODULE_SUFFIX)
      .relativeClassName
      .asString()

    val bindingMethods = generatedMethods.filterIsInstance<BindingMethod>()
    val providerMethods = generatedMethods.filterIsInstance<ProviderMethod>()

    return FileSpec.buildFile(generatePackageName(clazz), className) {
      val builder = if (bindingMethods.isEmpty()) {
        TypeSpec.objectBuilder(className)
          .addFunctions(providerMethods.specs)
      } else {
        TypeSpec.classBuilder(className)
          .addModifiers(ABSTRACT)
          .addFunctions(bindingMethods.specs)
          .apply {
            if (providerMethods.isNotEmpty()) {
              addType(
                TypeSpec.companionObjectBuilder()
                  .addFunctions(providerMethods.specs)
                  .build()
              )
            }
          }
      }

      addType(
        builder
          .addAnnotation(Module::class)
          .apply {
            scopes.forEach { scope ->
              addAnnotation(
                AnnotationSpec
                  .builder(ContributesTo::class)
                  .addMember("%T::class", scope.asClassName())
                  .build()
              )
            }
          }
          .build()
      )
    }
  }
}

private fun generatePackageName(clazz: ClassReference): String = MODULE_PACKAGE_PREFIX +
  clazz.packageFqName.safePackageString(dotPrefix = true)

private sealed class GeneratedMethod : Comparable<GeneratedMethod> {
  abstract val spec: FunSpec
  abstract val contributedClass: ClassReference
  abstract val boundType: ClassReference

  override fun compareTo(other: GeneratedMethod): Int = COMPARATOR.compare(this, other)

  data class ProviderMethod(
    override val spec: FunSpec,
    override val contributedClass: ClassReference,
    override val boundType: ClassReference,
  ) : GeneratedMethod()

  data class BindingMethod(
    override val spec: FunSpec,
    override val contributedClass: ClassReference,
    override val boundType: ClassReference,
  ) : GeneratedMethod()

  companion object {
    val COMPARATOR = compareBy<GeneratedMethod>(
      { it.spec.name }, { it.contributedClass }, { it.boundType }
    )
  }
}

private fun List<ContributedBinding>.findHighestPriorityBinding(): ContributedBinding {
  if (size == 1) return this[0]

  val bindings = groupBy { it.priority }
    .toSortedMap()
    .let { it.getValue(it.lastKey()) }
    // In some very rare cases we can see a binding for the same type twice. Just in case filter
    // them, see https://github.com/square/anvil/issues/460.
    .distinctBy { it.contributedClass }

  if (bindings.size > 1) {
    throw AnvilCompilationException(
      "There are multiple contributed bindings with the same bound type. The bound type is " +
        "${bindings[0].boundType.fqName}. The contributed binding classes are: " +
        bindings.joinToString(
          prefix = "[",
          postfix = "]"
        ) { it.contributedClass.fqName.asString() }
    )
  }

  return bindings[0]
}

private fun ContributedBinding.toGeneratedMethod(
  isMultibinding: Boolean
): GeneratedMethod {

  val isMapMultibinding = mapKeys.isNotEmpty()

  val methodNameSuffix = buildString {
    append(boundType.shortName.capitalize())
    if (isMultibinding) {
      append("Multi")
    }
  }

  val boundTypeWithStarGenerics = boundType.asClassName()
    .let {
      if (boundType.typeParameters.isEmpty()) {
        it
      } else {
        it.parameterizedBy(boundType.typeParameters.map { STAR })
      }
    }

  return if (contributedClass.isObject()) {
    ProviderMethod(
      spec = FunSpec.builder(name = "provide$methodNameSuffix")
        .addAnnotation(Provides::class)
        .apply {
          when {
            isMapMultibinding -> addAnnotation(IntoMap::class)
            isMultibinding -> addAnnotation(IntoSet::class)
          }
        }
        .addAnnotations(qualifiers)
        .addAnnotations(mapKeys)
        .returns(boundTypeWithStarGenerics)
        .addStatement("return %T", contributedClass.asClassName())
        .build(),
      contributedClass = contributedClass,
      boundType = boundType
    )
  } else {
    BindingMethod(
      spec = FunSpec.builder(name = "bind$methodNameSuffix")
        .addAnnotation(Binds::class)
        .apply {
          when {
            isMapMultibinding -> addAnnotation(IntoMap::class)
            isMultibinding -> addAnnotation(IntoSet::class)
          }
        }
        .addAnnotations(qualifiers)
        .addAnnotations(mapKeys)
        .addModifiers(ABSTRACT)
        .addParameter(
          name = contributedClass.shortName.decapitalize(),
          type = contributedClass.asClassName()
        )
        .returns(boundTypeWithStarGenerics)
        .build(),
      contributedClass = contributedClass,
      boundType = boundType
    )
  }
}

private fun List<GeneratedMethod>.renameDuplicateFunctions(): List<GeneratedMethod> {
  return groupBy { it.spec.name }
    .values
    .flatMap { duplicates ->
      if (duplicates.size == 1) {
        duplicates
      } else {
        duplicates.mapIndexed { index, generatedMethod ->
          val newSpec = generatedMethod.spec
            .toBuilder(name = generatedMethod.spec.name + index)
            .build()

          when (generatedMethod) {
            is BindingMethod -> generatedMethod.copy(spec = newSpec)
            is ProviderMethod -> generatedMethod.copy(spec = newSpec)
          }
        }
      }
    }
}

private val Collection<GeneratedMethod>.specs get() = map { it.spec }
