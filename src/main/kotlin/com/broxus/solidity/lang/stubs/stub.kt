package com.broxus.solidity.lang.stubs

import com.intellij.psi.stubs.IStubElementType
import com.intellij.psi.stubs.StubElement
import com.broxus.solidity.lang.TSolidityLanguage
import com.broxus.solidity.lang.psi.SolElement

interface SolNamedStub {
  val name: String?
}

abstract class SolStubElementType<S : StubElement<*>, P : SolElement>(debugName: String)
  : IStubElementType<S, P>(debugName, TSolidityLanguage) {
  final override fun getExternalId(): String = "t-sol.${super.toString()}"
}
