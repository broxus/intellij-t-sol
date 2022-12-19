package me.serce.solidity.lang.core

import com.intellij.extapi.psi.PsiFileBase
import com.intellij.openapi.fileTypes.FileType
import com.intellij.psi.FileViewProvider
import com.intellij.psi.tree.IElementType
import me.serce.solidity.lang.TSolidityFileType
import me.serce.solidity.lang.TSolidityLanguage
import me.serce.solidity.lang.psi.SolElement

class SolidityFile(viewProvider: FileViewProvider) : PsiFileBase(viewProvider, TSolidityLanguage), SolElement {
  override fun getFileType(): FileType = TSolidityFileType
  override fun toString(): String = "T-Sol File"
}

class SolElementType(val name: String) : IElementType(name, TSolidityLanguage)
