package com.broxus.solidity.lang.completion

import com.broxus.solidity.ide.SolidityIcons
import com.broxus.solidity.ide.inspections.fixes.ImportFileAction
import com.broxus.solidity.lang.psi.*
import com.broxus.solidity.lang.psi.impl.getAliases
import com.broxus.solidity.lang.resolve.SolResolver
import com.broxus.solidity.lang.stubs.SolErrorIndex
import com.broxus.solidity.lang.stubs.SolEventIndex
import com.broxus.solidity.lang.stubs.SolGotoClassIndex
import com.broxus.solidity.lang.stubs.SolModifierIndex
import com.broxus.solidity.lang.types.*
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.completion.PrioritizedLookupElement
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex
import javax.swing.Icon

const val TYPED_COMPLETION_PRIORITY = 15.0

object SolCompleter {

  fun completeEventName(element: PsiElement): Array<out LookupElementBuilder> {
    val project = element.project
    val allTypeNames = StubIndex.getInstance().getAllKeys(
      SolEventIndex.KEY,
      project
    )
    return allTypeNames
      .map {
        LookupElementBuilder
          .create(it, it)
          .withIcon(SolidityIcons.EVENT)
      }
      .toTypedArray()
  }

  fun completeErrorName(element: PsiElement): Array<out LookupElementBuilder> {
    val project = element.project
    val allTypeNames = StubIndex.getInstance().getAllKeys(
      SolErrorIndex.KEY,
      project
    )
    return allTypeNames
      .map {
        LookupElementBuilder
          .create(it, it)
          .withIcon(SolidityIcons.ERROR)
      }
      .toTypedArray()
  }

  fun completeTypeName(element: PsiElement): Array<out LookupElement> {
    val project = element.project
    val allTypeNames = StubIndex.getInstance().getAllKeys(
      SolGotoClassIndex.KEY,
      project
    )


    val aliases = element.getAliases()

    return (allTypeNames
      .flatMap {
        StubIndex.getElements(SolGotoClassIndex.KEY, it, project, GlobalSearchScope.allScope(project), SolNamedElement::class.java)
      }
      .filterIsInstance<SolUserDefinedType>()
      .map { UserDefinedTypeLookupElement(it) }
      + aliases.mapNotNull { LookupElementBuilder.create(it.name ?: return@mapNotNull null).withIcon(it.getIcon(0)) })
      .toTypedArray()
  }

  fun completeModifier(element: SolModifierInvocationElement): Array<out LookupElement> {
    val project = element.project
    val allModifiers = StubIndex.getInstance().getAllKeys(
      SolModifierIndex.KEY,
      project
    )
    return allModifiers
      .map { LookupElementBuilder.create(it, it).withIcon(SolidityIcons.FUNCTION) }
      .toTypedArray()
  }

  fun completeLiteral(element: PsiElement): Sequence<LookupElement> {
    val lexicalDeclarations = SolResolver.lexicalDeclarations(element).mapNotNull {
      when (it) {
        is SolFunctionDefinition -> it.toFunctionLookup()
        is SolStructDefinition -> it.toStructLookup()
        else -> it.toVarLookup()
      }
    }.associateBy { it.lookupString }
    val keys = lexicalDeclarations.keys
    return lexicalDeclarations.values.asSequence() + completeTypeName(element).filterNot { keys.contains(it.lookupString) }
  }

  fun completeMemberAccess(element: SolMemberAccessExpression): Array<out LookupElement> {
    val expr = element.expression
    val contextType = when {
      expr is SolPrimaryExpression && expr.varLiteral?.name == "super" -> ContextType.SUPER
      expr.type.isBuiltin -> ContextType.BUILTIN
      element.childOfType<SolVarLiteral>()?.reference?.resolve()?.findContract()?.contractType == ContractType.LIBRARY -> ContextType.LIBRARY
      else -> ContextType.EXTERNAL
    }

    return element.getMembers()
      .mapNotNull {
        when (it.getPossibleUsage(contextType)) {
          Usage.CALLABLE -> {
            // could also be a builtin, com.broxus.solidity.lang.types.BuiltinCallable
            (it as? SolCallableElement ?: it.resolveElement() as? SolCallableElement)?.toFunctionLookup()
          }
          Usage.VARIABLE -> it.getName()?.let { name ->
            PrioritizedLookupElement.withPriority(
              LookupElementBuilder.create(name).withIcon(SolidityIcons.STATE_VAR),
              TYPED_COMPLETION_PRIORITY
            )
          }
          else -> null
        }
      }
      .distinctBy { it.lookupString }
      .toTypedArray()
  }

  private fun Sequence<SolNamedElement>.createVarLookups(): Sequence<LookupElement> = createVarLookups(SolidityIcons.STATE_VAR)

  private fun Sequence<SolNamedElement>.createVarLookups(icon: Icon): Sequence<LookupElement> = map {
    PrioritizedLookupElement.withPriority(
      LookupElementBuilder.create(it.name ?: "").withIcon(it.getIcon(0) ?: icon),
      TYPED_COMPLETION_PRIORITY
    )
  }
}

class UserDefinedTypeLookupElement(val type: SolUserDefinedType) : LookupElement() {
  private val typeName = type.name.let { tn ->
    "${type.parentOfType<SolContractDefinition>()?.name?.let { "$it." } ?: ""}$tn"
  }
  override fun getLookupString(): String = typeName

  override fun renderElement(presentation: LookupElementPresentation) {
    presentation.icon = type.getIcon(0)
    presentation.itemText = type.name
    presentation.typeText = "from ${type.containingFile.name}"
  }

  override fun handleInsert(context: InsertionContext) {
    if (!ImportFileAction.isImportedAlready(context.file, type.containingFile, typeName = (type.outerContract() ?: type).name)) {
      ImportFileAction.addImport(type.project, context.file, type.containingFile, type)
    }
  }
}
