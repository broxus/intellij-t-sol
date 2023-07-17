package com.broxus.solidity.ide.inspections.fixes

import com.broxus.solidity.ide.actions.ImplementMethodsHandler
import com.broxus.solidity.lang.psi.SolContractDefinition
import com.intellij.codeInspection.LocalQuickFixOnPsiElement
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.parentOfType

class ImplementMembersFix(
  element: PsiElement,
  private val fixName: String = "Implement members"
) : LocalQuickFixOnPsiElement(element) {
  override fun getText() = fixName
  override fun getFamilyName() = "Implement members"

  override fun invoke(project: Project, file: PsiFile, element: PsiElement, endElement: PsiElement) {
    ImplementMethodsHandler().invoke(element.parentOfType<SolContractDefinition>(true) ?: return)
  }

  override fun startInWriteAction() = false
}
