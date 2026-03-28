package com.github.fuqiangrepository.unionapiintellijplatformplugin.toolWindow

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import javax.swing.JSplitPane

class ApiToolWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val responsePanel = ResponsePanel()
        val requestPanel = RequestPanel(project, responsePanel)
        val collectionPanel = CollectionPanel(project, requestPanel)

        val rightSplit = JSplitPane(JSplitPane.VERTICAL_SPLIT, requestPanel, responsePanel).apply {
            dividerLocation = 300
            dividerSize = 0
            resizeWeight = 0.5
            border = null
        }

        val mainSplit = JSplitPane(JSplitPane.VERTICAL_SPLIT, collectionPanel, rightSplit).apply {
            dividerLocation = 200
            dividerSize = 0
            resizeWeight = 0.3
            border = null
        }

        val content = ContentFactory.getInstance().createContent(mainSplit, null, false)
        toolWindow.contentManager.addContent(content)
    }

    override fun shouldBeAvailable(project: Project) = true
}
