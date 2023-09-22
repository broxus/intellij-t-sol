package com.broxus.solidity.lang.completion

class SolIdentifierCompletionTest : SolCompletionTestBase() {
  fun testVariableCompletion() = checkCompletion(hashSetOf("Foo", "FooBar"), """
    contract Foo {}
    contract FooBar {}

    contract A {
        function f() {
            Fo/*caret*/ kk;
        }
    }

  """)

  fun testCompletionWithImport() {
    InlineFile(
      code = "contract test {}",
      name = "test.tsol"
    )

    InlineFile("""

    contract A is tes/*caret*/{}""").withCaret()
    myFixture.completeBasic()
    myFixture.checkResult("""import {test} from "./test.tsol";

contract A is test{}""")
  }

  fun testCompletionWithImportRecursion() {
    InlineFile(
      code = """contract test {}""",
      name = "test.tsol"
    )

    InlineFile(
      code = """import "./rec2.tsol"; contract rec1 {}""",
      name = "rec1.tsol"
    )

    InlineFile(
      code = """import "./rec1.tsol"; contract rec2 {}""",
      name = "rec2.tsol"
    )

    InlineFile("""import "./rec1.tsol"; contract A is tes/*caret*/{}""")

    myFixture.completeBasic()
    checkResult("""import "./rec1.tsol";
import {test} from "./test.tsol";

contract A is test{}""")
  }
}
