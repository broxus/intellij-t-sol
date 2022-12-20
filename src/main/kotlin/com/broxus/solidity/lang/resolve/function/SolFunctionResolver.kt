package com.broxus.solidity.lang.resolve.function

import com.broxus.solidity.ide.navigation.findAllImplementations
import com.broxus.solidity.lang.psi.SolContractDefinition
import com.broxus.solidity.lang.psi.SolFunctionDefinition
import com.broxus.solidity.lang.psi.impl.LinearizationImpossibleException
import com.broxus.solidity.lang.psi.parentOfType
import com.broxus.solidity.lang.types.SolContract
import com.broxus.solidity.lang.types.getSolType

object SolFunctionResolver {
  fun collectOverriden(func: SolFunctionDefinition): Collection<SolFunctionDefinition> {
    val contract = func.parentOfType<SolContractDefinition>() ?: return emptyList()
    val parents = try {
      SolContract(contract).linearizeParents().map { it.ref }
    } catch (e: LinearizationImpossibleException) {
      emptyList<SolContractDefinition>()
    }
    return parents
      .flatMap { it.functionDefinitionList }
      .filter { signatureEquals(func, it) }
  }

  fun collectOverrides(func: SolFunctionDefinition): Collection<SolFunctionDefinition> {
    val contract = func.parentOfType<SolContractDefinition>() ?: return emptyList()
    return contract.findAllImplementations()
      .flatMap { it.functionDefinitionList }
      .filter { signatureEquals(func, it) }
  }

  private fun signatureEquals(f1: SolFunctionDefinition, f2: SolFunctionDefinition): Boolean {
    if (f1.name != f2.name) {
      return false
    }
    if (f1.parameters.size != f2.parameters.size) {
      return false
    }
    for ((p1, p2) in f1.parameters.zip(f2.parameters)) {
      if (getSolType(p1.typeName) != getSolType(p2.typeName)) {
        return false
      }
    }
    return true
  }
}
