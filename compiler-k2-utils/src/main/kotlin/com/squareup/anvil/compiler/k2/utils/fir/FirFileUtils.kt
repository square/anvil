package com.squareup.anvil.compiler.k2.utils.fir

import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.builder.buildPackageDirective
import org.jetbrains.kotlin.fir.declarations.FirDeclaration
import org.jetbrains.kotlin.fir.declarations.FirDeclarationOrigin
import org.jetbrains.kotlin.fir.declarations.FirFile
import org.jetbrains.kotlin.fir.declarations.FirProperty
import org.jetbrains.kotlin.fir.declarations.FirRegularClass
import org.jetbrains.kotlin.fir.declarations.builder.buildFile
import org.jetbrains.kotlin.fir.declarations.utils.classId
import org.jetbrains.kotlin.fir.moduleData
import org.jetbrains.kotlin.fir.packageFqName
import org.jetbrains.kotlin.fir.resolve.providers.firProvider
import org.jetbrains.kotlin.fir.resolve.providers.impl.FirProviderImpl
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.utils.exceptions.checkWithAttachment

public fun FirRegularClass.wrapInSyntheticFile(session: FirSession): FirRegularClass = apply {
  session.createSyntheticFile(
    origin = origin,
    packageName = classId.packageFqName,
    simpleName = "${classId.shortClassName.asString()}.kt",
    declarations = listOf(this),
  )
}

public fun FirProperty.wrapInSyntheticFile(session: FirSession): FirProperty = apply {
  session.createSyntheticFile(
    origin = origin,
    packageName = symbol.packageFqName(),
    simpleName = "${name.asString()}.kt",
    declarations = listOf(this),
  )
}

public fun FirSession.createSyntheticFile(
  origin: FirDeclarationOrigin,
  packageName: FqName,
  simpleName: String,
  declarations: List<FirDeclaration>,
): FirFile = buildFile {
  this.origin = origin
  this@buildFile.moduleData = this@createSyntheticFile.moduleData
  packageDirective = buildPackageDirective {
    this.packageFqName = packageName
  }
  checkWithAttachment(
    simpleName.matches(".+\\.kts?$".toRegex()),
    { "simpleName must end with .kt or .kts" },
  ) {
    withEntry("simpleName", simpleName)
  }
  this.name = simpleName
  this.declarations.addAll(declarations)
}.also {
  (firProvider as FirProviderImpl).recordFile(it)
}
