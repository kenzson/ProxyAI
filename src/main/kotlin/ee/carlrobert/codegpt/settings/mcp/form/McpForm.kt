package ee.carlrobert.codegpt.settings.mcp.form

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.components.service
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.DialogBuilder
import com.intellij.openapi.ui.DialogWrapper.OK_EXIT_CODE
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.ui.TextBrowseFolderListener
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.BottomGap
import com.intellij.ui.render.LabelBasedRenderer
import com.intellij.ui.treeStructure.SimpleTree
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.components.BorderLayoutPanel
import ee.carlrobert.codegpt.settings.mcp.McpClientManager
import ee.carlrobert.codegpt.settings.mcp.McpSettings
import ee.carlrobert.codegpt.settings.mcp.McpSettingsState
import ee.carlrobert.codegpt.settings.mcp.form.McpFormUtil.toState
import ee.carlrobert.codegpt.settings.mcp.form.details.McpServerDetails
import ee.carlrobert.codegpt.settings.mcp.form.details.McpServerDetailsPanel
import ee.carlrobert.codegpt.ui.OverlayUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.awt.Component
import java.awt.Font
import javax.swing.JComponent
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath
import javax.swing.tree.TreeSelectionModel

class McpServerTreeNode(val details: McpServerDetails) : DefaultMutableTreeNode() {
    override fun toString(): String = details.name
}

class McpForm {
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val serverDetailsPanel = McpServerDetailsPanel()
    private val serversNode = DefaultMutableTreeNode("MCP Servers")
    private val root = DefaultMutableTreeNode("Root").apply { add(serversNode) }
    private val treeModel = DefaultTreeModel(root)
    private val tree = SimpleTree(treeModel).apply {
        isRootVisible = false
        selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION
        cellRenderer = McpTreeCellRenderer()

        setupChildNodes()

        addTreeSelectionListener { e ->
            val node = e.newLeadSelectionPath?.lastPathComponent as? McpServerTreeNode
            node?.let {
                serverDetailsPanel.updateData(it.details)
            }
        }
    }

    private val project = ProjectManager.getInstance().defaultProject
    private val mcpFileProvider = McpFileProvider()

    init {
        runInEdt(ModalityState.any()) {
            expandAll()
            selectFirstServer()
        }
    }

    private fun createHeaderPanel() = com.intellij.ui.dsl.builder.panel {
        row {
            label("MCP Servers")
                .applyToComponent {
                    font = JBUI.Fonts.label().deriveFont(Font.BOLD, 16f)
                }
                .gap(com.intellij.ui.dsl.builder.RightGap.COLUMNS)

            button("Import from JSON") { importFromJson() }
                .gap(com.intellij.ui.dsl.builder.RightGap.SMALL)
            button("Import from File") { importSettingsFromFile() }
                .gap(com.intellij.ui.dsl.builder.RightGap.SMALL)
            button("Export") { exportSettingsToFile() }
        }

        row {
            text("Connect AI assistants to external tools and data sources")
                .applyToComponent {
                    foreground = JBUI.CurrentTheme.ContextHelp.FOREGROUND
                    font = JBUI.Fonts.smallFont()
                }
        }.bottomGap(BottomGap.MEDIUM)
    }

    fun createPanel(): JComponent {
        return BorderLayoutPanel(8, 0)
            .addToTop(createHeaderPanel())
            .addToLeft(createToolbarDecorator().createPanel())
            .addToCenter(serverDetailsPanel.getPanel())
    }

    fun isModified(): Boolean {
        val settings = service<McpSettings>().state
        val formServers = getFormServers()

        if (settings.servers.size != formServers.size) return true

        return !settings.servers.zip(formServers).all { (state, form) ->
            state.id == form.id &&
                    state.name == form.name &&
                    state.command == form.command &&
                    state.arguments == form.arguments &&
                    state.environmentVariables == form.environmentVariables
        }
    }

    fun applyChanges() {
        val settings = service<McpSettings>().state
        settings.servers = getFormServers().map { it.toState() }.toMutableList()
    }

    fun resetChanges() {
        removeAllChildNodes()
        setupChildNodes()
        reloadTreeView()
    }

    private fun getFormServers(): List<McpServerDetails> {
        return serversNode.children().toList()
            .filterIsInstance<McpServerTreeNode>()
            .map { it.details }
    }

    private fun setupChildNodes() {
        service<McpSettings>().state.servers.forEach {
            serversNode.add(McpServerTreeNode(McpServerDetails(it)))
        }
    }

    private fun createToolbarDecorator(): ToolbarDecorator =
        ToolbarDecorator.createDecorator(tree)
            .setPreferredSize(java.awt.Dimension(300, 0))
            .setAddAction { handleAddAction() }
            .setRemoveAction { handleRemoveAction() }
            .setRemoveActionUpdater {
                tree.selectionPath?.lastPathComponent is McpServerTreeNode
            }
            .addExtraAction(object :
                AnAction("Test Connection", "Test server connection", AllIcons.Actions.Execute) {
                override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

                override fun update(e: AnActionEvent) {
                    val node = tree.selectionPath?.lastPathComponent as? McpServerTreeNode
                    e.presentation.isEnabled = node != null
                }

                override fun actionPerformed(e: AnActionEvent) {
                    handleTestConnection()
                }
            })
            .addExtraAction(object : AnAction(
                "View JSON",
                "View configuration as JSON",
                AllIcons.FileTypes.Json
            ) {
                override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

                override fun update(e: AnActionEvent) {
                    e.presentation.isEnabled =
                        tree.selectionPath?.lastPathComponent is McpServerTreeNode
                }

                override fun actionPerformed(e: AnActionEvent) {
                    viewServerAsJson()
                }
            })
            .disableUpDownActions()

    private fun handleAddAction() {
        val nextId = (getFormServers().maxOfOrNull { it.id } ?: 0) + 1
        val newServer = McpServerDetails(
            id = nextId,
            name = "New MCP Server",
            command = "npx",
            arguments = mutableListOf(),
            environmentVariables = mutableMapOf()
        )
        val newNode = McpServerTreeNode(newServer)
        insertAndSelectNode(newNode)
    }

    private fun handleRemoveAction() {
        val selectedNode = tree.selectionPath?.lastPathComponent as? McpServerTreeNode ?: return
        treeModel.removeNodeFromParent(selectedNode)
        serverDetailsPanel.remove(selectedNode.details)
    }

    private fun handleTestConnection() {
        val selectedNode = tree.selectionPath?.lastPathComponent as? McpServerTreeNode ?: return
        val server = selectedNode.details
        val dialog = McpConnectionTestResultDialog(server.name, null, server.command)

        coroutineScope.launch(Dispatchers.IO) {
            try {
                val serverState = server.toState()
                val testResult = service<McpClientManager>().testConnection(serverState)

                runInEdt(ModalityState.any()) { dialog.updateResult(testResult) }
            } catch (e: Exception) {
                val testResult = McpClientManager.ConnectionTestResult(
                    success = false,
                    errorMessage = e.message ?: "Unknown error occurred"
                )
                runInEdt(ModalityState.any()) {
                    dialog.updateResult(testResult)
                }
            }
        }

        dialog.show()
    }

    private fun insertAndSelectNode(newNode: McpServerTreeNode) {
        treeModel.insertNodeInto(newNode, serversNode, serversNode.childCount)
        tree.selectionPath = TreePath(newNode.path)
    }

    private fun removeAllChildNodes() {
        serversNode.removeAllChildren()
    }

    private fun reloadTreeView() {
        treeModel.reload()
        expandAll()
        selectFirstServer()
    }

    private fun expandAll() {
        tree.expandPath(TreePath(serversNode.path))
    }

    private fun selectFirstServer() {
        val firstServer = serversNode.getFirstChild() as? McpServerTreeNode
        firstServer?.let {
            tree.selectionPath = TreePath(it.path)
        }
    }

    private fun exportSettingsToFile() {
        val defaultFileName = "mcp-servers.json"
        val settings = service<McpSettings>().state

        val fileNameTextField = JBTextField(defaultFileName).apply { columns = 20 }
        val fileChooserDescriptor =
            FileChooserDescriptorFactory.createSingleFolderDescriptor().apply {
                isForcedToUseIdeaFileChooser = true
            }
        val textFieldWithBrowseButton = TextFieldWithBrowseButton().apply {
            text = project.basePath ?: System.getProperty("user.home")
            addBrowseFolderListener(TextBrowseFolderListener(fileChooserDescriptor, project))
        }

        val result = createExportDialog(fileNameTextField, textFieldWithBrowseButton).show()
        val fileName = fileNameTextField.text.ifEmpty { defaultFileName }
        val filePath = textFieldWithBrowseButton.text

        if (result == OK_EXIT_CODE) {
            val fullFilePath = "$filePath/$fileName"
            coroutineScope.launch {
                runCatching {
                    mcpFileProvider.writeSettings(fullFilePath, settings)
                }.onFailure {
                    showExportErrorMessage()
                }
            }
        }
    }

    private fun importSettingsFromFile() {
        val fileChooserDescriptor = FileChooserDescriptorFactory
            .createSingleFileDescriptor("json")
            .apply { isForcedToUseIdeaFileChooser = true }

        FileChooser.chooseFile(fileChooserDescriptor, project, null)?.let { file ->
            ReadAction.nonBlocking<McpSettingsState> {
                file.canonicalPath?.let { mcpFileProvider.readFromFile(it) }
            }
                .inSmartMode(project)
                .finishOnUiThread(ModalityState.defaultModalityState()) { settings ->
                    insertServers(settings.servers)
                    reloadTreeView()
                }
                .submit(AppExecutorUtil.getAppExecutorService())
                .onError { showImportErrorMessage() }
        }
    }

    private fun insertServers(servers: List<ee.carlrobert.codegpt.settings.mcp.McpServerDetailsState>) {
        serversNode.removeAllChildren()
        servers.forEachIndexed { index, server ->
            val node = McpServerTreeNode(McpServerDetails(server))
            treeModel.insertNodeInto(node, serversNode, index)
        }
    }

    private fun createExportDialog(
        fileNameTextField: JBTextField,
        filePathButton: TextFieldWithBrowseButton
    ): DialogBuilder {
        val form = FormBuilder.createFormBuilder()
            .addLabeledComponent("File name:", fileNameTextField)
            .addLabeledComponent("Save to:", filePathButton)
            .panel

        return DialogBuilder().apply {
            setTitle("Export MCP Settings")
            centerPanel(form)
            addOkAction()
            addCancelAction()
        }
    }

    private fun showExportErrorMessage() {
        OverlayUtil.showBalloon(
            "Failed to export MCP settings",
            MessageType.ERROR,
            tree
        )
    }

    private fun showImportErrorMessage() {
        OverlayUtil.showBalloon(
            "Failed to import MCP settings",
            MessageType.ERROR,
            tree
        )
    }

    private fun importFromJson() {
        val dialog = McpJsonImportDialog()
        if (dialog.showAndGet()) {
            val importedServers = dialog.importedServers
            if (importedServers.isNotEmpty()) {
                importedServers.forEach { server ->
                    val newNode = McpServerTreeNode(server)
                    insertAndSelectNode(newNode)
                }

                val message = if (importedServers.size == 1) {
                    "Successfully imported 1 MCP server"
                } else {
                    "Successfully imported ${importedServers.size} MCP servers"
                }

                OverlayUtil.showBalloon(
                    message,
                    MessageType.INFO,
                    tree
                )

                if (importedServers.isNotEmpty()) {
                    val firstImportedNode = serversNode.children().toList()
                        .filterIsInstance<McpServerTreeNode>()
                        .find { it.details.id == importedServers.first().id }
                    firstImportedNode?.let {
                        tree.selectionPath = TreePath(it.path)
                    }
                }
            }
        }
    }

    private fun viewServerAsJson() {
        val selectedNode = tree.selectionPath?.lastPathComponent as? McpServerTreeNode ?: return
        val server = selectedNode.details

        val dialog = McpJsonViewDialog(server)
        dialog.show()
    }

    private class McpTreeCellRenderer : LabelBasedRenderer.Tree() {
        override fun getTreeCellRendererComponent(
            tree: JTree,
            value: Any?,
            selected: Boolean,
            expanded: Boolean,
            leaf: Boolean,
            row: Int,
            focused: Boolean
        ): Component {
            super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, focused)

            if (value is McpServerTreeNode) {
                icon = AllIcons.Nodes.Services
                iconTextGap = 6
            } else {
                icon = AllIcons.Nodes.Folder
            }

            return this
        }
    }
}