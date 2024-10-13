package com.broxus.solidity.ide.inspections

import com.broxus.solidity.ide.annotation.SolProblemsHolder
import com.broxus.solidity.ide.annotation.convert
import com.broxus.solidity.lang.psi.SolAssignmentExpression
import com.broxus.solidity.lang.psi.SolExpression
import com.broxus.solidity.lang.psi.SolVariableDefinition
import com.broxus.solidity.lang.psi.SolVisitor
import com.broxus.solidity.lang.types.SolType
import com.broxus.solidity.lang.types.getSolType
import com.broxus.solidity.lang.types.type
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor

class AssignmentInspection : LocalInspectionTool() {

  override fun getDisplayName(): String = ""

  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
    return object : SolVisitor() {
      override fun visitAssignmentExpression(o: SolAssignmentExpression) {
        inspectAssigmentExpression(o, holder.convert())
      }
      override fun visitVariableDefinition(o: SolVariableDefinition) {
        inspectVariableDefinition(o, holder.convert())
      }
    }
  }
}

fun inspectVariableDefinition(expression: SolVariableDefinition, holder: SolProblemsHolder) {
  val type = getSolType(expression.variableDeclaration.typeName).takeIf { it.isResolved } ?: return
  val right = expression.expression ?: return
  inspectAssigment(type, right, holder)
}


fun inspectAssigmentExpression(expression: SolAssignmentExpression, holder: SolProblemsHolder) {
  val expressionList = expression.expressionList
  val right = expressionList.getOrNull(1) ?: return
  val left = expressionList[0]
  inspectAssigment(left.type, right, holder)
}

private fun inspectAssigment(leftType: SolType, expression: SolExpression, holder: SolProblemsHolder) {
  val rightType = expression.type.takeIf { it.isResolved && leftType.isResolved } ?: return
  if (!leftType.isAssignableFrom(rightType) ) {
    holder.registerProblem(expression, "Expected $leftType but $rightType found")
  }
}
