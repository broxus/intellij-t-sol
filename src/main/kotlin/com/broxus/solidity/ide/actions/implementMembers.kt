package com.broxus.solidity.ide.actions

import com.broxus.solidity.lang.psi.*
import com.broxus.solidity.lang.resolve.function.SolFunctionResolver
import com.broxus.solidity.lang.types.getSolType
import com.intellij.codeInsight.CodeInsightActionHandler
import com.intellij.codeInsight.generation.ClassMemberWithElement
import com.intellij.codeInsight.generation.MemberChooserObject
import com.intellij.codeInsight.generation.MemberChooserObjectBase
import com.intellij.codeInsight.generation.actions.PresentableActionHandlerBasedAction
import com.intellij.ide.util.MemberChooserBuilder
import com.intellij.lang.CodeInsightActions
import com.intellij.lang.ContextAwareActionHandler
import com.intellij.lang.LanguageCodeInsightActionHandler
import com.intellij.lang.LanguageExtension
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.codeStyle.CodeStyleManager
import javax.swing.Icon


class ImplementMembersAction : PresentableActionHandlerBasedAction() {
  override fun getHandler(): CodeInsightActionHandler {
    return ImplementMethodsHandler()
  }

  override fun getLanguageExtension(): LanguageExtension<LanguageCodeInsightActionHandler> {
    return CodeInsightActions.IMPLEMENT_METHOD
  }
}

class ImplementMethodsHandler : ContextAwareActionHandler, LanguageCodeInsightActionHandler {

  override fun isAvailableForQuickList(editor: Editor, file: PsiFile, dataContext: DataContext): Boolean {
    return false
  }

  private fun SolParameterDef.signature() = getSolType(typeName).toString() + (name?.let { " $it" } ?: "")

  override fun invoke(project: Project, editor: Editor, file: PsiFile) {
    val context = findContextContract(editor, file) ?: return
    invoke(context)
  }

  fun invoke(context: SolContractDefinition) {
    val candidates = collectCandidates(context)
    val parents = mutableMapOf<SolContractDefinition, SolMemberElement<*>>()
    val chooser = MemberChooserBuilder<SolMemberElement<SolFunctionDefinition>>(context.project).createBuilder(
      candidates.map {
        val parent = it.el.contract?.let { c ->
          parents.computeIfAbsent(c) {
            SolMemberElement(c, null, c.name ?: "N/A", c.getIcon(0))
          }
        }
        val signiture = it.el.let { "${it.name}(${it.parameters.joinToString { it.signature() }})${it.returns?.let { " returns (${it.parameterDefList.joinToString { it.signature() }})" } ?: ""}" }
        SolMemberElement(it.el, parent, signiture, it.el.getIcon(0))
      }.toTypedArray()
      )
    chooser.show()
    chooser.selectedElements?.takeIf { it.isNotEmpty() }?.let {
      runWriteAction {
        implementMembers(it, context)
      }
      }
  }

  private fun implementMembers(candidates: List<SolMemberElement<SolFunctionDefinition>>, context: SolContractDefinition) {
    val factory = SolPsiFactory(context.project)
    val start = context.lastChild.textOffset
    candidates.forEach {
      val func = context.addBefore(factory.createFunction("${it.el.text.replace(";", "")} {\n}"), context.lastChild)
      context.addAfter(factory.createNewLine(), func)
    }
    CodeStyleManager.getInstance(context.project).reformatText(
        context.containingFile,
        start,
        context.lastChild.endOffset()
    )
  }

  private fun collectCandidates(context: SolContractDefinition): List<CandidateInfo> {
    return SolFunctionResolver.collectNotImplemented(context).map { CandidateInfo(it) }
  }


  override fun isValidFor(editor: Editor, file: PsiFile?): Boolean {
    return findContextContract(editor, file) != null
  }

  private fun findContextContract(editor: Editor, file: PsiFile?) : SolContractDefinition? {
    val offset = editor.caretModel.offset
    val element = (file ?: return null).findElementAt(offset)?.parentOfType<SolContractDefinition>() ?: return null
    return element.takeIf { it.contractType != ContractType.INTERFACE }
  }

}

private data class CandidateInfo(val el: SolFunctionDefinition)

private class SolMemberElement<T : PsiElement>(val el: T, val parent: SolMemberElement<*>?, text: String, icon: Icon) : MemberChooserObjectBase(text, icon), ClassMemberWithElement {
  override fun getParentNodeDelegate(): MemberChooserObject {
    return parent ?: this
  }

  override fun getElement(): PsiElement = el
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as SolMemberElement<*>

    if (el != other.el) return false
    return parent == other.parent
  }

  override fun hashCode(): Int {
    var result = el.hashCode()
    result = 31 * result + (parent?.hashCode() ?: 0)
    return result
  }

}

