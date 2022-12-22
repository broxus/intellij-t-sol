package com.broxus.solidity.lang

import com.intellij.lang.Language
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.vfs.VirtualFile
import com.broxus.solidity.ide.SolidityIcons
import java.nio.charset.StandardCharsets.UTF_8

object TSolidityLanguage : Language("T-Sol", "text/T-Sol") {
  override fun isCaseSensitive() = true
}

object TSolidityFileType : LanguageFileType(TSolidityLanguage) {
  object DEFAULTS {
    const val DESCRIPTION = "T-Sol File"
  }

  override fun getName() = DEFAULTS.DESCRIPTION
  override fun getDescription() = DEFAULTS.DESCRIPTION
  override fun getDefaultExtension() = "tsol"
  override fun getIcon() = SolidityIcons.FILE_ICON
  override fun getCharset(file: VirtualFile, content: ByteArray): String = UTF_8.name()
}
