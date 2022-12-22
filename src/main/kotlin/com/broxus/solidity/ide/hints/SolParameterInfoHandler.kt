package com.broxus.solidity.ide.hints

import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.lang.parameterInfo.ParameterInfoContext
import com.intellij.lang.parameterInfo.ParameterInfoUIContext
import com.intellij.lang.parameterInfo.ParameterInfoUtils
import com.intellij.lang.parameterInfo.UpdateParameterInfoContext
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.broxus.solidity.lang.core.SolidityTokenTypes
import com.broxus.solidity.lang.psi.SolCallable
import com.broxus.solidity.lang.psi.SolFunctionCallArguments
import com.broxus.solidity.lang.psi.SolFunctionCallExpression
import com.broxus.solidity.lang.resolve.canBeApplied
import com.broxus.solidity.lang.resolve.ref.SolFunctionCallReference
import com.broxus.solidity.lang.types.findParentOrNull

class SolParameterInfoHandler : AbstractParameterInfoHandler<SolFunctionCallExpression, SolArgumentsDescription>() {
  override fun couldShowInLookup() = true

  override fun getParametersForLookup(item: LookupElement, context: ParameterInfoContext?): Array<out Any>? = null

  override fun findTargetElement(file: PsiFile, offset: Int): SolFunctionCallExpression? {
    return file.findElementAt(offset)?.findParentOrNull()
  }

  override fun calculateParameterInfo(element: SolFunctionCallExpression): Array<SolArgumentsDescription>? {
    val result = SolArgumentsDescription.findDescriptions(element)
    if (result.isEmpty()) return null
    return result.toTypedArray()
  }

  override fun updateParameterInfo(parameterOwner: SolFunctionCallExpression, context: UpdateParameterInfoContext) {
    if (context.parameterOwner != parameterOwner) {
      context.removeHint()
      return
    }
    val currentParameterIndex = if (parameterOwner.startOffset == context.offset) {
      -1
    } else {
      ParameterInfoUtils.getCurrentParameterIndex(parameterOwner.functionCallArguments.node, context.offset, SolidityTokenTypes.COMMA)
    }
    context.setCurrentParameter(currentParameterIndex)
  }

  override fun updateUI(p: SolArgumentsDescription, context: ParameterInfoUIContext) {
    val range = p.getArgumentRange(context.currentParameterIndex)
    context.setupUIComponentPresentation(
      p.presentText,
      range.startOffset,
      range.endOffset,
      !context.isUIComponentEnabled,
      false,
      false,
      if (p.valid) context.defaultParameterColor.brighter() else context.defaultParameterColor)
  }

  override fun getParameterCloseChars() = ",)"

  override fun tracksParameterIndex() = true

  override fun getParametersForDocumentation(p: SolArgumentsDescription, context: ParameterInfoContext?) =
    p.arguments
}

class SolArgumentsDescription(
  callable: SolCallable,
  callArguments: SolFunctionCallArguments,
  val arguments: Array<String>
) {

  val valid = callable.canBeApplied(callArguments)
  val presentText = if (arguments.isEmpty()) "<no arguments>" else arguments.joinToString(", ")

  fun getArgumentRange(index: Int): TextRange {
    if (index < 0 || index >= arguments.size) {
      return TextRange.EMPTY_RANGE
    }
    val start = arguments.take(index).sumOf { it.length + 2 }
    return TextRange(start, start + arguments[index].length)
  }

  companion object {
    fun findDescriptions(call: SolFunctionCallExpression): List<SolArgumentsDescription> {
      val ref = call.reference
      return if (ref is SolFunctionCallReference) {
        ref.resolveFunctionCall()
          .map { def ->
            val parameters = def.parseParameters()
              .map { "${it.second}${it.first?.let { name -> " $name" } ?: ""}" }
              .toTypedArray()
            SolArgumentsDescription(def, call.functionCallArguments, parameters)
          }
      } else {
        emptyList()
      }
    }
  }
}
