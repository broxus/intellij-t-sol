package com.broxus.solidity.ide.inspections.fixes

import com.broxus.solidity.ide.formatting.SolImportOptimizer
import com.broxus.solidity.lang.psi.*
import com.broxus.solidity.nullIfError
import com.intellij.codeInsight.hint.QuestionAction
import com.intellij.ide.util.DefaultPsiElementCellRenderer
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.openapi.util.RecursionManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.ui.popup.list.ListPopupImpl
import com.intellij.ui.popup.list.PopupListElementRenderer
import java.awt.BorderLayout
import java.nio.file.Paths
import javax.swing.Icon
import javax.swing.JPanel
import javax.swing.ListCellRenderer

class ImportFileAction(
        val editor: Editor,
        private val file: PsiFile,
        private val suggestions: Set<SolNamedElement>
) : QuestionAction {

  val project: Project
    get() = file.project

  override fun execute(): Boolean {
    PsiDocumentManager.getInstance(project).commitAllDocuments()

    if (suggestions.size == 1) {
      val suggestion = suggestions.first()
      addImport(project, file, suggestion.containingFile, suggestion)
    } else {
      chooseFileToImport()
    }

    return true
  }

  private fun chooseFileToImport() {
    val step = object : BaseListPopupStep<PsiFile>("File to Import", suggestions.toMutableList().map { it.containingFile }) {
      override fun isAutoSelectionEnabled(): Boolean {
        return false
      }

      override fun isSpeedSearchEnabled(): Boolean {
        return true
      }

      override fun onChosen(selectedValue: PsiFile?, finalChoice: Boolean): PopupStep<*>? {
        if (selectedValue == null) {
          return PopupStep.FINAL_CHOICE
        }

        return doFinalStep {
          PsiDocumentManager.getInstance(project).commitAllDocuments()
          addImport(project, file, selectedValue)
        }
      }

      override fun hasSubstep(selectedValue: PsiFile?): Boolean {
        return true
      }

      override fun getTextFor(value: PsiFile): String {
        return value.name
      }

      override fun getIconFor(aValue: PsiFile): Icon? {
        return aValue.getIcon(0)
      }
    }

    val popup = object : ListPopupImpl(project, step) {
      override fun getListElementRenderer(): ListCellRenderer<PsiFile> {
        @Suppress("UNCHECKED_CAST")
        val baseRenderer = super.getListElementRenderer() as PopupListElementRenderer<PsiFile>
        val psiRenderer = DefaultPsiElementCellRenderer()
        return ListCellRenderer { list, value, index, isSelected, cellHasFocus ->
          val panel = JPanel(BorderLayout())
          baseRenderer.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
          panel.add(baseRenderer.nextStepLabel, BorderLayout.EAST)
          panel.add(psiRenderer.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus))
          panel
        }
      }
    }
    popup.showInBestPositionFor(editor)
  }

  companion object {
    fun isImportedAlready(file: PsiFile, to: PsiFile, recursive : Boolean = true, typeName : String? = null): Boolean {
      return if (file == to) {
        true
      } else if (recursive) {
        RecursionManager.doPreventingRecursion(file, true) {
          file.children
            .filterIsInstance<SolImportDirective>()
            .mapNotNull {import -> nullIfError {import.importPath?.reference?.resolve()?.containingFile }?.let { Pair( it, import.importAliasedPairList.isEmpty()) }
                    .also { if (typeName != null && import.importAliasedPairList.let { it.isNotEmpty() && it.none { it.userDefinedTypeName.name == typeName } } ) return@mapNotNull null }
            }
            .any {
              isImportedAlready(it.first, to, it.second)
            }
        } ?: false
      } else false
    }

    fun addImport(project: Project, file: PsiFile, to: PsiFile, solUserDefinedTypeName: SolNamedElement? = null) {
      CommandProcessor.getInstance().runUndoTransparentAction {
        ApplicationManager.getApplication().runWriteAction {
          val after = file.children.filterIsInstance<SolImportDirective>().lastOrNull()
            ?: file.children.filterIsInstance<SolPragmaDirective>().lastOrNull()
          val factory = SolPsiFactory(project)
          file.addAfter(createImport(factory, solUserDefinedTypeName?.let { (it.outerContract() ?: it).name?.let { listOf(it) }} ?: emptyList(), to.virtualFile, file.virtualFile), after)
          file.addAfter(factory.createNewLine(), after)
          SolImportOptimizer().processFile(file, false).run()
        }
      }
    }

    fun createImport(factory: SolPsiFactory, solUserDefinedTypeName: List<String>, file: VirtualFile, to: VirtualFile): SolImportDirective {
      val content = "${(solUserDefinedTypeName.takeIf { it.isNotEmpty() }?.let { "{${it.joinToString(", ")}} from " } ?: "")}\"${buildImportPath(to, file)}\""
      return factory.createImportDirective(content)
    }

    fun buildImportPath(source: VirtualFile, destination: VirtualFile): String {
      return Paths.get(source.path).parent.relativize(Paths.get(destination.path)).toString().let {
        val separator = "/"
        when {
            it.contains("node_modules$separator") -> {
              val idx = it.indexOf("node_modules$separator")
              it.substring(idx + "node_modules$separator".length)
            }
            it.contains("lib$separator") -> {
              val idx = it.indexOf("lib$separator")
              it.substring(idx + "lib$separator".length)
            }
            it.contains("installed_contracts$separator") -> {
              val idx = it.indexOf("installed_contracts$separator")
              it.substring(idx + "installed_contracts$separator".length)
                .replaceFirst("${separator}contracts${separator}", separator)
            }
            !it.startsWith(".") -> ".$separator$it"
            else -> it
        }.replace("\\", "/")
      }
    }
  }
}
