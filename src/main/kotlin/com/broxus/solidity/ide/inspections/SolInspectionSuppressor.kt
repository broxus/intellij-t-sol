package com.broxus.solidity.ide.inspections

import com.broxus.solidity.lang.psi.SolContractDefinition
import com.broxus.solidity.lang.psi.SolElement
import com.broxus.solidity.lang.psi.ancestors
import com.broxus.solidity.lang.psi.parentOfType
import com.intellij.codeInsight.daemon.impl.actions.AbstractBatchSuppressByNoInspectionCommentFix
import com.intellij.codeInspection.InspectionSuppressor
import com.intellij.codeInspection.SuppressQuickFix
import com.intellij.codeInspection.SuppressionUtil
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiTreeUtil

class SolInspectionSuppressor : InspectionSuppressor {
  override fun getSuppressActions(element: PsiElement?, toolId: String): Array<out SuppressQuickFix> = arrayOf(
    SuppressInspectionFix(toolId),
    SuppressInspectionFix(SuppressionUtil.ALL)
  )

  override fun isSuppressedFor(element: PsiElement, toolId: String): Boolean {
    val unused = toolId == "TSolUnusedElement" && element.parent !is SolContractDefinition
    return element.ancestors.filter { !unused || it !is SolContractDefinition }
      .filterIsInstance<SolElement>()
      .any { isSuppressedByComment(it, toolId) }
  }

  private fun isSuppressedByComment(element: PsiElement, toolId: String): Boolean {
    val comment = PsiTreeUtil.skipSiblingsBackward(element, PsiWhiteSpace::class.java) as? PsiComment
    if (comment == null) {
      return false
    }
    val matcher = SuppressionUtil.SUPPRESS_IN_LINE_COMMENT_PATTERN.matcher(comment.text)
    return matcher.matches() && SuppressionUtil.isInspectionToolIdMentioned(matcher.group(1), toolId)
  }

  private class SuppressInspectionFix(ID: String)
    : AbstractBatchSuppressByNoInspectionCommentFix(ID, ID == SuppressionUtil.ALL) {

    init {
      text = when (ID) {
        SuppressionUtil.ALL -> "Suppress all inspections for item"
        else -> "Suppress for item"
      }
    }

    override fun getContainer(context: PsiElement?) = context?.parentOfType<SolElement>(strict = false)
  }
}
