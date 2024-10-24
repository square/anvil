package com.squareup.anvil.compiler.codegen.dagger

import com.google.auto.service.AutoService
import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.getAnnotationsByType
import com.google.devtools.ksp.getDeclaredProperties
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.squareup.anvil.compiler.api.AnvilApplicabilityChecker
import com.squareup.anvil.compiler.api.AnvilContext
import com.squareup.anvil.compiler.api.CodeGenerator
import com.squareup.anvil.compiler.api.GeneratedFileWithSources
import com.squareup.anvil.compiler.api.createGeneratedFile
import com.squareup.anvil.compiler.codegen.PrivateCodeGenerator
import com.squareup.anvil.compiler.codegen.ksp.AnvilSymbolProcessor
import com.squareup.anvil.compiler.codegen.ksp.AnvilSymbolProcessorProvider
import com.squareup.anvil.compiler.codegen.ksp.KspAnvilException
import com.squareup.anvil.compiler.codegen.ksp.isAnnotationClass
import com.squareup.anvil.compiler.internal.createAnvilSpec
import com.squareup.anvil.compiler.internal.reference.AnvilCompilationExceptionClassReference
import com.squareup.anvil.compiler.internal.reference.ClassReference
import com.squareup.anvil.compiler.internal.reference.MemberPropertyReference
import com.squareup.anvil.compiler.internal.reference.argumentAt
import com.squareup.anvil.compiler.internal.reference.asClassName
import com.squareup.anvil.compiler.internal.reference.asTypeName
import com.squareup.anvil.compiler.internal.reference.classAndInnerClassReferences
import com.squareup.anvil.compiler.internal.safePackageString
import com.squareup.anvil.compiler.mapKeyFqName
import com.squareup.kotlinpoet.ARRAY
import com.squareup.kotlinpoet.BOOLEAN
import com.squareup.kotlinpoet.BOOLEAN_ARRAY
import com.squareup.kotlinpoet.BYTE
import com.squareup.kotlinpoet.BYTE_ARRAY
import com.squareup.kotlinpoet.CHAR
import com.squareup.kotlinpoet.CHAR_ARRAY
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.DOUBLE
import com.squareup.kotlinpoet.DOUBLE_ARRAY
import com.squareup.kotlinpoet.FLOAT
import com.squareup.kotlinpoet.FLOAT_ARRAY
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.INT
import com.squareup.kotlinpoet.INT_ARRAY
import com.squareup.kotlinpoet.LONG
import com.squareup.kotlinpoet.LONG_ARRAY
import com.squareup.kotlinpoet.ParameterizedTypeName
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.SHORT
import com.squareup.kotlinpoet.SHORT_ARRAY
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.joinToCode
import com.squareup.kotlinpoet.ksp.toClassName
import com.squareup.kotlinpoet.ksp.toTypeName
import com.squareup.kotlinpoet.ksp.writeTo
import dagger.MapKey
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.psi.KtFile
import java.io.File
import kotlin.reflect.KClass

/**
 * Generates [MapKey] creator classes + static functions for cases where [MapKey.unwrapValue] is set
 * to false.
 *
 * Implemented from eyeballing https://github.com/google/dagger/blob/b5990a0641a7860b760aa9055b90a99d06186af6/javatests/dagger/internal/codegen/MapKeyProcessorTest.java
 */
internal object MapKeyCreatorCodeGen : AnvilApplicabilityChecker {
  override fun isApplicable(context: AnvilContext) = context.generateFactories

  internal class KspGenerator(
    override val env: SymbolProcessorEnvironment,
  ) : AnvilSymbolProcessor() {
    @AutoService(SymbolProcessorProvider::class)
    class Provider : AnvilSymbolProcessorProvider(MapKeyCreatorCodeGen, ::KspGenerator)

    @OptIn(KspExperimental::class)
    override fun processChecked(resolver: Resolver): List<KSAnnotated> {
      resolver.getSymbolsWithAnnotation(mapKeyFqName.asString())
        .filterIsInstance<KSClassDeclaration>()
        .filter { clazz ->
          val mapKey = clazz.getAnnotationsByType(MapKey::class)
            .singleOrNull()
            ?: return@filter false
          return@filter !mapKey.unwrapValue
        }
        .forEach { clazz ->
          generateCreatorClass(clazz)
            .writeTo(
              env.codeGenerator,
              aggregating = false,
              originatingKSFiles = listOf(clazz.containingFile!!),
            )
        }

      return emptyList()
    }

    private fun generateCreatorClass(
      clazz: KSClassDeclaration,
    ): FileSpec {
      // // Given this
      // @MapKey(unwrapValue = false)
      // annotation class ActivityKey(
      //   val value: KClass<out Activity>,
      //   val scope: KClass<*>,
      // )
      //
      // // Generate this
      // object ActivityKeyCreator {
      //   @JvmStatic
      //   fun createActivityKey(
      //     value: Class<out Activity>,
      //     scope: Class<*>
      //   ): ActivityKey {
      //     return ActivityKey(value.kotlin, scope.kotlin)
      //   }
      // }

      val className = clazz.toClassName()

      if (!clazz.isAnnotationClass()) {
        throw KspAnvilException(
          message = "@MapKey is only applicable to annotation classes.",
          node = clazz,
        )
      }

      val creatorsToGenerate = mutableSetOf<KSClassDeclaration>()

      fun visitAnnotations(clazz: KSClassDeclaration) {
        if (clazz.isAnnotationClass()) {
          val added = creatorsToGenerate.add(clazz)
          if (added) {
            for (property in clazz.getDeclaredProperties()) {
              val type = property.type.resolve().declaration as? KSClassDeclaration?
              if (type?.isAnnotationClass() == true) {
                visitAnnotations(type)
              }
            }
          }
        }
      }

      // Populate all used annotations
      visitAnnotations(clazz)

      val creatorFunctions = creatorsToGenerate
        .associateBy { annotationClass ->
          annotationClass.toClassName()
        }
        .toSortedMap()
        .map { (className, clazz) ->
          val properties = clazz.getDeclaredProperties()
            .map { AnnotationProperty(it) }
            .associateBy { it.name }
          generateCreatorFunction(className, properties)
        }

      return generateCreatorFileSpec(className, creatorFunctions)
    }
  }

  @AutoService(CodeGenerator::class)
  internal class EmbeddedGenerator : PrivateCodeGenerator() {

    override fun isApplicable(context: AnvilContext) = MapKeyCreatorCodeGen.isApplicable(context)

    override fun generateCodePrivate(
      codeGenDir: File,
      module: ModuleDescriptor,
      projectFiles: Collection<KtFile>,
    ): Collection<GeneratedFileWithSources> = projectFiles
      .classAndInnerClassReferences(module)
      .filter { classRef ->
        val mapKey = classRef.annotations.find { it.fqName == mapKeyFqName }
        if (mapKey != null) {
          mapKey.argumentAt("unwrapValue", 0)?.value<Boolean>() == false
        } else {
          false
        }
      }
      .map { clazz ->
        generateCreatorClass(codeGenDir, clazz)
      }
      .toList()

    private fun generateCreatorClass(
      codeGenDir: File,
      clazz: ClassReference,
    ): GeneratedFileWithSources {
      // // Given this
      // @MapKey(unwrapValue = false)
      // annotation class ActivityKey(
      //   val value: KClass<out Activity>,
      //   val scope: KClass<*>,
      // )
      //
      // // Generate this
      // object ActivityKeyCreator {
      //   @JvmStatic
      //   fun createActivityKey(
      //     value: Class<out Activity>,
      //     scope: Class<*>
      //   ): ActivityKey {
      //     return ActivityKey(value.kotlin, scope.kotlin)
      //   }
      // }

      val packageName = clazz.packageFqName.safePackageString()

      if (!clazz.isAnnotationClass()) {
        throw AnvilCompilationExceptionClassReference(
          message = "@MapKey is only applicable to annotation classes.",
          classReference = clazz,
        )
      }

      val className = clazz.asClassName()

      val creatorsToGenerate = mutableSetOf<ClassReference>()

      fun visitAnnotations(clazz: ClassReference) {
        if (clazz.isAnnotationClass()) {
          val added = creatorsToGenerate.add(clazz)
          if (added) {
            for (property in clazz.declaredMemberProperties) {
              val type = property.type().asClassReferenceOrNull()
              if (type?.isAnnotationClass() == true) {
                visitAnnotations(type)
              }
            }
          }
        }
      }

      // Populate all used annotations
      visitAnnotations(clazz)

      val creatorFunctions = creatorsToGenerate
        .associateBy { annotationClass ->
          annotationClass.asTypeName().rawTypeOrNull()
            ?: throw AnvilCompilationExceptionClassReference(
              message = "@MapKey is only applicable to non-generic annotation classes.",
              classReference = annotationClass,
            )
        }
        .toSortedMap()
        .map { (className, clazz) ->
          val properties = clazz.declaredMemberProperties
            .map { AnnotationProperty(it) }
            .associateBy { it.name }
          generateCreatorFunction(className, properties)
        }

      val spec = generateCreatorFileSpec(className, creatorFunctions)
      val content = spec.toString()

      return createGeneratedFile(
        codeGenDir = codeGenDir,
        packageName = packageName,
        fileName = spec.name,
        content = content,
        sourceFile = clazz.containingFileAsJavaFile,
      )
    }

    private fun generateCreatorFunction(
      className: ClassName,
      annotationClass: ClassReference,
    ): FunSpec {
      val properties = annotationClass.declaredMemberProperties
        .map { AnnotationProperty(it) }
        .associateBy { it.name }
      return generateCreatorFunction(className, properties)
    }
  }

  private fun generateCreatorFileSpec(
    sourceClass: ClassName,
    creatorFunctions: List<FunSpec>,
  ): FileSpec {
    val simpleName = sourceClass.simpleNames.joinToString("_")
    val generatedClassName = "${simpleName}Creator"
    val spec = FileSpec.createAnvilSpec(sourceClass.packageName, generatedClassName) {
      addType(
        TypeSpec.objectBuilder(generatedClassName)
          .addFunctions(creatorFunctions)
          .build(),
      )
    }
    return spec
  }

  /**
   * Generates a single static creator function for a given annotation [annotationClass].
   */
  private fun generateCreatorFunction(
    className: ClassName,
    properties: Map<String, AnnotationProperty>,
  ): FunSpec {
    return FunSpec.builder("create${className.simpleName}")
      .addAnnotation(JvmStatic::class)
      .apply {
        properties.forEach { (name, property) ->
          addParameter(name, property.javaType)
        }
      }
      .addStatement(
        "return %T(%L)",
        className,
        properties.entries.map { it.value.callExpression }.joinToCode(),
      )
      .returns(className)
      .build()
  }
}

private class AnnotationProperty(
  val name: String,
  val javaType: TypeName,
  val callExpression: CodeBlock,
) {
  companion object {
    private fun create(name: String, type: TypeName): AnnotationProperty {
      val javaType = type.resolveJavaType()
      val codeBlock = when {
        javaType.rawTypeOrNull() == CLASS_CLASS_NAME -> CodeBlock.of("%L.kotlin", name)
        type is ParameterizedTypeName &&
          type.rawType == ARRAY &&
          type.typeArguments[0].rawTypeOrNull() == KCLASS_CLASS_NAME -> {
          // Dense but this avoids an intermediate list allocation compared to .map { ... }.toTypedArray()
          CodeBlock.of("%1T(%2L.size)·{·%2L[it].kotlin·}", ARRAY, name)
        }

        else -> CodeBlock.of("%L", name)
      }
      return AnnotationProperty(
        name,
        javaType,
        codeBlock,
      )
    }

    operator fun invoke(
      property: KSPropertyDeclaration,
    ): AnnotationProperty = create(property.simpleName.asString(), property.type.toTypeName())

    operator fun invoke(
      property: MemberPropertyReference,
    ): AnnotationProperty = create(property.name, property.type().asTypeName())
  }
}

private val CLASS_CLASS_NAME = Class::class.asClassName()
private val KCLASS_CLASS_NAME = KClass::class.asClassName()

private fun TypeName.rawTypeOrNull(): ClassName? {
  return when (this) {
    is ClassName -> this
    is ParameterizedTypeName -> rawType
    else -> null
  }
}

private fun TypeName.resolveJavaType(): TypeName {
  var type = this
  if (this is ParameterizedTypeName) {
    if (rawType == KCLASS_CLASS_NAME) {
      // This needs to be converted to a java.lang.Class type.
      type = CLASS_CLASS_NAME.parameterizedBy(typeArguments.map { it.resolveJavaType() })
    } else if (rawType == ARRAY) {
      // These need to use their primitive forms where possible
      type = when (val componentType = typeArguments.single()) {
        BOOLEAN -> BOOLEAN_ARRAY
        BYTE -> BYTE_ARRAY
        CHAR -> CHAR_ARRAY
        SHORT -> SHORT_ARRAY
        INT -> INT_ARRAY
        LONG -> LONG_ARRAY
        FLOAT -> FLOAT_ARRAY
        DOUBLE -> DOUBLE_ARRAY
        else -> ARRAY.parameterizedBy(componentType.resolveJavaType())
      }
    }
  }
  return type
}
