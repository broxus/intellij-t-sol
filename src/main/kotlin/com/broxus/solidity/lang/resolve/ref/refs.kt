package com.broxus.solidity.lang.resolve.ref

import com.broxus.solidity.lang.completion.SolCompleter
import com.broxus.solidity.lang.psi.*
import com.broxus.solidity.lang.psi.impl.SolFunctionDefMixin
import com.broxus.solidity.lang.psi.impl.SolNewExpressionElement
import com.broxus.solidity.lang.resolve.SolResolver
import com.broxus.solidity.lang.resolve.canBeApplied
import com.broxus.solidity.lang.resolve.function.SolFunctionResolver
import com.broxus.solidity.lang.types.*
import com.broxus.solidity.wrap
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement

class SolUserDefinedTypeNameReference(element: SolUserDefinedTypeName) : SolReferenceBase<SolUserDefinedTypeName>(element), SolReference {
  override fun multiResolve(): Collection<PsiElement> {
    val parent = element.parent
    if (parent is SolNewExpressionElement) {
      return SolResolver.resolveNewExpression(parent)
    }
    return SolResolver.resolveTypeNameUsingImports(element).takeIf { it.isNotEmpty()} ?: run {
      val free = (parent as? SolUsingForDeclaration)?.freeFunc ?: return emptyList()
      return listOf(free)
    }
  }

  override fun getVariants() = SolCompleter.completeTypeName(element)
}

class SolVarLiteralReference(element: SolVarLiteral) : SolReferenceBase<SolVarLiteral>(element), SolReference {
  override fun multiResolve() = SolResolver.resolveVarLiteralReference(element)

  override fun getVariants() = SolCompleter.completeLiteral(element).toList().toTypedArray()
}

class SolModifierReference(
  element: SolReferenceElement,
  private val modifierElement: SolModifierInvocationElement
) : SolReferenceBase<SolReferenceElement>(element), SolReference {

  override fun calculateDefaultRangeInElement() = element.referenceNameElement.parentRelativeRange

  override fun multiResolve(): List<SolNamedElement> {
    val contract = modifierElement.findContract()!!
    val superNames: List<String> = (contract.collectSupers.map { it.name } + contract.name).filterNotNull()
    return SolResolver.resolveModifier(modifierElement)
      .filter { it.contract.name in superNames }
  }

  override fun getVariants() = SolCompleter.completeModifier(modifierElement)
}

class SolMemberAccessReference(element: SolMemberAccessExpression) : SolReferenceBase<SolMemberAccessExpression>(element), SolReference {
  override fun calculateDefaultRangeInElement(): TextRange {
    return element.identifier?.parentRelativeRange ?: super.calculateDefaultRangeInElement()
  }

  override fun multiResolve() : List<SolNamedElement> {
      val importAlias = element.childOfType<SolPrimaryExpression>()
        .let { it?.varLiteral?.let { varLiteral -> SolResolver.resolveAlias(varLiteral) } }
      if (importAlias != null && SolResolver.isAliasOfFile(importAlias)) {
        return when (element.parent is SolFunctionCallExpression) {
          true -> (element.parent.reference as SolFunctionCallReference).resolveFunctionCallAndFilter()
            .mapNotNull { it.resolveElement() }

          else -> SolResolver.collectContracts(importAlias)
            .filter { contract -> contract.name == element.identifier!!.text }
        }
      }
      return SolResolver.resolveMemberAccess(element).mapNotNull { it.resolveElement() }
    }

  override fun getVariants() = SolCompleter.completeMemberAccess(element)
}

class SolNewExpressionReference(val element: SolNewExpression) : SolReferenceBase<SolNewExpression>(element), SolReference {

  override fun calculateDefaultRangeInElement(): TextRange {
    return element.referenceNameElement.parentRelativeRange
  }

  override fun multiResolve(): Collection<PsiElement> {
    val types = SolResolver.resolveTypeNameUsingImports(element.referenceNameElement)
    return types
      .filterIsInstance(SolContractDefinition::class.java)
      .flatMap {
        val constructors = it.findConstructors()
        if (constructors.isEmpty()) {
          listOf(it)
        } else {
          constructors
        }
      }
  }
}

fun SolContractDefinition.findConstructors(): List<SolElement> {
  return if (this.constructorDefinitionList.isNotEmpty()) {
    this.constructorDefinitionList
  } else {
    this.functionDefinitionList
      .filter { it.name == this.name }
  }
}

class SolFunctionCallReference(element: SolFunctionCallExpression) : SolReferenceBase<SolFunctionCallExpression>(element), SolReference {
  override fun calculateDefaultRangeInElement(): TextRange {
    return element.referenceNameElement.parentRelativeRange
  }

  fun resolveFunctionCall(): Collection<SolCallable> {
    if (element.parent is SolRevertStatement) {
      return SolResolver.resolveTypeNameUsingImports(element).filterIsInstance<SolErrorDefinition>()
    }
    if (element.firstChild is SolPrimaryExpression) {
      val structs = SolResolver.resolveTypeNameUsingImports(element.firstChild).filterIsInstance<SolStructDefinition>()
      if (structs.isNotEmpty()) {
        return structs
      }
    }
    val resolved: Collection<SolCallable> = when (val expr = element.expression) {
      is SolPrimaryExpression -> {
        val regular = expr.varLiteral?.let { SolResolver.resolveVarLiteral(it) }
          ?.filter { it !is SolStateVariableDeclaration }
          ?.filterIsInstance<SolCallable>()
          ?: emptyList()
        val casts = resolveElementaryTypeCasts(expr)
        regular + casts
      }
      is SolMemberAccessExpression -> {
        resolveMemberFunctions(expr)
      }
      else ->
        emptyList()
    }
    return removeOverrides(resolved.groupBy { it.callablePriority }.entries.minByOrNull { it.key }?.value ?: emptyList())
  }

  private fun removeOverrides(callables: Collection<SolCallable>): Collection<SolCallable> {
    val test = callables.filterIsInstance<SolFunctionDefinition>().flatMap { SolFunctionResolver.collectOverriden(it) }.toSet()
    return callables
      .filter {
        when (it) {
          is SolFunctionDefinition -> !test.contains(it)
          else -> true
        }
      }
  }

  private fun resolveElementaryTypeCasts(expr: SolPrimaryExpression): Collection<SolCallable> {
    return expr.elementaryTypeName
      ?.let {
        val type = getSolType(it)
        object : SolCallable {
          override fun resolveElement(): SolNamedElement? = null
          override fun parseParameters(): List<Pair<String?, SolType>> = listOf(null to SolUnknown)
          override fun parseType(): SolType = type
          override val callablePriority: Int = 1000
          override fun getName(): String? = null
        }
      }
      .wrap()
  }

  private fun resolveMemberFunctions(expression: SolMemberAccessExpression): Collection<SolCallable> {
    val name = expression.identifier?.text
    val importAlias = expression.childOfType<SolPrimaryExpression>()
      .let { it?.varLiteral?.let { varLiteral -> SolResolver.resolveAlias(varLiteral) } }

    return if (importAlias != null && name != null) {
         if (expression.firstChild is SolPrimaryExpression) {
           SolResolver.collectContracts(importAlias).filter { contract -> contract.name == name }
         } else {
           val contractToLook = when (expression.firstChild) {
             is SolMemberAccessExpression -> expression.firstChild.lastChild.text
             is SolFunctionCallExpression -> expression.childOfType<SolMemberAccessExpression>()?.lastChild?.text
             else -> null
           }

           SolResolver.collectContracts(importAlias).filter { contract -> contract.name == contractToLook }.map {
             SolResolver.resolveContractMembers(it).filterIsInstance<SolCallable>()
               .filter { member -> member.getName() == name }
           }.flatten()
         }
       } else if (name != null) {
      expression.getMembers()
        .filterIsInstance<SolCallable>()
        .filter { it.getName() == name }
    } else {
      emptyList()
    }
  }

  override fun multiResolve(): Collection<PsiElement> {
    return resolveFunctionCallAndFilter()
      .mapNotNull { it.resolveElement() }
  }

  fun resolveFunctionCallAndFilter(): List<SolCallable> {
    return resolveFunctionCall()
      .filter { it.canBeApplied(element.functionCallArguments) }
  }
}

class LibraryFunDefinition(private val original: SolFunctionDefinition) : SolFunctionDefinition by original {
  override val parameters: List<SolParameterDef>
    get() = original.parameters.drop(1)


  override fun parseParameters(): List<Pair<String?, SolType>> {
    return SolFunctionDefMixin.parseParameters(parameters)
  }

  override fun equals(other: Any?): Boolean {
    if (other is LibraryFunDefinition) {
      return original == other.original
    }
    return super.equals(other)
  }

  override fun hashCode(): Int {
    return original.hashCode()
  }

}
fun SolFunctionDefinition.toLibraryFunDefinition(): SolFunctionDefinition {
  return LibraryFunDefinition(this)
}
