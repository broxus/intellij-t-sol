package me.serce.solidity.lang.core.resolve

import junit.framework.TestCase
import me.serce.solidity.lang.psi.SolContractDefinition
import me.serce.solidity.lang.psi.SolExpression
import me.serce.solidity.lang.psi.SolStructDefinition
import me.serce.solidity.lang.types.*
import org.intellij.lang.annotations.Language

class InferenceTest : SolResolveTestBase() {

  fun testCyclicImportsDontCrashIDE() {
    InlineFile(
      code = """
         import "./base2.tsol";
         contract Contract1 {
                  //x

         }
      """,
      name = "base1.tsol"
    )
    InlineFile(
      code = """
         import "./base1.tsol";
         contract Contract2 {
                  //x

         }
      """,
      name = "base2.tsol"
    )

    checkType(SolAddress, """
        import './base1.sol';
        contract Test  {
            function test() {
                var test = address(this);
                test;
               //^
            }
        }""")
  }

  fun testCastContract() {
    InlineFile(
      code = """
         contract Contract {
                  //x

         }
      """,
      name = "base.tsol"
    )

    val (contract, _) = findElementAndDataInEditor<SolContractDefinition>("x")
    checkType(SolContract(contract), """
        import './base.tsol';
        contract Test  {
            function test() {
                var test = Contract(address(this));
                test;
               //^
            }
        }""")
  }

  fun testNegateBoolean() {
    checkType(SolBoolean, """
        contract Contract {
            function test() {
                var test = !true;
                test;
               //^
            }
        }""")
  }

  fun testCastAddress() {
    checkType(SolAddress, """
        contract Contract {
            function test() {
                var test = address(this);
                test;
               //^
            }
        }""")
  }

  fun testStorageStruct() {
    InlineFile(
      code = """
         contract StructBase {
           struct Struct {}
                  //x
         }
      """,
      name = "base.tsol"
    )

    val (struct, _) = findElementAndDataInEditor<SolStructDefinition>("x")
    checkType(SolStruct(struct), """
        import './base.tsol';
        contract Contract is StructBase {
            function test() {
                Struct storage s;
                var test = s;
                         //^
            }
        }""")
  }

  private fun checkType(type: SolType, @Language("T-Sol") code: String) {
    val (refElement, _) = resolveInCode<SolExpression>(code)
    TestCase.assertEquals(type, refElement.type)
  }
}
