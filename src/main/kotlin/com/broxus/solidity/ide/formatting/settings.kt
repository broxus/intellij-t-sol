package com.broxus.solidity.ide.formatting

import com.intellij.application.options.CodeStyleAbstractConfigurable
import com.intellij.application.options.IndentOptionsEditor
import com.intellij.application.options.SmartIndentOptionsEditor
import com.intellij.application.options.TabbedLanguageCodeStylePanel
import com.intellij.lang.Language
import com.intellij.psi.codeStyle.*
import com.broxus.solidity.lang.TSolidityLanguage
import com.broxus.solidity.loadCodeSampleResource

class SolCodeStyleSettingsProvider : CodeStyleSettingsProvider() {
  override fun createCustomSettings(settings: CodeStyleSettings) = SolCodeStyleSettings(settings)

  override fun getConfigurableDisplayName() = TSolidityLanguage.displayName

  override fun createSettingsPage(settings: CodeStyleSettings, originalSettings: CodeStyleSettings) =
    object : CodeStyleAbstractConfigurable(settings, originalSettings, configurableDisplayName) {
      override fun createPanel(settings: CodeStyleSettings) = SolCodeStyleMainPanel(currentSettings, settings)
      override fun getHelpTopic() = null
    }

  private class SolCodeStyleMainPanel(currentSettings: CodeStyleSettings, settings: CodeStyleSettings) :
    TabbedLanguageCodeStylePanel(TSolidityLanguage, currentSettings, settings) {

    override fun initTabs(settings: CodeStyleSettings?) {
      addIndentOptionsTab(settings)
    }
  }
}

class SolCodeStyleSettings(container: CodeStyleSettings) : CustomCodeStyleSettings("SolCodeStyleSettings", container)

class SolLanguageCodeStyleSettingsProvider : LanguageCodeStyleSettingsProvider() {
  override fun getLanguage(): Language = TSolidityLanguage

  override fun getCodeSample(settingsType: SettingsType): String =
    when (settingsType) {
      SettingsType.INDENT_SETTINGS -> INDENT_SAMPLE
      else -> ""
    }

  override fun getDefaultCommonSettings(): CommonCodeStyleSettings {
    val settings = CommonCodeStyleSettings(TSolidityLanguage)
    settings.initIndentOptions()
    return settings
  }

  override fun getIndentOptionsEditor(): IndentOptionsEditor? = SmartIndentOptionsEditor()

  private val INDENT_SAMPLE: String by lazy {
    loadCodeSampleResource(this, "com/broxus/solidity/ide/formatting/indent_sample.tsol")
  }
}
