package com.broxus.solidity.ide.inspections

class ValidateFunctionArgumentsInspectionTest : SolInspectionsTestBase(FunctionArgumentsInspection()) {
  fun testTypeAssertFailure() = checkByText("""
        contract a {
            function a() {
                require(/*@error descr="Argument of type 'string' is not assignable to parameter of type 'bool'"@*/"myString"/*@/error@*/, "myMsg");
            }
        }
    """)

  fun testTypeAssertSuccess() = checkByText("""
        contract a {
            function a() {
                require(true, "myMsg");
            }
        }
    """)

  fun testWrongNumberOfArguments() = checkByText("""
        contract a {
            function a() {
                b(/*@error descr="Expected 1 argument, but got 2"@*/1, 2/*@/error@*/);
            }
            
            function b(int a) {
            
            }
        }
    """)

  fun testValidationDisabled() = checkByText("""
        contract a {
            function a() {
                b(1, 2);
            }
            
            /**
            * @custom:no_validation
            */
            function b(int a) {
            
            }
        }
    """)

}
