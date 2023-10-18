package com.squareup.anvil.compiler.ksp.v2

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.isAnnotationPresent
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.squareup.anvil.annotations.ContributesBinding
import com.squareup.anvil.annotations.ContributesMultibinding
import com.squareup.anvil.annotations.ContributesSubcomponent
import com.squareup.anvil.annotations.ContributesTo
import com.squareup.anvil.annotations.compat.MergeModules
import com.squareup.anvil.compiler.ANVIL_MODULE_SUFFIX
import com.squareup.anvil.compiler.MODULE_PACKAGE_PREFIX
import com.squareup.anvil.compiler.SUBCOMPONENT_MODULE
import com.squareup.anvil.compiler.classReferenceOrNull
import com.squareup.anvil.compiler.codegen.generatedAnvilSubcomponent
import com.squareup.anvil.compiler.codegen.ksp.KspAnvilException
import com.squareup.anvil.compiler.codegen.ksp.argumentAt
import com.squareup.anvil.compiler.codegen.ksp.checkClassIsPublic
import com.squareup.anvil.compiler.codegen.ksp.getKSAnnotationsByQualifiedName
import com.squareup.anvil.compiler.codegen.ksp.getKSAnnotationsByType
import com.squareup.anvil.compiler.codegen.ksp.isInterface
import com.squareup.anvil.compiler.codegen.ksp.scope
import com.squareup.anvil.compiler.contributesBindingFqName
import com.squareup.anvil.compiler.contributesMultibindingFqName
import com.squareup.anvil.compiler.contributesSubcomponentFqName
import com.squareup.anvil.compiler.contributesToFqName
import com.squareup.anvil.compiler.internal.classIdBestGuess
import com.squareup.anvil.compiler.internal.reference.ClassReference
import com.squareup.anvil.compiler.isAnvilModule
import com.squareup.anvil.compiler.ksp.atLeastOneAnnotation
import com.squareup.anvil.compiler.ksp.classDeclaration
import com.squareup.anvil.compiler.ksp.classDeclarationOrNull
import com.squareup.anvil.compiler.ksp.classId
import com.squareup.anvil.compiler.ksp.declaringClass
import com.squareup.anvil.compiler.ksp.exclude
import com.squareup.anvil.compiler.ksp.findAllKSAnnotations
import com.squareup.anvil.compiler.ksp.fqName
import com.squareup.anvil.compiler.ksp.generateClassName
import com.squareup.anvil.compiler.ksp.parentScope
import com.squareup.anvil.compiler.ksp.replaces
import com.squareup.anvil.compiler.ksp.safePackageString
import com.squareup.kotlinpoet.ksp.toClassName
import dagger.Module
import org.jetbrains.kotlin.codegen.ImplementationBodyCodegen
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.org.objectweb.asm.Type

/**
 * Finds all contributed Dagger modules and adds them to Dagger components annotated with
 * `@MergeComponent` or `@MergeSubcomponent`. This class is responsible for generating the
 * `@Component` and `@Subcomponent` annotation required for Dagger.
 */
@OptIn(KspExperimental::class)
internal object ModuleMergerKSP {

  fun mergeModules(
    classScanner: ClassScannerKSP,
    resolver: Resolver,
    clazz: KSClassDeclaration,
    annotations: List<KSAnnotation>,
    scopes: Set<KSClassDeclaration>,
  ): Set<KSClassDeclaration> {
    @Suppress("UNCHECKED_CAST")
    val predefinedModules = annotations.flatMap {
      it.argumentAt("modules")?.value as? List<KSType> ?: emptyList()
    }.map { it.classDeclaration }
    val anvilModuleName = createAnvilModuleName(clazz)

    val contributesAnnotations = scopes
      .flatMap { scope ->
        classScanner
          .findContributedClasses(
            resolver = resolver,
            annotation = contributesToFqName,
            scope = scope
          )
      }
      .filter {
        // We generate a Dagger module for each merged component. We use Anvil itself to
        // contribute this generated module. It's possible that there are multiple components
        // merging the same scope or the same scope is merged in different Gradle modules which
        // depend on each other. This would cause duplicate bindings, because the generated
        // modules contain the same bindings and are contributed to the same scope. To avoid this
        // issue we filter all generated Anvil modules except for the one that was generated for
        // this specific class.
        !it.fqName.isAnvilModule() || it.fqName == anvilModuleName
      }
      .flatMap { contributedClass ->
        contributedClass
          .getKSAnnotationsByType(ContributesTo::class)
          .filter { it.scope() in scopes }
      }
      .filter { contributesAnnotation ->
        val contributedClass = contributesAnnotation.declaringClass()
        val moduleAnnotation = contributedClass.getKSAnnotationsByType(Module::class)
          .singleOrNull()
        val mergeModulesAnnotation =
          contributedClass.getKSAnnotationsByType(MergeModules::class)
            .singleOrNull()

        if (!contributedClass.isInterface() &&
          moduleAnnotation == null &&
          mergeModulesAnnotation == null
        ) {
          throw KspAnvilException(
            "${contributedClass.fqName} is annotated with " +
              "@${ContributesTo::class.simpleName}, but this class is neither an interface " +
              "nor a Dagger module. Did you forget to add @${Module::class.simpleName}?",
            contributedClass,
          )
        }

        contributedClass.checkClassIsPublic {
          "${contributedClass.fqName} is contributed to the Dagger graph, but the " +
            "module is not public. Only public modules are supported."
        }

        moduleAnnotation != null || mergeModulesAnnotation != null
      }
      // Convert the sequence to a list to avoid iterating it twice. We use the result twice
      // for replaced classes and the final result.
      .toList()

    val excludedModules = annotations.flatMap { it.exclude() }
      .map { it.classDeclaration }
      .onEach { excludedClass ->
        // Verify that the excluded classes use the same scope.
        val contributesToOurScope = excludedClass
          .findAllKSAnnotations(
            ContributesTo::class,
            ContributesBinding::class,
            ContributesMultibinding::class
          )
          .map { it.scope() }
          .plus(
            excludedClass.findAllKSAnnotations(ContributesSubcomponent::class)
              .map { it.parentScope() }
          )
          .any { scope -> scope in scopes }

        if (!contributesToOurScope) {
          throw KspAnvilException(
            message = "${clazz.fqName} with scopes " +
              "${scopes.joinToString(prefix = "[", postfix = "]") { it.fqName.asString() }} " +
              "wants to exclude ${excludedClass.fqName}, but the excluded class isn't " +
              "contributed to the same scope.",
            node = clazz
          )
        }
      }

    val replacedModules = contributesAnnotations
      // Ignore replaced modules or bindings specified by excluded modules.
      .filter { contributesAnnotation ->
        contributesAnnotation.declaringClass() !in excludedModules
      }
      .flatMap { contributesAnnotation ->
        val contributedClass = contributesAnnotation.declaringClass()
        contributesAnnotation.replaces()
          .map { it.classDeclaration }
          .onEach { classToReplace ->
            // Verify has @Module annotation. It doesn't make sense for a Dagger module to
            // replace a non-Dagger module.
            if (!classToReplace.isAnnotationPresent(Module::class) &&
              !classToReplace.isAnnotationPresent(ContributesBinding::class) &&
              !classToReplace.isAnnotationPresent(ContributesMultibinding::class)
            ) {
              throw KspAnvilException(
                message = "${contributedClass.fqName} wants to replace " +
                  "${classToReplace.fqName}, but the class being replaced is not a Dagger module.",
                node = contributedClass
              )
            }

            checkSameScope(contributedClass, classToReplace, scopes)
          }
      }

    fun replacedModulesByContributedBinding(
      annotationFqName: FqName
    ): Sequence<KSClassDeclaration> {
      return scopes.asSequence()
        .flatMap { scope ->
          classScanner
            .findContributedClasses(
              resolver = resolver,
              annotation = annotationFqName,
              scope = scope
            )
        }
        .flatMap { contributedClass ->
          contributedClass.getKSAnnotationsByQualifiedName(annotationFqName.asString())
            .filter { it.scope() in scopes }
            .flatMap { it.replaces() }
            .onEach { classToReplace ->
              checkSameScope(contributedClass, classToReplace.classDeclaration, scopes)
            }
        }
        .map { it.classDeclaration }
    }

    val replacedModulesByContributedBindings = replacedModulesByContributedBinding(
      annotationFqName = contributesBindingFqName
    )

    val replacedModulesByContributedMultibindings = replacedModulesByContributedBinding(
      annotationFqName = contributesMultibindingFqName
    )

    val intersect = predefinedModules.intersect(excludedModules.toSet())
    if (intersect.isNotEmpty()) {
      throw KspAnvilException(
        "${clazz.toClassName()} includes and excludes modules " +
          "at the same time: ${intersect.joinToString { it.classId.relativeClassName.asString() }}",
        clazz,
      )
    }

    val contributedSubcomponentModules =
      findContributedSubcomponentModules(classScanner, clazz, scopes, resolver)

    val contributedModuleTypes = contributesAnnotations
      .asSequence()
      .map { it.declaringClass() }
      .minus(replacedModules.toSet())
      .minus(replacedModulesByContributedBindings.toSet())
      .minus(replacedModulesByContributedMultibindings.toSet())
      .minus(excludedModules.toSet())
      .plus(predefinedModules)
      .plus(contributedSubcomponentModules)
      .distinct()
      .toSet()

    return contributedModuleTypes
  }

  @Suppress("UNCHECKED_CAST")
  private fun Collection<ClassReference>.types(codegen: ImplementationBodyCodegen): List<Type> {
    return (this as Collection<ClassReference.Descriptor>)
      .map { codegen.typeMapper.mapType(it.clazz) }
  }

  private fun createAnvilModuleName(clazz: KSClassDeclaration): FqName {
    val name = "$MODULE_PACKAGE_PREFIX." +
      clazz.packageName.safePackageString() +
      clazz.generateClassName(
        separator = "",
        suffix = ANVIL_MODULE_SUFFIX
      ).relativeClassName.toString()
    return FqName(name)
  }

  private fun checkSameScope(
    contributedClass: KSClassDeclaration,
    classToReplace: KSClassDeclaration,
    scopes: Set<KSClassDeclaration>
  ) {
    val contributesToOurScope = classToReplace
      .findAllKSAnnotations(
        ContributesTo::class,
        ContributesBinding::class,
        ContributesMultibinding::class
      )
      .map { it.scope() }
      .any { scope -> scope in scopes }

    if (!contributesToOurScope) {
      throw KspAnvilException(
        node = contributedClass,
        message = "${contributedClass.fqName} with scopes " +
          "${scopes.joinToString(prefix = "[", postfix = "]") { it.fqName.asString() }} " +
          "wants to replace ${classToReplace.fqName}, but the replaced class isn't " +
          "contributed to the same scope."
      )
    }
  }

  private fun findContributedSubcomponentModules(
    classScanner: ClassScannerKSP,
    clazz: KSClassDeclaration,
    scopes: Set<KSClassDeclaration>,
    resolver: Resolver
  ): Sequence<KSClassDeclaration> {
    return classScanner
      .findContributedClasses(
        resolver = resolver,
        annotation = contributesSubcomponentFqName,
        scope = null
      )
      .filter { contributedClass ->
        contributedClass
          .atLeastOneAnnotation(ContributesSubcomponent::class)
          .any { it.parentScope() in scopes }
      }
      .mapNotNull { contributedSubcomponent ->
        contributedSubcomponent.classId
          .generatedAnvilSubcomponent(clazz.classId)
          .createNestedClassId(Name.identifier(SUBCOMPONENT_MODULE))
          .classDeclarationOrNull(resolver)
      }
  }
}
