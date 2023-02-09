package com.broxus.solidity.ide.annotation

import com.broxus.solidity.ide.colors.SolColor
import com.broxus.solidity.ide.hints.NO_VALIDATION_TAG
import com.broxus.solidity.ide.hints.comments
import com.broxus.solidity.ide.hints.startOffset
import com.broxus.solidity.lang.core.SolidityTokenTypes
import com.broxus.solidity.lang.psi.*
import com.broxus.solidity.lang.psi.impl.SolErrorDefMixin
import com.broxus.solidity.lang.resolve.SolResolver
import com.broxus.solidity.lang.types.SolUnknown
import com.broxus.solidity.lang.types.getSolType
import com.broxus.solidity.lang.types.type
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.markup.EffectType
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
      is SolContractDefinition -> element.identifier?.let { applyColor(holder, it, SolColor.CONTRACT_NAME) }
      is SolStructDefinition -> element.identifier?.let { applyColor(holder, it, SolColor.STRUCT_NAME) }
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
      is SolModifierInvocation -> applyColor(holder, element.varLiteral.identifier, SolColor.MODIFIER_INVOCATION)
      is SolUserDefinedTypeName -> {
        when(val resolved = SolResolver.resolveTypeNameUsingImports(element).firstOrNull()) {
          is SolContractDefinition, is SolStructDefinition, is SolEnumDefinition,
          is SolErrorDefinition, is SolEventDefinition -> applyColor(holder, element, SolColor.TYPE_REFERENCE)
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
              applyColor(holder, element.referenceNameElement, SolColor.TYPE_REFERENCE)
            } else {
              applyColor(holder, element.referenceNameElement, SolColor.FUNCTION_CALL)
            }
          }
        }
      }
      is SolFunctionCallArguments -> {
        val args = element.expressionList
        fun PsiElement.getDefs() : List<SolFunctionDefElement>? {
          return ((parent.parent?.children?.firstOrNull() as? SolMemberAccessExpression)?.let {
            SolResolver.resolveMemberAccess(it)
          } ?: (parent?.parent as? SolFunctionCallExpression)?.let {
            SolResolver.resolveVarLiteralReference(it)
          })?.filterIsInstance<SolFunctionDefElement>()
        }
        if (args.firstOrNull() !is SolMapExpression) {
          element.getDefs()?.let {
            val funDefs = it.filterNot { it.comments().any { it.elementType == SolidityTokenTypes.NAT_SPEC_TAG && it.text == NO_VALIDATION_TAG } }
            if (funDefs.isNotEmpty()) {
              var wrongNumberOfArgs = ""
              var wrongTypes = ""
              var wrongElement = element
              if (funDefs.none { ref ->
                  val expArgs = ref.parameters.size
                  val actArgs = args.size
                  if (actArgs != expArgs) {
                    wrongNumberOfArgs = "Expected $expArgs argument${if (expArgs > 1) "s" else ""}, but got $actArgs"
                    false
                  } else {
                    args.withIndex().all { argtype ->
                      ref.parameters.getOrNull(argtype.index)?.let {
                        val expType = getSolType(it.typeName)
                        val actType = argtype.value.type
                        expType == SolUnknown || actType == SolUnknown || expType.isAssignableFrom(actType).also {
                          if (!it) {
                            wrongTypes = "Argument of type '$actType' is not assignable to parameter of type '${expType}'"
                            wrongElement = argtype.value
                          }
                        }
                      } == true
                    }
                  }
                }) {
                holder.newAnnotation(HighlightSeverity.ERROR, wrongTypes.takeIf { it.isNotEmpty() } ?: wrongNumberOfArgs)
                  .range(wrongElement)
                  .enforcedTextAttributes(TextAttributes().apply { effectType = EffectType.WAVE_UNDERSCORE; effectColor = JBColor.RED })
                  .create()
              }
            }
          }
        }
      }
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
