package com.broxus.solidity.lang.core.resolve

class SolAliasResolveTest : SolResolveTestBase() {
    fun testResolveImportedFunctionFromBracketWithAlias() = testResolveBetweenFiles(
        InlineFile(
            code = """
          pragma solidity ^0.8.26;

          library a {
            function doit() internal {
                     //x
            }
          }
      """,
            name = "a.tsol"
        ),
        InlineFile(
            """
          pragma solidity ^0.8.26;

          import {a as A} from "./a.tsol";

          contract b {
            function test() public {
                A.doit();
                //^
            }
          }

    """
        )
    )

//    fun testResolveContractFromBracketWithAlias() = testResolveBetweenFiles(
//        InlineFile(
//            code = """
//            pragma solidity ^0.8.26;
//
//            library a {
//                  //x
//                function doit() internal {
//                }
//            }
//      """,
//            name = "a.tsol"
//        ),
//        InlineFile(
//            """
//            pragma solidity ^0.8.26;
//
//            import {a as A} from "./a.tsol";
//
//            contract b {
//                function test() public {
//                    A.doit();
//                  //^
//                }
//            }
//    """
//        )
//    )

    fun testResolveImportedFunctionFromPathWithAlias() = testResolveBetweenFiles(
        InlineFile(
            code = """
          pragma solidity ^0.8.26;

          contract a {
            function doit() public {
                     //x
            }
          }
      """,
            name = "a.tsol"
        ),
        InlineFile(
            """
          pragma solidity ^0.8.26;

          import "./a.tsol" as A;

          contract b {
            function test(address x) public {
                A.a(x).doit();
                     //^
            }
          }

    """
        )
    )

    fun testResolveContractFromPathWithAlias() = checkByCode(
        """
          pragma solidity ^0.8.26;

          import "./a.tsol" as A;
                             //x
          contract b {
            function test(address x) public {
                A.a(x).doit();
              //^
            }
          }
    """
    )

    fun testResolveContractFromPathWithAlias2() = testResolveBetweenFiles(
        InlineFile(
            code = """
          pragma solidity ^0.8.26;

          contract a {
                 //x
            function doit() public {
            }
          }
      """,
            name = "a.tsol"
        ),
        InlineFile(
            """
          pragma solidity ^0.8.26;

          import "./a.tsol" as A;

          contract b {
            function test(address x) public {
                A.a(x).doit();
                //^
            }
          }
    """
        )
    )

    fun testResolveImportedFunctionFromAsteriskWithAlias() = testResolveBetweenFiles(
        InlineFile(
            code = """
          pragma solidity ^0.8.26;

          library a {
            function doit() internal {
                     //x
            }
          }
      """,
            name = "a.tsol"
        ),
        InlineFile(
            """
          pragma solidity ^0.8.26;

          import * as A from "./a.tsol";

          contract b {
            function test() public {
                A.a.doit();
                  //^
            }
          }
    """
        )
    )

    fun testResolveContractFromAsteriskWithAlias() = checkByCode(
        """
          pragma solidity ^0.8.26;

          import * as A from "./a.tsol";
                    //x
          contract b {
            function test() public {
                A.a.doit();
              //^
            }
          }
    """
    )

    fun testResolveContractFromAsteriskWithAlias2() = testResolveBetweenFiles(
        InlineFile(
            code = """
          pragma solidity ^0.8.26;

          library a {
                //x
            function doit() internal {
            }
          }
      """,
            name = "a.tsol"
        ),
        InlineFile(
            """
          pragma solidity ^0.8.26;

          import * as A from "./a.tsol";

          contract b {
            function test() public {
                A.a.doit();
                //^
            }
          }
    """
        )
    )

    fun testResolveContractFromAsteriskWithAliasMultipleContracts() = testResolveBetweenFiles(
        InlineFile(
            code = """
            pragma solidity ^0.8.26;

            contract a {
                function doit() public {
                }
            }

            contract ab {
                function doit() public {
                       //x
                }
            }
      """,
            name = "a.tsol"
        ),
        InlineFile(
            """
          pragma solidity ^0.8.26;

          import * as A from "./a.tsol";

          contract b {
            function test(address x) public {
                A.ab(x).doit();
                       //^
            }
          }
    """
        )
    )

    fun testResolveLibraryFromAsteriskWithAliasMultipleLibrary() = testResolveBetweenFiles(
        InlineFile(
            code = """
            pragma solidity ^0.8.26;

            library a {
                function doit() internal {
                }
            }

            library ab {
                function doit() internal {
                       //x
                }
            }
      """,
            name = "a.tsol"
        ),
        InlineFile(
            """
          pragma solidity ^0.8.26;

          import * as A from "./a.tsol";

          contract b {
            function test(address x) public {
                A.ab.doit();
                    //^
            }
          }
    """
        )
    )

    fun testResolveContractFromAsteriskWithAliasMultipleContracts2() {
        InlineFile(
                code = """
            pragma solidity ^0.8.26;

            import "./z.tsol";

            contract a {
                function doit() public {
                }
            }
            """, name = "a.tsol"
        )

        testResolveBetweenFiles(
            InlineFile(
                code = """
                pragma solidity ^0.8.26;

                contract z {
                    function doit2() public {
                            //x
                    }
                }
                """, name = "z.tsol"
            ),

            InlineFile("""
                pragma solidity ^0.8.26;

                import * as A from "./a.tsol";

                contract b {
                    function test(address x) public {
                        A.z(x).doit2();
                               //^
                    }
                }
                """
            )
        )
    }
}
