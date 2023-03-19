package com.broxus.solidity.lang.completion

class SolKeywordCompletion : SolCompletionTestBase() {

  fun testRootCompletion() = checkCompletion(
    hashSetOf("pragma ever-solidity", "pragma ", "library ", "contract "), """
      /*caret*/
  """
  )

  fun testContractKeyword() = checkCompletion(
    hashSetOf("contract ", "library "), """
        contract A{}
        /*caret*/
  """
  )

  fun testThisKeyword() = checkCompletion(
    hashSetOf("this"), """
        contract A {
            function test() {
                /*caret*/
            }
        } 
  """)

  fun testThisKeywordInMemberAccess() = checkCompletion(
    hashSetOf("this"), """
        contract A {
            address a;
            
            function test() {
                a.transfer(/*caret*/);
            }
        } 
  """)

  fun testThisKeywordNotInMemberAccess() = checkCompletion(
    hashSetOf("isNone", "makeAddrExtern", "isExternZero", "makeAddrStd", "wid", "transfer", "balance", "isStdAddrWithoutAnyCast", "getType", "isStdZero", "unpack", "value", "currencies", "makeAddrNone"), """
        contract A {
            address a;
        
            function test() {
                a./*caret*/
            }
        } 
  """, strict = true)

}
