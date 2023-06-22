package com.broxus.solidity.ide.refactoring

import com.broxus.solidity.lang.core.SolidityFile
import com.broxus.solidity.lang.psi.SolContractOrLibElement
import com.intellij.lang.refactoring.RefactoringSupportProvider
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.psi.PsiElement
import com.intellij.psi.search.SearchScope
import com.intellij.refactoring.rename.RenamePsiFileProcessor

class SolRenameFileProcessor : RenamePsiFileProcessor() {
  override fun prepareRenaming(element: PsiElement, newName: String, allRenames: MutableMap<PsiElement, String>, scope: SearchScope) {
    (element as? SolidityFile)?.let { file ->
      val contract = file.children.find { it is SolContractOrLibElement && it.name == file.virtualFile.nameWithoutExtension && it.name != newName } as? SolContractOrLibElement
      if (contract != null) {
        val description = contract.contractType.docName
        val renameContract = /*!RefactoringSettings.getInstance().ASK_FOR_RENAME_DECLARATION_WHEN_RENAME_FILE ||*/ MessageDialogBuilder.yesNo("Rename $description", "Do you also want to rename the $description").ask(element.getProject())
        if (renameContract) {
          allRenames[contract] = newName
        }
      }
    }
    super.prepareRenaming(element, newName, allRenames, scope)
  }

  override fun isInplaceRenameSupported(): Boolean = true
}


class SolRenameFileRefactoringSupportProvider : RefactoringSupportProvider() {
  override fun isMemberInplaceRenameAvailable(element: PsiElement, context: PsiElement?): Boolean {
    return true
  }
}
