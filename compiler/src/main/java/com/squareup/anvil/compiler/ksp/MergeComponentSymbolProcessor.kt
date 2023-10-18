@file:OptIn(KspExperimental::class)

package com.squareup.anvil.compiler.ksp

import com.google.auto.service.AutoService
import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.isAnnotationPresent
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.symbol.KSName
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeAlias
import com.squareup.anvil.annotations.compat.MergeModules
import com.squareup.anvil.compiler.api.AnvilApplicabilityChecker
import com.squareup.anvil.compiler.api.AnvilContext
import com.squareup.anvil.compiler.codegen.ksp.AnvilSymbolProcessor
import com.squareup.anvil.compiler.codegen.ksp.AnvilSymbolProcessorProvider
import com.squareup.anvil.compiler.codegen.ksp.KspAnvilException
import com.squareup.anvil.compiler.codegen.ksp.argumentAt
import com.squareup.anvil.compiler.codegen.ksp.isInterface
import com.squareup.anvil.compiler.codegen.ksp.scope
import com.squareup.anvil.compiler.internal.capitalize
import com.squareup.anvil.compiler.internal.createAnvilSpec
import com.squareup.anvil.compiler.mergeComponentFqName
import com.squareup.anvil.compiler.mergeModulesFqName
import com.squareup.anvil.compiler.mergeSubcomponentFqName
import com.squareup.kotlinpoet.ANY
import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.joinToCode
import com.squareup.kotlinpoet.jvm.jvmStatic
import com.squareup.kotlinpoet.ksp.addOriginatingKSFile
import com.squareup.kotlinpoet.ksp.toAnnotationSpec
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.writeTo
import dagger.Component
import dagger.Module
import dagger.Subcomponent

internal class MergeComponentSymbolProcessor(
  override val env: SymbolProcessorEnvironment
) : AnvilSymbolProcessor() {
  @AutoService(SymbolProcessorProvider::class)
  class Provider : AnvilSymbolProcessorProvider(
    applicabilityChecker = ApplicabilityChecker,
    delegate = ::MergeComponentSymbolProcessor
  )

  object ApplicabilityChecker : AnvilApplicabilityChecker {
    override fun isApplicable(context: AnvilContext) = !context.disableComponentMerging
  }

  private val classScanner = ClassScannerKSP()

  override fun processChecked(resolver: Resolver): List<KSAnnotated> {
    // Look for merge annotations
    // Process merged modules
    // Process merged interfaces
    // Generated merged component with contributed interfaces and merged modules
    // generate DaggerComponent shim

    val deferred = mutableListOf<KSAnnotated>()

    sequence {
      yieldAll(resolver.getSymbolsWithAnnotation(mergeComponentFqName.asString()))
      yieldAll(resolver.getSymbolsWithAnnotation(mergeSubcomponentFqName.asString()))
      yieldAll(resolver.getSymbolsWithAnnotation(mergeModulesFqName.asString()))
    }
      .filterIsInstance<KSClassDeclaration>()
      .filterNot { it.shouldIgnore() }
      // Elements could have multiple annotations, we'll only process them once
      .distinct()
      .forEach { generateComponents(resolver, it) }

    return deferred
  }

  @OptIn(KspExperimental::class)
  private fun generateComponents(
    resolver: Resolver,
    clazz: KSClassDeclaration
  ) {
    val isMergeModules = clazz.isAnnotationPresent(MergeModules::class)
    val annotations = clazz.annotations.toList()
    val originalAnnotation = annotations.first()
    val daggerAnnotationFqName = originalAnnotation.daggerAnnotationFqName
    val scopes = annotations.mapTo(LinkedHashSet()) { it.scope() }

    val contributedModules =
      ModuleMergerKSP.mergeModules(
        classScanner,
        resolver,
        clazz,
        annotations,
        scopes,
      )

    val daggerAnnotation = createAnnotation(
      originalAnnotation,
      daggerAnnotationFqName,
      contributedModules
    )

    val typesToAdd = mutableListOf<TypeSpec>()
    val originClassName = clazz.toClassName()
    val generatedClassName = "Anvil${clazz.simpleName.asString().capitalize()}"
    val originatingFile = clazz.containingFile ?: throw KspAnvilException(
      "No containing file found for ${clazz.qualifiedName?.asString()}",
      node = clazz
    )
    if (isMergeModules) {
      // Generate a module with the annotation and be done
      typesToAdd += TypeSpec.interfaceBuilder(generatedClassName)
        .addAnnotation(daggerAnnotation)
        .addOriginatingKSFile(originatingFile)
        .build()
    } else {
      val anvilClassName = ClassName(originClassName.packageName, generatedClassName)
      val generatedDaggerComponentName = anvilClassName.generatedDaggerComponentName()

      val creator = createFactoryOrBuilderFunSpec(clazz, generatedDaggerComponentName)

      val mergedInterfaces = InterfaceMergerKSP(classScanner)
        .computeSyntheticSupertypes(
          clazz,
          resolver,
          scopes,
        )

      // Build the component
      typesToAdd += TypeSpec.interfaceBuilder(generatedClassName)
        .addAnnotation(daggerAnnotation)
        .addSuperinterface(originClassName)
        .addSuperinterfaces(mergedInterfaces.map { it.toClassName() }.filterNot { it == ANY })
        .apply {
          creator?.let {
            // Add the creator function
            addType(
              TypeSpec.companionObjectBuilder()
                .addFunction(it)
                .build()
            )
          }
        }
        // TODO generate extension of creator interface?
        .addOriginatingKSFile(originatingFile)
        .build()

      if (creator != null) {
        typesToAdd += generateDaggerComponentShim(
          originClassName,
          creator,
          originatingFile
        )
      }
    }


    val fileSpec = FileSpec.createAnvilSpec(
      originClassName.packageName,
      generatedClassName
    ) {
      for (type in typesToAdd) {
        addType(type)
      }
    }

    fileSpec.writeTo(env.codeGenerator, aggregating = true)
  }

  @OptIn(KspExperimental::class)
  private fun createFactoryOrBuilderFunSpec(
    origin: KSClassDeclaration,
    generatedAnvilClassName: ClassName
  ): FunSpec? {
    val (className, functionName) = origin.declarations
      .filterIsInstance<KSClassDeclaration>()
      .filter { it.isInterface() }
      .mapNotNull { declaration ->
        if (declaration.isAnnotationPresent(Component.Factory::class)) {
          val className = declaration.toClassName()
          className to "factory"
        } else if (declaration.isAnnotationPresent(Component.Builder::class)) {
          val className = declaration.toClassName()
          className to "builder"
        } else {
          null
        }
      }
      .firstOrNull()
      ?: return null

    return FunSpec.builder(functionName)
      .jvmStatic()
      .returns(className)
      .addStatement("return·%T.$functionName()", generatedAnvilClassName)
      .build()
  }

  /**
   * Adapted from how Dagger generates component class file names: https://github.com/google/dagger/blob/f6ddcc3cdd7ca2983497612b987153929fe7f32c/java/dagger/internal/codegen/writing/ComponentNames.java#L52-L57
   */
  private fun ClassName.generatedDaggerComponentName(): ClassName {
    return ClassName(packageName, "Dagger${simpleNames.joinToString("_")}")
  }

  private fun createAnnotation(
    originalAnnotation: KSAnnotation,
    daggerAnnotation: ClassName,
    contributedModules: Set<KSClassDeclaration>
  ): AnnotationSpec {
    val builder = AnnotationSpec.builder(daggerAnnotation)
      .addMember(
        "modules = [%L]",
        contributedModules.map { CodeBlock.of("%T::class", it.toClassName()) }
          .joinToCode()
      )

    fun copyArrayValue(name: String) {
      originalAnnotation.toAnnotationSpec()
      val member = CodeBlock.builder()
      member.add("%N = ", name)
      val value = originalAnnotation.argumentAt(name) ?: return
      addValueToBlock(value, member)
      builder.addMember(member.build())
    }

    when (daggerAnnotation) {
      Component::class.asClassName() -> {
        copyArrayValue("dependencies")
      }

      MergeModules::class.asClassName() -> {
        copyArrayValue("subcomponents")
      }
    }

    return builder.build()
  }

  // TODO generate a kdoc?
  private fun generateDaggerComponentShim(
    originClassName: ClassName,
    creator: FunSpec,
    originatingFile: KSFile,
  ): TypeSpec {
    return TypeSpec.objectBuilder(originClassName.generatedDaggerComponentName())
      .addFunction(creator)
      .addOriginatingKSFile(originatingFile)
      .build()
  }
}

private fun addValueToBlock(
  value: Any,
  member: CodeBlock.Builder
) {
  when (value) {
    is List<*> -> {
      // Array type
      val arrayType = when (value.firstOrNull()) {
        is Boolean -> "booleanArrayOf"
        is Byte -> "byteArrayOf"
        is Char -> "charArrayOf"
        is Short -> "shortArrayOf"
        is Int -> "intArrayOf"
        is Long -> "longArrayOf"
        is Float -> "floatArrayOf"
        is Double -> "doubleArrayOf"
        else -> "arrayOf"
      }
      member.add("$arrayType(⇥⇥")
      value.forEachIndexed { index, innerValue ->
        if (index > 0) member.add(", ")
        addValueToBlock(innerValue!!, member)
      }
      member.add("⇤⇤)")
    }

    is KSType -> {
      val unwrapped = value.unwrapTypeAlias()
      val isEnum = (unwrapped.declaration as KSClassDeclaration).classKind == ClassKind.ENUM_ENTRY
      if (isEnum) {
        val parent = unwrapped.declaration.parentDeclaration as KSClassDeclaration
        val entry = unwrapped.declaration.simpleName.getShortName()
        member.add("%T.%L", parent.toClassName(), entry)
      } else {
        member.add("%T::class", unwrapped.toClassName())
      }
    }

    is KSName ->
      member.add(
        "%T.%L",
        ClassName.bestGuess(value.getQualifier()),
        value.getShortName(),
      )

    is KSAnnotation -> member.add("%L", value.toAnnotationSpec())
    else -> member.add(memberForValue(value))
  }
}

private fun KSType.unwrapTypeAlias(): KSType {
  return if (this.declaration is KSTypeAlias) {
    (this.declaration as KSTypeAlias).type.resolve()
  } else {
    this
  }
}

/**
 * Creates a [CodeBlock] with parameter `format` depending on the given `value` object.
 * Handles a number of special cases, such as appending "f" to `Float` values, and uses
 * `%L` for other types.
 */
private fun memberForValue(value: Any) = when (value) {
  is Class<*> -> CodeBlock.of("%T::class", value)
  is Enum<*> -> CodeBlock.of("%T.%L", value.javaClass, value.name)
  is String -> CodeBlock.of("%S", value)
  is Float -> CodeBlock.of("%Lf", value)
  is Double -> CodeBlock.of("%L", value)
  is Char -> CodeBlock.of("'%L'", value)
  is Byte -> CodeBlock.of("$value.toByte()")
  is Short -> CodeBlock.of("$value.toShort()")
  // Int or Boolean
  else -> CodeBlock.of("%L", value)
}

private val KSAnnotation.daggerAnnotationFqName: ClassName
  get() = when (annotationType.resolve().classDeclaration.fqName) {
    mergeComponentFqName -> Component::class.asClassName()
    mergeSubcomponentFqName -> Subcomponent::class.asClassName()
    mergeModulesFqName -> Module::class.asClassName()
    else -> throw NotImplementedError("Don't know how to handle $this.")
  }
