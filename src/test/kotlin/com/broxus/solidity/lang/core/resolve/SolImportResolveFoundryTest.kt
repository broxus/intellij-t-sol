package com.broxus.solidity.lang.core.resolve

import com.broxus.solidity.lang.psi.SolNamedElement

class SolImportResolveFoundryTest : SolResolveTestBase() {

  fun testImportPathResolveFoundryRemappings() {
    val testcases = arrayListOf<Pair<String, String>>(
      Pair("lib/forge-std/src/Test.tsol","contracts/ImportUsageFoundryStd.tsol"),
      Pair("lib/solmate/src/tokens/ERC721.tsol","contracts/ImportUsageFoundrySolmate.tsol"),
      Pair("lib/openzeppelin-contracts/contracts/token/ERC20/ERC20.tsol","contracts/ImportUsageFoundryOpenzeppelin.tsol"),
    );
    testcases.forEach { (targetFile, contractFile) ->
      val file1 = myFixture.configureByFile(targetFile)
      myFixture.configureByFile("remappings.txt")
      myFixture.configureByFile(contractFile)
      val (refElement) = findElementAndDataInEditor<SolNamedElement>("^")
      val resolved = checkNotNull(refElement.reference?.resolve()) {
        "Failed to resolve ${refElement.text}"
      }
      assertEquals(file1.name, resolved.containingFile.name)
    }
  }

  override fun getTestDataPath() = "src/test/resources/fixtures/importRemappings/"
}
