package com.squareup.anvil.compiler

import com.squareup.anvil.annotations.ContributesTo
import com.squareup.anvil.compiler.api.AnvilCompilationException
import com.squareup.anvil.compiler.codegen.generatedAnvilSubcomponent
import com.squareup.anvil.compiler.codegen.reference.RealAnvilModuleDescriptor
import com.squareup.anvil.compiler.internal.safePackageString
import dagger.Module
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.parentsWithSelf
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.expressions.IrConstructorCall
import org.jetbrains.kotlin.ir.expressions.impl.IrClassReferenceImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrConstructorCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrVarargImpl
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.types.impl.IrDynamicTypeImpl
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.isInterface
import org.jetbrains.kotlin.ir.util.packageFqName
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
          val annotationContext = AnnotationContext.create(declaration)
            ?: return super.visitClass(declaration)

          annotationContext.generateDaggerAnnotation(moduleFragment, pluginContext, declaration)
          return super.visitClass(declaration)
        }
      },
      null
    )
  }

  private fun AnnotationContext.generateDaggerAnnotation(
    moduleFragment: IrModuleFragment,
    pluginContext: IrPluginContext,
    declaration: IrClass
  ) {
    if (declaration.annotationOrNull(daggerFqName) != null) {
      throw AnvilCompilationException(
        message = "When using @${annotationFqName.shortName()} it's not allowed to annotate " +
          "the same class with @${daggerFqName.shortName()}. The Dagger annotation will " +
          "be generated.",
        element = declaration
      )
    }

    val scope = annotationConstructorCall.scope()
    val predefinedModules = annotationConstructorCall.argumentClassArray(modulesKeyword)

    val anvilModuleName = createAnvilModuleName(declaration)

    val modules = classScanner
      .findContributedClasses(
        pluginContext = pluginContext,
        moduleFragment = moduleFragment,
        packageName = HINT_CONTRIBUTES_PACKAGE_PREFIX,
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
        val fqName = it.fqName
        !fqName.isAnvilModule() || fqName == anvilModuleName
      }
      .mapNotNull {
        val contributesAnnotation =
          it.owner.annotationOrNull(contributesToFqName, scope = scope)
            ?: return@mapNotNull null
        it to contributesAnnotation
      }
      .filter { (classSymbol, _) ->
        val moduleAnnotation = classSymbol.owner.annotationOrNull(daggerModuleFqName)
        val mergeModulesAnnotation = classSymbol.owner.annotationOrNull(mergeModulesFqName)
        if (!classSymbol.owner.isInterface &&
          moduleAnnotation == null &&
          mergeModulesAnnotation == null
        ) {
          throw AnvilCompilationException(
            message = "${classSymbol.fqName} is annotated with " +
              "@${ContributesTo::class.simpleName}, but this class is neither an interface " +
              "nor a Dagger module. Did you forget to add @${Module::class.simpleName}?",
            element = classSymbol
          )
        }

        moduleAnnotation != null || mergeModulesAnnotation != null
      }
      .onEach { (classSymbol, _) ->
        if (classSymbol.owner.visibility != DescriptorVisibilities.PUBLIC) {
          throw AnvilCompilationException(
            message = "${classSymbol.fqName} is contributed to the Dagger graph, but the " +
              "module is not public. Only public modules are supported.",
            element = classSymbol
          )
        }
      }
      // Convert the sequence to a list to avoid iterating it twice. We use the result twice
      // for replaced classes and the final result.
      .toList()

    val excludedModules = annotationConstructorCall.exclude()
      .onEach { excludedClass ->
        val contributesToAnnotation = excludedClass
          .annotationOrNull(contributesToFqName)
        val contributesBindingAnnotation = excludedClass
          .annotationOrNull(contributesBindingFqName)
        val contributesMultibindingAnnotation = excludedClass
          .annotationOrNull(contributesMultibindingFqName)
        val contributesSubcomponentAnnotation = excludedClass
          .annotationOrNull(contributesSubcomponentFqName)

        // Verify that the replaced classes use the same scope.
        val scopeOfExclusion = contributesToAnnotation?.scope()
          ?: contributesBindingAnnotation?.scope()
          ?: contributesMultibindingAnnotation?.scope()
          ?: contributesSubcomponentAnnotation?.parentScope()
          ?: throw AnvilCompilationException(
            message = "Could not determine the scope of the excluded class " +
              "${excludedClass.fqName}.",
            element = declaration
          )

        if (scopeOfExclusion != scope) {
          throw AnvilCompilationException(
            message = "${declaration.fqName} with scope $scope wants to exclude " +
              "${excludedClass.fqName} with scope $scopeOfExclusion. The exclusion must " +
              "use the same scope.",
            element = declaration
          )
        }
      }

    val replacedModules = modules
      // Ignore replaced modules or bindings specified by excluded modules.
      .filter { (classSymbol, _) -> classSymbol.owner !in excludedModules }
      .flatMap { (classSymbol, contributesAnnotation) ->
        contributesAnnotation.replaces()
          .onEach { irClassForReplacement ->
            // Verify has @Module annotation. It doesn't make sense for a Dagger module to
            // replace a non-Dagger module.
            if (irClassForReplacement.annotationOrNull(daggerModuleFqName) == null &&
              irClassForReplacement.annotationOrNull(contributesBindingFqName) == null &&
              irClassForReplacement.annotationOrNull(contributesMultibindingFqName) == null
            ) {
              throw AnvilCompilationException(
                message = "${classSymbol.fqName} wants to replace " +
                  "${irClassForReplacement.fqName}, but the class being " +
                  "replaced is not a Dagger module.",
                element = classSymbol
              )
            }

            checkSameScope(classSymbol, irClassForReplacement, scope)
          }
      }

    fun replacedModulesByContributedBinding(
      annotationFqName: FqName,
      hintPackagePrefix: String
    ): Sequence<IrClass> {
      return classScanner
        .findContributedClasses(
          pluginContext = pluginContext,
          moduleFragment = moduleFragment,
          packageName = hintPackagePrefix,
          annotation = annotationFqName,
          scope = scope,
          moduleDescriptorFactory = moduleDescriptorFactory
        )
        .flatMap { classSymbol ->
          val annotation = classSymbol.owner.annotation(annotationFqName)
          if (scope == annotation.scope()) {
            annotation.replaces()
              .onEach { classDescriptorForReplacement ->
                checkSameScope(classSymbol, classDescriptorForReplacement, scope)
              }
          } else {
            emptyList()
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
        throw AnvilCompilationException(
          message = "${declaration.fqName} includes and excludes modules " +
            "at the same time: ${intersect.joinToString { it.fqName.asString() }}",
          element = declaration
        )
      }
    }

    val contributedSubcomponentModules =
      findContributedSubcomponentModules(declaration, scope, pluginContext, moduleFragment)

    val contributedModules = modules
      .asSequence()
      .map { it.first.owner }
      .minus(replacedModules.toSet())
      .minus(replacedModulesByContributedBindings.toSet())
      .minus(replacedModulesByContributedMultibindings.toSet())
      .minus(excludedModules.toSet())
      .plus(predefinedModules)
      .plus(contributedSubcomponentModules)
      .distinct()

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
          declaration.annotation(annotationFqName, scope)
            .argument(name)
            ?.second
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

    declaration.annotations += annotationConstructorCall
  }

  private fun createAnvilModuleName(declaration: IrClass): FqName {
    val packageName = declaration.packageFqName?.safePackageString() ?: ""

    val name = "$MODULE_PACKAGE_PREFIX.$packageName" +
      declaration.parentsWithSelf
        .filterIsInstance<IrClass>()
        .toList()
        .reversed()
        .joinToString(separator = "", postfix = ANVIL_MODULE_SUFFIX) {
          it.name.asString()
        }
    return FqName(name)
  }

  private fun checkSameScope(
    contributedClass: IrClassSymbol,
    classDescriptorForReplacement: IrClass,
    scopeFqName: FqName
  ) {
    val contributesToAnnotation = classDescriptorForReplacement
      .annotationOrNull(contributesToFqName)
    val contributesBindingAnnotation = classDescriptorForReplacement
      .annotationOrNull(contributesBindingFqName)
    val contributesMultibindingAnnotation = classDescriptorForReplacement
      .annotationOrNull(contributesMultibindingFqName)

    // Verify that the replaced classes use the same scope.
    val scopeOfReplacement = contributesToAnnotation?.scope()
      ?: contributesBindingAnnotation?.scope()
      ?: contributesMultibindingAnnotation?.scope()
      ?: throw AnvilCompilationException(
        message = "Could not determine the scope of the replaced class " +
          "${classDescriptorForReplacement.fqName}.",
        element = contributedClass
      )

    if (scopeOfReplacement != scopeFqName) {
      throw AnvilCompilationException(
        message = "${contributedClass.fqName} with scope $scopeFqName wants to replace " +
          "${classDescriptorForReplacement.fqName} with scope $scopeOfReplacement. The " +
          "replacement must use the same scope.",
        element = contributedClass
      )
    }
  }

  private fun findContributedSubcomponentModules(
    declaration: IrClass,
    scope: FqName,
    pluginContext: IrPluginContext,
    moduleFragment: IrModuleFragment
  ): Sequence<IrClass> {
    return classScanner
      .findContributedClasses(
        pluginContext = pluginContext,
        moduleFragment = moduleFragment,
        packageName = HINT_SUBCOMPONENTS_PACKAGE_PREFIX,
        annotation = contributesSubcomponentFqName,
        scope = null,
        moduleDescriptorFactory = moduleDescriptorFactory
      )
      .filter {
        it.owner.annotation(contributesSubcomponentFqName).parentScope() == scope
      }
      .mapNotNull { contributedSubcomponent ->
        contributedSubcomponent.requireClassId()
          .generatedAnvilSubcomponent(declaration.requireClassId())
          .createNestedClassId(Name.identifier(SUBCOMPONENT_MODULE))
          .irClassOrNull(pluginContext)
          ?.owner
      }
  }
}

@Suppress("DataClassPrivateConstructor")
private data class AnnotationContext private constructor(
  val annotationConstructorCall: IrConstructorCall,
  val annotationFqName: FqName,
  val daggerFqName: FqName,
  val modulesKeyword: String
) {
  val isComponent = annotationFqName == mergeComponentFqName
  val isModule = annotationFqName == mergeModulesFqName

  companion object {
    fun create(declaration: IrClass): AnnotationContext? {
      declaration.annotationOrNull(mergeComponentFqName)
        ?.let {
          return AnnotationContext(
            it,
            mergeComponentFqName,
            daggerComponentFqName,
            "modules"
          )
        }
      declaration.annotationOrNull(mergeSubcomponentFqName)
        ?.let {
          return AnnotationContext(
            it,
            mergeSubcomponentFqName,
            daggerSubcomponentFqName,
            "modules"
          )
        }
      declaration.annotationOrNull(mergeModulesFqName)
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
