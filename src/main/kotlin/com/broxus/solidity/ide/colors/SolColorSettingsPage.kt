package com.broxus.solidity.ide.colors

import com.intellij.openapi.options.colors.AttributesDescriptor
import com.intellij.openapi.options.colors.ColorDescriptor
import com.intellij.openapi.options.colors.ColorSettingsPage
import com.broxus.solidity.ide.SolHighlighter
import com.broxus.solidity.ide.SolidityIcons
import com.broxus.solidity.lang.TSolidityLanguage
import com.broxus.solidity.loadCodeSampleResource

class SolColorSettingsPage : ColorSettingsPage {
  private val ATTRIBUTES: Array<AttributesDescriptor> = SolColor.values().map { it.attributesDescriptor }.toTypedArray()
  private val ANNOTATOR_TAGS = SolColor.values().associateBy({ it.name }, { it.textAttributesKey })

  private val DEMO_TEXT by lazy {
    loadCodeSampleResource(this, "com/broxus/solidity/ide/colors/highlighter_example.tsol")
  }

  override fun getDisplayName() = TSolidityLanguage.displayName
  override fun getIcon() = SolidityIcons.FILE_ICON
  override fun getAttributeDescriptors() = ATTRIBUTES
  override fun getColorDescriptors(): Array<ColorDescriptor> = ColorDescriptor.EMPTY_ARRAY
  override fun getHighlighter() = SolHighlighter
  override fun getAdditionalHighlightingTagToDescriptorMap() = ANNOTATOR_TAGS
  override fun getDemoText() = DEMO_TEXT
}
