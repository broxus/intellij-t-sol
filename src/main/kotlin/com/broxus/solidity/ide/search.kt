package com.broxus.solidity.ide

import com.intellij.lang.HelpID
import com.intellij.lang.cacheBuilder.DefaultWordsScanner
import com.intellij.lang.cacheBuilder.WordsScanner
import com.intellij.lang.findUsages.FindUsagesProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.TokenSet
import com.broxus.solidity.lang.core.SolidityLexer
import com.broxus.solidity.lang.core.SolidityParserDefinition
import com.broxus.solidity.lang.core.SolidityTokenTypes.IDENTIFIER
import com.broxus.solidity.lang.core.SolidityTokenTypes.STRINGLITERAL
import com.broxus.solidity.lang.psi.SolNamedElement

class SolFindUsagesProvider : FindUsagesProvider {
  override fun getWordsScanner(): WordsScanner = SolWordScanner()
  override fun canFindUsagesFor(element: PsiElement) = element is SolNamedElement

  override fun getHelpId(element: PsiElement) = HelpID.FIND_OTHER_USAGES
  override fun getType(element: PsiElement) = ""
  override fun getDescriptiveName(element: PsiElement) = ""
  override fun getNodeText(element: PsiElement, useFullName: Boolean) = ""
}

class SolWordScanner : DefaultWordsScanner(
  SolidityLexer(),
  TokenSet.create(IDENTIFIER),
  SolidityParserDefinition.COMMENTS,
  TokenSet.create(STRINGLITERAL)
)
