package com.broxus.solidity.ide

import com.intellij.lang.Commenter

class SolCommenter : Commenter {
  override fun getLineCommentPrefix() = "//"

  override fun getCommentedBlockCommentPrefix() = null
  override fun getCommentedBlockCommentSuffix() = null
  override fun getBlockCommentPrefix() = "/*"
  override fun getBlockCommentSuffix() = "*/"
}
