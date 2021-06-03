package com.squareup.anvil.compiler.codegen

import com.squareup.anvil.annotations.ContributesTo
import com.squareup.anvil.compiler.ANVIL_MODULE_SUFFIX
import com.squareup.anvil.compiler.ClassScanner
import com.squareup.anvil.compiler.HINT_BINDING_PACKAGE_PREFIX
import com.squareup.anvil.compiler.HINT_CONTRIBUTES_PACKAGE_PREFIX
import com.squareup.anvil.compiler.HINT_MULTIBINDING_PACKAGE_PREFIX
import com.squareup.anvil.compiler.MODULE_PACKAGE_PREFIX
import com.squareup.anvil.compiler.api.AnvilCompilationException
import com.squareup.anvil.compiler.api.AnvilContext
import com.squareup.anvil.compiler.api.GeneratedFile
import com.squareup.anvil.compiler.boundType
import com.squareup.anvil.compiler.codegen.GeneratedMethod.BindingMethod
import com.squareup.anvil.compiler.codegen.GeneratedMethod.ProviderMethod
import com.squareup.anvil.compiler.contributesBindingFqName
import com.squareup.anvil.compiler.contributesMultibindingFqName
import com.squareup.anvil.compiler.contributesToFqName
import com.squareup.anvil.compiler.daggerModuleFqName
import com.squareup.anvil.compiler.ignoreQualifier
import com.squareup.anvil.compiler.internal.annotation
import com.squareup.anvil.compiler.internal.annotationOrNull
import com.squareup.anvil.compiler.internal.argumentType
import com.squareup.anvil.compiler.internal.asClassName
import com.squareup.anvil.compiler.internal.buildFile
import com.squareup.anvil.compiler.internal.capitalize
import com.squareup.anvil.compiler.internal.classDescriptorForType
import com.squareup.anvil.compiler.internal.classesAndInnerClass
import com.squareup.anvil.compiler.internal.decapitalize
import com.squareup.anvil.compiler.internal.generateClassName
import com.squareup.anvil.compiler.internal.getAnnotationValue
import com.squareup.anvil.compiler.internal.hasAnnotation
import com.squareup.anvil.compiler.internal.isQualifier
import com.squareup.anvil.compiler.internal.requireClassDescriptor
import com.squareup.anvil.compiler.internal.requireFqName
import com.squareup.anvil.compiler.internal.safePackageString
import com.squareup.anvil.compiler.internal.scope
import com.squareup.anvil.compiler.internal.toAnnotationSpec
import com.squareup.anvil.compiler.isMapKey
import com.squareup.anvil.compiler.mergeComponentFqName
import com.squareup.anvil.compiler.mergeModulesFqName
import com.squareup.anvil.compiler.mergeSubcomponentFqName
import com.squareup.anvil.compiler.priority
import com.squareup.anvil.compiler.replaces
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
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.descriptors.annotations.AnnotationDescriptor
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtClassLiteralExpression
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.resolve.DescriptorUtils
import org.jetbrains.kotlin.resolve.constants.ArrayValue
import org.jetbrains.kotlin.resolve.constants.EnumValue
import org.jetbrains.kotlin.resolve.constants.KClassValue
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe
import org.jetbrains.kotlin.resolve.descriptorUtil.getAllSuperClassifiers
import org.jetbrains.kotlin.types.KotlinType
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
  private val mergedScopes = mutableMapOf<FqName, MutableList<Pair<File, KtClassOrObject>>>()
    .withDefault { mutableListOf() }

  private val excludedTypesForScope = mutableMapOf<FqName, List<ClassDescriptor>>()

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
      .filter { psiClass -> supportedFqNames.any { psiClass.hasAnnotation(it, module) } }
      .map { psiClass ->
        val classDescriptor = psiClass.requireClassDescriptor(module)

        // The annotation must be present due to the filter above.
        val mergeAnnotation = supportedFqNames
          .firstNotNullOf { classDescriptor.annotationOrNull(it) }

        val scope = mergeAnnotation.scope(module).fqNameSafe

        // Remember for which scopes which types were excluded so that we later don't generate
        // a binding method for these types.
        (mergeAnnotation.getAnnotationValue("exclude") as? ArrayValue)?.value
          ?.map {
            it.argumentType(module).classDescriptorForType()
          }
          ?.let { excludedTypesForScope[scope] = it }

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

        mergedScopes[scope] = mergedScopes.getValue(scope)
          .apply { this += file to psiClass }

        GeneratedFile(file, content)
      }
      .toList()
  }

  override fun flush(
    codeGenDir: File,
    module: ModuleDescriptor
  ): Collection<GeneratedFile> {
    return mergedScopes.flatMap { (scope, daggerModuleFiles) ->
      // Contributed Dagger modules can replace other Dagger modules but also contributed bindings.
      // If a binding is replaced, then we must not generate the binding method.
      //
      // We precompute this list here and share the result in the methods below. Resolving classes
      // and types can be an expensive operation, so avoid doing it twice.
      val bindingsReplacedInDaggerModules = contributedModuleClasses
        .asSequence()
        .filter { it.scope(contributesToFqName, module) == scope }
        .filter { it.fqName !in excludedTypesForScope[scope].orEmpty().map { it.fqNameSafe } }
        .flatMap { it.replaces(contributesToFqName, module) }
        .plus(
          classScanner
            .findContributedClasses(
              module = module,
              packageName = HINT_CONTRIBUTES_PACKAGE_PREFIX,
              annotation = contributesToFqName,
              scope = scope
            )
            .filter { it.annotationOrNull(daggerModuleFqName) != null }
            .filter { it !in excludedTypesForScope[scope].orEmpty() }
            .flatMap {
              it.annotationOrNull(contributesToFqName, scope)
                ?.replaces(module)
                ?: emptyList()
            }
            .map { it.fqNameSafe }
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
        val contributedBindingsThisModule = collectedClasses
          .asSequence()
          .map { it.requireClassDescriptor(module) }

        val contributedBindingsDependencies = classScanner.findContributedClasses(
          module,
          packageName = hintPackagePrefix,
          annotation = annotationFqName,
          scope = scope
        )

        val replacedBindings = (contributedBindingsThisModule + contributedBindingsDependencies)
          .flatMap {
            it.annotationOrNull(annotationFqName, scope = scope)
              ?.replaces(module)
              ?: emptyList()
          }

        return (contributedBindingsThisModule + contributedBindingsDependencies)
          .minus(replacedBindings)
          .filterNot {
            it.fqNameSafe in bindingsReplacedInDaggerModules
          }
          .minus(excludedTypesForScope[scope].orEmpty())
          .filter {
            val annotation = it.annotationOrNull(annotationFqName)
            annotation != null && scope == annotation.scope(module).fqNameSafe
          }
          .map { contributedClass ->
            val annotation = contributedClass.annotation(annotationFqName)
            val boundType = annotation.boundType(module, contributedClass, annotationFqName)

            checkExtendsBoundType(type = contributedClass, boundType = boundType)
            checkNotGeneric(type = contributedClass, boundTypeDescriptor = boundType)

            Binding(contributedClass, annotation, boundType)
          }
          .let { bindings ->
            if (isMultibinding) {
              bindings
            } else {
              bindings
                .groupBy { binding -> binding.key(module) }
                .map {
                  it.value.findHighestPriorityBinding()
                }
                .asSequence()
            }
          }
          .map { (contributedClass, annotation, boundType) ->
            val concreteType = contributedClass.fqNameSafe

            val qualifiers = if (annotation.ignoreQualifier()) {
              emptyList()
            } else {
              contributedClass.annotations
                .filter { it.isQualifier() }
                .map { it.toAnnotationSpec(module) }
            }

            val mapKeys = if (isMultibinding) {
              contributedClass.annotations
                .filter { it.isMapKey() }
                .map { it.toAnnotationSpec(module) }
            } else {
              emptyList()
            }
            val isMapMultibinding = mapKeys.isNotEmpty()

            if (DescriptorUtils.isObject(contributedClass)) {
              ProviderMethod(
                FunSpec
                  .builder(
                    name = concreteType
                      .asString()
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
                  .returns(boundType.asClassName())
                  .addStatement("return %T", contributedClass.asClassName())
                  .build()
              )
            } else {
              BindingMethod(
                FunSpec
                  .builder(
                    name = concreteType
                      .asString()
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
                    name = concreteType.shortName().asString().decapitalize(),
                    type = contributedClass.asClassName()
                  )
                  .returns(boundType.asClassName())
                  .build()
              )
            }
          }
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

      daggerModuleFiles.map { (file, psiClass) ->
        val content = daggerModuleContent(
          scope = scope.asString(),
          psiClass = psiClass,
          generatedMethods = generatedMethods
        )

        file.writeText(content)

        GeneratedFile(file, content)
      }
    }
  }

  private fun checkNotGeneric(
    type: ClassDescriptor,
    boundTypeDescriptor: ClassDescriptor
  ) {
    if (boundTypeDescriptor.declaredTypeParameters.isNotEmpty()) {

      fun KotlinType.describeTypeParameters(): String = arguments
        .ifEmpty { return "" }
        .joinToString(prefix = "<", postfix = ">") { typeArgument ->
          typeArgument.type.classDescriptorForType().name.asString() +
            typeArgument.type.describeTypeParameters()
        }

      val boundType = type.typeConstructor
        .supertypes
        .first { it.classDescriptorForType() == boundTypeDescriptor }

      throw AnvilCompilationException(
        classDescriptor = boundTypeDescriptor,
        message = "Binding ${boundTypeDescriptor.fqNameSafe} contains type parameters(s)" +
          " ${boundType.describeTypeParameters()}." +
          " Type parameters in bindings are not supported. This binding needs" +
          " to be contributed to a dagger module manually"
      )
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

  private fun checkExtendsBoundType(
    type: ClassDescriptor,
    boundType: ClassDescriptor
  ) {
    val boundFqName = boundType.fqNameSafe
    val hasSuperType = type.getAllSuperClassifiers()
      .any { it.fqNameSafe == boundFqName }

    if (!hasSuperType) {
      throw AnvilCompilationException(
        classDescriptor = type,
        message = "${type.fqNameSafe} contributes a binding for $boundFqName, but doesn't " +
          "extend this type."
      )
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

private data class Binding(
  val contributedClass: ClassDescriptor,
  val annotation: AnnotationDescriptor,
  val boundType: ClassDescriptor
)

private fun List<Binding>.findHighestPriorityBinding(): Binding {
  if (size == 1) return this[0]

  val bindings = groupBy { it.annotation.priority() }
    .toSortedMap()
    .let { it.getValue(it.lastKey()) }

  if (bindings.size > 1) {
    throw AnvilCompilationException(
      "There are multiple contributed bindings with the same bound type. The bound type is " +
        "${bindings[0].boundType.fqNameSafe}. The contributed binding classes are: " +
        bindings.joinToString(
          prefix = "[",
          postfix = "]"
        ) { it.contributedClass.fqNameSafe.asString() }
    )
  }

  return bindings[0]
}

private val Collection<GeneratedMethod>.specs get() = map { it.spec }

private fun Binding.key(module: ModuleDescriptor): String {
  // Careful! If we ever decide to support generic types, then we might need to use the
  // Kotlin type and not just the FqName.
  val fqName = boundType.fqNameSafe.asString()
  if (annotation.ignoreQualifier()) return fqName

  // Note that we sort all elements. That's important for a stable string comparison.
  val allArguments = contributedClass.annotations
    .filter { it.isQualifier() }
    .sortedBy { it.requireFqName().hashCode() }
    .joinToString(separator = "") { annotation ->
      val annotationFqName = annotation.requireFqName().asString()

      val argumentString = annotation.allValueArguments
        .toList()
        .sortedBy { it.first }
        .map { (name, value) ->
          val valueString = when (value) {
            is KClassValue -> value.argumentType(module)
              .classDescriptorForType().fqNameSafe.asString()
            is EnumValue -> value.enumEntryName.asString()
            // String, int, long, ... other primitives.
            else -> value.toString()
          }

          name.asString() + valueString
        }

      annotationFqName + argumentString
    }

  return fqName + allArguments
}
