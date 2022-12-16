package me.serce.solidity.ide.liveTemplates

import com.intellij.codeInsight.template.FileTypeBasedContextType
import me.serce.solidity.lang.TSolidityFileType
import me.serce.solidity.lang.TSolidityLanguage
import java.util.*

class SolTemplateContextType : FileTypeBasedContextType(
  TSolidityLanguage.id.uppercase(Locale.getDefault()),
  TSolidityLanguage.id,
  TSolidityFileType
)
