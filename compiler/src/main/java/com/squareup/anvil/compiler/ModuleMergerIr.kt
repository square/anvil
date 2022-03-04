package com.squareup.anvil.compiler

import com.squareup.anvil.annotations.ContributesTo
import com.squareup.anvil.compiler.codegen.generatedAnvilSubcomponent
import com.squareup.anvil.compiler.codegen.reference.AnnotationReferenceIr
import com.squareup.anvil.compiler.codegen.reference.AnvilCompilationExceptionClassReferenceIr
import com.squareup.anvil.compiler.codegen.reference.ClassReferenceIr
import com.squareup.anvil.compiler.codegen.reference.RealAnvilModuleDescriptor
import com.squareup.anvil.compiler.codegen.reference.find
import com.squareup.anvil.compiler.codegen.reference.toClassReference
import com.squareup.anvil.compiler.internal.reference.Visibility.PUBLIC
import com.squareup.anvil.compiler.internal.safePackageString
import dagger.Module
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.expressions.impl.IrClassReferenceImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrConstructorCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrVarargImpl
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.types.impl.IrDynamicTypeImpl
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.types.Variance.INVARIANT

internal class ModuleMergerIr(
  private val classScanner: ClassScanner,
  private val moduleDescriptorFactory: RealAnvilModuleDescriptor.Factory
) : IrGenerationExtension {
  override fun generate(
    moduleFragment: IrModuleFragment,
    pluginContext: IrPluginContext
  ) {
    moduleFragment.transform(
      object : IrElementTransformerVoid() {
        override fun visitClass(declaration: IrClass): IrStatement {
          if (declaration.shouldIgnore()) return super.visitClass(declaration)

          val declarationReference = declaration.symbol.toClassReference(pluginContext)
          val annotationContext = AnnotationContext.create(declarationReference)
            ?: return super.visitClass(declaration)

          annotationContext.generateDaggerAnnotation(
            moduleFragment,
            pluginContext,
            declarationReference
          )
          return super.visitClass(declaration)
        }
      },
      null
    )
  }

  private fun AnnotationContext.generateDaggerAnnotation(
    moduleFragment: IrModuleFragment,
    pluginContext: IrPluginContext,
    declaration: ClassReferenceIr
  ) {
    if (declaration.isAnnotatedWith(daggerFqName)) {
      throw AnvilCompilationExceptionClassReferenceIr(
        message = "When using @${annotationFqName.shortName()} it's not allowed to annotate " +
          "the same class with @${daggerFqName.shortName()}. The Dagger annotation will " +
          "be generated.",
        classReference = declaration
      )
    }

    val scope = annotation.scope
    val predefinedModules =
      annotation.argumentOrNull(modulesKeyword)?.value<List<ClassReferenceIr>>().orEmpty()

    val anvilModuleName = createAnvilModuleName(declaration)

    val modules = classScanner
      .findContributedClasses(
        pluginContext = pluginContext,
        moduleFragment = moduleFragment,
        annotation = contributesToFqName,
        scope = scope,
        moduleDescriptorFactory = moduleDescriptorFactory
      )
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
      .flatMap {
        it.annotations.find(annotationName = contributesToFqName, scopeName = scope.fqName)
      }
      .filter { contributesAnnotation ->
        val contributedClass = contributesAnnotation.declaringClass
        val moduleAnnotation = contributedClass.annotations.find(daggerModuleFqName).singleOrNull()
        val mergeModulesAnnotation =
          contributedClass.annotations.find(mergeModulesFqName).singleOrNull()

        if (!contributedClass.isInterface &&
          moduleAnnotation == null &&
          mergeModulesAnnotation == null
        ) {
          throw AnvilCompilationExceptionClassReferenceIr(
            message = "${contributedClass.fqName} is annotated with " +
              "@${ContributesTo::class.simpleName}, but this class is neither an interface " +
              "nor a Dagger module. Did you forget to add @${Module::class.simpleName}?",
            classReference = contributedClass
          )
        }

        moduleAnnotation != null || mergeModulesAnnotation != null
      }
      .onEach { contributesAnnotation ->
        val contributedClass = contributesAnnotation.declaringClass
        if (contributedClass.visibility != PUBLIC) {
          throw AnvilCompilationExceptionClassReferenceIr(
            message = "${contributedClass.fqName} is contributed to the Dagger graph, but the " +
              "module is not public. Only public modules are supported.",
            classReference = contributedClass
          )
        }
      }
      // Convert the sequence to a list to avoid iterating it twice. We use the result twice
      // for replaced classes and the final result.
      .toList()

    val excludedModules = annotation
      .excludedClasses
      .onEach { excludedClass ->
        val scopes = excludedClass.annotations.find(contributesToFqName).map { it.scope } +
          excludedClass.annotations.find(contributesBindingFqName).map { it.scope } +
          excludedClass.annotations.find(contributesMultibindingFqName).map { it.scope } +
          excludedClass.annotations.find(contributesSubcomponentFqName).map { it.parentScope }

        // Verify that the replaced classes use the same scope.
        val contributesToOurScope = scopes.any { it == scope }

        if (!contributesToOurScope) {
          throw AnvilCompilationExceptionClassReferenceIr(
            message = "${declaration.fqName} with scope ${scope.fqName} wants to exclude " +
              "${excludedClass.fqName}, but the excluded class isn't contributed to the " +
              "same scope.",
            classReference = declaration
          )
        }
      }

    val replacedModules = modules
      // Ignore replaced modules or bindings specified by excluded modules.
      .filter { contributesAnnotation ->
        contributesAnnotation.declaringClass !in excludedModules
      }
      .flatMap { contributesAnnotation ->
        val contributedClass = contributesAnnotation.declaringClass
        contributesAnnotation.replacedClasses
          .onEach { classToReplace ->
            // Verify has @Module annotation. It doesn't make sense for a Dagger module to
            // replace a non-Dagger module.
            if (!classToReplace.isAnnotatedWith(daggerModuleFqName) &&
              !classToReplace.isAnnotatedWith(contributesBindingFqName) &&
              !classToReplace.isAnnotatedWith(contributesMultibindingFqName)
            ) {
              throw AnvilCompilationExceptionClassReferenceIr(
                message = "${contributedClass.fqName} wants to replace " +
                  "${classToReplace.fqName}, but the class being " +
                  "replaced is not a Dagger module.",
                classReference = contributedClass
              )
            }

            checkSameScope(contributedClass, classToReplace, scope)
          }
      }

    fun replacedModulesByContributedBinding(
      annotationFqName: FqName,
      hintPackagePrefix: String
    ): Sequence<ClassReferenceIr> {
      return classScanner
        .findContributedClasses(
          pluginContext = pluginContext,
          moduleFragment = moduleFragment,
          annotation = annotationFqName,
          scope = scope,
          moduleDescriptorFactory = moduleDescriptorFactory
        )
        .flatMap { contributedClass ->
          contributedClass.annotations
            .find(annotationName = annotationFqName, scopeName = scope.fqName)
            .flatMap { it.replacedClasses }
            .onEach { classToReplace ->
              checkSameScope(contributedClass, classToReplace, scope)
            }
        }
    }

    val replacedModulesByContributedBindings = replacedModulesByContributedBinding(
      annotationFqName = contributesBindingFqName,
      hintPackagePrefix = HINT_BINDING_PACKAGE_PREFIX
    )

    val replacedModulesByContributedMultibindings = replacedModulesByContributedBinding(
      annotationFqName = contributesMultibindingFqName,
      hintPackagePrefix = HINT_MULTIBINDING_PACKAGE_PREFIX
    )

    if (predefinedModules.isNotEmpty()) {
      val intersect = predefinedModules.intersect(excludedModules.toSet())
      if (intersect.isNotEmpty()) {
        throw AnvilCompilationExceptionClassReferenceIr(
          message = "${declaration.fqName} includes and excludes modules " +
            "at the same time: ${intersect.joinToString { it.fqName.asString() }}",
          classReference = declaration
        )
      }
    }

    val contributedSubcomponentModules =
      findContributedSubcomponentModules(
        declaration,
        scope,
        pluginContext,
        moduleFragment
      )

    val contributedModules = modules
      .asSequence()
      .map { it.declaringClass }
      .minus(replacedModules.toSet())
      .minus(replacedModulesByContributedBindings.toSet())
      .minus(replacedModulesByContributedMultibindings.toSet())
      .minus(excludedModules.toSet())
      .plus(predefinedModules)
      .plus(contributedSubcomponentModules)
      .distinct()
      .map { it.clazz.owner }

    val annotationConstructorCall = IrConstructorCallImpl
      .fromSymbolOwner(
        startOffset = UNDEFINED_OFFSET,
        endOffset = UNDEFINED_OFFSET,
        type = pluginContext.requireReferenceClass(daggerFqName).defaultType,
        constructorSymbol = pluginContext
          .referenceConstructors(daggerFqName)
          .single { it.owner.isPrimary }
      )
      .apply {
        putValueArgument(
          index = 0,
          valueArgument = IrVarargImpl(
            startOffset = UNDEFINED_OFFSET,
            endOffset = UNDEFINED_OFFSET,
            type = pluginContext.symbols.array.defaultType,
            varargElementType = IrDynamicTypeImpl(null, emptyList(), INVARIANT),
            elements = contributedModules
              .map {
                IrClassReferenceImpl(
                  startOffset = UNDEFINED_OFFSET,
                  endOffset = UNDEFINED_OFFSET,
                  type = it.defaultType,
                  symbol = it.symbol,
                  classType = it.defaultType
                )
              }
              .toList()
          )
        )

        fun copyArrayValue(name: String) {
          declaration.annotations.find(annotationFqName, scope.fqName).single()
            .argumentOrNull(name)
            ?.argumentExpression
            ?.let { expression ->
              putValueArgument(1, expression)
            }
        }

        if (isComponent) {
          copyArrayValue("dependencies")
        }

        if (isModule) {
          copyArrayValue("subcomponents")
        }
      }

    // Since we are modifying the state of the code here, this does not need to be reflected in
    // the associated [ClassReferenceIr] which is more of an initial snapshot.
    declaration.clazz.owner.annotations += annotationConstructorCall
  }

  private fun createAnvilModuleName(declaration: ClassReferenceIr): FqName {
    val packageName = declaration.packageFqName?.safePackageString() ?: ""

    val name = "$MODULE_PACKAGE_PREFIX.$packageName" +
      declaration.enclosingClassesWithSelf
        .joinToString(separator = "", postfix = ANVIL_MODULE_SUFFIX) {
          it.shortName
        }
    return FqName(name)
  }

  private fun checkSameScope(
    contributedClass: ClassReferenceIr,
    classToReplace: ClassReferenceIr,
    scope: ClassReferenceIr
  ) {
    val scopes = classToReplace.annotations.find(contributesToFqName).map { it.scope } +
      classToReplace.annotations.find(contributesBindingFqName).map { it.scope } +
      classToReplace.annotations.find(contributesMultibindingFqName).map { it.scope }

    val contributesToOurScope = scopes.any { it == scope }

    if (!contributesToOurScope) {
      throw AnvilCompilationExceptionClassReferenceIr(
        classReference = contributedClass,
        message = "${contributedClass.fqName} with scope ${scope.fqName} wants to replace " +
          "${classToReplace.fqName}, but the replaced class isn't " +
          "contributed to the same scope."
      )
    }
  }

  private fun findContributedSubcomponentModules(
    declaration: ClassReferenceIr,
    scope: ClassReferenceIr,
    pluginContext: IrPluginContext,
    moduleFragment: IrModuleFragment
  ): Sequence<ClassReferenceIr> {
    return classScanner
      .findContributedClasses(
        pluginContext = pluginContext,
        moduleFragment = moduleFragment,
        annotation = contributesSubcomponentFqName,
        scope = null,
        moduleDescriptorFactory = moduleDescriptorFactory
      )
      .filter { clazz ->
        clazz.annotations.find(contributesSubcomponentFqName).any { it.parentScope == scope }
      }
      .mapNotNull { contributedSubcomponent ->
        contributedSubcomponent.classId
          .generatedAnvilSubcomponent(declaration.classId)
          .createNestedClassId(Name.identifier(SUBCOMPONENT_MODULE))
          .referenceClassOrNull(pluginContext)
      }
  }
}

@Suppress("DataClassPrivateConstructor")
private data class AnnotationContext private constructor(
  val annotation: AnnotationReferenceIr,
  val annotationFqName: FqName,
  val daggerFqName: FqName,
  val modulesKeyword: String
) {
  val isComponent = annotationFqName == mergeComponentFqName
  val isModule = annotationFqName == mergeModulesFqName

  companion object {
    fun create(declaration: ClassReferenceIr): AnnotationContext? {
      declaration.annotations.find(mergeComponentFqName).singleOrNull()
        ?.let {
          return AnnotationContext(
            it,
            mergeComponentFqName,
            daggerComponentFqName,
            "modules"
          )
        }
      declaration.annotations.find(mergeSubcomponentFqName).singleOrNull()
        ?.let {
          return AnnotationContext(
            it,
            mergeSubcomponentFqName,
            daggerSubcomponentFqName,
            "modules"
          )
        }
      declaration.annotations.find(mergeModulesFqName).singleOrNull()
        ?.let {
          return AnnotationContext(
            it,
            mergeModulesFqName,
            daggerModuleFqName,
            "includes"
          )
        }
      return null
    }
  }
}
