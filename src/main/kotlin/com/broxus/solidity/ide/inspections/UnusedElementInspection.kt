package com.broxus.solidity.ide.inspections

import com.broxus.solidity.ide.annotation.SolProblemsHolder
import com.broxus.solidity.ide.annotation.convert
import com.broxus.solidity.lang.psi.*
import com.broxus.solidity.lang.psi.impl.SolConstructorOrFunctionDef
import com.broxus.solidity.lang.resolve.SolResolver
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.parents

class UnusedElementInspection : LocalInspectionTool() {


  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
    return object : SolVisitor() {
      override fun visitImportDirective(o: SolImportDirective) {
        inspectImportDirective(o, holder.convert())
      }

      override fun visitContractDefinition(o: SolContractDefinition) {
        inspectContractDefinition(o, holder.convert())
      }

      override fun visitConstantVariableDeclaration(o: SolConstantVariableDeclaration) {
        inspectConstantVariableDeclaration(o, holder.convert())
      }

      override fun visitEnumDefinition(o: SolEnumDefinition) {
        inspectEnumDefinition(o, holder.convert())
      }

      override fun visitFunctionDefinition(o: SolFunctionDefinition) {
        inspectFunctionDefinition(o, holder.convert())
      }

      override fun visitStructDefinition(o: SolStructDefinition) {
        inspectStructDefinition(o, holder.convert())
      }

      override fun visitStateVariableDeclaration(o: SolStateVariableDeclaration) {
        inspectStateVariableDeclaration(o, holder.convert())
      }

        override fun visitVariableDeclaration(o: SolVariableDeclaration) {
          inspectVariableDeclaration(o, holder.convert())
        }

      override fun visitParameterDef(o: SolParameterDef) {
        inspectParameterDef(o, holder.convert())
      }

      override fun visitModifierDefinition(o: SolModifierDefinition) {
        inspectModifierDefinition(o, holder.convert())
      }
    }
  }
}

fun inspectStructDefinition(o: SolStructDefinition, holder: SolProblemsHolder) {
  o.identifier?.checkForUsage(o, holder, "Struct '${o.name}' is never used")
}

fun inspectFunctionDefinition(o: SolFunctionDefinition, holder: SolProblemsHolder) {
  o.identifier?.takeIf { o.visibility != Visibility.EXTERNAL && o.inheritance != FunctionInheritance.OVERRIDE }?.checkForUsage(o, holder, "Function '${o.name}' is never used")
}

fun inspectEnumDefinition(o: SolEnumDefinition, holder: SolProblemsHolder) {
  o.identifier?.checkForUsage(o, holder, "Enum '${o.name}' is never used")
}

fun inspectConstantVariableDeclaration(o: SolConstantVariableDeclaration, holder: SolProblemsHolder) {
  o.identifier.checkForUsage(o, holder, "Constant '${o.name}' is never used")
}

fun inspectContractDefinition(o: SolContractDefinition, holder: SolProblemsHolder) {
  o.identifier?.checkForUsage(o, holder, "Contract '${o.name}' is never used")
}

fun inspectStateVariableDeclaration(o: SolStateVariableDeclaration, holder: SolProblemsHolder) {
  o.identifier.checkForUsage(o, holder, "State variable '${o.name}' is never used")
}

fun inspectVariableDeclaration(o: SolVariableDeclaration, holder: SolProblemsHolder) {
  o.identifier?.checkForUsage(o, holder, "Variable '${o.name}' is never used")
}


fun inspectParameterDef(o: SolParameterDef, holder: SolProblemsHolder) {
  o.identifier?.takeIf { o.parents(false).filterIsInstance<SolConstructorOrFunctionDef>().firstOrNull()?.getBlock() != null }?.checkForUsage(o, holder, "Parameter '${o.name}' is never used")
}

fun inspectImportDirective(o: SolImportDirective, holder: SolProblemsHolder) {
  val used = SolResolver.collectUsedElements(o)
  if (used.isEmpty()) {
    holder.registerProblem(o, "Unused import directive", ProblemHighlightType.LIKE_UNUSED_SYMBOL)
  }
}

fun inspectModifierDefinition(o: SolModifierDefinition, holder: SolProblemsHolder) {
  o.identifier?.checkForUsage(o, holder, "Modifier '${o.name}' is never used")
}

private fun PsiElement.checkForUsage(o: PsiElement, holder: SolProblemsHolder, msg: String) {
  if (ReferencesSearch.search(o).findFirst() == null) {
    holder.registerProblem(this, msg, ProblemHighlightType.LIKE_UNUSED_SYMBOL)
  }
}

