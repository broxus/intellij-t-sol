package com.broxus.solidity.ide.hierarchy

import com.broxus.solidity.ide.SubtypesHierarchyTreeStructure
import com.broxus.solidity.ide.SupertypesHierarchyTreeStructure
import com.broxus.solidity.ide.TypeHierarchyTreeStructure
import com.broxus.solidity.lang.psi.SolContractDefinition
import com.broxus.solidity.utils.SolTestBase
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.codeInsight.hierarchy.HierarchyViewTestFixture

class SolHierarchyTypeProviderTest : SolTestBase() {
  fun testSupertypesHierarchy() {
    val file = InlineFile(
      """
      contract A {}
      contract B is A {}
      contract C is B {}
      """.trimIndent()
    )
    val contracts = PsiTreeUtil.findChildrenOfType(file.psiFile, SolContractDefinition::class.java)
    val contractC = contracts.first { it.name == "C" }
    val structure = SupertypesHierarchyTreeStructure(contractC)
    HierarchyViewTestFixture.doHierarchyTest(
      structure,
      """
      <node text="C" base="true">
        <node text="B">
          <node text="A"/>
        </node>
        <node text="A"/>
      </node>
      """.trimIndent()
    )
  }

  fun testSubtypesHierarchy() {
    val file = InlineFile(
      """
      contract A {}
      contract B is A {}
      contract C is B {}
      """.trimIndent()
    )
    val contracts = PsiTreeUtil.findChildrenOfType(file.psiFile, SolContractDefinition::class.java)
    val contractA = contracts.first { it.name == "A" }
    val structure = SubtypesHierarchyTreeStructure(contractA, "")
    HierarchyViewTestFixture.doHierarchyTest(
      structure,
      """
      <node text="A" base="true">
        <node text="C"/>
        <node text="B">
          <node text="C"/>
        </node>
      </node>
      """.trimIndent()
    )
  }

  fun testTypeHierarchy() {
    val file = InlineFile(
      """
      contract A {}
      contract B is A {}
      contract C is B {}
      """.trimIndent()
    )
    val contracts = PsiTreeUtil.findChildrenOfType(file.psiFile, SolContractDefinition::class.java)
    val contractC = contracts.first { it.name == "C" }
    val structure = TypeHierarchyTreeStructure(contractC, "")
    HierarchyViewTestFixture.doHierarchyTest(
      structure,
      """
      <node text="A">
        <node text="B">
          <node text="C" base="true"/>
        </node>
      </node>
      """.trimIndent()
    )
  }
}

