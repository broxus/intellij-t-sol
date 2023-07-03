package com.broxus.solidity.ide.inspections

class UnusedElementInspectionTest : SolInspectionsTestBase(UnusedElementInspection()) {
  fun testUnusedElements() = checkByText("""
        contract /*@weak_warning descr="Contract 'a' is never used"@*/a/*@/weak_warning@*/ {
            var myStateVariable = 1;
            var /*@weak_warning descr="State variable 'myStateVariable2' is never used"@*/myStateVariable2/*@/weak_warning@*/ = 2;
            modifier myModifier() {
                _;
            }
            modifier /*@weak_warning descr="Modifier 'myModifier2' is never used"@*/myModifier2/*@/weak_warning@*/() {
                _;
            }
            
            function /*@weak_warning descr="Function 'a' is never used"@*/a/*@/weak_warning@*/(int /*@weak_warning descr="Parameter 'myParam' is never used"@*/myParam/*@/weak_warning@*/, int myParam2) {
                var myLocalVar2 = myParam2 + myStateVariable;
                var /*@weak_warning descr="Variable 'myLocalVar' is never used"@*/myLocalVar/*@/weak_warning@*/ = myLocalVar2;
                b();
            }
            
            function b() myModifier {
                MyEnum.a;
                var b = MyStruct(1, 2);
                b.a = 3;
                b.b = 4;                                                
            }
        }
        
        enum MyEnum {
            a,
            b
        }
        
        enum /*@weak_warning descr="Enum 'MyEnum2' is never used"@*/MyEnum2/*@/weak_warning@*/ {
            a,
            b
        }
        struct MyStruct {
            int a;
            int b;
        }
        struct /*@weak_warning descr="Struct 'MyStruct2' is never used"@*/MyStruct2/*@/weak_warning@*/ {
            int /*@weak_warning descr="Variable 'a' is never used"@*/a/*@/weak_warning@*/;
            int /*@weak_warning descr="Variable 'b' is never used"@*/b/*@/weak_warning@*/;
        }
    """, checkWarn = false, checkWeakWarn = true)

}
