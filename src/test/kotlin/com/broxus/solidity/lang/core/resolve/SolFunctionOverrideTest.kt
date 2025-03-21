package com.broxus.solidity.lang.core.resolve

import com.broxus.solidity.lang.psi.SolFunctionDefinition
import com.broxus.solidity.lang.resolve.function.SolFunctionResolver

class SolFunctionOverrideTest : SolResolveTestBase() {
  fun testFindOverrides() {
    InlineFile("""
        contract A {
            function test(uint256 test);
                     //X
            function test();
        }

        contract B is A {
            function test(uint test1) override {
                     //^

            }

            function test(uint128 test2) override {

            }

        }

        contract C is B {
            function test(uint256 test2) override  {
                     //Y
            }

            function test(uint128 test) override {

            }
        }
  """)
    val (func, _) = findElementAndDataInEditor<SolFunctionDefinition>("^")
    val (overriden, _) = findElementAndDataInEditor<SolFunctionDefinition>("X")
    val (overrides, _) = findElementAndDataInEditor<SolFunctionDefinition>("Y")

    val overridenList = SolFunctionResolver.collectOverriden(func)
    assert(overridenList.size == 1)
    assert(overridenList.firstOrNull() == overriden)

    val overridesList = SolFunctionResolver.collectOverrides(func)
    assert(overridesList.size == 1)
    assert(overridesList.firstOrNull() == overrides)
  }
}
