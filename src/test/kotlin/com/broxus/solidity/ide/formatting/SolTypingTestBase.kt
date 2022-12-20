package com.broxus.solidity.ide.formatting

import com.broxus.solidity.utils.SolTestBase

abstract class SolTypingTestBase : SolTestBase() {
  protected fun doTest(c: Char = '\n') = checkByFile {
    myFixture.type(c)
  }
}
