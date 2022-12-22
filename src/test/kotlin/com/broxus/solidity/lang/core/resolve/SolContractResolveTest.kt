package com.broxus.solidity.lang.core.resolve

import com.broxus.solidity.lang.psi.SolElement
import com.broxus.solidity.lang.psi.SolNamedElement

class SolContractResolveTest : SolResolveTestBase() {

  fun testNewContractResolveToContractDefSelf() = checkByCode(
    """
        contract A {
               //x
          function resolve() {
            A a = new A();
                    //^
          }
        }
          """
  )

  fun testNewContractResolveToContractDefAnother() = checkByCode(
    """
        contract B {}
               //x
        contract A {
          function resolve() {
            B a = new B();
                    //^
          }
        }
          """
  )

  fun testNewResolveToConstructor() = checkByCodeSearchType<SolElement>(
    """
        contract B {
          constructor() {
               //x
          }
        }
        contract A {
          function resolve() {
            B a = new B();
                    //^
          }
        }
          """
  )

  fun testNewResolveToConstructorFunction() = checkByCodeSearchType<SolElement>(
    """
        contract B {
          function B() {
                 //x
          }
        }
        contract A {
          function resolve() {
            B a = new B();
                    //^
          }
        }
          """
  )

  fun testField() = checkByCode("""
        contract A {}
               //x

        contract B {
            A myField;
          //^
        }
  """)

  fun testInheritance() = checkByCode("""
        contract A {}
               //x

        contract B is A {
                    //^
        }
  """)

  fun testResolveSymbolAliases() = testResolveBetweenFiles(
    InlineFile(
      code = """
          contract a {}
                 //x
      """,
      name = "a.tsol"
    ),
    InlineFile("""
          import {a as A} from "./a.tsol";

          contract b is A {}
                      //^
    """)
  )

  fun testResolveSymbolAliasesChain() {
    InlineFile(
      code = """
            contract d {}
        """,
      name = "d.tsol"
    )
    InlineFile(
      code = """
            import {d as a} from "./d.tsol";
            contract b {}
        """,
      name = "b.tsol"
    )
    testResolveBetweenFiles(
      InlineFile(
        code = """
            import {b as B} from "./b.tsol";
            contract a {}
                   //x
        """,
        name = "a.tsol"
      ),
      InlineFile("""
            import {a as A} from "./a.tsol";
                  //^
      """)
    )
  }

  fun testResolveUsingImport() = testResolveBetweenFiles(
    InlineFile(
      code = """
          contract a {}
                 //x
      """,
      name = "a.tsol"
    ),
    InlineFile("""
          import "./a.tsol";

          contract b is a {}
                      //^
    """)
  )

  fun testNotImported() {
    InlineFile(
      name = "a.tsol",
      code = "contract a {}"
    )

    InlineFile("""
          import "./error.tsol";

          contract b is a {}
                      //^
    """)

    val (refElement, _) = findElementAndDataInEditor<SolNamedElement>("^")
    assertNull(refElement.reference?.resolve())
  }
}
