package com.broxus.solidity.ide

import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

object SolidityIcons {
  private fun getIcon(path: String) = IconLoader.getIcon(path, SolidityIcons.javaClass)

  val FILE_ICON: Icon = getIcon("/icons/TS.png")

  val ENUM_FILE: Icon = getIcon("/icons/E.png")
  val CONTRACT_FILE: Icon = getIcon("/icons/C.png")
  val STRUCT_FILE: Icon = getIcon("/icons/S.png")
  val ABSTRACT: Icon = getIcon("/icons/A.png")
  val INTERFACE: Icon = getIcon("/icons/I.png")
  val LIBRARY: Icon = getIcon("/icons/L.png")

  val EVENT: Icon = getIcon("/icons/event.png")
  val ENUM: Icon = getIcon("/icons/enum.png")
  val STATE_VAR: Icon = getIcon("/icons/variable.png")
  val FUNCTION: Icon = getIcon("/icons/function.png")
  val ERROR: Icon = getIcon("/icons/error.png")
  val STRUCT: Icon = getIcon("/icons/struct.png")

}
