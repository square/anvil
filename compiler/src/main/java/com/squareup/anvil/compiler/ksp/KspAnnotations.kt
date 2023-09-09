package com.squareup.anvil.compiler.ksp

import com.squareup.anvil.compiler.daggerComponentFqName
import com.squareup.anvil.compiler.daggerModuleFqName
import com.squareup.anvil.compiler.daggerSubcomponentFqName
import com.squareup.anvil.compiler.mergeComponentFqName
import com.squareup.anvil.compiler.mergeModulesFqName
import com.squareup.anvil.compiler.mergeSubcomponentFqName

internal object KspAnnotations {
  val moduleName = daggerModuleFqName.asString()
  val componentName = daggerComponentFqName.asString()
  val subcomponentName = daggerSubcomponentFqName.asString()
  val annotationNames =
    setOf(moduleName, componentName, subcomponentName)
  val mergeModulesName = mergeModulesFqName.asString()
  val mergeComponentName = mergeComponentFqName.asString()
  val mergeSubcomponentName = mergeSubcomponentFqName.asString()
  val annotationMapping =
    mapOf(
      moduleName to mergeModulesName,
      componentName to mergeComponentName,
      subcomponentName to mergeSubcomponentName,
    )
  val mergeAnnotationNames = setOf(
    mergeModulesName,
    mergeComponentName,
    mergeSubcomponentName,
  )
}
