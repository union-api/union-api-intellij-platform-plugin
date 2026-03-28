package com.github.fuqiangrepository.unionapiintellijplatformplugin.toolWindow

import com.github.fuqiangrepository.unionapiintellijplatformplugin.model.ApiRequest
import com.intellij.util.ui.JBUI
import java.awt.*
import javax.swing.*

class AuthPanel : JPanel(BorderLayout()) {

    private val authTypeCombo = JComboBox(arrayOf("none", "bearer", "basic", "apiKey"))
    private val cardPanel = JPanel(CardLayout())

    // Bearer Token fields
    private val bearerTokenField = JTextField()

    // Basic Auth fields
    private val basicUsernameField = JTextField()
    private val basicPasswordField = JPasswordField()

    // API Key fields
    private val apiKeyNameField = JTextField()
    private val apiKeyValueField = JTextField()
    private val apiKeyLocationCombo = JComboBox(arrayOf("header", "query"))

    init {
        setupUI()
    }

    private fun setupUI() {
        val topBar = JPanel(FlowLayout(FlowLayout.LEFT)).apply {
            add(JLabel("Auth Type:"))
            add(authTypeCombo)
        }

        cardPanel.add(buildNoneCard(), "none")
        cardPanel.add(buildBearerCard(), "bearer")
        cardPanel.add(buildBasicCard(), "basic")
        cardPanel.add(buildApiKeyCard(), "apiKey")

        add(topBar, BorderLayout.NORTH)
        add(cardPanel, BorderLayout.CENTER)

        authTypeCombo.addActionListener {
            val selected = authTypeCombo.selectedItem as String
            (cardPanel.layout as CardLayout).show(cardPanel, selected)
        }
    }

    private fun buildNoneCard(): JPanel = JPanel().apply {
        add(JLabel("No authentication"))
    }

    private fun buildBearerCard(): JPanel {
        return JPanel(GridBagLayout()).apply {
            border = JBUI.Borders.empty(8)
            val gbc = GridBagConstraints().apply {
                insets = JBUI.insets(4)
                fill = GridBagConstraints.HORIZONTAL
            }
            gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0.0
            add(JLabel("Token:"), gbc)
            gbc.gridx = 1; gbc.weightx = 1.0
            add(bearerTokenField, gbc)
        }
    }

    private fun buildBasicCard(): JPanel {
        return JPanel(GridBagLayout()).apply {
            border = JBUI.Borders.empty(8)
            val gbc = GridBagConstraints().apply {
                insets = JBUI.insets(4)
                fill = GridBagConstraints.HORIZONTAL
            }
            gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0.0
            add(JLabel("Username:"), gbc)
            gbc.gridx = 1; gbc.weightx = 1.0
            add(basicUsernameField, gbc)

            gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0.0
            add(JLabel("Password:"), gbc)
            gbc.gridx = 1; gbc.weightx = 1.0
            add(basicPasswordField, gbc)
        }
    }

    private fun buildApiKeyCard(): JPanel {
        return JPanel(GridBagLayout()).apply {
            border = JBUI.Borders.empty(8)
            val gbc = GridBagConstraints().apply {
                insets = JBUI.insets(4)
                fill = GridBagConstraints.HORIZONTAL
            }
            gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0.0
            add(JLabel("Key:"), gbc)
            gbc.gridx = 1; gbc.weightx = 1.0
            add(apiKeyNameField, gbc)

            gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0.0
            add(JLabel("Value:"), gbc)
            gbc.gridx = 1; gbc.weightx = 1.0
            add(apiKeyValueField, gbc)

            gbc.gridx = 0; gbc.gridy = 2; gbc.weightx = 0.0
            add(JLabel("Add to:"), gbc)
            gbc.gridx = 1; gbc.weightx = 0.0
            add(apiKeyLocationCombo, gbc)
        }
    }

    fun loadAuth(request: ApiRequest) {
        authTypeCombo.selectedItem = request.authType
        (cardPanel.layout as CardLayout).show(cardPanel, request.authType)

        bearerTokenField.text = request.authData["token"] ?: ""
        basicUsernameField.text = request.authData["username"] ?: ""
        basicPasswordField.text = request.authData["password"] ?: ""
        apiKeyNameField.text = request.authData["keyName"] ?: ""
        apiKeyValueField.text = request.authData["keyValue"] ?: ""
        apiKeyLocationCombo.selectedItem = request.authData["location"] ?: "header"
    }

    fun saveAuth(request: ApiRequest) {
        request.authType = authTypeCombo.selectedItem as String
        request.authData.clear()
        when (request.authType) {
            "bearer" -> {
                request.authData["token"] = bearerTokenField.text
            }
            "basic" -> {
                request.authData["username"] = basicUsernameField.text
                request.authData["password"] = String(basicPasswordField.password)
            }
            "apiKey" -> {
                request.authData["keyName"] = apiKeyNameField.text
                request.authData["keyValue"] = apiKeyValueField.text
                request.authData["location"] = apiKeyLocationCombo.selectedItem as String
            }
        }
    }
}
