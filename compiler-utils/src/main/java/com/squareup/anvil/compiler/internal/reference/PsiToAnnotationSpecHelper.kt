package com.squareup.anvil.compiler.internal.reference

import com.squareup.anvil.compiler.api.AnvilCompilationException
import com.squareup.anvil.compiler.internal.asClassName
import com.squareup.anvil.compiler.internal.requireFqName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.MemberName
import org.jetbrains.kotlin.com.intellij.psi.PsiElement
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.fir.lightTree.converter.nameAsSafeName
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassLiteralExpression
import org.jetbrains.kotlin.psi.KtCollectionLiteralExpression
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtQualifiedExpression
import org.jetbrains.kotlin.psi.psiUtil.containingClass

internal fun KtExpression.codeBlock(module: ModuleDescriptor): CodeBlock {
  return when (this) {
    // MyClass::class
    is KtClassLiteralExpression -> {
      val className = requireFqName(module)
        .toClassReference(module)
        .asClassName()
      CodeBlock.of("%T::class", className)
    }
    // Enums or qualified references.
    is KtNameReferenceExpression, is KtQualifiedExpression -> {
      val fqName = try {
        requireFqName(module)
      } catch (e: AnvilCompilationException) {
        if (this is KtNameReferenceExpression) {
          null
        } else {
          throw e
        }
      }
      if (fqName != null) {
        if (fqName.canResolveFqName(module)) {
          CodeBlock.of("%T", fqName.asClassName(module))
        } else {
          CodeBlock.of("%M", fqName.asMemberName(module))
        }
      } else {
        // It's (hopefully) an in-scope constant, look up the hierarchy.
        val ref = (this as KtNameReferenceExpression).getReferencedName()
        findConstantDefinitionInScope(ref, module)
          ?.asMemberName(module)
          ?.let { memberRef ->
            CodeBlock.of("%M", memberRef)
          }
        // If we reach here, it's a top-level constant defined in the same package but a different
        // file. In this case, we can just do the same in our generated code because we're in the
        // same package and can play by the same rules.
          ?: CodeBlock.of("%L", ref)
      }
    }
    is KtCollectionLiteralExpression -> CodeBlock.of(
      getInnerExpressions()
        .map { it.codeBlock(module) }
        .joinToString(prefix = "[", postfix = "]")
    )
    // Literals.
    else -> CodeBlock.of("%L", text)
  }
}

private fun FqName.asMemberName(module: ModuleDescriptor): MemberName {
  fun default() = MemberName(parent().asString(), shortName().asString())

  val classReference = parent().toClassReferenceOrNull(module) ?: return default()
  val classPlusCompanions = listOf(classReference) + classReference.companionObjects()

  return classPlusCompanions
    .flatMap { it.properties }
    .firstOrNull {
      // The 2nd condition is necessary for accessors of properties in companions, which can be
      // Foo.CONSTANT instead of Foo.Companion.CONSTANT.
      it.fqName == this || it.fqName.parent().parent().child(shortName()) == this
    }
    ?.memberName
    ?: default()
}

private fun List<KtProperty>.containsConstPropertyWithName(name: String): Boolean {
  return any { property ->
    property.hasModifier(KtTokens.CONST_KEYWORD) && property.name == name
  }
}

private fun PsiElement.findConstantDefinitionInScope(
  name: String,
  module: ModuleDescriptor
): FqName? {
  when (this) {
    is KtProperty, is KtNamedFunction -> {
      // Function or Property, traverse up
      return (this as KtElement).containingClass()?.findConstantDefinitionInScope(name, module)
        ?: containingFile.findConstantDefinitionInScope(name, module)
    }
    is KtObjectDeclaration -> {
      val containsConstPropertyWithName = toClassReference(module)
        .properties
        .map { it.property }
        .containsConstPropertyWithName(name)

      if (containsConstPropertyWithName) {
        return requireFqName().child(name.nameAsSafeName())
      } else if (isCompanion()) {
        // Nowhere else to look and don't try to traverse up because this is only looked at from a
        // class already.
        return null
      }
    }
    is KtFile -> {
      if (children.filterIsInstance<KtProperty>().containsConstPropertyWithName(name)) {
        return packageFqName.child(name.nameAsSafeName())
      }
    }
    is KtClass -> {
      // Look in companion object or traverse up
      return companionObjects.asSequence()
        .mapNotNull { it.findConstantDefinitionInScope(name, module) }
        .firstOrNull()
        ?: containingClass()?.findConstantDefinitionInScope(name, module)
        ?: containingKtFile.findConstantDefinitionInScope(name, module)
    }
  }

  return parent?.findConstantDefinitionInScope(name, module)
}
