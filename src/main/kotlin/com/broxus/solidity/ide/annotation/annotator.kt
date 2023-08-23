package com.broxus.solidity.ide.annotation

import com.broxus.solidity.ide.colors.SolColor
import com.broxus.solidity.ide.hints.startOffset
import com.broxus.solidity.ide.inspections.*
import com.broxus.solidity.lang.psi.*
import com.broxus.solidity.lang.psi.impl.SolErrorDefMixin
import com.broxus.solidity.lang.resolve.SolResolver
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.colors.CodeInsightColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.refactoring.suggested.endOffset
import com.intellij.ui.JBColor

class SolidityAnnotator : Annotator {
  override fun annotate(element: PsiElement, holder: AnnotationHolder) {
    if (element is SolElement) {
      highlight(element, holder)
    }
  }

  private val specialFunctionNames = setOf("receive", "fallback", "onBounce", "onTickTock")

  private fun highlight(element: SolElement, holder: AnnotationHolder) {
    when (element) {
      is SolNumberType -> applyColor(holder, element, SolColor.TYPE)
      is SolElementaryTypeName -> applyColor(holder, element, SolColor.TYPE)
      is SolStateMutability -> if (element.text == "payable") {
        applyColor(holder, element, SolColor.KEYWORD)
      }
      is SolEnumValue -> applyColor(holder, element, SolColor.ENUM_VALUE)
      is SolMapExpressionClause -> applyColor(holder, element.identifier, SolColor.MAPPING)
      is SolMemberAccessExpression -> {
        when(element.expression.firstChild.text) {
          "super" -> applyColor(holder, element.expression.firstChild, SolColor.KEYWORD)
          "msg", "block", "abi" -> applyColor(holder, element.expression.firstChild, SolColor.GLOBAL)
        }
        inspectMemberAccess(element, holder.convert())
      }
      is SolErrorDefMixin -> {
        applyColor(holder, element.identifier, SolColor.KEYWORD)
        element.nameIdentifier?.let { applyColor(holder, it, SolColor.ERROR_NAME) }
      }
      is SolHexLiteral -> {
        if ((element.endOffset - element.startOffset) > 4) {
          applyColor(holder, TextRange(element.startOffset, element.startOffset + 3), SolColor.KEYWORD)
          applyColor(holder, TextRange(element.startOffset + 3, element.endOffset), SolColor.STRING)
        } else {
          applyColor(holder, element, SolColor.STRING)
        }
      }
      is SolRevertStatement -> applyColor(holder, element.firstChild, SolColor.KEYWORD)
      is SolOverrideSpecifier -> {
        if ((element.endOffset - element.startOffset) > 8) {
          applyColor(holder, TextRange(element.startOffset, element.startOffset + 8), SolColor.KEYWORD)
        }
      }
      is SolContractDefinition -> {
        element.identifier?.let { applyColor(holder, it, SolColor.CONTRACT_NAME) }
        inspectContractDefinition(element, holder.convert())
      }
      is SolStructDefinition -> {
        element.identifier?.let { applyColor(holder, it, SolColor.STRUCT_NAME) }
        inspectStructDefinition(element, holder.convert())
      }
      is SolEnumDefinition -> {
        element.identifier?.let { applyColor(holder, it, SolColor.ENUM_NAME) }
        inspectEnumDefinition(element, holder.convert())
      }
      is SolEventDefinition -> element.identifier?.let { applyColor(holder, it, SolColor.EVENT_NAME) }
      is SolUserDefinedValueTypeDefinition -> element.identifier?.let { applyColor(holder, it, SolColor.USER_DEFINED_VALUE_TYPE) }
      is SolConstantVariableDeclaration -> {
        applyColor(holder, element.identifier, SolColor.CONSTANT)
        inspectConstantVariableDeclaration(element, holder.convert())
      }
      is SolStateVariableDeclaration -> {
        if (element.mutationModifier?.textMatches("constant") == true) {
          applyColor(holder, element.identifier, SolColor.CONSTANT)
        } else {
          applyColor(holder, element.identifier, SolColor.STATE_VARIABLE)
        }
        inspectStateVariableDeclaration(element, holder.convert())
      }
      is SolFunctionDefinition -> {
        val identifier = element.identifier
        if (identifier !== null) {
          applyColor(holder, identifier, SolColor.FUNCTION_DECLARATION)
        } else {
          val firstChildNode = element.node.firstChildNode
          if (firstChildNode.text in specialFunctionNames) {
            applyColor(holder, firstChildNode.textRange, SolColor.RECEIVE_FALLBACK_DECLARATION)
          }
        }
        inspectFunctionDefinition(element, holder.convert())
      }
      is SolModifierDefinition -> {
        element.identifier?.let { applyColor(holder, it, SolColor.FUNCTION_DECLARATION) }
        inspectModifierDefinition(element, holder.convert())
      }
      is SolModifierInvocation -> applyColor(holder, element.varLiteral.identifier, SolColor.MODIFIER_INVOCATION)
      is SolUserDefinedTypeName -> {
        when(SolResolver.resolveTypeNameUsingImports(element).firstOrNull()) {
          is SolContractDefinition, is SolStructDefinition, is SolEnumDefinition,
          is SolErrorDefinition, is SolEventDefinition -> applyColor(holder, element, SolColor.TYPE_REFERENCE)
          is SolUserDefinedValueTypeDefinition -> applyColor(holder, element, SolColor.USER_DEFINED_VALUE_TYPE)
        }

        inspectUserDefinedTypeName(element, holder.convert())
      }
      is SolFunctionCallElement -> when(element.firstChild.text) {
        "keccak256" -> applyColor(holder, element.firstChild, SolColor.GLOBAL_FUNCTION_CALL)
        "require" -> applyColor(holder, element.firstChild, SolColor.KEYWORD)
        "assert" -> applyColor(holder, element.firstChild, SolColor.KEYWORD)
        else -> when(val typeName = SolResolver.resolveTypeNameUsingImports(element).firstOrNull()) {
          is SolErrorDefinition -> applyColor(holder, element.referenceNameElement, SolColor.ERROR_NAME)
          is SolEventDefinition -> applyColor(holder, element.referenceNameElement, SolColor.EVENT_NAME)
          else -> element.firstChild.let {
            if (it is SolPrimaryExpression && SolResolver.resolveTypeNameUsingImports(element.firstChild).filterIsInstance<SolStructDefinition>().isNotEmpty()) {
              applyColor(holder, element.referenceNameElement, SolColor.TYPE_REFERENCE)
            } else {
              applyColor(holder, element.referenceNameElement, SolColor.FUNCTION_CALL)
            }
          }
        }
      }
      is SolFunctionCallArguments -> inspectFunctionCallArguments(element, holder.convert())
      is SolVariableDeclaration -> inspectVariableDeclaration(element, holder.convert())
      is SolParameterDef -> inspectParameterDef(element, holder.convert())
      is SolImportDirective -> inspectImportDirective(element, holder.convert())
      is SolVarLiteral -> inspectVarLiteralRef(element, holder.convert())
      is SolVariableDefinition -> inspectVariableDefinition(element, holder.convert())
      is SolAssignmentExpression -> inspectAssigmentExpression(element, holder.convert())

    }
  }
  private fun applyColor(holder: AnnotationHolder, element: PsiElement, color: SolColor) {
    holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
      .range(element)
      .textAttributes(color.textAttributesKey)
      .create()
  }

  private fun applyColor(holder: AnnotationHolder, range: TextRange, color: SolColor) {
    holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
      .range(range)
      .textAttributes(color.textAttributesKey)
      .create()
  }
}

fun ProblemsHolder.convert() : SolProblemsHolder {
  val dis = this;
  return object : SolProblemsHolder {
    override fun registerProblem(psiElement: PsiElement, descriptionTemplate: String, type: ProblemHighlightType, vararg fix: LocalQuickFix) {
      dis.registerProblem(psiElement, descriptionTemplate, type, *fix);
    }
  }
}

interface SolProblemsHolder {
  fun registerProblem(psiElement: PsiElement, descriptionTemplate: String, type: ProblemHighlightType = ProblemHighlightType.GENERIC_ERROR, vararg fix: LocalQuickFix)

}

val createTempTextAttributesKey = TextAttributesKey.createTempTextAttributesKey("solUnused", TextAttributes().apply { foregroundColor = JBColor.GRAY })

private fun AnnotationHolder.convert(): SolProblemsHolder {
  val dis = this;
  return object : SolProblemsHolder {
    override fun registerProblem(psiElement: PsiElement, descriptionTemplate: String, type: ProblemHighlightType, vararg fix: LocalQuickFix) {
      if (ApplicationManager.getApplication().isUnitTestMode) {
        return
      }
      val severity = when (type) {
        ProblemHighlightType.GENERIC_ERROR_OR_WARNING -> HighlightSeverity.ERROR
        ProblemHighlightType.LIKE_UNUSED_SYMBOL -> HighlightSeverity.WEAK_WARNING
        else -> HighlightSeverity.ERROR
      }
      var builder = dis.newAnnotation(severity, descriptionTemplate).range(psiElement)
      if (type == ProblemHighlightType.LIKE_UNUSED_SYMBOL) {
        builder = builder.textAttributes(CodeInsightColors.NOT_USED_ELEMENT_ATTRIBUTES)
      }
      builder.create()
    }
  }
}
