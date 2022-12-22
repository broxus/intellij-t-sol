package com.broxus.solidity.ide.liveTemplates

import com.intellij.codeInsight.template.FileTypeBasedContextType
import com.broxus.solidity.lang.TSolidityFileType
import com.broxus.solidity.lang.TSolidityLanguage
import java.util.*

class SolTemplateContextType : FileTypeBasedContextType(
  TSolidityLanguage.id.uppercase(Locale.getDefault()),
  TSolidityLanguage.id,
  TSolidityFileType
)
