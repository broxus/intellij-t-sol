package com.broxus.solidity.ide.hints


import com.broxus.solidity.lang.psi.SolDeclarationItem
import com.broxus.solidity.lang.psi.SolExpression
import com.broxus.solidity.lang.psi.SolFunctionCallArguments
import com.broxus.solidity.lang.psi.SolFunctionCallElement
import com.broxus.solidity.lang.types.inferDeclType
import com.intellij.codeInsight.hints.Option
import com.intellij.codeInsight.hints.InlayInfo
import com.intellij.codeInsight.hints.InlayParameterHintsProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentOfType

class SolParameterInlayHintProvider : InlayParameterHintsProvider {

  override fun getParameterHints(element: PsiElement): List<InlayInfo> {
    return HintType.values().filter { it.isApplicable(element) }.flatMap { it.provideHints(element) }
      // return when {
      //     HintType.PARAMETER_HINT.isApplicable(element) -> HintType.PARAMETER_HINT.provideHints(element)
      //     else -> emptyList()
      // }
  }

  override fun getDefaultBlackList(): Set<String> {
    return emptySet()
  }
}


enum class HintType(
     private val description: String,
    defaultEnabled: Boolean
) {

  TUPLE_HINT(
    "myTupleHintDescription",
      true
  ) {
      override fun provideHints(e: PsiElement): List<InlayInfo> {
          val item = e as? SolDeclarationItem ?: return emptyList()
          return listOf(InlayInfo(inferDeclType(item).toString(), e.textOffset))
      }

      override fun isApplicable(e: PsiElement): Boolean = e is SolDeclarationItem
  },

    PARAMETER_HINT(
      "myHintDescription",
        true
    ) {
        override fun provideHints(e: PsiElement): List<InlayInfo> {
            val callElement = e.parentOfType<SolFunctionCallElement>(true) ?: return emptyList()
            return provideArgumentNameHints(callElement)
        }

        override fun isApplicable(e: PsiElement): Boolean = e is SolFunctionCallArguments
    };


    companion object {
        private val values = values()

        fun resolve(e: PsiElement): List<HintType> =
            values.filter { it.isApplicable(e) }

        // private fun KtOperationReferenceExpression.hasIllegalLiteralPrefixOrSuffix(): Boolean {
        //     val prevLeaf = PsiTreeUtil.prevLeaf(this)
        //     val nextLeaf = PsiTreeUtil.nextLeaf(this)
        //     return prevLeaf?.illegalLiteralPrefixOrSuffix() == true || nextLeaf?.illegalLiteralPrefixOrSuffix() == true
        // }
        // private fun PsiElement.illegalLiteralPrefixOrSuffix(): Boolean {
        //     val elementType = this.node.elementType
        //     return (elementType === KtTokens.IDENTIFIER) ||
        //             (elementType === KtTokens.INTEGER_LITERAL) ||
        //             (elementType === KtTokens.FLOAT_LITERAL) ||
        //             elementType is KtKeywordToken
        // }

    }

    abstract fun isApplicable(e: PsiElement): Boolean
    open fun provideHints(e: PsiElement): List<InlayInfo> = emptyList()
    open fun provideHintDetails(e: PsiElement): List<InlayInfoDetails> =
        provideHints(e).map { InlayInfoDetails(it, listOf(TextInlayInfoDetail(it.text))) }

    val option = Option("SHOW_${this.name}", { this.description }, defaultEnabled)
    val enabled
        get() = option.get()
}

data class InlayInfoDetails(val inlayInfo: InlayInfo, val details: List<InlayInfoDetail>)

sealed class InlayInfoDetail(val text: String)

class TextInlayInfoDetail(text: String, val smallText: Boolean = true): InlayInfoDetail(text) {
    override fun toString(): String = "[$text]"
}
class TypeInlayInfoDetail(text: String, val fqName: String?): InlayInfoDetail(text) {
    override fun toString(): String = "[$text :$fqName]"
}
class PsiInlayInfoDetail(text: String, val element: PsiElement): InlayInfoDetail(text) {
    override fun toString(): String = "[$text @ $element]"
}



fun provideArgumentNameHints(element: SolFunctionCallElement): List<InlayInfo> {
  val params = element.resolveDefinitions().takeIf { it?.size == 1 }?.get(0)?.parameters ?: return emptyList()
  // if (expressionList.none { it.isUnclearExpression() }) return emptyList()
  val args = element.functionCallArguments.expressionList

  return params.zip(args).map { InlayInfo(it.first.run { identifier ?: typeName }.text ?: "", it.second.textOffset) }

    // val ctx = element.safeAnalyzeNonSourceRootCode(BodyResolveMode.PARTIAL)
    // val call = element.getCall(ctx) ?: return emptyList()
    // val resolvedCall = call.getResolvedCall(ctx)
    // if (resolvedCall != null) {
    //     return getArgumentNameHintsForCallCandidate(resolvedCall, call.valueArgumentList)
    // }
    // val candidates = call.resolveCandidates(ctx, element.getResolutionFacade())
    // if (candidates.isEmpty()) return emptyList()
    // candidates.singleOrNull()?.let { return getArgumentNameHintsForCallCandidate(it, call.valueArgumentList) }
    // return candidates.map { getArgumentNameHintsForCallCandidate(it, call.valueArgumentList) }.reduce { infos1, infos2 ->
    //     for (index in infos1.indices) {
    //         if (index >= infos2.size || infos1[index] != infos2[index]) {
    //             return@reduce infos1.subList(0, index)
    //         }
    //     }
    //     infos1
    // }
}

// private fun getArgumentNameHintsForCallCandidate(
//     resolvedCall: ResolvedCall<out CallableDescriptor>,
//     valueArgumentList: KtValueArgumentList?
// ): List<InlayInfo> {
//     val resultingDescriptor = resolvedCall.resultingDescriptor
//     if (resultingDescriptor.hasSynthesizedParameterNames() && resultingDescriptor !is FunctionInvokeDescriptor) {
//         return emptyList()
//     }
//
//     if (resultingDescriptor.valueParameters.size == 1
//         && resultingDescriptor.name == resultingDescriptor.valueParameters.single().name
//     ) {
//         // method name equals to single parameter name
//         return emptyList()
//     }
//
//     return resolvedCall.valueArguments.mapNotNull { (valueParam: ValueParameterDescriptor, resolvedArg) ->
//         if (resultingDescriptor.isAnnotationConstructor() && valueParam.name.asString() == "value") {
//             return@mapNotNull null
//         }
//
//         if (resultingDescriptor is FunctionInvokeDescriptor &&
//             valueParam.type.extractParameterNameFromFunctionTypeArgument() == null
//         ) {
//             return@mapNotNull null
//         }
//
//         if (resolvedArg == resolvedCall.valueArgumentsByIndex?.firstOrNull()
//             && resultingDescriptor.valueParameters.firstOrNull()?.name == resultingDescriptor.name) {
//             // first argument with the same name as method name
//             return@mapNotNull null
//         }
//
//         resolvedArg.arguments.firstOrNull()?.let { arg ->
//             arg.getArgumentExpression()?.let { argExp ->
//                 if (!arg.isNamed() && !argExp.isAnnotatedWithComment(valueParam, resultingDescriptor) && !valueParam.name.isSpecial && argExp.isUnclearExpression()) {
//                     val prefix = if (valueParam.varargElementType != null) "..." else ""
//                     val offset = if (arg == valueArgumentList?.arguments?.firstOrNull() && valueParam.varargElementType != null)
//                         valueArgumentList.leftParenthesis?.textRange?.endOffset ?: argExp.startOffset
//                     else
//                         arg.getSpreadElement()?.startOffset ?: argExp.startOffset
//                     return@mapNotNull InlayInfo(prefix + valueParam.name.identifier + ":", offset)
//                 }
//             }
//         }
//         null
//     }
// }

private fun SolExpression.isUnclearExpression() = when (this) {
    // is KtConstantExpression, is KtThisExpression, is KtBinaryExpression, is KtStringTemplateExpression -> true
    // is KtPrefixExpression -> baseExpression is KtConstantExpression && (operationToken == KtTokens.PLUS || operationToken == KtTokens.MINUS)
    else -> false
}

private fun SolExpression.isAnnotatedWithComment(valueParameter: Any /*ValueParameterDescriptor*/, descriptor: Any /*CallableDescriptor*/): Boolean = false
    // (descriptor is JavaMethodDescriptor || descriptor is JavaClassConstructorDescriptor) &&
    //         prevLeafs
    //             .takeWhile { it is PsiWhiteSpace || it is PsiComment }
    //             .any { it is PsiComment && it.isParameterNameComment(valueParameter) }
