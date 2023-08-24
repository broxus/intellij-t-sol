package com.broxus.solidity.ide.formatting

import com.broxus.solidity.lang.TSolidityFileType
import com.broxus.solidity.lang.psi.SolImportDirective
import com.broxus.solidity.lang.psi.childOfType
import com.broxus.solidity.lang.resolve.SolResolver
import com.intellij.lang.ImportOptimizer
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.util.descendantsOfType

class SolImportOptimizer : ImportOptimizer {
  override fun supports(file: PsiFile) = file.fileType == TSolidityFileType

  override fun processFile(file: PsiFile): Runnable {
    return processFile(file, true)
  }

  fun processFile(file: PsiFile, removeUnused: Boolean): Runnable {
    val list = file.descendantsOfType<SolImportDirective>().toList().takeIf { it.size > 1 } ?: return Runnable{}
    val factory = PsiFileFactory.getInstance(file.project)

    val importedNames = SolResolver.collectImportedNames(file)

    val unused = if (removeUnused) list.filter { SolResolver.collectUsedElements(it, importedNames).isEmpty() } else emptyList()

    val usedList = list.filterNot { unused.contains(it) }

    val sorted = usedList.sortedBy { it.text }.zip(usedList).map {
      if (it.first != it.second) {
        factory.createFileFromText("DUMMY.tsol", TSolidityFileType, it.first.text).childOfType() ?: it.first
      } else it.first
    }
    return Runnable {
      unused.forEach { it.delete() }
      usedList.zip(sorted).forEach {
        if (it.first != it.second) {
          it.first.replace(it.second)
        }
      }
    }
  }
}
