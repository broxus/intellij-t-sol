package com.broxus.solidity.lang.completion

import com.broxus.solidity.ide.SolidityIcons
import com.broxus.solidity.lang.core.SolidityFile
import com.broxus.solidity.lang.core.SolidityTokenTypes
import com.broxus.solidity.lang.psi.*
import com.broxus.solidity.lang.psi.impl.addContext
import com.broxus.solidity.lang.resolve.SolResolver
import com.broxus.solidity.lang.types.SolInternalTypeFactory
import com.broxus.solidity.lang.types.SolType
import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.project.DumbAware
import com.intellij.patterns.StandardPatterns.and
import com.intellij.patterns.StandardPatterns.not
import com.intellij.psi.TokenType
import com.intellij.psi.util.PsiTreeUtil.findElementOfClassAtOffset
import com.intellij.psi.util.PsiTreeUtil.getParentOfType
import com.intellij.util.ProcessingContext

class SolFunctionCompletionContributor : CompletionContributor(), DumbAware {

  init {
    extend(CompletionType.BASIC, and(expression(), and(not(mapExpression()), not(memberAccessExpression()))), FunctionCompletionProvider)
  }

  override fun beforeCompletion(context: CompletionInitializationContext) {
    val file = context.file
    if (file is SolidityFile) {
      if (context.completionType == CompletionType.BASIC) {
        val dummySuffix = deduceSemicolonOrBracket(context.editor, file, context.startOffset)
        context.dummyIdentifier = CompletionInitializationContext.DUMMY_IDENTIFIER_TRIMMED + dummySuffix
      }
    }
  }
}

private fun deduceSemicolonOrBracket(editor: Editor, file: SolidityFile, startOffset: Int): String {
  if (editor !is EditorEx) {
    return ""
  }

  val element = file.findElementAt(startOffset)

  val iterator = editor.highlighter.createIterator(startOffset)
  if (iterator.atEnd()) {
    return ""
  }

  if (iterator.tokenType === TokenType.WHITE_SPACE) {
    iterator.advance()
  }

  if (!iterator.atEnd() && iterator.tokenType === SolidityTokenTypes.IDENTIFIER) {
    iterator.advance()
  }

  if (!iterator.atEnd() && iterator.tokenType === SolidityTokenTypes.LPAREN
    && getParentOfType(element, SolFunctionCallExpression::class.java) != null
  ) {
    return "" // function call
  }

  if (!iterator.atEnd() && iterator.tokenType === SolidityTokenTypes.RPAREN) {
    iterator.advance()
  }

  if (!iterator.atEnd() && iterator.tokenType == SolidityTokenTypes.SEMICOLON) {
    return ""
  }

  // lets check where we are in the source code
  if (findElementOfClassAtOffset(file, startOffset, SolFunctionCallArguments::class.java, false) != null) {
    return ""
  }

  // it may be array access
  if (!iterator.atEnd() && iterator.tokenType === SolidityTokenTypes.RBRACKET) {
    return "];"
  }

  return ");"
}

object FunctionCompletionProvider : CompletionProvider<CompletionParameters>() {
  override fun addCompletions(
    parameters: CompletionParameters,
    context: ProcessingContext,
    result: CompletionResultSet
  ) {
    val position = parameters.originalPosition

    val project = parameters.editor.project ?: return
    val availableRefs = listOf(SolInternalTypeFactory.of(project).globalType.ref) + (getParentOfType(position, SolFunctionDefinition::class.java)?.contract?.let { contract ->
      contract.collectSupers.flatMap { SolResolver.resolveTypeName(it) } + contract
    }?.filterIsInstance<SolContractDefinition>() ?: emptyList())

    val expr = parameters.originalPosition?.parentOfType<SolExpression>()

    val functions = availableRefs
      .flatMap { (if (expr != null) SolInternalTypeFactory.of(project).getDeclarations(expr, it.functionDefinitionList).toList() else it.functionDefinitionList).onEach { it.addContext(expr) } }
      .toFunctionLookups()

    val structs = availableRefs
      .flatMap { it.structDefinitionList }
      .filterNot { it.name == null }
      .map {
        LookupElementBuilder
          .create(it)
          .withBoldness(true)
          .withIcon(SolidityIcons.FUNCTION)
          .withTypeText(funcOutType(it))
          .withTailText(funcInType(it))
          .insertParenthesis(false)
      }

    (functions + structs)
      .forEach(result::addElement)
  }
}

fun List<SolFunctionDefinition>.toFunctionLookups() =
  this.mapNotNull { it.toFunctionLookup() }

fun SolCallableElement.toFunctionLookup(): LookupElementBuilder? {
  return if (name == null) {
    null
  } else {
    LookupElementBuilder
      .create(this)
      .withBoldness(true)
      .withIcon(SolidityIcons.FUNCTION)
      .withTypeText(funcOutType(this))
      .withTailText(funcInType(this))
      .insertParenthesis(false)
  }
}

fun SolStructDefinition.toStructLookup(): LookupElementBuilder? {
  return name?.let {  LookupElementBuilder
      .create(this)
      .withBoldness(true)
      .withIcon(SolidityIcons.STRUCT)
      .withTypeText(funcOutType(this))
      .withTailText(funcInType(this))
      .insertParenthesis(false)
  }
}

fun SolNamedElement.toVarLookup(): LookupElementBuilder? {
  return name?.let {
    LookupElementBuilder.create(this).withIcon(SolidityIcons.STATE_VAR)
  }
}


private fun funcOutType(elem: SolCallableElement): String {
  val type = elem.parseType()
  return type?.toString() ?: ""
}

private fun funcInType(elem: SolCallableElement): String {
  val params = elem.parseParameters()
  val joinedParams = params.joinToString { formatParam(it) }
  return "($joinedParams)"
}

private fun formatParam(param: Pair<String?, SolType>): String {
  return when (param.first) {
    null -> param.second.toString()
    else -> "${param.first}: ${param.second}"
  }
}
