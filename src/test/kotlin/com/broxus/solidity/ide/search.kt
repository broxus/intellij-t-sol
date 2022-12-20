package com.broxus.solidity.ide

import com.broxus.solidity.lang.psi.SolNamedElement
import com.broxus.solidity.utils.SolTestBase
import org.intellij.lang.annotations.Language

class SolFindUsagesTest : SolTestBase() {
  fun testFindInheritance() = doTest("""
      contract A {}
             //^
      contract B is A {}
      contract C is A {}
  """, 2)

  fun testFields() = doTest("""
      contract A {}
             //^
      contract B {
          A field1;
          A field2;
      }
      contract C {
          A field1;
      }
    """, 3)

  private fun doTest(@Language("T-Sol") code: String, expectedUsages: Int) {
    InlineFile(code)
    val source = findElementInEditor<SolNamedElement>()
    val usages = myFixture.findUsages(source)
    assertEquals(expectedUsages, usages.size)
  }
}
