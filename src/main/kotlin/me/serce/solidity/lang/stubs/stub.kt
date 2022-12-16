package me.serce.solidity.lang.stubs

import com.intellij.psi.stubs.IStubElementType
import com.intellij.psi.stubs.StubElement
import me.serce.solidity.lang.TSolidityLanguage
import me.serce.solidity.lang.psi.SolElement

interface SolNamedStub {
  val name: String?
}

abstract class SolStubElementType<S : StubElement<*>, P : SolElement>(debugName: String)
  : IStubElementType<S, P>(debugName, TSolidityLanguage) {
  final override fun getExternalId(): String = "solidity.${super.toString()}"
}
