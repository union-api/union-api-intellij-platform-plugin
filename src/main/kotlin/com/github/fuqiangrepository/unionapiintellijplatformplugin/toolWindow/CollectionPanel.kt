package com.github.fuqiangrepository.unionapiintellijplatformplugin.toolWindow

import com.github.fuqiangrepository.unionapiintellijplatformplugin.model.ApiCollection
import com.github.fuqiangrepository.unionapiintellijplatformplugin.model.ApiRequest
import com.github.fuqiangrepository.unionapiintellijplatformplugin.services.ApiStateService
import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBScrollPane
import java.awt.BorderLayout
import java.awt.Component
import javax.swing.*
import javax.swing.tree.*

class CollectionPanel(
    private val project: Project,
    private val requestPanel: RequestPanel
) : JPanel(BorderLayout()) {

    private val stateService = ApiStateService.getInstance(project)
    private val root = DefaultMutableTreeNode("root")
    private val treeModel = DefaultTreeModel(root)
    private val tree = JTree(treeModel)

    init {
        setupToolbar()
        setupTree()
        loadState()
    }

    private fun setupToolbar() {
        val toolbar = JPanel().apply { layout = BoxLayout(this, BoxLayout.X_AXIS) }

        toolbar.add(iconButton(AllIcons.General.Add, "新建分组") { addCollection() })
        toolbar.add(iconButton(AllIcons.Actions.AddFile, "新建请求") { addRequest() })
        toolbar.add(iconButton(AllIcons.General.Remove, "删除") { deleteSelected() })
        toolbar.add(Box.createHorizontalGlue())

        add(toolbar, BorderLayout.NORTH)
    }

    private fun setupTree() {
        tree.isRootVisible = false
        tree.showsRootHandles = true
        tree.cellRenderer = ApiTreeCellRenderer()

        tree.addTreeSelectionListener {
            val node = tree.selectionPath?.lastPathComponent as? DefaultMutableTreeNode ?: return@addTreeSelectionListener
            if (node.userObject is ApiRequest) {
                requestPanel.loadRequest(node.userObject as ApiRequest)
            }
        }

        add(JBScrollPane(tree), BorderLayout.CENTER)
    }

    private fun addCollection() {
        val name = Messages.showInputDialog(project, "分组名称", "新建分组", null) ?: return
        val collection = ApiCollection().apply { this.name = name }
        stateService.state.collections.add(collection)
        val node = DefaultMutableTreeNode(collection)
        root.add(node)
        treeModel.reload()
        tree.expandPath(TreePath(root))
    }

    private fun addRequest() {
        val selectedNode = tree.selectionPath?.lastPathComponent as? DefaultMutableTreeNode
        val collectionNode: DefaultMutableTreeNode? = when {
            selectedNode?.userObject is ApiCollection -> selectedNode
            selectedNode?.userObject is ApiRequest -> selectedNode.parent as? DefaultMutableTreeNode
            else -> null
        }
        val collection = collectionNode?.userObject as? ApiCollection
        if (collection == null) {
            Messages.showInfoMessage(project, "请先选择一个分组", "提示")
            return
        }
        val name = Messages.showInputDialog(project, "请求名称", "新建请求", null) ?: return
        val request = ApiRequest().apply { this.name = name }
        collection.requests.add(request)
        val requestNode = DefaultMutableTreeNode(request)
        collectionNode.add(requestNode)
        treeModel.reload()
        tree.expandPath(TreePath(collectionNode.path))
        tree.selectionPath = TreePath(requestNode.path)
    }

    private fun deleteSelected() {
        val selectedNode = tree.selectionPath?.lastPathComponent as? DefaultMutableTreeNode ?: return
        when (val obj = selectedNode.userObject) {
            is ApiCollection -> {
                stateService.state.collections.remove(obj)
                root.remove(selectedNode)
            }
            is ApiRequest -> {
                val parentNode = selectedNode.parent as? DefaultMutableTreeNode
                (parentNode?.userObject as? ApiCollection)?.requests?.remove(obj)
                parentNode?.remove(selectedNode)
            }
        }
        treeModel.reload()
    }

    private fun loadState() {
        root.removeAllChildren()
        stateService.state.collections.forEach { collection ->
            val collectionNode = DefaultMutableTreeNode(collection)
            collection.requests.forEach { collectionNode.add(DefaultMutableTreeNode(it)) }
            root.add(collectionNode)
        }
        treeModel.reload()
    }

    private fun iconButton(icon: Icon, tooltip: String, action: () -> Unit) = JButton(icon).apply {
        toolTipText = tooltip
        isBorderPainted = false
        isContentAreaFilled = false
        addActionListener { action() }
    }

    private class ApiTreeCellRenderer : DefaultTreeCellRenderer() {
        override fun getTreeCellRendererComponent(
            tree: JTree, value: Any, sel: Boolean, expanded: Boolean,
            leaf: Boolean, row: Int, hasFocus: Boolean
        ): Component {
            super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus)
            val node = value as? DefaultMutableTreeNode
            when (val obj = node?.userObject) {
                is ApiCollection -> { text = obj.name; icon = AllIcons.Nodes.Folder }
                is ApiRequest -> { text = "[${obj.method}] ${obj.name}"; icon = AllIcons.Actions.Execute }
            }
            return this
        }
    }
}
