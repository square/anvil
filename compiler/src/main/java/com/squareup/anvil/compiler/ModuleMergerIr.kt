package com.squareup.anvil.compiler

import com.squareup.anvil.annotations.ContributesTo
import com.squareup.anvil.compiler.api.AnvilCompilationException
import com.squareup.anvil.compiler.internal.safePackageString
import com.squareup.anvil.compiler.internal.singleOrEmpty
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
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.types.impl.IrDynamicTypeImpl
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.isInterface
import org.jetbrains.kotlin.ir.util.packageFqName
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.types.Variance.INVARIANT

internal class ModuleMergerIr(
  private val classScanner: ClassScanner
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
    if (declaration.annotations(daggerFqName).isNotEmpty()) {
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
      .findContributedHints(
        pluginContext = pluginContext,
        moduleFragment = moduleFragment,
        annotation = contributesToFqName
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
      .filter { it.isContributedToScope(scope) }
      .filter { hint ->
        val moduleAnnotation = hint.irClass.owner.annotations(daggerModuleFqName).singleOrEmpty()
        val mergeModulesAnnotation = hint.irClass.owner
          .annotations(mergeModulesFqName).singleOrEmpty()

        if (!hint.irClass.owner.isInterface &&
          moduleAnnotation == null &&
          mergeModulesAnnotation == null
        ) {
          throw AnvilCompilationException(
            message = "${hint.fqName} is annotated with " +
              "@${ContributesTo::class.simpleName}, but this class is neither an interface " +
              "nor a Dagger module. Did you forget to add @${Module::class.simpleName}?",
            element = hint.irClass
          )
        }

        moduleAnnotation != null || mergeModulesAnnotation != null
      }
      .onEach { hint ->
        if (hint.irClass.owner.visibility != DescriptorVisibilities.PUBLIC) {
          throw AnvilCompilationException(
            message = "${hint.fqName} is contributed to the Dagger graph, but the " +
              "module is not public. Only public modules are supported.",
            element = hint.irClass
          )
        }
      }
      // Convert the sequence to a list to avoid iterating it twice. We use the result twice
      // for replaced classes and the final result.
      .toList()

    val excludedModules = annotationConstructorCall.exclude()
      .onEach { excludedClass ->
        val scopes = excludedClass.annotations(contributesToFqName).map { it.scope() } +
          excludedClass.annotations(contributesBindingFqName).map { it.scope() } +
          excludedClass.annotations(contributesMultibindingFqName).map { it.scope() } +
          excludedClass.annotations(contributesSubcomponentFqName).map { it.parentScope() }

        // Verify that the replaced classes use the same scope.
        val contributesToOurScope = scopes.any { it == scope }

        if (!contributesToOurScope) {
          throw AnvilCompilationException(
            message = "${declaration.fqName} with scope $scope wants to exclude " +
              "${excludedClass.fqName}, but the excluded class isn't contributed to the " +
              "same scope.",
            element = declaration
          )
        }
      }

    val replacedModules = modules
      // Ignore replaced modules or bindings specified by excluded modules.
      .filter { hint -> hint.irClass.owner !in excludedModules }
      .flatMap { hint ->
        hint.contributedAnnotation(scope).replaces()
          .onEach { irClassForReplacement ->
            // Verify has @Module annotation. It doesn't make sense for a Dagger module to
            // replace a non-Dagger module.
            if (irClassForReplacement.annotations(daggerModuleFqName).isEmpty() &&
              irClassForReplacement.annotations(contributesBindingFqName).isEmpty() &&
              irClassForReplacement.annotations(contributesMultibindingFqName).isEmpty()
            ) {
              throw AnvilCompilationException(
                message = "${hint.fqName} wants to replace " +
                  "${irClassForReplacement.fqName}, but the class being " +
                  "replaced is not a Dagger module or binding.",
                element = hint.irClass
              )
            }

            checkSameScope(hint, irClassForReplacement, scope)
          }
      }

    fun replacedModulesByContributedBinding(
      annotationFqName: FqName
    ): Sequence<IrClass> {
      return classScanner
        .findContributedHints(
          pluginContext = pluginContext,
          moduleFragment = moduleFragment,
          annotation = annotationFqName
        )
        .flatMap { hint ->
          hint.contributedAnnotations()
            .filter { it.scope() == scope }
            .flatMap { it.replaces() }
            .onEach { irClassForReplacement ->
              checkSameScope(hint, irClassForReplacement, scope)
            }
        }
    }

    val replacedModulesByContributedBindings = replacedModulesByContributedBinding(
      annotationFqName = contributesBindingFqName
    )

    val replacedModulesByContributedMultibindings = replacedModulesByContributedBinding(
      annotationFqName = contributesMultibindingFqName
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

    val contributedModules = modules
      .asSequence()
      .map { it.irClass.owner }
      .minus(replacedModules.toSet())
      .minus(replacedModulesByContributedBindings.toSet())
      .minus(replacedModulesByContributedMultibindings.toSet())
      .minus(excludedModules.toSet())
      .plus(predefinedModules)
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
          annotationConstructorCall.argument(name)
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
    hint: ContributedHintIr,
    classForReplacement: IrClass,
    scopeFqName: FqName
  ) {
    val scopes = classForReplacement.annotations(contributesToFqName).map { it.scope() } +
      classForReplacement.annotations(contributesBindingFqName).map { it.scope() } +
      classForReplacement.annotations(contributesMultibindingFqName).map { it.scope() }

    val contributesToOurScope = scopes.any { it == scopeFqName }

    if (!contributesToOurScope) {
      throw AnvilCompilationException(
        element = hint.irClass,
        message = "${hint.fqName} with scope $scopeFqName wants to replace " +
          "${classForReplacement.fqName}, but the replaced class isn't " +
          "contributed to the same scope."
      )
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
      declaration.annotations(mergeComponentFqName).singleOrEmpty()
        ?.let {
          return AnnotationContext(
            it,
            mergeComponentFqName,
            daggerComponentFqName,
            "modules"
          )
        }
      declaration.annotations(mergeSubcomponentFqName).singleOrEmpty()
        ?.let {
          return AnnotationContext(
            it,
            mergeSubcomponentFqName,
            daggerSubcomponentFqName,
            "modules"
          )
        }
      declaration.annotations(mergeModulesFqName).singleOrEmpty()
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
