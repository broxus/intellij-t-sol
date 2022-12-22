package com.broxus.solidity.lang.core

import com.intellij.lexer.FlexAdapter
import com.intellij.psi.tree.IElementType
import com.broxus.solidity._SolidityLexer
import com.broxus.solidity.lang.TSolidityLanguage
import java.io.Reader

class SolidityTokenType(debugName: String) : IElementType(debugName, TSolidityLanguage)
class SolidityLexer : FlexAdapter(_SolidityLexer(null as Reader?))
