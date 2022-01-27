package com.squareup.anvil.compiler.codegen

import com.squareup.anvil.annotations.ContributesTo
import com.squareup.anvil.compiler.ANVIL_MODULE_SUFFIX
import com.squareup.anvil.compiler.ClassScanner
import com.squareup.anvil.compiler.HINT_BINDING_PACKAGE_PREFIX
import com.squareup.anvil.compiler.HINT_CONTRIBUTES_PACKAGE_PREFIX
import com.squareup.anvil.compiler.HINT_MULTIBINDING_PACKAGE_PREFIX
import com.squareup.anvil.compiler.MODULE_PACKAGE_PREFIX
import com.squareup.anvil.compiler.REFERENCE_SUFFIX
import com.squareup.anvil.compiler.api.AnvilCompilationException
import com.squareup.anvil.compiler.api.AnvilContext
import com.squareup.anvil.compiler.api.GeneratedFile
import com.squareup.anvil.compiler.codegen.GeneratedMethod.BindingMethod
import com.squareup.anvil.compiler.codegen.GeneratedMethod.ProviderMethod
import com.squareup.anvil.compiler.contributesBindingFqName
import com.squareup.anvil.compiler.contributesMultibindingFqName
import com.squareup.anvil.compiler.contributesToFqName
import com.squareup.anvil.compiler.daggerModuleFqName
import com.squareup.anvil.compiler.internal.annotationOrNull
import com.squareup.anvil.compiler.internal.asClassName
import com.squareup.anvil.compiler.internal.buildFile
import com.squareup.anvil.compiler.internal.capitalize
import com.squareup.anvil.compiler.internal.classesAndInnerClass
import com.squareup.anvil.compiler.internal.decapitalize
import com.squareup.anvil.compiler.internal.findAnnotation
import com.squareup.anvil.compiler.internal.generateClassName
import com.squareup.anvil.compiler.internal.hasAnnotation
import com.squareup.anvil.compiler.internal.replaces
import com.squareup.anvil.compiler.internal.requireFqName
import com.squareup.anvil.compiler.internal.safePackageString
import com.squareup.anvil.compiler.internal.scope
import com.squareup.anvil.compiler.internal.scopeOrNull
import com.squareup.anvil.compiler.internal.toClassReference
import com.squareup.anvil.compiler.mergeComponentFqName
import com.squareup.anvil.compiler.mergeModulesFqName
import com.squareup.anvil.compiler.mergeSubcomponentFqName
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier.ABSTRACT
import com.squareup.kotlinpoet.TypeSpec
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoMap
import dagger.multibindings.IntoSet
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtClassLiteralExpression
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import java.io.File

private val supportedFqNames = listOf(
  mergeComponentFqName,
  mergeSubcomponentFqName,
  mergeModulesFqName
)

internal class BindingModuleGenerator(
  private val classScanner: ClassScanner
) : FlushingCodeGenerator {

  override fun isApplicable(context: AnvilContext): Boolean {
    throw NotImplementedError("This should not actually be checked as we instantiate this manually")
  }

  // Keeps track of for which scopes which files were generated. Usually there is only one file,
  // but technically there can be multiple.
  private val mergedClasses = mutableMapOf<MergedClassKey, File>()

  private val excludedTypesForClass = mutableMapOf<MergedClassKey, List<FqName>>()

  private val contributedBindingClasses = mutableListOf<FqName>()
  private val contributedMultibindingClasses = mutableListOf<FqName>()
  private val contributedModuleClasses = mutableListOf<KtClassOrObject>()

  override fun generateCode(
    codeGenDir: File,
    module: ModuleDescriptor,
    projectFiles: Collection<KtFile>
  ): Collection<GeneratedFile> {
    // Even though we support multiple rounds the Kotlin compiler let's us generate code only once.
    // It's possible that we use the @ContributesBinding annotation in the module in which we
    // merge components. Remember for which classes a hint was generated and generate a @Binds
    // method for them later.
    contributedBindingClasses += findContributedBindingClasses(
      module = module,
      projectFiles = projectFiles,
      hintPackagePrefix = HINT_BINDING_PACKAGE_PREFIX
    )
    contributedMultibindingClasses += findContributedBindingClasses(
      module = module,
      projectFiles = projectFiles,
      hintPackagePrefix = HINT_MULTIBINDING_PACKAGE_PREFIX
    )

    val classes = projectFiles.classesAndInnerClass(module).toList()

    // Similar to the explanation above, we must track contributed modules.
    findContributedModules(classes, module)

    // Generate a Dagger module for each @MergeComponent and friends.
    return classes
      .mapNotNull { psiClass ->
        supportedFqNames
          .firstNotNullOfOrNull { supportedFqName ->
            psiClass.findAnnotation(supportedFqName, module)
          }
          ?.let { psiClass to it }
      }
      .map { (psiClass, mergeAnnotation) ->
        val annotationFqName = mergeAnnotation.requireFqName(module)
        val scope = psiClass.scope(annotationFqName, module)
        val mergedClassKey = MergedClassKey(scope, psiClass)

        // Remember for which scopes which types were excluded so that we later don't generate
        // a binding method for these types.
        psiClass.excludeOrNull(module, annotationFqName)
          ?.let { excludedTypesForClass[mergedClassKey] = it }

        val packageName = generatePackageName(psiClass)
        val className = psiClass.generateClassName()

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
          scope = scope.asString(),
          psiClass = psiClass,
          generatedMethods = emptyList()
        )

        mergedClasses[mergedClassKey] = file

        GeneratedFile(file, content)
      }
      .toList()
  }

  override fun flush(
    codeGenDir: File,
    module: ModuleDescriptor
  ): Collection<GeneratedFile> {
    return mergedClasses.map { (mergedClassKey, daggerModuleFile) ->
      val scope = mergedClassKey.scope

      // Precompute this list once per class since it's expensive.
      val excludedNames = excludedTypesForClass[mergedClassKey].orEmpty()

      // Contributed Dagger modules can replace other Dagger modules but also contributed bindings.
      // If a binding is replaced, then we must not generate the binding method.
      //
      // We precompute this list here and share the result in the methods below. Resolving classes
      // and types can be an expensive operation, so avoid doing it twice.
      val bindingsReplacedInDaggerModules = contributedModuleClasses
        .asSequence()
        .filter { it.scope(contributesToFqName, module) == scope }
        // Ignore replaced bindings specified by excluded modules for this scope.
        .filter { it.fqName !in excludedNames }
        .flatMap { clazz ->
          clazz.toClassReference().replaces(module, contributesToFqName).map { it.fqName }
        }
        .plus(
          classScanner
            .findContributedClasses(
              module = module,
              packageName = HINT_CONTRIBUTES_PACKAGE_PREFIX,
              annotation = contributesToFqName,
              scope = scope
            )
            .filter { it.annotationOrNull(daggerModuleFqName) != null }
            // Ignore replaced bindings specified by excluded modules for this scope.
            .filter { it.fqNameSafe !in excludedNames }
            .flatMap {
              it.toClassReference().replaces(module, contributesToFqName)
            }
            .map { it.fqName }
        )
        .toList()

      // Note that this is an inner function to share some of the parameters. It computes all
      // generated functions for contributed bindings and multibindings. The generated functions
      // are so similar that it makes sense to share the code between normal binding and
      // multibinding methods.
      fun getContributedBindingClasses(
        collectedClasses: List<FqName>,
        hintPackagePrefix: String,
        annotationFqName: FqName,
        isMultibinding: Boolean
      ): List<GeneratedMethod> {

        val contributedBindingsDependencies = classScanner.findContributedClasses(
          module,
          packageName = hintPackagePrefix,
          annotation = annotationFqName,
          scope = scope
        ).map { it.toClassReference() }

        val allContributedClasses = collectedClasses
          .map { name -> name.toClassReference(module) }
          .plus(contributedBindingsDependencies)

        val replacedBindings = allContributedClasses
          .flatMap { classReference ->
            classReference.replaces(module, annotationFqName).map { it.fqName }
          }

        return allContributedClasses
          .asSequence()
          .filterNot { it.fqName in replacedBindings }
          .filterNot { it.fqName in bindingsReplacedInDaggerModules }
          .filterNot { it.fqName in excludedNames }
          .filter { scope == it.scopeOrNull(module, annotationFqName) }
          .map {
            it.toContributedBinding(
              module = module,
              annotationFqName = annotationFqName,
              isMultibinding = isMultibinding
            )
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
          .map { binding -> binding.toGeneratedMethod(module, isMultibinding) }
          .toList()
      }

      val generatedMethods = getContributedBindingClasses(
        collectedClasses = contributedBindingClasses,
        hintPackagePrefix = HINT_BINDING_PACKAGE_PREFIX,
        annotationFqName = contributesBindingFqName,
        isMultibinding = false
      ) + getContributedBindingClasses(
        collectedClasses = contributedMultibindingClasses,
        hintPackagePrefix = HINT_MULTIBINDING_PACKAGE_PREFIX,
        annotationFqName = contributesMultibindingFqName,
        isMultibinding = true
      )

      val content = daggerModuleContent(
        scope = scope.asString(),
        psiClass = mergedClassKey.clazz,
        generatedMethods = generatedMethods
      )

      daggerModuleFile.writeText(content)

      GeneratedFile(daggerModuleFile, content)
    }
  }

  private fun findContributedBindingClasses(
    module: ModuleDescriptor,
    projectFiles: Collection<KtFile>,
    hintPackagePrefix: String
  ): List<FqName> {
    return projectFiles
      .filter {
        it.packageFqName.asString().startsWith(hintPackagePrefix)
      }
      .flatMap {
        it.findChildrenByClass(KtProperty::class.java).toList()
      }
      .mapNotNull { ktProperty ->
        // We can safely ignore scopes, we only care about the reference classes.
        if (ktProperty.name?.endsWith(REFERENCE_SUFFIX) != true) return@mapNotNull null

        (ktProperty.initializer as? KtClassLiteralExpression)
          ?.firstChild
          ?.requireFqName(module)
      }
  }

  private fun findContributedModules(
    classes: List<KtClassOrObject>,
    module: ModuleDescriptor
  ) {
    contributedModuleClasses += classes
      .filter {
        it.hasAnnotation(contributesToFqName, module) &&
          it.hasAnnotation(daggerModuleFqName, module)
      }
  }

  private fun KtClassOrObject.generateClassName(): String =
    generateClassName(separator = "") + ANVIL_MODULE_SUFFIX

  private fun generatePackageName(psiClass: KtClassOrObject): String = MODULE_PACKAGE_PREFIX +
    psiClass.containingKtFile.packageFqName.safePackageString(dotPrefix = true)

  private fun daggerModuleContent(
    scope: String,
    psiClass: KtClassOrObject,
    generatedMethods: List<GeneratedMethod>
  ): String {
    val className = psiClass.generateClassName()

    val bindingMethods = generatedMethods.filterIsInstance<BindingMethod>()
    val providerMethods = generatedMethods.filterIsInstance<ProviderMethod>()

    return FileSpec.buildFile(generatePackageName(psiClass), className) {
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
          .addAnnotation(
            AnnotationSpec
              .builder(ContributesTo::class)
              .addMember("$scope::class")
              .build()
          )
          .build()
      )
    }
  }
}

private sealed class GeneratedMethod {
  abstract val spec: FunSpec

  class ProviderMethod(override val spec: FunSpec) : GeneratedMethod()
  class BindingMethod(override val spec: FunSpec) : GeneratedMethod()
}

private fun List<ContributedBinding>.findHighestPriorityBinding(): ContributedBinding {
  if (size == 1) return this[0]

  val bindings = groupBy { it.priority }
    .toSortedMap()
    .let { it.getValue(it.lastKey()) }
    // In some very rare cases we can see a binding for the same type twice. Just in case filter
    // them, see https://github.com/square/anvil/issues/460.
    .distinctBy { it.contributedFqName }

  if (bindings.size > 1) {
    throw AnvilCompilationException(
      "There are multiple contributed bindings with the same bound type. The bound type is " +
        "${bindings[0].boundTypeClassName}. The contributed binding classes are: " +
        bindings.joinToString(
          prefix = "[",
          postfix = "]"
        ) { it.contributedFqName.asString() }
    )
  }

  return bindings[0]
}

private fun ContributedBinding.toGeneratedMethod(
  module: ModuleDescriptor,
  isMultibinding: Boolean
): GeneratedMethod {

  val isMapMultibinding = mapKeys.isNotEmpty()

  return if (contributedClassIsObject) {
    ProviderMethod(
      FunSpec.builder(
        name = contributedFqName.asString()
          .split(".")
          .joinToString(
            separator = "",
            prefix = "provide",
            postfix = if (isMultibinding) "Multi" else ""
          ) {
            it.capitalize()
          }
      )
        .addAnnotation(Provides::class)
        .apply {
          when {
            isMapMultibinding -> addAnnotation(IntoMap::class)
            isMultibinding -> addAnnotation(IntoSet::class)
          }
        }
        .addAnnotations(qualifiers)
        .addAnnotations(mapKeys)
        .returns(boundTypeClassName)
        .addStatement("return %T", contributedFqName.asClassName(module))
        .build()
    )
  } else {
    BindingMethod(
      FunSpec.builder(
        name = contributedFqName.asString()
          .split(".")
          .joinToString(
            separator = "",
            prefix = "bind",
            postfix = if (isMultibinding) "Multi" else ""
          ) {
            it.capitalize()
          }
      )
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
          name = contributedFqName.shortName().asString().decapitalize(),
          type = contributedFqName.asClassName(module)
        )
        .returns(boundTypeClassName)
        .build()
    )
  }
}

private val Collection<GeneratedMethod>.specs get() = map { it.spec }

private class MergedClassKey(
  val scope: FqName,
  val clazz: KtClassOrObject
) {
  private val classFqName = clazz.requireFqName()

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as MergedClassKey

    if (scope != other.scope) return false
    if (classFqName != other.classFqName) return false

    return true
  }

  override fun hashCode(): Int {
    var result = scope.hashCode()
    result = 31 * result + classFqName.hashCode()
    return result
  }
}
