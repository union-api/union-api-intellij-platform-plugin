package com.github.fuqiangrepository.unionapiintellijplatformplugin.toolWindow

import com.github.fuqiangrepository.unionapiintellijplatformplugin.model.ApiRequest
import com.github.fuqiangrepository.unionapiintellijplatformplugin.model.KeyValueParam
import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import java.awt.*
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest as JavaHttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import javax.swing.*
import javax.swing.table.DefaultTableModel

class RequestPanel(
    private val project: Project,
    private val responsePanel: ResponsePanel
) : JPanel(BorderLayout()) {

    private var currentRequest: ApiRequest? = null

    private val methodCombo = JComboBox(arrayOf("GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS"))
    private val urlField = JTextField()
    private val sendButton = JButton("Send", AllIcons.Actions.Execute)

    private val paramsModel = kvTableModel()
    private val headersModel = kvTableModel()

    private val bodyNone = JRadioButton("none", true)
    private val bodyJson = JRadioButton("JSON")
    private val bodyForm = JRadioButton("Form")
    private val bodyRaw = JRadioButton("Raw")
    private val bodyArea = JBTextArea().apply {
        font = Font(Font.MONOSPACED, Font.PLAIN, 13)
        isEnabled = false
    }

    init {
        setupUI()
    }

    private fun kvTableModel() = object : DefaultTableModel(arrayOf("Key", "Value"), 0) {
        override fun isCellEditable(row: Int, column: Int) = true
    }

    private fun setupUI() {
        // ── 顶部请求栏 ──
        val requestBar = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(4, 4, 4, 4)
            methodCombo.preferredSize = Dimension(100, 28)
            add(methodCombo, BorderLayout.WEST)
            add(urlField, BorderLayout.CENTER)
            sendButton.preferredSize = Dimension(80, 28)
            add(sendButton, BorderLayout.EAST)
        }

        // ── Tabs ──
        val tabs = JTabbedPane()
        tabs.addTab("Params", kvPanel(paramsModel))
        tabs.addTab("Headers", kvPanel(headersModel))
        tabs.addTab("Body", buildBodyPanel())

        add(requestBar, BorderLayout.NORTH)
        add(tabs, BorderLayout.CENTER)

        sendButton.addActionListener { sendRequest() }
    }

    private fun kvPanel(model: DefaultTableModel): JPanel {
        val table = JTable(model)
        val addBtn = JButton("+").apply { addActionListener { model.addRow(arrayOf("", "")) } }
        val delBtn = JButton("-").apply {
            addActionListener {
                val row = table.selectedRow
                if (row >= 0) model.removeRow(row)
            }
        }
        return JPanel(BorderLayout()).apply {
            add(JBScrollPane(table), BorderLayout.CENTER)
            add(JPanel(FlowLayout(FlowLayout.LEFT)).apply { add(addBtn); add(delBtn) }, BorderLayout.SOUTH)
        }
    }

    private fun buildBodyPanel(): JPanel {
        ButtonGroup().apply { add(bodyNone); add(bodyJson); add(bodyForm); add(bodyRaw) }
        listOf(bodyNone, bodyJson, bodyForm, bodyRaw).forEach { rb ->
            rb.addActionListener { bodyArea.isEnabled = rb != bodyNone }
        }
        return JPanel(BorderLayout()).apply {
            add(JPanel(FlowLayout(FlowLayout.LEFT)).apply {
                add(bodyNone); add(bodyJson); add(bodyForm); add(bodyRaw)
            }, BorderLayout.NORTH)
            add(JBScrollPane(bodyArea), BorderLayout.CENTER)
        }
    }

    fun loadRequest(request: ApiRequest) {
        currentRequest = request
        methodCombo.selectedItem = request.method
        urlField.text = request.url

        paramsModel.rowCount = 0
        request.params.forEach { paramsModel.addRow(arrayOf(it.key, it.value)) }

        headersModel.rowCount = 0
        request.headers.forEach { headersModel.addRow(arrayOf(it.key, it.value)) }

        bodyArea.text = request.body
        when (request.bodyType) {
            "json" -> { bodyJson.isSelected = true; bodyArea.isEnabled = true }
            "form" -> { bodyForm.isSelected = true; bodyArea.isEnabled = true }
            "raw"  -> { bodyRaw.isSelected = true;  bodyArea.isEnabled = true }
            else   -> { bodyNone.isSelected = true;  bodyArea.isEnabled = false }
        }
    }

    private fun saveToCurrentRequest() {
        val req = currentRequest ?: return
        req.method = methodCombo.selectedItem as String
        req.url = urlField.text
        req.params = (0 until paramsModel.rowCount).map {
            KeyValueParam().apply {
                key = paramsModel.getValueAt(it, 0) as? String ?: ""
                value = paramsModel.getValueAt(it, 1) as? String ?: ""
            }
        }.toCollection(ArrayList())
        req.headers = (0 until headersModel.rowCount).map {
            KeyValueParam().apply {
                key = headersModel.getValueAt(it, 0) as? String ?: ""
                value = headersModel.getValueAt(it, 1) as? String ?: ""
            }
        }.toCollection(ArrayList())
        req.bodyType = when {
            bodyJson.isSelected -> "json"
            bodyForm.isSelected -> "form"
            bodyRaw.isSelected  -> "raw"
            else                -> "none"
        }
        req.body = bodyArea.text
    }

    private fun sendRequest() {
        saveToCurrentRequest()
        val req = currentRequest ?: return
        val urlStr = buildUrlWithParams(req)
        if (urlStr.isBlank()) { responsePanel.showError("URL 不能为空"); return }

        sendButton.isEnabled = false
        responsePanel.showLoading()

        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(30))
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build()

                val builder = JavaHttpRequest.newBuilder()
                    .uri(URI.create(urlStr))
                    .timeout(Duration.ofSeconds(30))

                req.headers.filter { it.key.isNotBlank() }.forEach { builder.header(it.key, it.value) }

                val bodyPublisher = when (req.bodyType) {
                    "json" -> {
                        builder.header("Content-Type", "application/json")
                        JavaHttpRequest.BodyPublishers.ofString(req.body)
                    }
                    "form" -> {
                        builder.header("Content-Type", "application/x-www-form-urlencoded")
                        JavaHttpRequest.BodyPublishers.ofString(req.body)
                    }
                    "raw"  -> JavaHttpRequest.BodyPublishers.ofString(req.body)
                    else   -> JavaHttpRequest.BodyPublishers.noBody()
                }

                val httpReq = when (req.method) {
                    "GET"    -> builder.GET().build()
                    "DELETE" -> builder.DELETE().build()
                    else     -> builder.method(req.method, bodyPublisher).build()
                }

                val t0 = System.currentTimeMillis()
                val response = client.send(httpReq, HttpResponse.BodyHandlers.ofString())
                val elapsed = System.currentTimeMillis() - t0

                SwingUtilities.invokeLater {
                    responsePanel.showResponse(response, elapsed)
                    sendButton.isEnabled = true
                }
            } catch (e: Exception) {
                SwingUtilities.invokeLater {
                    responsePanel.showError(e.message ?: "请求失败")
                    sendButton.isEnabled = true
                }
            }
        }
    }

    private fun buildUrlWithParams(req: ApiRequest): String {
        val base = req.url.trim()
        val query = req.params.filter { it.key.isNotBlank() }
            .joinToString("&") { "${it.key}=${it.value}" }
        if (query.isEmpty()) return base
        return base + (if ('?' in base) "&" else "?") + query
    }
}
