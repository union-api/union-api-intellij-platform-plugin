package com.github.fuqiangrepository.unionapiintellijplatformplugin.toolWindow

import com.intellij.ui.JBColor
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import java.awt.*
import java.net.http.HttpResponse
import javax.swing.*
import javax.swing.table.DefaultTableModel

class ResponsePanel : JPanel(BorderLayout()) {

    private val statusLabel = JLabel("就绪")
    private val timeLabel = JLabel("")

    private val bodyArea = JBTextArea().apply {
        isEditable = false
        font = Font(Font.MONOSPACED, Font.PLAIN, 13)
    }

    private val headersModel = DefaultTableModel(arrayOf("Key", "Value"), 0)
    private val headersTable = JTable(headersModel).apply { isEnabled = false }

    init {
        setupUI()
    }

    private fun setupUI() {
        val statusBar = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(4)
            add(statusLabel, BorderLayout.WEST)
            add(timeLabel, BorderLayout.EAST)
        }

        val tabs = JTabbedPane()
        tabs.addTab("Body", JBScrollPane(bodyArea))
        tabs.addTab("Headers", JBScrollPane(headersTable))

        add(statusBar, BorderLayout.NORTH)
        add(tabs, BorderLayout.CENTER)
    }

    fun showLoading() {
        statusLabel.text = "请求中..."
        statusLabel.foreground = JBColor.GRAY
        timeLabel.text = ""
        bodyArea.text = ""
        headersModel.rowCount = 0
    }

    fun showResponse(response: HttpResponse<String>, elapsedMs: Long) {
        val code = response.statusCode()
        statusLabel.text = "$code ${statusText(code)}"
        statusLabel.foreground = when {
            code < 300 -> JBColor(Color(0, 153, 0), Color(98, 209, 98))
            code < 400 -> JBColor.ORANGE
            else       -> JBColor.RED
        }
        timeLabel.text = "${elapsedMs} ms"

        val contentType = response.headers().firstValue("content-type").orElse("")
        bodyArea.text = if ("json" in contentType) prettyJson(response.body()) else response.body()
        bodyArea.caretPosition = 0

        headersModel.rowCount = 0
        response.headers().map().forEach { (k, v) ->
            headersModel.addRow(arrayOf(k, v.joinToString(", ")))
        }
    }

    fun showError(message: String) {
        statusLabel.text = "错误"
        statusLabel.foreground = JBColor.RED
        timeLabel.text = ""
        bodyArea.text = message
    }

    private fun statusText(code: Int) = when (code) {
        200 -> "OK"; 201 -> "Created"; 204 -> "No Content"
        301 -> "Moved Permanently"; 302 -> "Found"; 304 -> "Not Modified"
        400 -> "Bad Request"; 401 -> "Unauthorized"; 403 -> "Forbidden"
        404 -> "Not Found"; 405 -> "Method Not Allowed"; 409 -> "Conflict"
        422 -> "Unprocessable Entity"; 429 -> "Too Many Requests"
        500 -> "Internal Server Error"; 502 -> "Bad Gateway"; 503 -> "Service Unavailable"
        else -> ""
    }

    private fun prettyJson(raw: String): String = try {
        val sb = StringBuilder()
        var indent = 0
        var inString = false
        var escape = false
        for (c in raw.trim()) {
            if (escape) { sb.append(c); escape = false; continue }
            if (c == '\\' && inString) { sb.append(c); escape = true; continue }
            if (c == '"') inString = !inString
            if (inString) { sb.append(c); continue }
            when (c) {
                '{', '[' -> { sb.append(c).append('\n'); indent++; sb.append("  ".repeat(indent)) }
                '}', ']' -> { sb.append('\n'); indent--; sb.append("  ".repeat(indent)).append(c) }
                ','      -> { sb.append(c).append('\n').append("  ".repeat(indent)) }
                ':'      -> sb.append(": ")
                ' ', '\n', '\r', '\t' -> { /* skip */ }
                else     -> sb.append(c)
            }
        }
        sb.toString()
    } catch (_: Exception) { raw }
}
