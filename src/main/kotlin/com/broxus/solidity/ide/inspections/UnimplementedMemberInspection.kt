package com.broxus.solidity.ide.inspections

import com.broxus.solidity.ide.inspections.fixes.ImplementMembersFix
import com.broxus.solidity.lang.psi.ContractType
import com.broxus.solidity.lang.psi.SolContractDefinition
import com.broxus.solidity.lang.psi.SolVisitor
import com.broxus.solidity.lang.resolve.function.SolFunctionResolver
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.search.searches.ReferencesSearch

class UnimplementedMemberInspection : LocalInspectionTool() {


  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
    return object : SolVisitor() {
      override fun visitContractDefinition(o: SolContractDefinition) {
        if (o.contractType != ContractType.COMMON || o.isAbstract) {
          return
        }
        if (SolFunctionResolver.collectNotImplemented(o).isNotEmpty()) {
            holder.registerProblem(o.identifier ?: return, "Some members are not implemented", ImplementMembersFix(o))
          }

      }
    }
  }

  private fun PsiElement.checkForUsage(o: PsiElement, holder: ProblemsHolder, msg: String) {
    if (ReferencesSearch.search(o).findFirst() == null) {
      holder.registerProblem(this, msg, ProblemHighlightType.LIKE_UNUSED_SYMBOL)
    }
  }
}

