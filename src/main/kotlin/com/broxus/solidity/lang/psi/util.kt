package com.broxus.solidity.lang.psi

import com.broxus.solidity.lang.core.SolidityFile
import com.broxus.solidity.lang.stubs.SolidityFileStub
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiUtilCore

fun PsiElement.rangeRelativeTo(ancestor: PsiElement): TextRange {
  check(ancestor.textRange.contains(textRange))
  return textRange.shiftRight(-ancestor.textRange.startOffset)
}

inline fun <reified T : PsiElement> PsiElement.childOfType(strict: Boolean = true): T? =
  PsiTreeUtil.findChildOfType(this, T::class.java, strict)

val PsiElement.parentRelativeRange: TextRange
  get() = rangeRelativeTo(parent)

val PsiElement.ancestors: Sequence<PsiElement> get() = generateSequence(this) { it.parent }

val PsiElement.elementType: IElementType
  get() = if (this is SolidityFile) SolidityFileStub.Type else PsiUtilCore.getElementType(this)

inline fun <reified T : PsiElement> PsiElement.parentOfType(strict: Boolean = true, minStartOffset: Int = -1): T? =
  PsiTreeUtil.getParentOfType(this, T::class.java, strict, minStartOffset)

fun PsiElement.endOffset() = this.textRange.endOffset
fun PsiElement.startOffset() = this.textRange.startOffset
