package com.broxus.solidity.ide.inspections

import com.broxus.solidity.ide.annotation.SolProblemsHolder
import com.broxus.solidity.ide.annotation.convert
import com.broxus.solidity.ide.inspections.fixes.ImportFileFix
import com.broxus.solidity.lang.psi.SolReferenceElement
import com.broxus.solidity.lang.psi.SolUserDefinedTypeName
import com.broxus.solidity.lang.psi.SolVarLiteral
import com.broxus.solidity.lang.psi.SolVisitor
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor

class ResolveNameInspection : LocalInspectionTool() {
  override fun getDisplayName(): String = ""

  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
    return object : SolVisitor() {
      override fun visitVarLiteral(element: SolVarLiteral) {
        inspectVarLiteralRef(element, holder.convert())
      }

      override fun visitUserDefinedTypeName(element: SolUserDefinedTypeName) {
        inspectUserDefinedTypeName(element, holder.convert())
      }

    }
  }
}

fun inspectVarLiteralRef(element: SolVarLiteral, holder: SolProblemsHolder) {
  checkReference(element) {
    holder.registerProblem(element, "'${element.identifier.text}' is undefined", ProblemHighlightType.WARNING)
  }
}

fun inspectUserDefinedTypeName(element: SolUserDefinedTypeName, holder: SolProblemsHolder) {
  checkReference(element) {
    holder.registerProblem(element, "Import file", ProblemHighlightType.WARNING, ImportFileFix(element))
  }
}
private fun checkReference(element: SolReferenceElement, report: () -> Unit) {
  if (element.reference != null) {
    // resolve return either 1 reference or null, and because our resolve is not perfect we can return a number
    // of references, so instead of showing false positives we can use multiresolve
    val results = element.reference?.multiResolve(false)
    if (results.isNullOrEmpty()) {
      report()
    }
  }
}

