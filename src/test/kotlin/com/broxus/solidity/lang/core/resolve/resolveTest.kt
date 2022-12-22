package com.broxus.solidity.lang.core.resolve

import com.intellij.psi.PsiElement
import com.broxus.solidity.lang.psi.SolFunctionCallExpression
import com.broxus.solidity.lang.psi.SolNamedElement
import com.broxus.solidity.utils.SolTestBase
import org.intellij.lang.annotations.Language

abstract class SolResolveTestBase : SolTestBase() {
  protected fun checkFunctionByCode(@Language("T-Sol") code: String) {
    checkByCodeInternal<SolFunctionCallExpression, SolNamedElement>(code)
  }

  protected open fun checkByCode(@Language("T-Sol") code: String) {
    checkByCodeSearchType<SolNamedElement>(code)
  }

  protected inline fun <reified T : PsiElement> checkByCodeSearchType(@Language("T-Sol") code: String) {
    checkByCodeInternal<SolNamedElement, T>(code)
  }

  protected inline fun <reified F : PsiElement, reified T : PsiElement> checkByCodeInternal(@Language("T-Sol") code: String) {
    val (refElement, data) = resolveInCode<F>(code)

    if (data == "unresolved") {
      val resolved = refElement.reference?.resolve()
      check(resolved == null) {
        "$refElement `${refElement.text}`should be unresolved, was resolved to\n$resolved `${resolved?.text}`"
      }
      return
    }

    val references = refElement.references.mapNotNull { it?.resolve() }
    assertTrue("Failed to resolve ${refElement.text}", references.isNotEmpty())
    val target = findElementInEditor<T>("x")

    if (references.size == 1) {
      assertEquals(target, references.first())
    } else {
      assertTrue(references.contains(target))
    }
  }

  protected inline fun <reified T : PsiElement> resolveInCode(@Language("T-Sol") code: String): Pair<T, String> {
    InlineFile(code)
    return findElementAndDataInEditor("^")
  }

  protected fun testResolveBetweenFiles(file1: InlineFile, file2: InlineFile) {
    myFixture.openFileInEditor(file2.psiFile.virtualFile)
    val (refElement, _) = findElementAndDataInEditor<SolNamedElement>("^")
    val resolved = checkNotNull(refElement.reference?.resolve()) {
      "failed to resolve ${refElement.name}"
    }
    myFixture.openFileInEditor(file1.psiFile.virtualFile)
    val (resElement, _) = findElementAndDataInEditor<SolNamedElement>("x")
    assertEquals(resElement, resolved)
  }
}

