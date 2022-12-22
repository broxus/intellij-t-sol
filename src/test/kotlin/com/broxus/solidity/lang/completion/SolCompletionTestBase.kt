package com.broxus.solidity.lang.completion

import com.broxus.solidity.utils.SolTestBase
import org.intellij.lang.annotations.Language

abstract class SolCompletionTestBase : SolTestBase() {
  protected fun checkCompletion(required: Set<String>, @Language("T-Sol") code: String, strict: Boolean = false) {
    InlineFile(code).withCaret()
    val variants = myFixture.completeBasic()
    checkNotNull(variants) {
      "Expected completions that contain $required, but no completions found"
    }
    val completions = variants.map { it.lookupString }.toHashSet()
    if (strict) {
      assertEquals(required.toHashSet(), completions)
    } else {
      assertTrue("$completions doesn't contain all $required", completions.containsAll(required))
    }
  }
}
