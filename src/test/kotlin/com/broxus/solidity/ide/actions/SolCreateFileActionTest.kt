package com.broxus.solidity.ide.actions

import com.broxus.solidity.utils.SolLightPlatformCodeInsightFixtureTestCase
import com.intellij.ide.IdeView
import com.intellij.ide.actions.TestDialogBuilder
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.LangDataKeys
import com.intellij.psi.PsiDirectory
import com.intellij.psi.impl.file.PsiDirectoryFactory
import com.intellij.testFramework.MapDataContext
import org.intellij.lang.annotations.Language

class SolCreateFileActionTest: SolLightPlatformCodeInsightFixtureTestCase() {

  fun testCreateContract() {
    val directoryFactory = PsiDirectoryFactory.getInstance(project)
    val dir = directoryFactory.createDirectory(myFixture.tempDirFixture.findOrCreateDir("foo"))
    val ctx = MapDataContext(mapOf(
      LangDataKeys.IDE_VIEW to com.broxus.solidity.ide.actions.SolCreateFileActionTest.TestIdeView(dir),
      CommonDataKeys.PROJECT to project,
      TestDialogBuilder.TestAnswers.KEY to TestDialogBuilder.TestAnswers("myContract", "T-Sol Contract")
    ))
    val event = AnActionEvent.createFromDataContext("", null, ctx)
    ActionManager.getInstance().getAction("t-sol.file.create")!!.actionPerformed(event)

    val file = dir.findFile("myContract.tsol")!!
    @Language("T-Sol")
    val content = """
      // SPDX-License-Identifier: UNLICENSED
      pragma ever-solidity >= 0.62.0;
      
      contract myContract {
          constructor() public {
      
          }
      }
      
      """.trimIndent()
    assertEquals(content, file.text)
  }

  private class TestIdeView(private val dir: PsiDirectory) : IdeView {
    override fun getDirectories() = arrayOf(dir)
    override fun getOrChooseDirectory() = dir
  }
}
