package com.broxus.solidity.lang.types

import com.broxus.solidity.lang.psi.*
import com.broxus.solidity.lang.psi.impl.Linearizable
import com.broxus.solidity.lang.resolve.SolResolver
import com.broxus.solidity.lang.types.SolInteger.Companion.UINT_160
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.RecursionManager
import com.intellij.openapi.util.UserDataHolder
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.psi.util.descendants
import java.math.BigInteger
import java.util.*

// http://solidity.readthedocs.io/en/develop/types.html

enum class ContextType {
  SUPER,
  EXTERNAL,
  BUILTIN,
  LIBRARY
}

enum class Usage {
  VARIABLE,
  CALLABLE
}

interface SolMember : UserDataHolder {
  fun getName(): String?
  fun parseType(): SolType?
  fun resolveElement(): SolNamedElement?
  fun getPossibleUsage(contextType: ContextType): Usage?
}

data class TypeRef(val name: String, val type: SolType)

interface SolType {
  fun isAssignableFrom(other: SolType): Boolean
  fun getMembers(project: Project): List<SolMember> {
    return emptyList()
  }
  fun getRefs(): List<TypeRef> = emptyList()

  val isBuiltin: Boolean
    get() = true
}

interface SolUserType : SolType {
  override val isBuiltin: Boolean
    get() = false
}

interface SolPrimitiveType : SolType
interface SolNumeric : SolPrimitiveType

object SolUnknown : SolPrimitiveType {
  override fun isAssignableFrom(other: SolType): Boolean = false

  override fun toString() = "<unknown>"
}

object SolBoolean : SolPrimitiveType {
  override fun isAssignableFrom(other: SolType): Boolean =
    other == SolBoolean

  override fun toString() = "bool"
}

object SolString : SolPrimitiveType {
  override fun isAssignableFrom(other: SolType): Boolean =
    other == SolString || (other as? SolContract)?.ref?.name == SolInternalTypeFactory::tvmSlice.name

  override fun getMembers(project: Project) = getSdkMembers(SolInternalTypeFactory.of(project).stringType)

  override fun toString() = "string"
}

data class SolOptional(val types: List<SolType>) : SolType {
  override fun isAssignableFrom(other: SolType): Boolean =
    when (other) {
      is SolOptional -> other.types.size == this.types.size && other.types.mapIndexed { index, solType -> types[index].isAssignableFrom(solType) }.all { it }
      else -> types.size == 1 && types[0].isAssignableFrom(other)
    }

  override fun getRefs(): List<TypeRef> = types.mapIndexed {i, t -> TypeRef("T$i", t) }

  override fun getMembers(project: Project) = getSdkMembers(SolInternalTypeFactory.of(project).optionalType)

  override fun toString() = "optional(${types.joinToString { it.toString() }})"

}

data class SolVector(val types: List<SolType>) : SolType {
  override fun isAssignableFrom(other: SolType): Boolean =
    when (other) {
      is SolVector -> other.types.size == this.types.size && other.types.mapIndexed { index, solType -> types[index].isAssignableFrom(solType) }.all { it }
      else -> types.size == 1 && types[0].isAssignableFrom(other)
    }

  override fun getRefs(): List<TypeRef> = types.mapIndexed {i, t -> TypeRef("T$i", t) }

  override fun getMembers(project: Project) = getSdkMembers(SolInternalTypeFactory.of(project).vectorType)

  override fun toString() = "SolVector(${types.joinToString { it.toString() }})"

}

object SolAddress : SolPrimitiveType {
  override fun isAssignableFrom(other: SolType): Boolean =
    when (other) {
      is SolAddress -> true
      is SolContract -> true
      else -> UINT_160.isAssignableFrom(other)
    }

  override fun getMembers(project: Project) = getSdkMembers(SolInternalTypeFactory.of(project).addressType)

  override fun toString() = "address"

}

data class SolInteger(val unsigned: Boolean, val size: Int, val isVarType : Boolean = false) : SolNumeric {
  companion object {
    val UINT_160 = SolInteger(true, 160)
    val UINT_256 = SolInteger(true, 256)

    fun parse(name: String): SolInteger {
      var unsigned = false
      var varType = false
      var size = 256
      var typeName = name
      if (typeName.startsWith("var")) {
        typeName = typeName.substring(3).replaceFirstChar { it.lowercase() }
        varType = true
      }
      if (typeName.startsWith("u")) {
        unsigned = true
        typeName = typeName.substring(1)
      }
      if (!typeName.startsWith("int")) {
        throw IllegalArgumentException("Incorrect int typename: $name")
      }
      typeName = typeName.substring(3)
      if (typeName.isNotEmpty()) {
        try {
          size = Integer.parseInt(typeName)
        } catch (e: NumberFormatException) {
          throw IllegalArgumentException("Incorrect int typename: $name")
        }
      }
      return SolInteger(unsigned, size, varType)
    }

    fun inferType(numberLiteral: SolNumberLiteral): SolInteger {
      return inferIntegerType(numberLiteral.toBigInteger(), numberLiteral)
    }

    private fun inferIntegerType(value: BigInteger, context: SolElement): SolInteger {
      val expType : SolInteger? =
        (context.parent?.parent as? SolFunctionCallArguments)?.let { args ->
        args.expressionList.indexOfFirst { it.descendants().any { it == context } }.takeIf { it >= 0 }?. let { index ->
            context.parentOfType<SolFunctionCallElement>()?.resolveDefinitions()?.takeIf { it.map { it.parseParameters().getOrNull(index)?.second }.toSet().size == 1 }?.let {
              it.first().parseParameters().getOrNull(index)?.second as? SolInteger
            }
        }
      }
      return run {if (value == BigInteger.ZERO) return@run SolInteger(true, 8)
      val positive = value >= BigInteger.ZERO
      if (positive) {
        var shifts = 0
        var current = value
        while (current != BigInteger.ZERO) {
          shifts++
          current = current.shiftRight(8)
        }
        return@run SolInteger(positive, shifts * 8)
      } else {
        var shifts = 1
        var current = value.abs().minus(BigInteger.ONE).shiftRight(7)
        while (current != BigInteger.ZERO) {
          shifts++
          current = current.shiftRight(8)
        }
        return@run SolInteger(positive, shifts * 8)
      }
        }.let { if (expType != null && expType.canBeCoercedFrom(it, value)) expType else it}
    }

    private fun SolNumberLiteral.toBigInteger(): BigInteger {
      this.decimalNumber?.let {
        return it.text.replace("_", "").toBigInteger()
      }
      this.hexNumber?.let {
        return it.text.removePrefix("0x").toBigInteger(16)
      }
      this.scientificNumber?.let {
        return it.text.replace("_", "").lowercase().toBigDecimal().toBigInteger()
      }
      //todo
      return BigInteger.ZERO
    }
  }

  fun canBeCoercedFrom(other: SolInteger, value: BigInteger) : Boolean =
    (!unsigned || value >= BigInteger.ZERO) && this.size >= other.size

  override fun isAssignableFrom(other: SolType): Boolean =
    when (other) {
      is SolInteger -> {
        if (this.unsigned && !other.unsigned) {
          false
        } else if (!this.unsigned && other.unsigned) {
          this.size - 2 >= other.size
        } else {
          this.size >= other.size
        }
      }
      else -> false
    }

  override fun toString() = "${if (isVarType) "var" else ""}${if (unsigned) "u" else ""}int$size".let {
    if (isVarType) it.substring(0, 3) + it.substring(3).capitalize() else it
  }
}

data class SolContract(val ref: SolContractDefinition, val builtin: Boolean = false) : SolUserType, Linearizable<SolContract> {
  override fun linearize(): List<SolContract> {
    return RecursionManager.doPreventingRecursion(ref, true) {
      CachedValuesManager.getCachedValue(ref) {
        CachedValueProvider.Result.create(super.linearize(), PsiModificationTracker.MODIFICATION_COUNT)
      }
    } ?: emptyList()
  }

  override fun linearizeParents(): List<SolContract> {
    return RecursionManager.doPreventingRecursion(ref, true) {
      CachedValuesManager.getCachedValue(ref) {
        CachedValueProvider.Result.create(super.linearizeParents(), PsiModificationTracker.MODIFICATION_COUNT)
      }
    } ?: emptyList()
  }

  fun linearizeParentsOrNull(): List<SolContractDefinition>? {
    return runCatching { linearizeParents().map { it.ref } }.getOrNull()
  }

  override fun getParents(): List<SolContract> {
    return ref.supers
      .flatMap { it.reference?.multiResolve() ?: emptyList() }
      .filterIsInstance<SolContractDefinition>()
      .map { SolContract(it) }
      .reversed()
  }

  override fun isAssignableFrom(other: SolType): Boolean =
    when (other) {
      is SolContract -> {
        other.ref == ref
          || other.ref.collectSupers.flatMap { SolResolver.resolveTypeNameUsingImports(it) }.contains(ref)
      }
      is SolString, is SolBytes -> this.ref.name == SolInternalTypeFactory::tvmSlice.name
      else -> false
    }

  override fun getMembers(project: Project): List<SolMember> {
    return SolResolver.resolveContractMembers(ref, false)
  }

  override val isBuiltin get() = builtin

  override fun toString() = ref.name ?: ref.text ?: "$ref"
}

data class SolStruct(val ref: SolStructDefinition, val builtin : Boolean = false) : UserDataHolderBase(), SolUserType, SolMember {
  override fun isAssignableFrom(other: SolType): Boolean =
    other is SolStruct && ref == other.ref

  override fun toString() = ref.name ?: ref.text ?: "$ref"

  override fun getMembers(project: Project): List<SolMember> {
    return ref.variableDeclarationList
      .map { SolStructVariableDeclaration(it) } + getSdkMembers(SolInternalTypeFactory.of(project).structType)
  }

  override val isBuiltin: Boolean
    get() = builtin

  override fun getName(): String? = ref.name

  override fun parseType(): SolType = this

  override fun resolveElement(): SolNamedElement? = ref

  override fun getPossibleUsage(contextType: ContextType): Usage? = null
}

data class SolStructVariableDeclaration(
  val ref: SolVariableDeclaration
) : UserDataHolderBase(), SolMember {
  override fun getName(): String? = ref.name

  override fun parseType(): SolType = getSolType(ref.typeName)

  override fun resolveElement(): SolNamedElement? = ref

  override fun getPossibleUsage(contextType: ContextType) = Usage.VARIABLE
}

data class SolStructConstructor(val ref: SolStructDefinition) : UserDataHolderBase(), SolMember, SolCallable {
  override val callablePriority: Int = 0

  override fun getName(): String? = ref.name

  override fun parseType(): SolType? = ref.parseType()
  override fun parseParameters(): List<Pair<String?, SolType>> = ref.parseParameters()

  override fun resolveElement(): SolNamedElement? = ref

  override fun getPossibleUsage(contextType: ContextType): Usage? = Usage.CALLABLE

}

data class SolEnum(val ref: SolEnumDefinition) : UserDataHolderBase(), SolUserType, SolMember {
  override fun isAssignableFrom(other: SolType): Boolean =
    other is SolEnum && ref == other.ref

  override fun getName(): String? = ref.name

  override fun parseType(): SolType = this

  override fun resolveElement(): SolNamedElement? = ref

  override fun getPossibleUsage(contextType: ContextType): Usage? = null

  override fun toString() = ref.name ?: ref.text ?: "$ref"

  override fun getMembers(project: Project): List<SolMember> {
    return ref.enumValueList
  }
}

data class SolMapping(val from: SolType, val to: SolType) : SolType {
  override fun isAssignableFrom(other: SolType): Boolean =
    other is SolMapping && from == other.from && to == other.to

  override fun getRefs(): List<TypeRef> = listOf(TypeRef("KeyType", from), TypeRef("ValueType", to))

  override fun getMembers(project: Project) = getSdkMembers(SolInternalTypeFactory.of(project).mappingType)

  override fun toString(): String {
    return "mapping($from => $to)"
  }
}

data class SolTuple(val types: List<SolType>) : SolType {
  override fun isAssignableFrom(other: SolType): Boolean = false

  override fun toString(): String {
    return "(${types.joinToString(separator = ",") { it.toString() }})"
  }
}

sealed class SolArray(val type: SolType) : SolType {
  override fun getRefs(): List<TypeRef> = listOf(TypeRef(this::type.name, type))

  class SolStaticArray(type: SolType, val size: Int) : SolArray(type) {
    override fun isAssignableFrom(other: SolType): Boolean =
      other is SolStaticArray && other.type == type && other.size == size

    override fun toString() = "$type[$size]"

    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (javaClass != other?.javaClass) return false

      other as SolStaticArray

      if (size != other.size) return false
      if (type != other.type) return false
      return true
    }

    override fun hashCode(): Int {
      return Objects.hash(size, type)
    }

    override fun getMembers(project: Project) = SolInternalTypeFactory.of(project).arrayType.ref.stateVariableDeclarationList;
  }

  class SolDynamicArray(type: SolType) : SolArray(type) {
    override fun isAssignableFrom(other: SolType): Boolean =
      other is SolDynamicArray && type == other.type

    override fun toString() = "$type[]"

    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (javaClass != other?.javaClass) return false

      other as SolDynamicArray

      if (type != other.type) return false
      return true
    }

    override fun hashCode(): Int {
      return type.hashCode()
    }

    override fun getMembers(project: Project): List<SolMember> {
      return SolInternalTypeFactory.of(project).arrayType.ref.let {
        it.functionDefinitionList + it.stateVariableDeclarationList
      }
    }
  }
}

object SolBytes : SolPrimitiveType {
  override fun isAssignableFrom(other: SolType): Boolean =
    other == SolBytes || other == SolString || (other as? SolContract)?.ref?.name == SolInternalTypeFactory::tvmSlice.name

  override fun getMembers(project: Project) = getSdkMembers(SolInternalTypeFactory.of(project).bytesType)

  override fun toString() = "bytes"
}

data class SolFixedBytes(val size: Int): SolPrimitiveType {
  override fun toString() = "bytes$size"

  override fun isAssignableFrom(other: SolType): Boolean =
    other is SolFixedBytes && other.size <= size

  companion object {
    fun parse(name: String): SolFixedBytes {
      return if (name.startsWith("bytes")) {
        SolFixedBytes(name.substring(5).toInt())
      } else {
        throw java.lang.IllegalArgumentException("should start with bytes")
      }
    }
  }
}

data class SolFixedByte(val size: Int): SolPrimitiveType {
  override fun toString() = "byte$size"

  override fun isAssignableFrom(other: SolType): Boolean =
    other is SolFixedByte && other.size <= size

  companion object {

    val regex = "byte\\d*$".toRegex()
    fun parse(name: String): SolFixedByte {
      return if (name.startsWith("byte")) {
        SolFixedByte(name.substring(4).toInt())
      } else {
        throw java.lang.IllegalArgumentException("should start with 'byte'")
      }
    }
  }
}

private const val INTERNAL_INDICATOR = "_sol1_s"

fun internalise(name: String): String = "$name$INTERNAL_INDICATOR"

fun isInternal(name: String): Boolean = name.endsWith(INTERNAL_INDICATOR)

fun deInternalise(name: String): String = when {
  name.endsWith(INTERNAL_INDICATOR) -> name.removeSuffix(INTERNAL_INDICATOR)
  else -> name
}

private fun getSdkMembers(solContract: SolContract): List<SolMember> {
    return solContract.ref.let { it.functionDefinitionList + it.stateVariableDeclarationList }
}
