package com.squareup.anvil.compiler.fir

import com.squareup.anvil.compiler.codegen.reference.toClassReference
import com.squareup.anvil.compiler.fir.internal.Names
import com.squareup.anvil.compiler.fir.internal.classId
import com.squareup.anvil.compiler.fqName
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.backend.common.lower.createIrBuilder
import org.jetbrains.kotlin.backend.jvm.ir.kClassReference
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.builders.irVararg
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.expressions.IrClassReference
import org.jetbrains.kotlin.ir.expressions.IrVararg
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.types.starProjectedType
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid

public class CanaryIrMerger : IrGenerationExtension {
  @OptIn(UnsafeDuringIrConstructionAPI::class)
  override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {

    val testComponent = pluginContext
      .referenceClass(Names.testComponent.classId())
      ?: error("TestComponent not found")

    val componentRef = testComponent.toClassReference(pluginContext)

    moduleFragment.transform(
      object : IrElementTransformerVoid() {
        override fun visitClass(declaration: IrClass): IrStatement {

          homer()

          if (declaration.fqName != testComponent.fqName) return super.visitClass(declaration)

          val mergeAnnotatedClass = testComponent.toClassReference(pluginContext)

          val componentAnnotationCall = mergeAnnotatedClass.annotations
            .singleOrNull { it.fqName == Names.dagger.component }
            ?.annotation
            ?: error("Component annotation not found")

          val modulesArg = componentAnnotationCall.getValueArgument(0)
            ?: error("Modules argument not found")

          val emptyModule = pluginContext
            .referenceClass(Names.emptyModule.classId())
            ?: error("EmptyModule not found")

          val componentConstructorSymbol = pluginContext
            .referenceConstructors(Names.dagger.component.classId())
            .singleOrNull { it.owner.isPrimary }
            ?: error("Component constructor not found")

          val modulesArgBlob = (modulesArg as IrVararg)
            .elements
            .filterIsInstance<IrClassReference>()
            .map { it.symbol.defaultType.classFqName }
            .joinToString("\n")

          error(
            """
            |~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~ IR TestComponent modules args
            |$modulesArgBlob
            |~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
            """.trimMargin(),
          )

          pluginContext.irBuiltIns
            .createIrBuilder(declaration.symbol)
            .apply {
              val newArgs = irVararg(
                elementType = pluginContext.irBuiltIns.kClassClass.starProjectedType,
                values = listOf(kClassReference(classType = emptyModule.defaultType)),
              )

              componentAnnotationCall.putValueArgument(index = 0, valueArgument = newArgs)
            }

          // homer()

          return super.visitClass(declaration)
        }
      },
      null,
    )
  }

  private fun homer(): Nothing {
    error(
      """
      |         _ _,---._
      |      ,-','       `-.___
      |     /-;'               `._
      |    /\/          ._   _,'o \
      |   ( /\       _,--'\,','"`. )
      |    |\      ,'o     \'    //\
      |    |      \        /   ,--'""`-.
      |    :       \_    _/ ,-'         `-._
      |     \        `--'  /                )
      |      `.  \`._    ,'     ________,','
      |        .--`     ,'  ,--` __\___,;'
      |         \`.,-- ,' ,`_)--'  /`.,'
      |          \( ;  | | )      (`-/
      |            `--'| |)       |-/
      |              | | |        | |
      |              | | |,.,-.   | |_
      |              | `./ /   )---`  )
      |             _|  /    ,',   ,-'
      |            ,'|_(    /-<._,' |--,
      |            |    `--'---.     \/ \
      |            |          / \    /\  \
      |          ,-^---._     |  \  /  \  \
      |       ,-'        \----'   \/    \--`.
      |      /            \              \   \
      """.trimMargin(),
    )
  }
}
