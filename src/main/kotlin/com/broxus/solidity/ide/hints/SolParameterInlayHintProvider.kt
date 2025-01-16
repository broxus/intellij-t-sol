package com.broxus.solidity.ide.hints


import com.broxus.solidity.lang.psi.*
import com.broxus.solidity.lang.resolve.SolResolver
import com.broxus.solidity.lang.types.inferDeclType
import com.intellij.codeInsight.hints.InlayInfo
import com.intellij.codeInsight.hints.InlayParameterHintsProvider
import com.intellij.codeInsight.hints.Option
import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentOfType

class SolParameterInlayHintProvider : InlayParameterHintsProvider {

  override fun getParameterHints(element: PsiElement): List<InlayInfo> {
    return HintType.values().filter { it.isApplicable(element) }.flatMap { it.provideHints(element) }
  }

  override fun getDefaultBlackList(): Set<String> {
    return emptySet()
  }
}


enum class HintType(
     private val description: String,
    defaultEnabled: Boolean
) {

    IMPORT_HINT(
      "myImportHintDescription",
        true
    ) {
        override fun provideHints(e: PsiElement): List<InlayInfo> {
            val item = e as? SolImportDirective ?: return emptyList()
            if (item.importAliasedPairList.isNotEmpty()) return emptyList()
            val used = SolResolver.collectUsedElements(item)

            return if (used.isNotEmpty()) listOf(InlayInfo(used.joinToString(), e.endOffset(), false, false, true)) else emptyList()
        }

        override fun isApplicable(e: PsiElement): Boolean = e is SolImportDirective
    },

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

fun provideArgumentNameHints(element: SolFunctionCallElement): List<InlayInfo> {
  val params = element.resolveDefinitions().takeIf { it?.size == 1 }?.get(0)?.parseParameters() ?: return emptyList()
    if (params.any { it.first == "varargs" } ) return emptyList()
  val args = element.functionCallArguments.expressionList

  return params.zip(args).map { InlayInfo(it.first.let { it.first ?: it.second.toString() }, it.second.startOffset) }

}

