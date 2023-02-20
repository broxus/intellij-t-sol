package com.broxus.solidity.ide.annotation

import com.broxus.solidity.ide.SolidityIcons
import com.broxus.solidity.lang.TSolidityFileType
import com.broxus.solidity.lang.psi.ContractType
import com.broxus.solidity.lang.psi.SolContractDefinition
import com.broxus.solidity.lang.psi.SolEnumDefinition
import com.broxus.solidity.lang.psi.SolStructDefinition
import com.intellij.ide.FileIconPatcher
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import javax.swing.Icon

class TSolFileIconPatcher : FileIconPatcher {
  override fun patchIcon(baseIcon: Icon, file: VirtualFile, flags: Int, project: Project?): Icon {
    if (project == null || file.extension != TSolidityFileType.defaultExtension) {
      return baseIcon
    }
    val psiFile = PsiManager.getInstance(project).findFile(file) ?: return baseIcon
    val children = psiFile.children.filter { it is SolContractDefinition || it is SolStructDefinition || it is SolEnumDefinition }
    fun Boolean.toInt() = if (this) 1 else 0

    val hasContract = children.any { it is SolContractDefinition }
    val hasStruct = children.any { it is SolStructDefinition }
    val hasEnum = children.any { it is SolEnumDefinition }

    return if (hasContract.toInt() + hasStruct.toInt() + hasEnum.toInt() == 1) {
      when  {
        hasContract -> {
          val contracts = children.filterIsInstance<SolContractDefinition>()
          if (contracts.all { it.isAbstract }) SolidityIcons.ABSTRACT
          else {
            val types = contracts.map { it.contractType }
            when {
              types.all { it == ContractType.LIBRARY } -> SolidityIcons.LIBRARY
              types.all { it == ContractType.INTERFACE } -> SolidityIcons.INTERFACE
              else -> SolidityIcons.CONTRACT_FILE
            }
          }
        }
        hasStruct -> SolidityIcons.STRUCT_FILE
        hasEnum -> SolidityIcons.ENUM_FILE
        else -> SolidityIcons.FILE_ICON
      }
    } else SolidityIcons.FILE_ICON
  }
}
