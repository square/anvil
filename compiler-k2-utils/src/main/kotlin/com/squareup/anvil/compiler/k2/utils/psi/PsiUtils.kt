package com.squareup.anvil.compiler.k2.utils.psi

import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.psi.KtPsiFactory

public fun PsiElement.ktPsiFactory(): KtPsiFactory {
  return KtPsiFactory.contextual(
    context = this@ktPsiFactory,
    markGenerated = false,
    eventSystemEnabled = false,
  )
}
