package com.broxus.solidity.ide.annotation

import com.broxus.solidity.ide.colors.SolColor
import com.broxus.solidity.ide.hints.startOffset
import com.broxus.solidity.lang.psi.*
import com.broxus.solidity.lang.psi.impl.SolErrorDefMixin
import com.broxus.solidity.lang.resolve.SolResolver
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.refactoring.suggested.endOffset

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
      is SolMemberAccessExpression -> when(element.expression.firstChild.text) {
        "super" -> applyColor(holder, element.expression.firstChild, SolColor.KEYWORD)
        "msg", "block", "abi" -> applyColor(holder, element.expression.firstChild, SolColor.GLOBAL)
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
      is SolContractDefinition -> element.identifier?.let { applyColor(holder, it, element.typeColor()) }
      is SolStructDefinition -> element.identifier?.let { applyColor(holder, it, element.typeColor()) }
      is SolEnumDefinition -> element.identifier?.let { applyColor(holder, it, SolColor.ENUM_NAME) }
      is SolEventDefinition -> element.identifier?.let { applyColor(holder, it, SolColor.EVENT_NAME) }
      is SolUserDefinedValueTypeDefinition -> element.identifier?.let { applyColor(holder, it, SolColor.USER_DEFINED_VALUE_TYPE) }
      is SolConstantVariableDeclaration -> applyColor(holder, element.identifier, SolColor.CONSTANT)
      is SolStateVariableDeclaration -> {
        if (element.mutationModifier?.textMatches("constant") == true) {
          applyColor(holder, element.identifier, SolColor.CONSTANT)
        } else {
          applyColor(holder, element.identifier, SolColor.STATE_VARIABLE)
        }
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
      }
      is SolModifierDefinition -> element.identifier?.let { applyColor(holder, it, SolColor.FUNCTION_DECLARATION) }
      is SolModifierInvocation -> applyColor(holder, element.varLiteral.identifier, SolColor.FUNCTION_CALL)
      is SolUserDefinedTypeName -> {
        when(val resolved = SolResolver.resolveTypeNameUsingImports(element).firstOrNull()) {
          is SolContractDefinition -> applyColor(holder, element, resolved.typeColor())
          is SolStructDefinition -> applyColor(holder, element, resolved.typeColor())
          is SolEnumDefinition -> applyColor(holder, element, SolColor.ENUM_NAME)
          is SolUserDefinedValueTypeDefinition -> applyColor(holder, element, SolColor.USER_DEFINED_VALUE_TYPE)
        }
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
              applyColor(holder, element.referenceNameElement, element.typeColor())
            } else {
              applyColor(holder, element.referenceNameElement, SolColor.FUNCTION_CALL)
            }
          }
        }
      }
    }
  }
  private fun PsiElement.typeColor() = if (isBuiltin()) SolColor.BUILTIN_TYPE else if (this is SolContractDefinition) SolColor.CONTRACT_NAME else SolColor.STRUCT_NAME
  private fun PsiElement.isBuiltin() = this.containingFile.virtualFile == null

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
