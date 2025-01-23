package com.broxus.solidity.lang.types

import com.broxus.solidity.firstOrElse
import com.broxus.solidity.ide.hints.TYPE_ARGUMENT_TAG
import com.broxus.solidity.ide.hints.comments
import com.broxus.solidity.lang.core.SolidityTokenTypes
import com.broxus.solidity.lang.psi.*
import com.broxus.solidity.lang.psi.impl.ResolveContext
import com.broxus.solidity.lang.psi.impl.SolFunctionDefMixin
import com.broxus.solidity.lang.psi.impl.SolMemberAccessElement
import com.broxus.solidity.lang.psi.impl.addContext
import com.broxus.solidity.lang.resolve.SolResolver
import com.broxus.solidity.lang.resolve.canBeApplied
import com.broxus.solidity.lang.resolve.ref.SolFunctionCallReference
import com.broxus.solidity.lang.resolve.ref.toLibraryFunDefinition
import com.broxus.solidity.lang.types.SolArray.SolDynamicArray
import com.broxus.solidity.lang.types.SolArray.SolStaticArray
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.RecursionManager
import com.intellij.psi.PsiElement
import com.intellij.psi.util.*
import kotlin.math.max

fun getSolType(type: SolTypeName?): SolType {
  return when (type) {
    is SolBytesArrayTypeName -> {
      if (type.bytesNumType.text == "bytes") {
        SolBytes
      } else {
        SolFixedBytes.parse(type.bytesNumType.text)
      }
    }
    is SolElementaryTypeName -> {
      when (val text = type.firstChild.text) {
        "bool" -> SolBoolean
        "qbool" -> SolQBoolean
        "string" -> SolString
        "address" -> SolAddress
        "address_std" -> SolAddressStd
        "coins" -> SolInteger.COINS
        else -> {
          if (text.matches(SolFixedByte.regex)) {
            SolFixedByte.parse(text)
          } else {
            try {
              SolInteger.parse(text, findPragmaVersion(type))
            } catch (e: IllegalArgumentException) {
              SolUnknown
            }
          }
        }
      }
    }
    is SolUserDefinedLocationTypeName ->
      type.userDefinedTypeName?.let { getSolTypeFromUserDefinedTypeName(it) } ?: SolUnknown
    is SolUserDefinedTypeName -> getSolTypeFromUserDefinedTypeName(type)
    is SolMappingTypeName -> when {
      type.typeNameList.size >= 2 -> SolMapping(
        getSolType(type.typeNameList[0]),
        getSolType(type.typeNameList[1])
      )
      else -> SolUnknown
    }
    is SolArrayTypeName -> {
      val sizeExpr = type.expression
      when {
        sizeExpr == null -> SolDynamicArray(getSolType(type.typeName))
        sizeExpr is SolPrimaryExpression && sizeExpr.firstChild is SolNumberLiteral ->
          SolStaticArray(getSolType(type.typeName), Integer.parseInt(sizeExpr.firstChild.text))
        else -> SolUnknown
      }
    }
    is SolOptionalTypeName -> SolOptional(type.typeNameList.foldTypes())
    is SolStackOrVectorTypeName -> type.typeNameList.foldTypes().let { t -> if (type.firstChild.text == "stack") SolStack(t) else SolVector(t) }
    else -> SolUnknown
  }
}

private fun List<SolTypeName>.foldTypes() = this.map { getSolType(it) }.let { if (it.size == 1) it[0] else SolTypeSequence(it) }

fun PsiElement.findResolveContext() : ResolveContext? {
  return this.parents(true).mapNotNull { it.getUserData(resolveContextKey) }.firstOrNull()
}

private fun getSolTypeFromUserDefinedTypeName(type: SolUserDefinedTypeName): SolType {
  val name = type.name
  if (name != null) {
    if (isInternal(name)) {
      val internalType = SolInternalTypeFactory.of(type.project).byName(name)
      return internalType ?: SolUnknown
    }
      fun resolveByComments(comments: List<PsiElement>, contextElement: SolFunctionDefinition?): SolType? =
      comments.indexOfFirst { it.text == TYPE_ARGUMENT_TAG }.takeIf { it >= 1 }
        ?.let { comments.getOrNull(it - 1)?.let { it.text.split("\n")[0] } }
        ?.let { resolveTypeArgument(name, it, type, contextElement) }
        ?.let {
          return it
        }
      (type.parentOfType<SolFunctionDefinition>(false)?.let { f -> resolveByComments(f.comments(), f) } ?:
      type.takeIf { it !is SolContractDefinition }?.parentOfType<SolContractDefinition>()?.let {
        resolveByComments(it.comments(), null)
      })?.let {
        return it
      }
  }

  val resolvedTypes = SolResolver.resolveTypeNameUsingImports(type)
  return resolvedTypes.asSequence()
    .map {
      when (it) {
        is SolContractDefinition -> SolContract(it)
        is SolStructDefinition -> SolStruct(it)
        is SolEnumDefinition -> SolEnum(it)
        is SolUserDefinedValueTypeDefinition -> getSolType(it.elementaryTypeName)
        is SolImportAlias -> getSolType(it.parentOfType<SolImportAliasedPair>()?.userDefinedTypeName)
        else -> null
      }
    }
    .filterNotNull()
    .firstOrElse(SolUnknown)
}

fun resolveTypeArgument(name: String, typeText: String, type: SolUserDefinedTypeName, baseElement: SolFunctionDefinition?): SolType? {
  data class TypeArg(val name: String, val ref: String? = null, val bound: String? = null)
  val types = typeText.split(",").map {
    return@map if (it.contains("=")) it.split("=").let { TypeArg(it[0].trim(), ref = it.getOrNull(1)?.trim()) }
    else it.split(":").let { TypeArg(it[0].trim(), bound = it.getOrNull(1)?.trim()) }
  }
  val expr = type.findResolveContext()?.expr?.get()
  return types.find { it.name == name }?.let { typeArg->
    return typeArg.ref?.let { ref ->
       expr?.type?.getRefs()?.find { it.name == ref }?.type
    } ?: baseElement?.let { baseElement ->
      val typeText2 = type.text
      type.findResolveContext()?.expr?.get()?.parent?.parent?.childOfType<SolFunctionCallArguments>()?.expressionList?.let { actArgs ->
        val parameters = baseElement.parameters
        parameters.mapIndexedNotNull { i, p -> i.takeIf { p.typeName.text == typeText2 } }.let { typedIndexes ->
          val varArgInd = parameters.indexOfFirst { it.identifier?.text == "varargs" }.takeIf { it >= 0 } ?: Int.MAX_VALUE
          val typedTypes = actArgs.mapIndexedNotNull { i, a -> a.takeIf { i in typedIndexes || varArgInd < i }?.let { RecursionManager.doPreventingRecursion(it, true) { inferExprType(it) } } }
          val resolved = resolveCommonType(typedTypes)
          val bound = typeArg.bound
          if (bound != null && resolved !is SolTypeError) {
              (SolInternalTypeFactory.of(type.project).builtinTypeByName(bound, typedTypes))?.let { b -> resolved.takeIf { b.isAssignableFrom(it) } ?: SolTypeError("Expect a sub type of $b, but $resolved found") } ?: resolved
          } else {
            resolved
          }
        }
      }
    } ?: SolUnknown
  }
}

fun resolveCommonType(typedTypes: List<SolType>) : SolType {
  if (typedTypes.isEmpty()) {
    return SolTypeError("No types inferred")
  }
  val groupBy = typedTypes.associateBy { it::class }
  if (groupBy.size > 1) {
    return SolTypeError("Incompatible types: ${groupBy.values}")
  }
  val type = typedTypes.first()
  return when (type) {
      is SolInteger -> typedTypes.maxBy { (it as SolInteger).size }
    else -> type
  }
}

fun inferDeclType(decl: SolNamedElement): SolType {
  return when (decl) {
    is SolDeclarationItem -> {
      val list = decl.findParent<SolDeclarationList>() ?: return SolUnknown
      val def = list.findParent<SolVariableDefinition>() ?: return SolUnknown
      val inferred = inferExprType(def.expression)
      val declarationItemList = list.declarationItemList
      val declIndex = declarationItemList.indexOf(decl)
      when (inferred) {
        is SolTypeSequence -> {
          // a workaround when declarations are not correctly resolved
          val hasTypeDeclarations = inferred.types.size * 2 == declarationItemList.size
          val index = if (hasTypeDeclarations) (declIndex - 1) / 2 else declIndex
          inferred.types.getOrNull(index) ?: SolUnknown
        }
        else -> SolUnknown
      }
    }
    is SolTypedDeclarationItem -> getSolType(decl.typeName)
    is SolVariableDeclaration -> {
      return if (decl.typeName == null || decl.typeName?.firstChild?.text == "var") {
        when (val parent = decl.parent) {
          is SolVariableDefinition -> inferExprType(parent.expression)
          else -> SolUnknown
        }
      } else getSolType(decl.typeName)
    }
    is SolContractDefinition -> SolContract(decl)
    is SolStructDefinition -> SolStruct(decl)
    is SolEnumDefinition -> SolEnum(decl)
    is SolEnumValue -> inferDeclType(decl.parent as SolNamedElement)
    is SolParameterDef -> getSolType(decl.typeName)
    is SolStateVariableDeclaration -> getSolType(decl.typeName)
    is SolImportAlias -> getSolType(decl.parentOfType<SolImportAliasedPair>()?.userDefinedTypeName)
    else -> SolUnknown
  }
}

fun inferRefType(ref: SolVarLiteral): SolType {
  return when (ref.name) {
    "this" -> {
      ref.findContract()
        ?.let { SolContract(it) } ?: SolUnknown
    }
    "super" -> SolUnknown
    else -> {
      val declarations = SolResolver.resolveVarLiteral(ref)
      return declarations.asSequence()
        .map { inferDeclType(it) }
        .filter { it != SolUnknown }
        .firstOrElse(SolUnknown)
    }
  }
}

inline fun <reified T : PsiElement> PsiElement.findParent(): T? {
  return this.ancestors
    .filterIsInstance<T>()
    .firstOrNull()
}

inline fun <reified T : PsiElement> PsiElement.findParentOrNull(): T? {
  return this.ancestors
    .filterIsInstance<T>()
    .firstOrNull()
}

fun PsiElement.findContract(): SolContractDefinition? = this.findParentOrNull()

fun inferExprType(expr: SolExpression?): SolType {
  return when (expr) {
    is SolPrimaryExpression -> {
      expr.varLiteral?.let { inferRefType(it) }
        ?: expr.booleanLiteral?.let { SolBoolean }
        ?: expr.stringLiteral?.let { SolString }
        ?: expr.numberLiteral?.let { SolInteger.inferType(it) }
        ?: expr.elementaryTypeName?.let { getSolType(it) }
        ?: SolUnknown
    }
    is SolPlusMinExpression -> getNumericExpressionType(
      inferExprType(expr.expressionList.firstOrNull()),
      inferExprType(expr.expressionList.secondOrNull())
    )
    is SolMultDivExpression -> getNumericExpressionType(
      inferExprType(expr.expressionList.firstOrNull()),
      inferExprType(expr.expressionList.secondOrNull())
    )
    is SolExponentExpression -> getNumericExpressionType(
      inferExprType(expr.expressionList.firstOrNull()),
      inferExprType(expr.expressionList.secondOrNull())
    )
    is SolFunctionCallExpression -> {
      (expr.firstChild as? SolNewExpression)?.let { getSolType(it.typeName)}
        ?: (expr.reference as SolFunctionCallReference)
        .resolveFunctionCall()
        .firstOrNull { it.canBeApplied(expr.functionCallArguments) }
        ?.let { (it as? SolFunctionDefMixin)?.parseType() ?: it.parseType() }
        ?: SolUnknown
    }
    is SolAndExpression,
    is SolOrExpression,
    is SolEqExpression,
    is SolCompExpression -> (expr as? SolExpressionListElement)?.expressionList?.let { if (it.any { it.type.let { it is SolInteger && it.isQuiet } }) SolQBoolean else SolBoolean } ?: SolBoolean
    is SolTernaryExpression -> inferExprType(expr.expressionList.secondOrNull())
    is SolIndexAccessExpression -> {
      when (val arrType = inferExprType(expr.expressionList.firstOrNull())) {
        is SolArray -> arrType.type
        is SolMapping -> arrType.to
        is SolBytes -> if (expr.children.any { it.elementType == SolidityTokenTypes.COLON }) SolBytes else SolFixedBytes(1)
        else -> SolUnknown
      }
    }
    is SolMemberAccessExpression -> {
      return SolResolver.resolveMemberAccess(expr)
        .firstOrNull()
        ?.parseType()
        ?: SolUnknown
    }
    is SolSeqExpression -> expr.expressionList.let {
      when (it.size) {
        0 -> SolUnknown
        1 -> inferExprType(it[0])
        else -> SolTypeSequence(it.map { inferExprType(it) })
      }
    }
    is SolUnaryExpression ->
      inferExprType(expr.expression).let {
        if (it is SolInteger && it.unsigned && expr.firstChild.elementType == SolidityTokenTypes.MINUS ) {
          SolInteger(false, it.size)
        } else it
      }
    is SolMetaTypeExpression -> SolMetaType(getSolType(expr.typeName))
    is SolNullExpression -> SolNull
    is SolNaNExpression -> SolNaN
    is SolInlineArrayExpression -> expr.childOfType<SolExpression>()?.type?.let { SolDynamicArray(it)} ?: SolUnknown
    else -> SolUnknown
  }
}

private fun <E> List<E>.secondOrNull(): E? {
  return if (size < 2) null else this[1]
}

private fun getNumericExpressionType(firstType: SolType, secondType: SolType): SolType {
  return if (firstType is SolInteger && secondType is SolInteger) {
    SolInteger(!(!firstType.unsigned || !secondType.unsigned), max(firstType.size, secondType.size))
  } else {
    SolUnknown
  }
}


val resolveContextKey = Key<ResolveContext>("broxus.ResolveContext")

fun SolMemberAccessExpression.getMembers(): List<SolMember> {
  val expr = expression
  return when {
    expr is SolPrimaryExpression && expr.varLiteral?.name == "super" -> {
      val contract = expr.findContract()
      contract?.let { SolResolver.resolveContractMembers(it, true) }
        ?: emptyList()
    }
    else -> {
      val fromLibraries = (this as? SolMemberAccessElement)?.collectUsingForLibraryFunctions() ?: emptyList()
      (expr.type.getMembers(this.project).partition { it is SolNamedElement }.let { it.second + SolInternalTypeFactory.of(this.project).getDeclarations(this, it.first as List<SolNamedElement>).toList() as List<SolMember>}
        + fromLibraries.map { it.toLibraryFunDefinition() }).onEach { it.addContext(expr) }
    }
  }
}

val SolExpression.type: SolType
  get() {
    if (!isValid) {
      return SolUnknown
    }
    if (ApplicationManager.getApplication().isUnitTestMode) {
      RecursionManager.disableMissedCacheAssertions {  }
    }
    return CachedValuesManager.getCachedValue(this) {
        val result = RecursionManager.doPreventingRecursion(this, false) {
          inferExprType(this)
        } ?: SolUnknown
        CachedValueProvider.Result.create(result, PsiModificationTracker.MODIFICATION_COUNT)
      }
  }

