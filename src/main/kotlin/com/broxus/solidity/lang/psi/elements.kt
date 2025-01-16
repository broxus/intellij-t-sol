package com.broxus.solidity.lang.psi

import com.broxus.solidity.lang.core.SolidityTokenTypes
import com.broxus.solidity.lang.resolve.ref.SolReference
import com.broxus.solidity.lang.types.SolMember
import com.broxus.solidity.lang.types.SolType
import com.broxus.solidity.lang.types.findContract
import com.intellij.psi.NavigatablePsiElement
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.PsiReference
import com.intellij.psi.util.findParentOfType

interface SolElement : PsiElement {
  override fun getReference(): PsiReference?
}

// Unable to move this inside the interface due to the `$DefaultImpls not found` error in runtime
fun SolNamedElement.outerContract() = parent?.takeIf { it is SolUserDefinedType }?.findContract()

fun SolNamedElement.outerContractOrSelf() = findParentOfType<SolContractDefinition>(false)


interface SolNamedElement : SolElement, PsiNamedElement, NavigatablePsiElement {
}

enum class Visibility {
  PRIVATE,
  INTERNAL,
  PUBLIC,
  EXTERNAL
}

enum class Mutability {
  PURE, CONSTANT, VIEW, PAYABLE, RESPONSIBLE;
}

enum class ContractType(val docName: String) {
  COMMON("contract"), LIBRARY("library"), INTERFACE("interface")
}

interface SolUserDefinedType : SolNamedElement

interface SolCallable {
  val callablePriority: Int
  fun getName(): String?
  fun parseType(): SolType?
  fun parseParameters(): List<Pair<String?, SolType>>
  fun resolveElement(): SolNamedElement?
}

interface SolCallableElement : SolCallable, SolNamedElement

interface SolStateVarElement : SolMember, SolCallableElement {
  val visibilityModifier: SolVisibilityModifier?
  val visibility: Visibility

  val mutationModifier: SolMutationModifier?
  val mutability: Mutability?
}

interface SolConstantVariable : SolNamedElement {}

val specialFunctionTypes = setOf(SolidityTokenTypes.RECEIVE, SolidityTokenTypes.FALLBACK,
  SolidityTokenTypes.ONBOUNCE, SolidityTokenTypes.ONTICKTOCK)

enum class FunctionInheritance { OVERRIDE, VIRTUAL}

interface SolFunctionDefElement : SolHasModifiersElement, SolMember, SolCallableElement {
  /** The contract can be null in the case of free functions. */
  val contract: SolContractDefinition?
  val modifiers: List<SolModifierInvocation>
  val parameters: List<SolParameterDef>
  val returns: SolParameterList?
  val isConstructor: Boolean
  val visibility: Visibility
  val inheritance: FunctionInheritance?
}

inline fun <reified T : Enum<*>> safeValueOf(name: String): T? =
  T::class.java.enumConstants.firstOrNull { it.name == name }

interface SolFunctionCallElement : SolReferenceElement {
  val expression: SolExpression?
  val functionCallArguments: SolFunctionCallArguments

  fun resolveDefinitions() : List<SolCallable>?
}

interface SolModifierInvocationElement : SolReferenceElement {
  val varLiteral: SolVarLiteral
  val functionCallArguments: SolFunctionCallArguments
}

interface SolEnumDefElement : SolNamedElement {
  val contract: SolContractDefinition
}

interface SolUserDefinedValueTypeDefElement : SolNamedElement

interface SolEnumItemElement : SolEnumDefElement, SolMember

interface SolModifierElement : SolNamedElement {
  val contract: SolContractDefinition
}

interface SolContractOrLibElement : SolCallableElement, SolUserDefinedType {
  val supers: List<SolUserDefinedTypeName>
  val collectSupers: Collection<SolUserDefinedTypeName>
  val isAbstract: Boolean
  val contractType: ContractType
}

interface SolReferenceElement : SolNamedElement {
  val referenceNameElement: PsiElement
  val referenceName: String

  override fun getReference(): SolReference?
}

interface SolUserDefinedTypeNameElement : SolReferenceElement {
  fun findIdentifiers(): List<PsiElement>
}

interface SolHasModifiersElement : SolReferenceElement

interface SolUsingForElement : PsiElement {
  val type: SolType?
  val library: SolContractDefinition?
  val freeFunc: SolFunctionDefinition?
  fun getTypeNameList(): List<SolTypeName>
}

interface SolExpressionListElement {
  val expressionList: List<SolExpression>
}
