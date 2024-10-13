package com.broxus.solidity.lang.types

import com.broxus.solidity.ide.hints.TYPE_ARGUMENT_TAG
import com.broxus.solidity.ide.hints.tagComments
import com.broxus.solidity.lang.psi.*
import com.broxus.solidity.lang.psi.impl.Linearizable
import com.broxus.solidity.lang.resolve.SolResolver
import com.broxus.solidity.lang.types.SolInteger.Companion.UINT_160
import com.github.yuchi.semver.Range
import com.github.yuchi.semver.SemVer
import com.github.yuchi.semver.Version
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

  val isResolved: Boolean
    get() = this != SolUnknown && getRefs().all { it.type.isResolved }

  val isBuiltin: Boolean
    get() = true
}

interface SolUserType : SolType {
  override val isBuiltin: Boolean
    get() = false
}

interface SolPrimitiveType : SolType
interface SolNumeric : SolPrimitiveType

object SolNumericType : SolNumeric {
  override fun isAssignableFrom(other: SolType): Boolean {
    return other is SolNumeric
  }

  override fun toString(): String {
    return "<Numeric> Type"
  }

}

interface SolInternalType : SolType

data class SolTypeSequence(val types: List<SolType>) : SolType {
  override fun isAssignableFrom(other: SolType): Boolean {
    return when  {
      other is SolTypeSequence -> types.size == other.types.size && types.zip(other.types).all { it.first.isAssignableFrom(it.second) }
      types.size == 1 -> types[0].isAssignableFrom(other)
      else -> false
    }
  }

  override fun toString(): String {
    return "(${types.joinToString(separator = ",") { it.toString() }})"
  }
}

object SolTypeType/*(val value: SolType)*/ : SolInternalType {
  val supportedTypes = setOf(SolInteger::class, SolFixedBytes::class, SolBoolean::class, SolFixedNumber::class, SolAddress::class, SolContract::class, SolFixedBytes::class, SolString::class, SolMapping::class, SolArray::class, SolOptional::class, SolStruct::class)
  override fun isAssignableFrom(other: SolType): Boolean {
    return other is SolTypeType /*(other as? SolTypeType)?.value == value*/
  }

}

data class SolTypeError(val details: String) : SolInternalType {
  override fun isAssignableFrom(other: SolType): Boolean = false

}

object SolUnknown : SolPrimitiveType {
  override fun isAssignableFrom(other: SolType): Boolean = false

  override fun toString() = "<unknown>"
}

object SolNull : SolPrimitiveType {
  override fun isAssignableFrom(other: SolType): Boolean = false

  override fun toString() = "null"
}

object SolNaN : SolPrimitiveType {
  override fun isAssignableFrom(other: SolType): Boolean = false

  override fun toString() = "NaN"
}


object SolBoolean : SolPrimitiveType {
  override fun isAssignableFrom(other: SolType): Boolean =
    other == SolBoolean

  override fun toString() = "bool"
}

object SolQBoolean : SolPrimitiveType {
  override fun isAssignableFrom(other: SolType): Boolean =
    other == SolQBoolean || other == SolBoolean || other == SolNaN

  override fun toString() = "qbool"
}


object SolString : SolPrimitiveType {
  override fun isAssignableFrom(other: SolType): Boolean =
    other == SolString || (other as? SolContract)?.ref?.name == SolInternalTypeFactory::tvmSlice.name

  override fun getMembers(project: Project) = getSdkMembers(SolInternalTypeFactory.of(project).stringType)

  override fun toString() = "string"
}

data class SolOptional(val type: SolType) : SolType {
  override fun isAssignableFrom(other: SolType): Boolean =
    when (other) {
      is SolOptional -> other.type == type
      is SolNull -> true
      else -> other == type
    }

  override fun getRefs(): List<TypeRef> = listOf(TypeRef("T0", type))

  override fun getMembers(project: Project) = getSdkMembers(SolInternalTypeFactory.of(project).optionalType)

  override fun toString() = "optional${if (type is SolTypeSequence) "$type" else "($type)"}"

}

data class SolVector(val type: SolType) : SolType {
  override fun isAssignableFrom(other: SolType): Boolean {
    return (other as? SolVector)?.type == type
  }

  override fun getRefs(): List<TypeRef> = listOf(TypeRef("T0", type))

  override fun getMembers(project: Project) = getSdkMembers(SolInternalTypeFactory.of(project).vectorType)

  override fun toString() = "SolVector($type)"

}

data class SolStack(val type: SolType) : SolType {
  override fun isAssignableFrom(other: SolType): Boolean {
    return (other as? SolStack)?.type == type
  }

  override fun getRefs(): List<TypeRef> = listOf(TypeRef("T0", type))

  override fun getMembers(project: Project) = getSdkMembers(SolInternalTypeFactory.of(project).stackType)

  override fun toString() = "SolStack($type)"

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

data class SolFixedNumber(val unsigned: Boolean, val size: Int, val decimaSize : Int) : SolNumeric {
  override fun isAssignableFrom(other: SolType): Boolean =
          when (other) {
            is SolFixedNumber -> {
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

}

data class SolInteger(val unsigned: Boolean, val size: Int, val isVarType : Boolean = false, val isQuiet: Boolean = false) : SolNumeric {
  companion object {
    val UINT_160 = SolInteger(true, 160)
    val INT_256 = SolInteger(false, 256)
    val MAX_INT_TYPE = SolInteger(false, 258)
    val COINS = SolInteger(true, 16, true)
    private val int257Version = Version("0.74.0")

    fun parse(name: String, pragmaRange: Range? = null): SolInteger {
      var unsigned = false
      var varType = false
      var qType = false
      val size : Int
      var typeName = name
      if (typeName.startsWith("var")) {
        typeName = typeName.substring(3).replaceFirstChar { it.lowercase() }
        varType = true
      }
      if (typeName.startsWith("q")) {
        qType = true
        typeName = typeName.substring(1)
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
      } else {
        size = if (unsigned || pragmaRange == null || SemVer.isGreaterThenRange(int257Version, pragmaRange)) 256 else 257
      }
      return SolInteger(unsigned, size, varType, qType)
    }

    fun inferType(numberLiteral: SolNumberLiteral): SolInteger {
      return inferIntegerType(numberLiteral.toBigInteger(), numberLiteral)
    }

    private fun inferIntegerType(value: BigInteger, context: SolElement): SolInteger {
      val expType : SolInteger? = run {
        val parent = context.parent?.parent
        when (parent) {
          is SolFunctionCallArguments -> {
            parent.expressionList.indexOfFirst { it.descendants().any { it == context } }.takeIf { it >= 0 }?.let { index ->
              context.parentOfType<SolFunctionCallElement>()?.resolveDefinitions()?.takeIf { it.filter {it.resolveElement()?.tagComments(TYPE_ARGUMENT_TAG) == null }.map { it.parseParameters().getOrNull(index)?.second }.toSet().size == 1 }?.let {
                it.first().parseParameters().getOrNull(index)?.second as? SolInteger
              }
            }
          }
          is SolVariableDefinition -> {
            val type = getSolType(parent.variableDeclaration.typeName)
            (type as? SolInteger) ?: (type as? SolOptional)?.type as? SolInteger
          }
          else -> null
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
      is SolNaN -> this.isQuiet
      else -> false
    }

  override fun getRefs(): List<TypeRef> = if (isQuiet) listOf(TypeRef("T0", SolInteger(unsigned, size, isVarType, false))) else emptyList()

  override fun getMembers(project: Project): List<SolMember> = getSdkMembers(SolInternalTypeFactory.of(project).integerType).let { if (isQuiet) it + getSdkMembers(SolInternalTypeFactory.of(project).quietType) else it }

  override fun toString() = "${if (isQuiet) "q" else ""}${if (isVarType) "var" else ""}${if (unsigned) "u" else ""}int$size"
}

data class SolContract(val ref: SolContractDefinition, val builtin: Boolean = false) : SolUserType, Linearizable<SolContract> {
  override fun linearize(): List<SolContract> {
    return CachedValuesManager.getCachedValue(ref) {
        CachedValueProvider.Result.create(RecursionManager.doPreventingRecursion(ref, true) {super.linearize() } ?: emptyList(), PsiModificationTracker.MODIFICATION_COUNT)
      }
  }

  override fun linearizeParents(): List<SolContract> {
    return CachedValuesManager.getCachedValue(ref) {
        CachedValueProvider.Result.create(RecursionManager.doPreventingRecursion(ref, true) { super.linearizeParents() } ?: emptyList(), PsiModificationTracker.MODIFICATION_COUNT)
      }
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
      is SolString, is SolBytes -> this.ref.name.equals(SolInternalTypeFactory::tvmSlice.name, ignoreCase = true)
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
        SolFixedByte(name.substring(4).toIntOrNull() ?: 1)
      } else {
        throw java.lang.IllegalArgumentException("should start with 'byte'")
      }
    }
  }
}

data class SolMetaType(val type: SolType): SolType {
  override fun isAssignableFrom(other: SolType): Boolean {
    return other is SolMetaType && this.type == other.type
  }

  override fun getMembers(project: Project): List<SolMember> {
    return getSdkMembers(SolInternalTypeFactory.of(project).metaType)
  }

  override fun getRefs(): List<TypeRef> = listOf(TypeRef(this::type.name, type))

  override fun toString(): String = "type($type)"
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
