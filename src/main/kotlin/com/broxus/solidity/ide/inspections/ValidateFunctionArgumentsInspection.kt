package com.broxus.solidity.ide.inspections

import com.broxus.solidity.ide.annotation.SolProblemsHolder
import com.broxus.solidity.ide.annotation.convert
import com.broxus.solidity.ide.hints.NO_VALIDATION_TAG
import com.broxus.solidity.ide.hints.comments
import com.broxus.solidity.lang.core.SolidityTokenTypes
import com.broxus.solidity.lang.psi.*
import com.broxus.solidity.lang.resolve.SolResolver
import com.broxus.solidity.lang.types.SolTypeError
import com.broxus.solidity.lang.types.getSolType
import com.broxus.solidity.lang.types.type
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor

fun SolFunctionCallArguments.getDefs(): List<SolFunctionDefElement>? {
  return ((parent.parent?.children?.firstOrNull() as? SolMemberAccessExpression)?.let {
    SolResolver.resolveMemberAccess(it)
  } ?: (parent?.parent as? SolFunctionCallExpression)?.let {
    SolResolver.resolveVarLiteralReference(it)
  })?.filterIsInstance<SolFunctionDefElement>()
}

class ValidateFunctionArgumentsInspection : LocalInspectionTool() {
  override fun getDisplayName(): String = ""

  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
    return object : SolVisitor() {
      override fun visitFunctionCallArguments(element: SolFunctionCallArguments) {
          inspectFunctionCallArguments(element, holder.convert())
      }
    }
  }
}

fun inspectFunctionCallArguments(element: SolFunctionCallArguments, holder: SolProblemsHolder) {
    val args = element.expressionList
    if (args.firstOrNull() !is SolMapExpression) {
        element.getDefs()?.let {
            val funDefs = it.filterNot { it.comments().any { it.elementType == SolidityTokenTypes.NAT_SPEC_TAG && it.text == NO_VALIDATION_TAG } }
            if (funDefs.isNotEmpty()) {
                var wrongNumberOfArgs = ""
                var wrongTypes = ""
                var wrongElement = element as SolElement
                if (funDefs.none { ref ->
                            val parameters = ref.parameters
                            val expArgs = parameters.size
                            val actArgs = args.size
                            if (actArgs != expArgs && parameters.none { it.name == "varargs" }) {
                                wrongNumberOfArgs = "Expected $expArgs argument${if (expArgs > 1) "s" else ""}, but got $actArgs"
                                false
                            } else {
                                args.zip(parameters).all { both ->
                                    val expType = getSolType(both.second.typeName)
                                    val actType = both.first.type
                                    !expType.isResolved || !actType.isResolved || expType.isAssignableFrom(actType).also {
                                        if (!it) {
                                            if (expType is SolTypeError) {
                                                wrongTypes = expType.details
                                            } else {
                                                wrongTypes = "Argument of type '$actType' is not assignable to parameter of type '${expType}'"
                                                wrongElement = both.first
                                            }
                                        }
                                    }
                                }
                            }
                        }) {
                    if (!wrongElement.textRange.isEmpty ) {
                        holder.registerProblem(wrongElement, wrongTypes.takeIf { it.isNotEmpty() } ?: wrongNumberOfArgs)
                    }
                }
            }
        }
    }
}
