package com.squareup.anvil.compiler.k2.fir.abstraction

import com.google.auto.service.AutoService
import com.squareup.anvil.compiler.k2.fir.AnvilFirContext2
import com.squareup.anvil.compiler.k2.fir.AnvilFirProcessor
import com.squareup.anvil.compiler.k2.fir.PendingTopLevelClass
import com.squareup.anvil.compiler.k2.fir.RequiresTypesResolutionPhase
import com.squareup.anvil.compiler.k2.fir.TopLevelClassProcessor
import com.squareup.anvil.compiler.k2.fir.abstraction.providers.anvilFirSymbolProvider
import com.squareup.anvil.compiler.k2.fir.abstraction.providers.scopedContributionProvider
import com.squareup.anvil.compiler.k2.fir.contributions.GeneratedBindingDeclarationKey
import com.squareup.anvil.compiler.k2.utils.fir.createFirAnnotation
import com.squareup.anvil.compiler.k2.utils.names.ClassIds
import com.squareup.anvil.compiler.k2.utils.names.FqNames
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.fir.caches.getValue
import org.jetbrains.kotlin.fir.extensions.ExperimentalTopLevelDeclarationsGenerationApi
import org.jetbrains.kotlin.fir.extensions.FirExtension
import org.jetbrains.kotlin.fir.extensions.MemberGenerationContext
import org.jetbrains.kotlin.fir.plugin.createConeType
import org.jetbrains.kotlin.fir.plugin.createMemberFunction
import org.jetbrains.kotlin.fir.symbols.impl.FirClassLikeSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

@AutoService(AnvilFirProcessor.Factory::class)
public class BindingModuleGeneratorFactory : AnvilFirProcessor.Factory {
  override fun create(anvilFirContext: AnvilFirContext2): AnvilFirProcessor {
    return BindingModuleGenerator(anvilFirContext)
  }
}

internal class BindingModuleGenerator(
  override val anvilFirContext: AnvilFirContext2,
) : TopLevelClassProcessor() {

  @OptIn(RequiresTypesResolutionPhase::class)
  private val contributedBindingsByModuleId by lazyValue {
    session.scopedContributionProvider.contributedBindings.associateBy { it.bindingModule }
  }

  override fun hasPackage(packageFqName: FqName): Boolean {
    return packageFqName == FqNames.anvilHintPackage &&
      session.anvilFirSymbolProvider.contributesBindingSymbols.isNotEmpty()
  }

  @ExperimentalTopLevelDeclarationsGenerationApi
  override fun getTopLevelClassIds(): Set<ClassId> {
    return contributedBindingsByModuleId.keys
  }

  @ExperimentalTopLevelDeclarationsGenerationApi
  override fun generateTopLevelClassLikeDeclaration(
    classId: ClassId,
    firExtension: FirExtension,
  ): PendingTopLevelClass = contributedBindingsByModuleId.getValue(classId).let { binding ->
    PendingTopLevelClass(
      classId = classId,
      key = GeneratedBindingDeclarationKey,
      classKind = ClassKind.INTERFACE,
      visibility = Visibilities.Public,
      annotations = lazyValue { listOf(createFirAnnotation(ClassIds.daggerModule)) },
      cachesFactory = cachesFactory,
      firExtension = firExtension,
    )
  }

  override fun getCallableNamesForClass(
    classSymbol: FirClassLikeSymbol<*>,
    context: MemberGenerationContext,
  ): Set<Name> {
    val binding = contributedBindingsByModuleId[classSymbol.classId] ?: return emptySet()

    return setOf(binding.bindingCallableName)
  }

  override fun generateFunctions(
    callableId: CallableId,
    context: MemberGenerationContext?,
    firExtension: FirExtension,
  ): List<FirNamedFunctionSymbol> {

    val binding = contributedBindingsByModuleId[callableId.classId!!] ?: return emptyList()

    return listOf(
      firExtension.createMemberFunction(
        owner = context!!.owner,
        key = GeneratedBindingDeclarationKey,
        name = callableId.callableName,
        returnType = binding.boundType.getValue().createConeType(session, nullable = false),
      ) {
        modality = Modality.ABSTRACT
        valueParameter(
          name = Name.identifier("concreteType"),
          type = binding.contributedType.createConeType(session, nullable = false),
        )
      }.apply {
        replaceAnnotations(listOf(createFirAnnotation(ClassIds.daggerBinds)))
      }
        .symbol,
    )
  }
}
