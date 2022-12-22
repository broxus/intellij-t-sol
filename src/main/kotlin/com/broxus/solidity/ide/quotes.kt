package com.broxus.solidity.ide

import com.intellij.codeInsight.editorActions.SimpleTokenSetQuoteHandler
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.highlighter.HighlighterIterator
import com.broxus.solidity.lang.core.SolidityTokenTypes

class SolQuoteTokenHandler : SimpleTokenSetQuoteHandler(SolidityTokenTypes.STRINGLITERAL) {
  override fun hasNonClosedLiteral(editor: Editor?, iterator: HighlighterIterator?, offset: Int) = true
}
