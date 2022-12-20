package com.broxus.solidity.lang.core.resolve

import com.broxus.solidity.lang.psi.SolFunctionCallExpression
import com.broxus.solidity.lang.psi.SolNamedElement

class SolErrorResolveTest : SolResolveTestBase() {
  fun testErrorWithNoArguments() = checkByCode("""
        contract B {
            error Closed();
                    //x
            function close() {
                revert Closed();
                     //^
            }
        }
  """)

  fun testErrorParent() = checkByCode("""
        contract A {
            error Closed();
                    //x
        }

        contract B is A {
            function close() {
                revert Closed();
                     //^
            }
        }
  """)

  fun testErrorWithParemeters() = checkByCode("""
        contract B {
            error Refunded(int a, uint256 b);
                    //x

            function close() {
                revert Refunded(1, 2);
                     //^
            }
        }
  """)

  override fun checkByCode(code: String) {
    checkByCodeInternal<SolFunctionCallExpression, SolNamedElement>(code)
  }
}
