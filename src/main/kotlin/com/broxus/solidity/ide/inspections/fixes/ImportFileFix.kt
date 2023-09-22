package com.broxus.solidity.ide.inspections.fixes

import com.broxus.solidity.ide.inspections.fixes.ImportFileAction.Companion.buildImportPath
import com.broxus.solidity.lang.psi.SolReferenceElement
import com.broxus.solidity.lang.psi.SolUserDefinedTypeName
import com.broxus.solidity.lang.resolve.SolResolver
import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInspection.HintAction
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.LocalQuickFixOnPsiElement
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile

class ImportFileFix(element: SolReferenceElement) : LocalQuickFixOnPsiElement(element), HintAction, LocalQuickFix {
  override fun startInWriteAction(): Boolean = false

  override fun getFamilyName(): String = "Import file"

  override fun showHint(editor: Editor): Boolean {
    val element = startElement as SolReferenceElement?
    if (element != null) {
      val suggestions = SolResolver.resolveTypeName(element).toSet()
      val fixText: String? = when {
          suggestions.size == 1 -> {
            val importPath = buildImportPath(element.containingFile.virtualFile, suggestions.first().containingFile.virtualFile)
            "$familyName $importPath"
          }
          suggestions.isNotEmpty() -> familyName
          else -> null
      }
      return when {
        fixText != null -> {
          HintManager.getInstance().showQuestionHint(editor, fixText, element.textOffset, element.textRange.endOffset,
            ImportFileAction(editor, element.containingFile, suggestions)
          )
          true
        }
        else -> false
      }
    } else {
      return false
    }
  }

  override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
    val element = startElement as SolUserDefinedTypeName?
    return when {
        element != null -> when {
            !element.isValid || element.reference?.resolve() != null -> false
            else -> SolResolver.resolveTypeName(element).isNotEmpty()
        }
        else -> false
    }
  }

  override fun getText(): String = familyName

  override fun invoke(project: Project, file: PsiFile, element: PsiElement, endElement: PsiElement) {
    val suggestions = SolResolver.resolveTypeName(element as SolReferenceElement).toSet()
    if (suggestions.size == 1) {
      val suggestion = suggestions.first()
      ImportFileAction.addImport(project, file, suggestion.containingFile, suggestion)
    }
  }

  override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
    val element = startElement as SolUserDefinedTypeName?
    if (element != null) {
      val suggestions = SolResolver.resolveTypeName(element as SolReferenceElement).toSet()
      if (suggestions.size == 1) {
        val suggestion = suggestions.first()
        ImportFileAction.addImport(project, element.containingFile, suggestion.containingFile, suggestion)
      }
    }
  }
}
