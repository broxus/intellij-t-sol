package com.broxus.solidity.ide.refactoring

import com.broxus.solidity.utils.SolTestBase
import org.intellij.lang.annotations.Language

class RenameTest : SolTestBase() {
  fun testContractName() = doTest("Newname", """
        contract Abcd {
            function Abcd() {}
        }

        contract BBB {
            /*caret*/Abcd field;

            function doSomething(Abcd aaa) returns(Abcd) {
               Abcd aaaa = aaa;
               return aaaa;
            }
        }
    """, """
        contract Newname {
            function Newname() {}
        }

        contract BBB {
            Newname field;

            function doSomething(Newname aaa) returns(Newname) {
               Newname aaaa = aaa;
               return aaaa;
            }
        }
    """)

  fun testField() = doTest("myNewname", """
        contract BBB {
            Abcd /*caret*/myField;

            function doSomething(Abcd fff) returns(Abcd) {
               Abcd aaaa = myField;

               myField++;
               return myField;
            }
        }
    """, """
        contract BBB {
            Abcd myNewname;

            function doSomething(Abcd fff) returns(Abcd) {
               Abcd aaaa = myNewname;

               myNewname++;
               return myNewname;
            }
        }
    """)

  fun testLocal() = doTest("newname", """
        contract BBB {
            function doSomething() {
               BBB /*caret*/aaaa = myField;
               aaaa++;
               return aaaa;
            }
        }
    """, """
        contract BBB {
            function doSomething() {
               BBB newname = myField;
               newname++;
               return newname;
            }
        }
    """)

  fun testConstructor() = doTest("Newname", """
        contract BBB {
            function /*caret*/BBB() {
            }
        }
    """, """
        contract Newname {
            function Newname() {
            }
        }
    """)

  fun testFileRename() {
    val labFile = myFixture.configureByFile("imports/Lab.tsol")
    val importingFile = myFixture.configureByFile("imports/nested/ImportingFile.tsol")

    myFixture.renameElement(labFile, "AssetGatewayToken.tsol")
    myFixture.openFileInEditor(importingFile.virtualFile)
    myFixture.checkResultByFile("imports/nested/ImportingFile_after.tsol")
  }

  private fun doTest(
    newName: String,
    @Language("T-Sol") before: String,
    @Language("T-Sol") after: String
  ) {
    InlineFile(before).withCaret()
    myFixture.renameElementAtCaret(newName)
    myFixture.checkResult(after)
  }

  override fun getTestDataPath() = "src/test/resources/fixtures/refactoring/rename/"
}
