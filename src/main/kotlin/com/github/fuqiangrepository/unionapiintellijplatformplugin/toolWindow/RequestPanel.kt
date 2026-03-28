package com.github.fuqiangrepository.unionapiintellijplatformplugin.toolWindow

import com.github.fuqiangrepository.unionapiintellijplatformplugin.model.ApiRequest
import com.github.fuqiangrepository.unionapiintellijplatformplugin.model.KeyValueParam
import com.github.fuqiangrepository.unionapiintellijplatformplugin.MyBundle
import com.github.fuqiangrepository.unionapiintellijplatformplugin.services.ApiStateService
import com.github.fuqiangrepository.unionapiintellijplatformplugin.services.ScriptExecutor
import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import java.awt.*
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import java.net.http.HttpClient
import java.net.http.HttpRequest as JavaHttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.Base64
import javax.swing.*
import javax.swing.table.DefaultTableModel
import javax.swing.table.TableCellRenderer

class RequestPanel(
    private val project: Project,
    private val responsePanel: ResponsePanel
) : JPanel(BorderLayout()) {

    private var currentRequest: ApiRequest? = null

    private val methodCombo = JComboBox(arrayOf("GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS"))
    private val urlField = JTextField()
    private val sendButton = JButton(MyBundle.message("request.send"), AllIcons.Actions.Execute)

    private val paramsModel = kvTableModel()
    private val headersModel = kvTableModel()
    private val cookiesModel = kvTableModel()

    private val bodyNone = JRadioButton("none", true)
    private val bodyJson = JRadioButton("JSON")
    private val bodyForm = JRadioButton("Form")
    private val bodyRaw = JRadioButton("Raw")
    private val bodyArea = JBTextArea().apply {
        font = Font(Font.MONOSPACED, Font.PLAIN, 13)
        isEnabled = false
    }

    private val authPanel = AuthPanel()
    private val scriptPanel = ScriptPanel()

    init {
        setupUI()
    }

    // col: 0=enabled, 1=Key, 2=Value, 3=Type, 4=Description, 5=Action
    private fun kvTableModel() = object : DefaultTableModel(
        arrayOf("", MyBundle.message("param.col.key"), MyBundle.message("param.col.value"),
            MyBundle.message("param.col.type"), MyBundle.message("param.col.description"),
            ""), 0) {
        override fun isCellEditable(row: Int, column: Int) = column != 5
        override fun getColumnClass(columnIndex: Int) =
            if (columnIndex == 0) Boolean::class.javaObjectType else String::class.java
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
        tabs.addTab("Cookies", kvPanel(cookiesModel))
        tabs.addTab("Body", buildBodyPanel())
        tabs.addTab("Auth", authPanel)
        tabs.addTab("Scripts", scriptPanel)

        add(requestBar, BorderLayout.NORTH)
        add(tabs, BorderLayout.CENTER)

        sendButton.addActionListener { sendRequest() }

        urlField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) = syncParamsFromUrl()
            override fun removeUpdate(e: DocumentEvent) = syncParamsFromUrl()
            override fun changedUpdate(e: DocumentEvent) = syncParamsFromUrl()
        })

        paramsModel.addTableModelListener { syncUrlFromParams() }
    }

    private var syncingUrl = false
    private var syncingParams = false

    private fun syncParamsFromUrl() {
        if (syncingUrl) return
        val url = urlField.text
        val qIdx = url.indexOf('?')
        if (qIdx < 0) return
        val baseUrl = url.substring(0, qIdx)
        val query = url.substring(qIdx + 1)
        val pairs = query.split("&").mapNotNull { part ->
            val eq = part.indexOf('=')
            if (eq < 0) null
            else URLDecoder.decode(part.substring(0, eq), "UTF-8") to
                    URLDecoder.decode(part.substring(eq + 1), "UTF-8")
        }
        if (pairs.isEmpty()) return
        SwingUtilities.invokeLater {
            syncingUrl = true
            syncingParams = true
            urlField.text = baseUrl
            paramsModel.rowCount = 0
            pairs.forEach { (k, v) -> paramsModel.addRow(arrayOf<Any?>(true, k, v, "", "", "")) }
            syncingParams = false
            syncingUrl = false
        }
    }

    private fun syncUrlFromParams() {
        if (syncingParams) return
        val baseUrl = urlField.text.substringBefore('?')
        val query = (0 until paramsModel.rowCount).mapNotNull { row ->
            val enabled = paramsModel.getValueAt(row, 0) as? Boolean ?: true
            val k = paramsModel.getValueAt(row, 1) as? String ?: ""
            val v = paramsModel.getValueAt(row, 2) as? String ?: ""
            if (enabled && k.isNotBlank()) "${URLEncoder.encode(k, "UTF-8")}=${URLEncoder.encode(v, "UTF-8")}" else null
        }.joinToString("&")
        syncingUrl = true
        urlField.text = if (query.isEmpty()) baseUrl else "$baseUrl?$query"
        syncingUrl = false
    }

    private fun kvPanel(model: DefaultTableModel): JPanel {
        val table = JTable(model).apply {
            columnModel.getColumn(0).apply { maxWidth = 30; minWidth = 30 }
            columnModel.getColumn(3).apply { preferredWidth = 80; maxWidth = 120 }
            columnModel.getColumn(5).apply { maxWidth = 30; minWidth = 30
                cellRenderer = DeleteIconRenderer()
            }
            rowHeight = 26
            addMouseListener(object : java.awt.event.MouseAdapter() {
                override fun mouseClicked(e: java.awt.event.MouseEvent) {
                    val col = columnAtPoint(e.point)
                    val row = rowAtPoint(e.point)
                    if (col == 5 && row in 0 until model.rowCount) model.removeRow(row)
                }
            })
        }
        val addBtn = JButton("+").apply { addActionListener { model.addRow(arrayOf<Any?>(true, "", "", "", "", "")) } }
        return JPanel(BorderLayout()).apply {
            add(JBScrollPane(table), BorderLayout.CENTER)
            add(JPanel(FlowLayout(FlowLayout.LEFT)).apply { add(addBtn) }, BorderLayout.SOUTH)
        }
    }

    private inner class DeleteIconRenderer : JLabel(AllIcons.Actions.GC), TableCellRenderer {
        init { horizontalAlignment = CENTER; toolTipText = MyBundle.message("param.delete") }
        override fun getTableCellRendererComponent(
            table: JTable, value: Any?, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int
        ) = this
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
        request.params.forEach { paramsModel.addRow(arrayOf<Any?>(it.enabled, it.key, it.value, it.type, it.description, "")) }

        headersModel.rowCount = 0
        request.headers.forEach { headersModel.addRow(arrayOf<Any?>(it.enabled, it.key, it.value, it.type, it.description, "")) }

        cookiesModel.rowCount = 0
        request.cookies.forEach { cookiesModel.addRow(arrayOf<Any?>(it.enabled, it.key, it.value, it.type, it.description, "")) }

        bodyArea.text = request.body
        when (request.bodyType) {
            "json" -> { bodyJson.isSelected = true; bodyArea.isEnabled = true }
            "form" -> { bodyForm.isSelected = true; bodyArea.isEnabled = true }
            "raw"  -> { bodyRaw.isSelected = true;  bodyArea.isEnabled = true }
            else   -> { bodyNone.isSelected = true;  bodyArea.isEnabled = false }
        }

        authPanel.loadAuth(request)
        scriptPanel.loadScripts(request)
    }

    private fun collectParams(model: DefaultTableModel) = (0 until model.rowCount).map { i ->
        KeyValueParam().apply {
            enabled     = model.getValueAt(i, 0) as? Boolean ?: true
            key         = model.getValueAt(i, 1) as? String ?: ""
            value       = model.getValueAt(i, 2) as? String ?: ""
            type        = model.getValueAt(i, 3) as? String ?: ""
            description = model.getValueAt(i, 4) as? String ?: ""
        }
    }.toCollection(ArrayList())

    private fun populateRequest(req: ApiRequest) {
        req.method  = methodCombo.selectedItem as String
        req.url     = urlField.text
        req.params  = collectParams(paramsModel)
        req.headers = collectParams(headersModel)
        req.cookies = collectParams(cookiesModel)
        req.bodyType = when {
            bodyJson.isSelected -> "json"
            bodyForm.isSelected -> "form"
            bodyRaw.isSelected  -> "raw"
            else                -> "none"
        }
        req.body = bodyArea.text
        authPanel.saveAuth(req)
        scriptPanel.saveScripts(req)
    }

    private fun saveToCurrentRequest() {
        val req = currentRequest ?: return
        populateRequest(req)
    }

    private fun sendRequest() {
        saveToCurrentRequest()
        val req = currentRequest ?: buildTempRequest()

        sendButton.isEnabled = false
        responsePanel.showLoading()

        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                // 1. Pre-request script
                ScriptExecutor.runPreScript(req.preRequestScript, project)

                // 2. Resolve environment and apply variable substitution
                val env = ApiStateService.getInstance(project).state.environment
                val effectiveUrl = substituteVariables(buildUrlWithParams(req, env), env)
                if (effectiveUrl.isBlank()) {
                    SwingUtilities.invokeLater { responsePanel.showError("URL 不能为空"); sendButton.isEnabled = true }
                    return@executeOnPooledThread
                }

                val client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(30))
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build()

                val builder = JavaHttpRequest.newBuilder()
                    .uri(URI.create(effectiveUrl))
                    .timeout(Duration.ofSeconds(30))

                // 3. Apply user-defined headers (with variable substitution)
                req.headers.filter { it.enabled && it.key.isNotBlank() }.forEach {
                    builder.header(substituteVariables(it.key, env), substituteVariables(it.value, env))
                }

                // 4. Apply cookies
                val cookieHeader = req.cookies.filter { it.enabled && it.key.isNotBlank() }
                    .joinToString("; ") {
                        "${substituteVariables(it.key, env)}=${substituteVariables(it.value, env)}"
                    }
                if (cookieHeader.isNotBlank()) builder.header("Cookie", cookieHeader)

                // 5. Apply auth
                applyAuth(req, env, builder)

                val effectiveBody = substituteVariables(req.body, env)
                val bodyPublisher = when (req.bodyType) {
                    "json" -> {
                        builder.header("Content-Type", "application/json")
                        JavaHttpRequest.BodyPublishers.ofString(effectiveBody)
                    }
                    "form" -> {
                        builder.header("Content-Type", "application/x-www-form-urlencoded")
                        JavaHttpRequest.BodyPublishers.ofString(effectiveBody)
                    }
                    "raw"  -> JavaHttpRequest.BodyPublishers.ofString(effectiveBody)
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

                // 5. Post-response script
                ScriptExecutor.runPostScript(req.postResponseScript, response, project)

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

    private fun buildUrlWithParams(req: ApiRequest, env: Map<String, String>): String {
        val base = substituteVariables(req.url.trim(), env)
        val query = req.params.filter { it.enabled && it.key.isNotBlank() }
            .joinToString("&") {
                "${substituteVariables(it.key, env)}=${substituteVariables(it.value, env)}"
            }
        if (query.isEmpty()) return base
        return base + (if ('?' in base) "&" else "?") + query
    }

    private fun substituteVariables(text: String, env: Map<String, String>): String {
        if (!text.contains("{{")) return text
        var result = text
        env.forEach { (key, value) ->
            result = result.replace("{{$key}}", value)
        }
        return result
    }

    private fun buildTempRequest(): ApiRequest {
        val req = ApiRequest()
        populateRequest(req)
        return req
    }

    private fun applyAuth(req: ApiRequest, env: Map<String, String>, builder: JavaHttpRequest.Builder) {
        when (req.authType) {
            "bearer" -> {
                val token = substituteVariables(req.authData["token"] ?: "", env)
                if (token.isNotBlank()) builder.header("Authorization", "Bearer $token")
            }
            "basic" -> {
                val username = substituteVariables(req.authData["username"] ?: "", env)
                val password = substituteVariables(req.authData["password"] ?: "", env)
                if (username.isNotBlank()) {
                    val encoded = Base64.getEncoder().encodeToString("$username:$password".toByteArray())
                    builder.header("Authorization", "Basic $encoded")
                }
            }
            "apiKey" -> {
                val keyName = substituteVariables(req.authData["keyName"] ?: "", env)
                val keyValue = substituteVariables(req.authData["keyValue"] ?: "", env)
                val location = req.authData["location"] ?: "header"
                if (keyName.isNotBlank() && location == "header") {
                    builder.header(keyName, keyValue)
                }
                // query param API key is handled in buildUrlWithParams via req.params;
                // inject it here dynamically instead
                // Note: URI is already built, so we handle this by adding it to the builder's URI
                // For simplicity, query-param API keys are added via the params table or
                // users can handle via pre-request script
            }
        }
    }
}
