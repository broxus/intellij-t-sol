package com.broxus.solidity.ide.refactoring

import com.broxus.solidity.lang.TSolidityFileType
import com.broxus.solidity.lang.psi.SolContractDefinition
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.psi.PsiElement
import com.intellij.psi.search.SearchScope
import com.intellij.refactoring.rename.RenamePsiElementProcessor

class RenameContractProcessor : RenamePsiElementProcessor() {
  override fun canProcessElement(element: PsiElement): Boolean {
    return element is SolContractDefinition
  }

  override fun prepareRenaming(element: PsiElement, newName: String, allRenames: MutableMap<PsiElement, String>, scope: SearchScope) {
    val contract = element as SolContractDefinition
    val file = element.containingFile
    if (contract.name == file.virtualFile.nameWithoutExtension) {
      if (newName != "") {
        val renameContract = !allRenames.keys.contains(file) && MessageDialogBuilder.yesNo("Rename file", "Do you also want to rename the ${file.name} file?").ask(element.getProject())
        if (renameContract) {
          allRenames[file] = "$newName.${TSolidityFileType.defaultExtension}"
        }
      }
    }
    contract.functionDefinitionList
      .filter { it.isConstructor }
      .forEach { allRenames[it] = newName }
  }
}
