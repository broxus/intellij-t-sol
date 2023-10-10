package com.broxus.solidity.ide.hints

import com.broxus.solidity.ide.SolHighlighter
import com.broxus.solidity.ide.colors.SolColor
import com.broxus.solidity.lang.core.SolidityTokenTypes
import com.broxus.solidity.lang.psi.*
import com.broxus.solidity.lang.resolve.SolResolver
import com.broxus.solidity.lang.types.getSolType
import com.intellij.lang.documentation.AbstractDocumentationProvider
import com.intellij.lang.documentation.DocumentationMarkup.*
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.ModificationTracker
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.descendantsOfType
import com.intellij.psi.util.siblings

const val NO_VALIDATION_TAG = "@custom:no_validation"
const val TYPE_ARGUMENT_TAG = "@custom:typeArgument"
const val DEPRECATED_TAG = "@custom:deprecated"
const val VERSION_TAG = "@custom:version"

fun PsiElement.isBuiltin() = this.containingFile.virtualFile == null

fun PsiElement.comments(): List<PsiElement> {
  return CachedValuesManager.getCachedValue(this) {
    val nonSolElements = siblings(false, false)
      .takeWhile { it !is SolElement }.toList()
    val res = if (!isBuiltin()) PsiDocumentManager.getInstance(project).getDocument(this.containingFile)?.let { document ->
      val tripleLines = nonSolElements.filter { it.text.startsWith("///") }.map { document.getLineNumber(it.textOffset) }.toSet()
      val tripleLineComments = nonSolElements.filter { tripleLines.contains(document.getLineNumber(it.startOffset)) }
      val blockComments = collectBlockComments(nonSolElements)
      tripleLineComments + blockComments
    } ?: emptyList()
    else {
      collectBlockComments(nonSolElements)
    }
    CachedValueProvider.Result.create(res, if (isBuiltin()) ModificationTracker.NEVER_CHANGED else this.parent)
  }
}

fun PsiElement.tagComments(tag: String) : String? {
  val comments = comments()
  return comments.indexOfFirst { it.text == tag }.takeIf { it >= 0 }
        ?.let { comments.getOrNull(it - 1)?.let { it.text.split("\n")[0] } }
}

private fun collectBlockComments(nonSolElements: List<PsiElement>): List<PsiElement> {
  val blockComments = nonSolElements.dropWhile { it.elementType != SolidityTokenTypes.COMMENT || !it.text.contains("*/") }.toList().let { l ->
    (l.indexOfFirst { it.elementType == SolidityTokenTypes.COMMENT && it.text.startsWith("/**") }.takeIf { it >= 0 }?.let { l.subList(0, it + 1) }
      ?: emptyList())
  }
  return blockComments
}

class SolDocumentationProvider : AbstractDocumentationProvider() {
  companion object {
    val abiHeaders = mapOf(
      "pubkey" to ("uint256" to "optional public key that the message can be signed with"),
      "notime" to ("" to "disables time abi header, which is enabled by default. Abi header time – uint64 local time when message was created, used for replay protection"),
      "expire" to ("uint32" to "time when the message should be meant as expired"))
    fun isAbiHeaderValue(element: PsiElement?) = element?.prevSibling?.text == "AbiHeader"
  }
  override fun getQuickNavigateInfo(element: PsiElement?, originalElement: PsiElement?): String? {
    if (element == null) return null
    val builder = StringBuilder()
    if (!builder.appendDefinition(element)) return null

    return builder.toString()
  }

  override fun getCustomDocumentationElement(editor: Editor, file: PsiFile, contextElement: PsiElement?, targetOffset: Int): PsiElement? {
    return if (contextElement?.elementType == SolidityTokenTypes.PRAGMAALL) contextElement else null
  }

  private val keywordColors = SolHighlighter.keywords().plus(SolHighlighter.types()).minus(SolidityTokenTypes.RETURN).map { it.toString() }
    .plus(setOf("u?int(\\d+)", "u?fixed(\\d+)", "bytes?(\\d+)"))
    .joinToString("|", "\\b(", ")\\b").toRegex()
  private val col = SolColor.TYPE.textAttributesKey.defaultAttributes.foregroundColor
  private val typeRGB = "rgb(${col.red},${col.green},${col.blue})"
  private fun String.colorizeKeywords(): String {
    return this.replace(keywordColors) { it.value.colorizeKeyword() }
  }
  private fun String.colorizeKeyword()  = "<b style='color:$typeRGB'>${this}</b>"

  override fun generateDoc(elementOrNull: PsiElement?, originalElement: PsiElement?): String? {
    var element = elementOrNull ?: return null
    if (element.elementType == SolidityTokenTypes.PRAGMAALL) {
      return showPragmaDocs(element)
    }
    if (element is SolMemberAccessExpression) {
      element = SolResolver.resolveMemberAccess(element).filterIsInstance<SolFunctionDefinition>().firstOrNull() ?: return null
    }
    val builder = StringBuilder()
    if (!builder.appendDefinition(element)) return null
    val comments = element.comments()
    if (comments.isNotEmpty()) {
      builder.append(CONTENT_START)
      comments.reversed().mapIndexed { i, e ->
        var text = e.text.let {
          if (comments.size == 1) it.replace("/**", "").replace("*/", "")
          else if (i == 0) it.replace("/**", "") else if (i == comments.size - 1) it.replace("*/", "") else it
        }
        if (e.elementType == SolidityTokenTypes.NAT_SPEC_TAG) {
          text = (if (e.text == NO_VALIDATION_TAG) "" else "<br/>$GRAYED_START${text.substring(1)}:$GRAYED_END")
        }
        builder.append(text)
      }
      builder.append(CONTENT_END)
    }

    return builder.toString()
  }

  private fun showPragmaDocs(element: PsiElement): String? {
    if (!isAbiHeaderValue(element)) return null
    val key = element.text.trim()
    val pair = abiHeaders[key] ?: return null
    return "$key (<i>${pair.first}</i>) : ${pair.second}"
  }

  private fun StringBuilder.appendDefinition(element: PsiElement) : Boolean {
    return calcDefinition(element)?.let {
      append(DEFINITION_START)
      append(it.colorizeKeywords())
      append(DEFINITION_END)
      true
    } ?: false
  }

  private fun calcDefinition(element: PsiElement): String? {
    return when (element) {
      is SolContractDefinition -> element.doc()
      is SolStructDefinition ->  "struct " + element.identifier.idName()
      is SolFunctionDefinition -> element.doc()
      is SolParameterDef -> element.colorizedTypeText()
      is SolVariableDeclaration -> element.colorizedTypeText()
      is SolTypedDeclarationItem -> "${getSolType(element.typeName).toString().colorizeKeywords()} ${element.identifier.idName()}"
      is SolStateVariableDeclaration -> "${getSolType(element.typeName).toString().colorizeKeywords()} ${element.identifier.idName()}"
      is SolEnumDefinition -> element.doc()
      is SolEventDefinition -> element.doc()
      is SolErrorDefinition -> element.doc()
      is SolModifierDefinition -> element.doc()

      else -> null
    }
  }

  private fun PsiElement?.idName() = this?.text ?: "<no_name>"

  private val colorizedTypes = SolHighlighter.types() + SolidityTokenTypes.CONTRACT_DEFINITION + SolidityTokenTypes.STRUCT
  private fun PsiElement.colorizedTypeText() = descendantsOfType<LeafPsiElement>().joinToString("") { el -> el.text.let { if (el.elementType in colorizedTypes || el.parent.elementType == SolidityTokenTypes.USER_DEFINED_TYPE_NAME) it.colorizeKeyword() else it } }

  private fun List<PsiElement>?.doc(separator: String = ", ", prefix: String = "", postfix : String = ""): String {
    return takeIf { it?.isNotEmpty() ?: false }
      ?.joinToString(separator, prefix, postfix) { e -> e.colorizedTypeText() } ?: ""
  }
  private fun SolContractDefinition.doc() : String {
    return "${if (isAbstract) "abstract " else ""}${contractType.docName} ${identifier.idName()}" +
      inheritanceSpecifierList.doc(prefix = " is ")
  }

  private fun SolFunctionDefinition.doc() : String {
    return "${if (isConstructor) "constructor" else "function"} ${identifier.idName()}(${parameters.doc()}) ${functionVisibilitySpecifierList.doc(" ")} " +
      "${stateMutabilityList.doc(" ")} ${modifierInvocationList.doc(" ")} ${returns?.parameterDefList?.doc(", ", "returns (", ")") ?: ""}"
  }

  private fun SolEnumDefinition.doc() : String {
    return "enum ${identifier.idName()} { ${enumValueList.doc()} }"
  }

  private fun SolEventDefinition.doc() : String {
    return "event ${identifier.idName()} ${children.joinToString { it.text.colorizeKeywords() }}"
  }

  private fun SolErrorDefinition.doc() : String {
    return "error ${identifier.idName()} ${children.joinToString { it.text.colorizeKeywords() }}"
  }

  private fun SolModifierDefinition.doc() : String {
    return "modifier ${identifier.idName()}(${parameterList?.parameterDefList?.doc() ?: ""}) " +
      "${virtualSpecifierList.takeIf { it.isNotEmpty() }?.doc() ?: ""} ${overrideSpecifierList.takeIf { it.isNotEmpty() }?.doc() ?: ""}"
  }
}
