package com.broxus.solidity.lang.psi

import com.broxus.solidity.lang.TSolidityFileType
import com.broxus.solidity.lang.core.SolidityTokenTypes
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.PsiParserFacade
import com.intellij.psi.impl.source.tree.LeafPsiElement
import org.intellij.lang.annotations.Language

class SolPsiFactory(val project: Project) {
  fun createIdentifier(name: String): PsiElement {
    return createFromText<SolContractDefinition>("contract $name {}")?.identifier
      ?: error("Failed to create identifier: `$name`")
  }

  fun createStringLiteral(text: String) = LeafPsiElement(SolidityTokenTypes.STRINGLITERAL, text)

  fun createStruct(structBody: String): SolStructDefinition {
    return createFromText("contract dummystruct$1 { $structBody }")
      ?: error("Failed to create struct: `$structBody`")
  }

  fun createFunction(func: String): SolFunctionDefinition {
    return createFromText("contract dummystruct$1 { $func }")
      ?: error("Failed to create function: `$func`")
  }


  fun createImportDirective(importPath: String): SolImportDirective {
    return createFromText("import \"$importPath\";")
      ?: error("Failed to create struct: `$importPath`")
  }

  fun createNewLine(project: Project): PsiElement {
    return PsiParserFacade.SERVICE.getInstance(project).createWhiteSpaceFromText("\n")
  }

  fun createContract(@Language("T-Sol") contractBody: String): SolContractDefinition {
    return createFromText(contractBody) ?: error("Failed to create contract: `$contractBody`")
  }

  private inline fun <reified T : SolElement> createFromText(code: String): T? =
    PsiFileFactory.getInstance(project)
      .createFileFromText("DUMMY.tsol", TSolidityFileType, code)
      .childOfType()
}
