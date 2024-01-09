package com.squareup.anvil.compiler

import com.squareup.anvil.annotations.ContributesTo
import com.squareup.anvil.compiler.codegen.generatedAnvilSubcomponent
import com.squareup.anvil.compiler.codegen.reference.AnnotationReferenceIr
import com.squareup.anvil.compiler.codegen.reference.AnvilCompilationExceptionClassReferenceIr
import com.squareup.anvil.compiler.codegen.reference.ClassReferenceIr
import com.squareup.anvil.compiler.codegen.reference.RealAnvilModuleDescriptor
import com.squareup.anvil.compiler.codegen.reference.find
import com.squareup.anvil.compiler.codegen.reference.findAll
import com.squareup.anvil.compiler.codegen.reference.toClassReference
import com.squareup.anvil.compiler.internal.classIdBestGuess
import com.squareup.anvil.compiler.internal.reference.Visibility.PUBLIC
import com.squareup.anvil.compiler.internal.safePackageString
import dagger.Module
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.createIrBuilder
import org.jetbrains.kotlin.backend.jvm.ir.kClassReference
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.builders.IrBuilderWithScope
import org.jetbrains.kotlin.ir.builders.irCallConstructor
import org.jetbrains.kotlin.ir.builders.irVararg
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrVararg
import org.jetbrains.kotlin.ir.types.starProjectedType
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

internal class ModuleMergerIr(
  private val classScanner: ClassScanner,
  private val moduleDescriptorFactory: RealAnvilModuleDescriptor.Factory,
) : IrGenerationExtension {
  // https://youtrack.jetbrains.com/issue/KT-56635
  override val shouldAlsoBeAppliedInKaptStubGenerationMode: Boolean get() = true

  override fun generate(
    moduleFragment: IrModuleFragment,
    pluginContext: IrPluginContext,
  ) {
    moduleFragment.transform(
      object : IrElementTransformerVoid() {
        override fun visitClass(declaration: IrClass): IrStatement {
          if (declaration.shouldIgnore()) return super.visitClass(declaration)

          val declarationReference = declaration.symbol.toClassReference(pluginContext)

          val annotations = declarationReference.annotations
            .findAll(mergeComponentFqName, mergeSubcomponentFqName, mergeModulesFqName)
            .ifEmpty { return super.visitClass(declaration) }

          pluginContext.irBuiltIns.createIrBuilder(declaration.symbol)
            .generateDaggerAnnotation(
              annotations = annotations,
              moduleFragment = moduleFragment,
              pluginContext = pluginContext,
              declaration = declarationReference,
            )
          return super.visitClass(declaration)
        }
      },
      null,
    )
  }

  private fun IrBuilderWithScope.generateDaggerAnnotation(
    annotations: List<AnnotationReferenceIr>,
    moduleFragment: IrModuleFragment,
    pluginContext: IrPluginContext,
    declaration: ClassReferenceIr,
  ) {
    val daggerAnnotationFqName = annotations[0].daggerAnnotationFqName

    val scopes = annotations.map { it.scope }
    val predefinedModules = annotations.flatMap {
      it.argumentOrNull(it.modulesKeyword)?.value<List<ClassReferenceIr>>().orEmpty()
    }

    val anvilModuleName = createAnvilModuleName(declaration)

    val contributesAnnotations = annotations
      .flatMap { annotation ->
        classScanner
          .findContributedClasses(
            pluginContext = pluginContext,
            moduleFragment = moduleFragment,
            annotation = contributesToFqName,
            scope = annotation.scope,
            moduleDescriptorFactory = moduleDescriptorFactory,
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
        contributedClass.annotations
          .find(annotationName = contributesToFqName)
          .filter { it.scope in scopes }
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
            classReference = contributedClass,
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
            classReference = contributedClass,
          )
        }
      }
      // Convert the sequence to a list to avoid iterating it twice. We use the result twice
      // for replaced classes and the final result.
      .toList()

    val excludedModules = annotations
      .flatMap { it.excludedClasses }
      .onEach { excludedClass ->

        // Verify that the replaced classes use the same scope.
        val contributesToOurScope = excludedClass.annotations
          .findAll(contributesToFqName, contributesBindingFqName, contributesMultibindingFqName)
          .map { it.scope }
          .plus(
            excludedClass.annotations
              .find(contributesSubcomponentFqName)
              .map { it.parentScope },
          )
          .any { scope -> scope in scopes }

        if (!contributesToOurScope) {
          throw AnvilCompilationExceptionClassReferenceIr(
            message = "${declaration.fqName} with scopes " +
              "${scopes.joinToString(prefix = "[", postfix = "]") { it.fqName.asString() }} " +
              "wants to exclude ${excludedClass.fqName}, but the excluded class isn't " +
              "contributed to the same scope.",
            classReference = declaration,
          )
        }
      }

    val replacedModules = contributesAnnotations
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
                classReference = contributedClass,
              )
            }

            checkSameScope(contributedClass, classToReplace, scopes)
          }
      }

    fun replacedModulesByContributedBinding(
      annotationFqName: FqName,
    ): Sequence<ClassReferenceIr> {
      return scopes.asSequence()
        .flatMap { scope ->
          classScanner
            .findContributedClasses(
              pluginContext = pluginContext,
              moduleFragment = moduleFragment,
              annotation = annotationFqName,
              scope = scope,
              moduleDescriptorFactory = moduleDescriptorFactory,
            )
        }
        .flatMap { contributedClass ->
          contributedClass.annotations
            .find(annotationName = annotationFqName)
            .filter { it.scope in scopes }
            .flatMap { it.replacedClasses }
            .onEach { classToReplace ->
              checkSameScope(contributedClass, classToReplace, scopes)
            }
        }
    }

    val replacedModulesByContributedBindings = replacedModulesByContributedBinding(
      annotationFqName = contributesBindingFqName,
    )

    val replacedModulesByContributedMultibindings = replacedModulesByContributedBinding(
      annotationFqName = contributesMultibindingFqName,
    )

    if (predefinedModules.isNotEmpty()) {
      val intersect = predefinedModules.intersect(excludedModules.toSet())
      if (intersect.isNotEmpty()) {
        throw AnvilCompilationExceptionClassReferenceIr(
          message = "${declaration.fqName} includes and excludes modules " +
            "at the same time: ${intersect.joinToString { it.fqName.asString() }}",
          classReference = declaration,
        )
      }
    }

    val contributedSubcomponentModules =
      findContributedSubcomponentModules(
        declaration,
        scopes,
        pluginContext,
        moduleFragment,
      )

    val contributedModules = contributesAnnotations
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

    val annotationConstructorCall = irCallConstructor(
      callee = pluginContext
        .referenceConstructors(daggerAnnotationFqName.classIdBestGuess())
        .single { it.owner.isPrimary },
      typeArguments = emptyList(),
    )
      .apply {
        putValueArgument(
          index = 0,
          valueArgument = irVararg(
            elementType = pluginContext.irBuiltIns.kClassClass.starProjectedType,
            values = contributedModules
              .map {
                kClassReference(
                  classType = it.defaultType,
                )
              }
              .toList(),
          ),
        )

        fun copyArrayValue(name: String) {
          val varargArguments = annotations
            .mapNotNull { it.argumentOrNull(name)?.argumentExpression as? IrVararg }
            .ifEmpty { return }

          putValueArgument(
            index = 1,
            valueArgument = irVararg(
              elementType = varargArguments[0].varargElementType,
              // These are always IrExpression instances too
              values = varargArguments.flatMap { it.elements }
                .filterIsInstance<IrExpression>(),
            ),
          )
        }

        if (annotations[0].fqName == mergeComponentFqName) {
          copyArrayValue("dependencies")
        }

        if (annotations[0].fqName == mergeModulesFqName) {
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
    scopes: List<ClassReferenceIr>,
  ) {
    val contributesToOurScope = classToReplace.annotations
      .findAll(contributesToFqName, contributesBindingFqName, contributesMultibindingFqName)
      .map { it.scope }
      .any { scope -> scope in scopes }

    if (!contributesToOurScope) {
      throw AnvilCompilationExceptionClassReferenceIr(
        classReference = contributedClass,
        message = "${contributedClass.fqName} with scopes " +
          "${scopes.joinToString(prefix = "[", postfix = "]") { it.fqName.asString() }} " +
          "wants to replace ${classToReplace.fqName}, but the replaced class isn't " +
          "contributed to the same scope.",
      )
    }
  }

  private fun findContributedSubcomponentModules(
    declaration: ClassReferenceIr,
    scopes: List<ClassReferenceIr>,
    pluginContext: IrPluginContext,
    moduleFragment: IrModuleFragment,
  ): Sequence<ClassReferenceIr> {
    return classScanner
      .findContributedClasses(
        pluginContext = pluginContext,
        moduleFragment = moduleFragment,
        annotation = contributesSubcomponentFqName,
        scope = null,
        moduleDescriptorFactory = moduleDescriptorFactory,
      )
      .filter { clazz ->
        clazz.annotations.find(contributesSubcomponentFqName).any { it.parentScope in scopes }
      }
      .mapNotNull { contributedSubcomponent ->
        contributedSubcomponent.classId
          .generatedAnvilSubcomponent(declaration.classId)
          .createNestedClassId(Name.identifier(SUBCOMPONENT_MODULE))
          .referenceClassOrNull(pluginContext)
      }
  }
}

private val AnnotationReferenceIr.daggerAnnotationFqName: FqName
  get() = when (fqName) {
    mergeComponentFqName -> daggerComponentFqName
    mergeSubcomponentFqName -> daggerSubcomponentFqName
    mergeModulesFqName -> daggerModuleFqName
    else -> throw NotImplementedError("Don't know how to handle $this.")
  }

private val AnnotationReferenceIr.modulesKeyword: String
  get() = when (fqName) {
    mergeComponentFqName, mergeSubcomponentFqName -> "modules"
    mergeModulesFqName -> "includes"
    else -> throw NotImplementedError("Don't know how to handle $this.")
  }
