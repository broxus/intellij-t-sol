package com.broxus.solidity.lang.psi.impl

import com.broxus.solidity.firstInstance
import com.broxus.solidity.ide.SolidityIcons
import com.broxus.solidity.lang.core.SolidityTokenTypes.*
import com.broxus.solidity.lang.psi.*
import com.broxus.solidity.lang.resolve.SolResolver
import com.broxus.solidity.lang.resolve.ref.*
import com.broxus.solidity.lang.stubs.*
import com.broxus.solidity.lang.types.*
import com.broxus.solidity.wrap
import com.intellij.lang.ASTNode
import com.intellij.openapi.util.RecursionManager
import com.intellij.openapi.util.UserDataHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.stubs.IStubElementType
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.psi.util.nextLeaf
import javax.naming.OperationNotSupportedException
import javax.swing.Icon

open class SolImportPathElement : SolStubbedNamedElementImpl<SolImportPathDefStub>, SolReferenceElement {
  constructor(node: ASTNode) : super(node)
  constructor(stub: SolImportPathDefStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

  override val referenceNameElement: PsiElement
    get() = findChildByType(STRINGLITERAL)!!
  override val referenceName: String
    get() = referenceNameElement.text

  override fun getReference() = SolImportPathReference(this)
}

open class SolImportAliasMixin : SolStubbedNamedElementImpl<SolImportAliasDefStub>, SolNamedElement, SolReferenceElement {
  constructor(node: ASTNode) : super(node)
  constructor(stub: SolImportAliasDefStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)
  override fun getIcon(flags: Int): Icon? {
    return SolidityIcons.LIBRARY
  }
  override val referenceNameElement: PsiElement
    get() = findChildByType(IDENTIFIER)!!

  override val referenceName: String
    get() = referenceNameElement.text


  override fun getReference(): SolReference? {
    return super.getReference()
  }
}

abstract class SolEnumItemImplMixin : SolStubbedNamedElementImpl<SolEnumDefStub>, SolEnumDefinition, SolUserDefinedType {
  constructor(node: ASTNode) : super(node)
  constructor(stub: SolEnumDefStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

  override val contract: SolContractDefinition
    get() = ancestors.firstInstance()

  override fun getIcon(flags: Int) = SolidityIcons.ENUM
}

abstract class SolUserDefinedValueTypeDefMixin : SolStubbedNamedElementImpl<SolUserDefinedValueTypeDefStub>,
  SolUserDefinedValueTypeDefinition {
  constructor(node: ASTNode) : super(node)
  constructor(stub: SolUserDefinedValueTypeDefStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)
}

abstract class SolEnumValueMixin(node: ASTNode) : SolNamedElementImpl(node), SolEnumValue {
  override val contract: SolContractDefinition
    get() = ancestors.firstInstance()

  override fun resolveElement(): SolNamedElement? = this

  override fun parseType(): SolType {
    val def = parentOfType<SolEnumDefinition>()
    return def?.let { SolEnum(it) } ?: SolUnknown
  }

  override fun getPossibleUsage(contextType: ContextType) = Usage.VARIABLE
}

abstract class SolContractOrLibMixin : SolStubbedNamedElementImpl<SolContractOrLibDefStub>, SolContractDefinition {
  override val supers: List<SolUserDefinedTypeName>
    get() = inheritanceSpecifierList
      .mapNotNull { it.userDefinedTypeName }

  override val collectSupers: Collection<SolUserDefinedTypeName>
    get() =
      CachedValuesManager.getCachedValue(this) {
        val result = RecursionManager.doPreventingRecursion(this, true) {
          val collectedSupers = LinkedHashSet<SolUserDefinedTypeName>()
          collectedSupers.addAll(supers)
          collectedSupers.addAll(
                  supers.mapNotNull { it.reference?.resolve() }
                          .filterIsInstance<SolContractOrLibElement>()
                          .flatMap { it.collectSupers }
          )
          collectedSupers
        } ?: emptyList()
        CachedValueProvider.Result.create(result, PsiModificationTracker.MODIFICATION_COUNT)
    }

  constructor(node: ASTNode) : super(node)
  constructor(stub: SolContractOrLibDefStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

  override fun getIcon(flags: Int) = if (isAbstract) SolidityIcons.ABSTRACT else {
    when (contractType) {
      ContractType.COMMON -> SolidityIcons.CONTRACT_FILE
      ContractType.LIBRARY -> SolidityIcons.LIBRARY
      ContractType.INTERFACE -> SolidityIcons.INTERFACE
    }
  }

  override fun parseParameters(): List<Pair<String?, SolType>> {
    return listOf(Pair(null, SolAddress))
  }

  override fun parseType(): SolType {
    return SolContract(this)
  }

  override fun resolveElement() = this
  override val callablePriority = 1000


  override val isAbstract: Boolean
    get() = firstChild?.elementType == ABSTRACT
  override val contractType: ContractType
    get() {
      val typeEl = (if (isAbstract) firstChild?.nextLeaf { it !is PsiWhiteSpace } else firstChild) ?: return ContractType.COMMON
      return when (typeEl.elementType) {
        LIBRARY -> ContractType.LIBRARY
        INTERFACE -> ContractType.INTERFACE
        else -> ContractType.COMMON
      }
    }
}

interface SolConstructorOrFunctionDef {
  fun getBlock(): SolBlock?
}

abstract class SolConstructorDefMixin(node: ASTNode) : SolElementImpl(node), SolConstructorDefinition, SolConstructorOrFunctionDef {
  override val referenceNameElement: PsiElement
    get() = this

  override val referenceName: String
    get() = "constructor"

  override fun setName(name: String): PsiElement {
    throw OperationNotSupportedException("constructors don't have name")
  }

  override fun getReference(): SolReference? = references.firstOrNull()

  override fun getReferences(): Array<SolReference> {
    return findChildrenByType<SolModifierInvocation>(MODIFIER_INVOCATION)
      .map { SolModifierReference(this, it) }.toTypedArray()
  }

  override fun getIcon(flags: Int) = SolidityIcons.CONTRACT_FILE
}

abstract class SolFunctionDefMixin : SolStubbedNamedElementImpl<SolFunctionDefStub>, SolFunctionDefinition, SolConstructorOrFunctionDef {
  override val referenceNameElement: PsiElement
    get() = findChildByType(IDENTIFIER)!!

  override val referenceName: String
    get() = referenceNameElement.text

  override val modifiers: List<SolModifierInvocation>
    get() = findChildrenByType(MODIFIER_INVOCATION)

  override val parameters: List<SolParameterDef>
    get() = findChildByType<SolParameterList>(PARAMETER_LIST)
      ?.children
      ?.filterIsInstance(SolParameterDef::class.java)
      ?: emptyList()

  override fun parseParameters(): List<Pair<String?, SolType>> {
    return Companion.parseParameters(parameters)
  }

  override val callablePriority = 0

  override fun parseType(): SolType? {
    return this.returns?.parameterDefList?.let {
          when (it.size) {
            1 -> getSolType(it[0].typeName)
            else -> SolTypeSequence(it.map { def -> getSolType(def.typeName) })
          }
    }
  }

  override val visibility
    get() = functionVisibilitySpecifierList
      .map { it.text.uppercase() }
      .mapNotNull { safeValueOf<Visibility>(it) }
      .firstOrNull()
      ?: Visibility.PUBLIC

  override val inheritance: FunctionInheritance?
    get() = functionVisibilitySpecifierList.find { it.overrideSpecifier != null  || it.virtualSpecifier != null }?.let {
      if (it.overrideSpecifier != null) FunctionInheritance.OVERRIDE else FunctionInheritance.VIRTUAL
    }

  override fun getPossibleUsage(contextType: ContextType) =
    if (isPossibleToUse(contextType))
      Usage.CALLABLE
    else
      null

  private fun isPossibleToUse(contextType: ContextType): Boolean {
    val visibility = this.visibility
    return contextType == ContextType.LIBRARY ||
      (visibility != Visibility.PRIVATE
      && !(visibility == Visibility.EXTERNAL && contextType == ContextType.SUPER)
      && !(visibility == Visibility.INTERNAL && contextType == ContextType.EXTERNAL))
  }

  override fun resolveElement() = this

  override val returns: SolParameterList?
    get() = if (parameterListList.size == 2) {
      parameterListList[1]
    } else {
      null
    }

  override val contract: SolContractDefinition?
    get() = this.ancestors.asSequence()
      .filterIsInstance<SolContractDefinition>()
      .firstOrNull()

  override val isConstructor: Boolean
    get() = contract?.name == name

  constructor(node: ASTNode) : super(node)
  constructor(stub: SolFunctionDefStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

  override fun getIcon(flags: Int): Icon {
    return SolidityIcons.FUNCTION
  }
  override fun getName(): String? {
    return super.getName() ?: firstChild.elementType.takeIf { it in specialFunctionTypes }?.toString()
  }

  companion object {
    fun parseParameters(parameters: List<SolParameterDef>): List<Pair<String?, SolType>> {
      return parameters.map { it.identifier?.text to getSolType(it.typeName) }
    }
  }
}

fun <T: UserDataHolder> T.addContext(expr: SolExpression?): T {
  expr?.let { ex -> this.putUserData(resolveContextKey, ResolveContext(this.getUserData(resolveContextKey)?.expr ?: ThreadLocal<SolExpression>()).apply { this.expr.set(ex) }) }
  return this
}


data class ResolveContext(val expr: ThreadLocal<SolExpression>)

abstract class SolModifierDefMixin : SolStubbedNamedElementImpl<SolModifierDefStub>, SolModifierDefinition {
  override val contract: SolContractDefinition
    get() = ancestors.firstInstance()

  constructor(node: ASTNode) : super(node)
  constructor(stub: SolModifierDefStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

  override fun getIcon(flags: Int): Icon {
    return SolidityIcons.FUNCTION
  }
}

abstract class SolStateVarDeclMixin : SolStubbedNamedElementImpl<SolStateVarDeclStub>, SolStateVariableDeclaration {
  constructor(node: ASTNode) : super(node)
  constructor(stub: SolStateVarDeclStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

  override fun getIcon(flags: Int) = SolidityIcons.STATE_VAR

  override fun parseParameters(): List<Pair<String?, SolType>> = emptyList()

  override fun parseType(): SolType = getSolType(typeName)

  override fun getPossibleUsage(contextType: ContextType): Usage? {
    val visibility = this.visibility
    return when {
        contextType in setOf(ContextType.SUPER, ContextType.BUILTIN, ContextType.LIBRARY) || mutability == Mutability.CONSTANT -> Usage.VARIABLE
        contextType == ContextType.EXTERNAL && visibility == Visibility.PUBLIC -> Usage.CALLABLE
        else -> null
    }
  }

  override val callablePriority = 0

  override fun resolveElement() = this

  override val visibility
    get() = visibilityModifier?.text?.let { safeValueOf(it.uppercase()) } ?: Visibility.INTERNAL

  override val mutability: Mutability?
    get() = mutationModifier?.text?.let { safeValueOf(it.uppercase()) }

  override val visibilityModifier: SolVisibilityModifier?
    get() = visibilityModifierList.getOrNull(0)
  override val mutationModifier: SolMutationModifier?
    get() = mutationModifierList.getOrNull(0)
}

abstract class SolConstantVariableDeclMixin : SolStubbedNamedElementImpl<SolConstantVariableDeclStub>, SolConstantVariableDeclaration {
  constructor(node: ASTNode) : super(node)
  constructor(stub: SolConstantVariableDeclStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

  // TODO: does it need a separate icon?
  override fun getIcon(flags: Int): Icon {
    return SolidityIcons.STATE_VAR
  }
}

abstract class SolStructDefMixin : SolStubbedNamedElementImpl<SolStructDefStub>, SolStructDefinition, SolCallableElement, SolUserDefinedType {
  constructor(node: ASTNode) : super(node)
  constructor(stub: SolStructDefStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

  override fun getIcon(flags: Int): Icon {
    return SolidityIcons.STRUCT
  }

  override fun parseParameters(): List<Pair<String?, SolType>> {
    return variableDeclarationList
      .map { it.identifier?.text to getSolType(it.typeName) }


  }

  override fun parseType(): SolType {
    return SolStruct(this)
  }

  override fun resolveElement() = this

  override val callablePriority = 1000
}

abstract class SolFunctionCallMixin(node: ASTNode) : SolNamedElementImpl(node), SolFunctionCallElement, SolFunctionCallExpression {

  private fun getReferenceNameElement(expr: SolExpression): PsiElement {
    return when (expr) {
      is SolPrimaryExpression ->
        expr.varLiteral ?: expr.elementaryTypeName!!
      is SolMemberAccessExpression ->
        expr.identifier!!
      is SolFunctionCallExpression ->
        expr.firstChild
      is SolIndexAccessExpression ->
        expr.firstChild
      is SolNewExpression ->
        expr.typeName as PsiElement
      is SolSeqExpression ->
        expr.expressionList.firstOrNull()?.let { getReferenceNameElement(it) } ?: expr
      // unable to extract reference name element
      else -> expr
    }
  }

//  override val expression: SolExpression
//    get() = this.getExpression()

  override val referenceNameElement: PsiElement
    get() = getReferenceNameElement(expression)

  override val referenceName: String
    get() = referenceNameElement.text

  override fun getName(): String? = referenceName

  override fun getReference(): SolReference = SolFunctionCallReference(this as SolFunctionCallExpression)

  override val functionCallArguments: SolFunctionCallArguments
    get() = functionInvocation.functionCallArguments!!

  override fun resolveDefinitions(): List<SolCallable>? {
    return ((children.firstOrNull() as? SolMemberAccessExpression)?.let {
      SolResolver.resolveMemberAccess(it)
    } ?: SolResolver.resolveVarLiteralReference(this)).filterIsInstance<SolCallable>()
  }
}

abstract class SolModifierInvocationMixin(node: ASTNode) : SolNamedElementImpl(node), SolModifierInvocationElement {

  override val referenceNameElement: PsiElement
    get() = this.varLiteral
  override val referenceName: String
    get() = this.varLiteral.text

  override fun getReference(): SolReference = SolModifierReference(this, this)
}

abstract class SolVarLiteralMixin(node: ASTNode) : SolNamedElementImpl(node), SolVarLiteral {
  override val referenceNameElement: PsiElement
    get() = findChildByType(IDENTIFIER)!!

  override val referenceName: String
    get() = referenceNameElement.text

  override fun getReference(): SolReference = SolVarLiteralReference(this)
}

open class SolDeclarationItemMixin(node: ASTNode) : SolNamedElementImpl(node)

open class SolTypedDeclarationItemMixin(node: ASTNode) : SolNamedElementImpl(node)

abstract class SolVariableDeclarationMixin(node: ASTNode) : SolVariableDeclaration, SolNamedElementImpl(node) {
  override fun getIcon(flags: Int) = SolidityIcons.STATE_VAR
}

open class SolParameterDefMixin(node: ASTNode) : SolNamedElementImpl(node)

abstract class SolUserDefinedTypeNameImplMixin : SolStubbedElementImpl<SolTypeRefStub>, SolUserDefinedTypeName {
  constructor(node: ASTNode) : super(node)

  constructor(stub: SolTypeRefStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

  override fun getReference(): SolReference = SolUserDefinedTypeNameReference(this)

  override val referenceNameElement: PsiElement
    get() = findIdentifiers().last()

  override fun findIdentifiers(): List<PsiElement> =
    findChildrenByType(IDENTIFIER)

  override val referenceName: String
    get() = referenceNameElement.text

  override fun getParent(): PsiElement? = parentByStub

  override fun getName(): String? {
    return referenceNameElement.text
  }

  override fun setName(name: String): SolUserDefinedTypeNameImplMixin {
    referenceNameElement.replace(SolPsiFactory(project).createIdentifier(name))
    return this
  }
}

abstract class SolMemberAccessElement(node: ASTNode) : SolNamedElementImpl(node), SolMemberAccessExpression {
  override val referenceNameElement: PsiElement
    get() = findChildByType(IDENTIFIER)!!
  override val referenceName: String
    get() = referenceNameElement.text

  override fun getReference() = SolMemberAccessReference(this)

  fun collectUsingForLibraryFunctions(): List<SolFunctionDefinition> {
    val type = expression.type.takeIf { it != SolUnknown } ?: return emptyList()
    val contract = findContract()
    val superContracts = contract
      ?.collectSupers
      ?.flatMap { SolResolver.resolveTypeNameUsingImports(it) }
      ?.filterIsInstance<SolContractDefinition>()
      ?: emptyList()
    val libraries = (superContracts + contract.wrap())
      .flatMap { it.usingForDeclarationList }
      .filter {
        val usingType = it.type
        usingType == null || usingType == type
      }
      .partition { it.library != null }
    return libraries.first.distinct().flatMap { it.library!!.functionDefinitionList } + libraries.second.mapNotNull { it.freeFunc }
  }
}

abstract class SolNewExpressionElement(node: ASTNode) : SolNamedElementImpl(node), SolNewExpression {
  override val referenceNameElement: PsiElement
    get() = typeName ?: firstChild
  override val referenceName: String
    get() = referenceNameElement.text

  override fun getReference() = SolNewExpressionReference(this)
}

abstract class SolEventDefMixin : SolStubbedNamedElementImpl<SolEventDefStub>, SolEventDefinition, SolCallableElement {
  constructor(node: ASTNode) : super(node)
  constructor(stub: SolEventDefStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

  //todo add event args identifiers
  override fun parseParameters(): List<Pair<String?, SolType>> {
    return indexedParameterList?.indexedParamDefList
      ?.map { it.identifier?.text to getSolType(it.typeName) }
      ?: emptyList()
  }

  override fun parseType(): SolType {
    return SolUnknown
  }

  override fun resolveElement() = this

  override val callablePriority = 1000

  override fun getIcon(flags: Int) = SolidityIcons.EVENT
}

abstract class SolErrorDefMixin : SolStubbedNamedElementImpl<SolErrorDefStub>, SolErrorDefinition, SolCallableElement {
  constructor(node: ASTNode) : super(node)
  constructor(stub: SolErrorDefStub, nodeType: IStubElementType<*, *>) : super(stub, nodeType)

  //todo add error args identifiers
  override fun parseParameters(): List<Pair<String?, SolType>> {
    return indexedParameterList?.indexedParamDefList
      ?.map { it.identifier?.text to getSolType(it.typeName) }
      ?: emptyList()
  }

  override fun getNameIdentifier(): PsiElement? {
    // use the second identifier because "error" isn't a keyword but also an identifier
    return findChildrenByType<PsiElement>(IDENTIFIER).getOrNull(1)
  }

  override fun parseType(): SolType {
    return SolUnknown
  }

  override fun resolveElement() = this

  override val callablePriority = 1000

  override fun getIcon(flags: Int) = SolidityIcons.ERROR
}


abstract class SolUsingForMixin(node: ASTNode) : SolElementImpl(node), SolUsingForElement {
  override val type: SolType?
    get() {
      val list = getTypeNameList()
      return if (list.size > 1) {
        getSolType(list[1])
      } else {
        null
      }
    }
  override val library: SolContractDefinition?
    get() = SolResolver.resolveTypeNameUsingImports(getTypeNameList()[0] as SolUserDefinedTypeName)
      .filterIsInstance<SolContractDefinition>()
      .firstOrNull()

  override val freeFunc: SolFunctionDefinition?
    get() = SolResolver.resolveVarLiteral(getTypeNameList()[0] as SolUserDefinedTypeName).filterIsInstance<SolFunctionDefinition>().firstOrNull()
}

fun PsiElement.getAliases() = containingFile.children.asSequence()
  .filterIsInstance<SolImportDirective>()
  .flatMap { it.importAliasedPairList.mapNotNull { it.importAlias } +
    (it.importAlias?.takeIf { it.identifier.text != "*" }?.let { listOf(it) } ?: emptyList())
  }
