package com.broxus.solidity.ide.formatting

import com.broxus.solidity.childrenOfType
import com.broxus.solidity.ide.inspections.fixes.ImportFileAction
import com.broxus.solidity.lang.TSolidityFileType
import com.broxus.solidity.lang.psi.*
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

    fun processFile(file: PsiFile, fullOptimization: Boolean): Runnable {
        val list = file.descendantsOfType<SolImportDirective>().toList().takeIf { fullOptimization || it.size > 1 } ?: return Runnable {}
        return if (fullOptimization) processFull(file, list) else processLight(file, list)
    }

    private fun processLight(file: PsiFile, list: List<SolImportDirective>): Runnable {
        val factory = PsiFileFactory.getInstance(file.project)

        val sorted = list.sortedBy { it.text }.zip(list).map {
            if (it.first != it.second) {
                factory.createFileFromText("DUMMY.tsol", TSolidityFileType, it.first.text).childOfType() ?: it.first
            } else it.first
        }
        return Runnable {
            list.zip(sorted).forEach {
                if (it.first != it.second) {
                    it.first.replace(it.second)
                }
            }
        }
    }
}

private fun processFull(file: PsiFile, list: List<SolImportDirective>): Runnable {
    val allTypes = SolResolver.collectImportedNames(file).map { it.target }
    val solFactory = SolPsiFactory(file.project)
    val imports = allTypes
            .mapNotNull {
                it.outerContract()?.let { parentContract ->
                    if (it is SolFunctionDefinition) return@mapNotNull null
                    return@mapNotNull Pair(it, parentContract.name)
                }
                Pair(it, it.name)
            }
            .groupBy { it.first.containingFile }
            .mapNotNull {
                val file1 = it.key.containingFile?.virtualFile ?: return@mapNotNull null
                val solUserDefinedTypeName = it.value.mapNotNull { it.second }.distinct()
                val to = file.virtualFile
                ImportFileAction.createImport(solFactory, solUserDefinedTypeName, file1, to)
            }

    return Runnable {
        val first = list.first()
        val parent = first.parent
        imports.reversed().forEach { parent.addAfter(it, first) }
        list.filterNot { it.importAliasedPairList.any { it.importAlias != null } }.forEach { it.delete() }
        parent.childrenOfType<SolImportDirective>().reversed().filter { SolResolver.collectUsedElements(it).isEmpty() }.forEach { it.delete() }
    }
}

