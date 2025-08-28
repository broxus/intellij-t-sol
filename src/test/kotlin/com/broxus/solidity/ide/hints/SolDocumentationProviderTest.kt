package com.broxus.solidity.ide.hints

import com.broxus.solidity.utils.SolTestBase
import com.intellij.openapi.util.text.StringUtil
import org.intellij.lang.annotations.Language
import org.junit.Assert


class SolDocumentationProviderTest : SolTestBase() {
  fun testDocumentation() = checkByText("""
          contract A {
          
              function main() {
                  withDoc/*caret*/();
              }
              /**
                my docs
              */
              function withDoc(int myInti, bool aBool) returns (uint) {
                 return 1;
              }
          }
      """, """<div class='definition'><pre><b style='color:rgb(0,0,128)'>function</b> withDoc(<b style='color:rgb(0,0,128)'>int</b> myInti, <b style='color:rgb(0,0,128)'>bool</b> aBool)  <b style='color:rgb(0,0,128)'>returns</b> (<b style='color:rgb(0,0,128)'>uint</b>)</pre></div><div class='content'><br/>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;my&nbsp;docs<br/>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;</div>""")

  fun testDocsForBuiltinFunction() = checkByText("""
            contract A {
                function main() {
                    abi.encode/*caret*/();
                }
            }
        """, """<div class='definition'><pre><b style='color:rgb(0,0,128)'>function</b> encode(<b style='color:rgb(0,0,128)'>AnyType</b> varargs)  <b style='color:rgb(0,0,128)'>returns</b> (<b style='color:rgb(0,0,128)'>TvmCell</b>)</pre></div><div class='content'><br/>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;creates&nbsp;<code>cell</code>&nbsp;from&nbsp;the&nbsp;values.<br/>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<br/>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<br/><span&nbsp;class='grayed'>custom:typeArgument:</span>&nbsp;Type<br/>							</div>""")

  fun testTripleSlashDocs() = checkByText("""
          contract A {
          
              function main() {
                  withDoc/*caret*/();
              }
              /// @notice my docs
              function withDoc(int myInti, bool aBool) returns (uint) {
                 return 1;
              }
          }
      """, """<div class='definition'><pre><b style='color:rgb(0,0,128)'>function</b> withDoc(<b style='color:rgb(0,0,128)'>int</b> myInti, <b style='color:rgb(0,0,128)'>bool</b> aBool)  <b style='color:rgb(0,0,128)'>returns</b> (<b style='color:rgb(0,0,128)'>uint</b>)</pre></div><div class='content'>///&nbsp;<br/><span&nbsp;class='grayed'>notice:</span>&nbsp;my&nbsp;docs</div>""")

//  fun testInheritedDocs() = checkByText("""
//          contract Base {
//              /// @notice base docs
//              function withDoc(int a) { }
//          }
//          contract Child is Base {
//              /// @inheritdoc
//              function withDoc(int a) { }
//              function main() {
//                  withDoc/*caret*/(1);
//              }
//          }
//      """, """<div class='definition'><pre><b style='color:rgb(0,0,128)'>function</b> withDoc(<b style='color:rgb(0,0,128)'>int</b> a)  </pre></div><div class='content'>&nbsp;<span&nbsp;class='grayed'>notice:</span>&nbsp;base&nbsp;docs<br/>&nbsp;<span&nbsp;class='grayed'>inheritdoc:</span></div>""")

  fun testParameterDocs() = checkByText("""
          contract A {
              /// @param myInt parameter docs
              function withDoc(uint myInt/*caret*/) { }
          }
      """, """<div class='definition'><pre><b style='color:rgb(0,0,128)'>uint</b> myInt</pre></div>""")

  fun testErrorDefinitionDocs() = checkByText("""
          contract A {
              error NotEnoughBalance(uint requested, uint available);
              function main() {
                  revert NotEnoughBalance/*caret*/(1, 2);
              }
          }
      """, """<div class='definition'><pre><b style='color:rgb(0,0,128)'>error</b> NotEnoughBalance (uint requested, uint available)</pre></div>""")

  private fun checkByText(@Language("T-Sol") code: String, @Language("HTML") expectedDoc: String) {
    myFixture.configureByText("main.tsol", code.replace("/*caret*/", "<caret>"))
    val originalElement = myFixture.getElementAtCaret()
    val element = originalElement.reference?.resolve() ?: originalElement
    val documentationProvider = SolDocumentationProvider()
    val generateDoc = documentationProvider.generateDoc(element, originalElement)
    assertNotNull(generateDoc)
    assertSameLinesTrimmed(expectedDoc, generateDoc!!)
  }

  fun assertSameLinesTrimmed(expected: String, actual: String) {
    val expectedText = StringUtil.convertLineSeparators(expected.trim { it <= ' ' })
    val actualText = StringUtil.convertLineSeparators(actual.trim { it <= ' ' })
    Assert.assertEquals(expectedText, actualText)
  }

}
