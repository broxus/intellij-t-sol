package me.serce.solidity.lang.core

import com.intellij.lexer.FlexAdapter
import com.intellij.psi.tree.IElementType
import me.serce.solidity._SolidityLexer
import me.serce.solidity.lang.TSolidityLanguage
import java.io.Reader

class SolidityTokenType(debugName: String) : IElementType(debugName, TSolidityLanguage)
class SolidityLexer : FlexAdapter(_SolidityLexer(null as Reader?))
