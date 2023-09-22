package com.broxus.solidity.ide.quickFix

import com.broxus.solidity.ide.inspections.ResolveNameInspection

class SolImportFileTest : SolQuickFixTestBase() {
  fun testImportFileFix() {
    myFixture.enableInspections(ResolveNameInspection().javaClass)

    InlineFile(
      code = "contract a {}",
      name = "a.tsol"
    )

    testQuickFix(
      "contract b is a {}",
      "\nimport {a} from \"./a.tsol\";contract b is a {}"
    )
  }

  // https://github.com/intellij-solidity/intellij-solidity/issues/64
  fun testNoImportFixPopup() {
    myFixture.enableInspections(ResolveNameInspection().javaClass)

    InlineFile(
      code = """
        contract A {
          struct MyStruct {}
        }
      """,
      name = "A.tsol"
    )

    InlineFile(
      code = """
        import "A.tsol";

        contract B is A {
        }
      """,
      name = "B.tsol"
    )

    assertNoQuickFix("""
      import "B.tsol";

      contract C is B {
         MyStruct A; //My struct is correctly imported as its part of B
      }
    """
    )
  }

}
