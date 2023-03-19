package com.broxus.solidity.ide

import com.broxus.solidity.ide.colors.SolColor
import com.broxus.solidity.lang.core.SolidityLexer
import com.broxus.solidity.lang.core.SolidityTokenTypes.*
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SingleLazyInstanceSyntaxHighlighterFactory
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.psi.tree.IElementType

class SolHighlighterFactory : SingleLazyInstanceSyntaxHighlighterFactory() {
  override fun createHighlighter() = SolHighlighter
}

object SolHighlighter : SyntaxHighlighterBase() {
  override fun getHighlightingLexer() = SolidityLexer()

  override fun getTokenHighlights(tokenType: IElementType): Array<out TextAttributesKey> {
    return pack(tokenMapping[tokenType])
  }

  private val tokenMapping: Map<IElementType, TextAttributesKey> = mapOf(
    COMMENT to SolColor.LINE_COMMENT,
    NAT_SPEC_TAG to SolColor.NAT_SPEC_TAG,

    LBRACE to SolColor.BRACES,
    RBRACE to SolColor.BRACES,
    LBRACKET to SolColor.BRACKETS,
    RBRACKET to SolColor.BRACKETS,
    LPAREN to SolColor.PARENTHESES,
    RPAREN to SolColor.PARENTHESES,
    SEMICOLON to SolColor.SEMICOLON,

    SCIENTIFICNUMBER to SolColor.NUMBER,
    FIXEDNUMBER to SolColor.NUMBER,
    DECIMALNUMBER to SolColor.NUMBER,
    HEXNUMBER to SolColor.NUMBER,
    NUMBERUNIT to SolColor.NUMBER,

    STRINGLITERAL to SolColor.STRING
  ).plus(
    keywords().map { it to SolColor.KEYWORD }
  ).plus(
    literals().map { it to SolColor.KEYWORD }
  ).plus(
    operators().map { it to SolColor.OPERATION_SIGN }
  ).plus(
    types().map { it to SolColor.TYPE }
  ).mapValues { it.value.textAttributesKey }

  fun keywords() = setOf<IElementType>(
    // Note, the ERROR/REVERT are not keywords and are excluded
    IMPORT, AS, PRAGMA, NEW, DELETE, EMIT, /*REVERT,*/ CONSTRUCTOR,
    CONTRACT, LIBRARY, INTERFACE, IS, STRUCT, FUNCTION, ENUM,
    PUBLIC, PRIVATE, INTERNAL, EXTERNAL, CONSTANT, PURE, VIEW, STATIC, ABSTRACT,
    IF, ELSE, FOR, WHILE, DO, BREAK, CONTINUE, THROW, USING, RETURN, RETURNS, RESPONSIBLE,
    MAPPING, EVENT, /*ERROR,*/ ANONYMOUS, MODIFIER, ASSEMBLY, OPTIONAL, VECTOR,
    VAR, STORAGE, MEMORY, WEI, ETHER, GWEI, SZABO, FINNEY,
    EVER, GEVER, KEVER, MEVER, NEVER, NANOEVER, MILLIEVER, KILOEVER, MEGAEVER, GIGAEVER,
    SECONDS, MINUTES, HOURS,
    DAYS, WEEKS, YEARS, TYPE, VIRTUAL, OVERRIDE, IMMUTABLE, INDEXED
  )

  fun types() = setOf<IElementType>(
    BYTENUMTYPE, BYTESNUMTYPE, FIXEDNUMTYPE, INTNUMTYPE, UFIXEDNUMTYPE, UINTNUMTYPE,
    STRING, BOOL, ADDRESS,
  )

  private fun literals() = setOf<IElementType>(BOOLEANLITERAL)

  private fun operators() = setOf<IElementType>(
    NOT, ASSIGN, PLUS_ASSIGN, MINUS_ASSIGN, MULT_ASSIGN, DIV_ASSIGN, PERCENT_ASSIGN,
    PLUS, MINUS, MULT, DIV, EXPONENT, CARET,
    LESS, MORE, LESSEQ, MOREEQ,
    AND, ANDAND, OR, OROR,
    EQ, NEQ, TO,
    INC, DEC,
    TILDE, PERCENT,
    LSHIFT, RSHIFT,
    LEFT_ASSEMBLY, RIGHT_ASSEMBLY
  )
}

