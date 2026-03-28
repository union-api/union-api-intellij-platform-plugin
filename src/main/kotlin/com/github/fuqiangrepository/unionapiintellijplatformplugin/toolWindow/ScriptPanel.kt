package com.github.fuqiangrepository.unionapiintellijplatformplugin.toolWindow

import com.github.fuqiangrepository.unionapiintellijplatformplugin.model.ApiRequest
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import java.awt.*
import javax.swing.*

class ScriptPanel : JPanel(BorderLayout()) {

    private val preScriptArea = JBTextArea().apply {
        font = Font(Font.MONOSPACED, Font.PLAIN, 13)
        rows = 10
    }

    private val postScriptArea = JBTextArea().apply {
        font = Font(Font.MONOSPACED, Font.PLAIN, 13)
        rows = 10
    }

    init {
        setupUI()
    }

    private fun helpText(): String = """
        // pm API reference:
        // pm.environment.get("key")          → String | null
        // pm.environment.set("key", "value") → stored in environment
        //
        // Pre-request only:
        // pm.sendRequest({ method, url, headers, body }) → { status, body, json() }
        //
        // Post-response only:
        // pm.response.status   → Int
        // pm.response.body     → String
        // pm.response.json()   → parsed JSON object
        // pm.response.headers  → Map
    """.trimIndent()

    private fun buildScriptTab(area: JBTextArea): JPanel {
        val helpArea = JBTextArea(helpText()).apply {
            font = Font(Font.MONOSPACED, Font.PLAIN, 11)
            isEditable = false
            foreground = UIManager.getColor("Label.disabledForeground")
            background = UIManager.getColor("Panel.background")
            border = JBUI.Borders.empty(4)
        }

        return JPanel(BorderLayout()).apply {
            add(JBScrollPane(helpArea).apply {
                preferredSize = Dimension(0, 120)
            }, BorderLayout.NORTH)
            add(JBScrollPane(area), BorderLayout.CENTER)
        }
    }

    private fun setupUI() {
        val tabs = JTabbedPane()
        tabs.addTab("Pre-request Script", buildScriptTab(preScriptArea))
        tabs.addTab("Post-response Script", buildScriptTab(postScriptArea))
        add(tabs, BorderLayout.CENTER)
    }

    fun loadScripts(request: ApiRequest) {
        preScriptArea.text = request.preRequestScript
        postScriptArea.text = request.postResponseScript
    }

    fun saveScripts(request: ApiRequest) {
        request.preRequestScript = preScriptArea.text
        request.postResponseScript = postScriptArea.text
    }
}
