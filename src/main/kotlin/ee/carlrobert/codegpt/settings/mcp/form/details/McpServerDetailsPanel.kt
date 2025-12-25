package ee.carlrobert.codegpt.settings.mcp.form.details

import com.intellij.icons.AllIcons
import com.intellij.openapi.components.service
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.CardLayoutPanel
import com.intellij.ui.CollectionListModel
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBList
import com.intellij.ui.dsl.builder.*
import com.intellij.util.ui.JBUI
import ee.carlrobert.codegpt.settings.mcp.McpSettings
import java.awt.Component
import javax.swing.DefaultListCellRenderer
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JList

class McpServerDetailsPanel : McpDetailsPanel {

    private val cardLayoutPanel =
        object : CardLayoutPanel<McpServerDetails, McpServerDetails, JComponent>() {
            override fun prepare(key: McpServerDetails): McpServerDetails = key

            override fun create(details: McpServerDetails): JComponent {
                return ServerEditorPanel(details).getPanel()
            }
        }

    init {
        service<McpSettings>().state.servers.forEach {
            cardLayoutPanel.select(McpServerDetails(it), true)
        }
    }

    override fun getPanel() = cardLayoutPanel

    override fun updateData(details: McpServerDetails) {
        cardLayoutPanel.select(details, true)
    }

    override fun remove(details: McpServerDetails) {
        cardLayoutPanel.remove(cardLayoutPanel.getValue(details, false))
    }

    private class ServerEditorPanel(private val details: McpServerDetails) {

        private val argumentsListModel = CollectionListModel(details.arguments)
        private val argumentsList = JBList(argumentsListModel).apply {
            cellRenderer = ServerEditorListCellRenderer(AllIcons.Nodes.Parameter)
            emptyText.text = "No arguments configured"
        }

        private val envVarsListModel = CollectionListModel(
            details.environmentVariables.map { "${it.key}=${it.value}" }.toMutableList()
        )
        private val envVarsList = JBList(envVarsListModel).apply {
            cellRenderer = ServerEditorListCellRenderer(AllIcons.Nodes.Variable)
            emptyText.text = "No environment variables configured"
        }

        fun getPanel(): DialogPanel = panel {
            row {
                label("Configure your MCP server settings below:")
            }.bottomGap(BottomGap.MEDIUM)

            group("Basic Configuration") {
                row("Name:") {
                    textField()
                        .bindText({ details.name }, { details.name = it })
                        .columns(COLUMNS_MEDIUM)
                        .validationOnApply {
                            if (it.text.isBlank()) ValidationInfo("Server name is required", it)
                            else null
                        }
                }.rowComment("A descriptive name for this MCP server")

                row("Command:") {
                    textField()
                        .bindText({ details.command }, { details.command = it })
                        .columns(COLUMNS_MEDIUM)
                        .validationOnApply {
                            if (it.text.isBlank()) ValidationInfo("Command is required", it)
                            else null
                        }
                }.rowComment("Executable command (e.g., npx, python, node)")
            }

            group("Command Arguments") {
                row {
                    cell(createArgumentsPanel()).align(Align.FILL)
                }.resizableRow()
                    .rowComment("Arguments passed to the server command")
            }

            group("Environment Variables") {
                row {
                    cell(createEnvironmentPanel()).align(Align.FILL)
                }.resizableRow()
                    .rowComment("Environment variables for the server process")
            }
        }

        private fun createArgumentsPanel(): JComponent {
            return ToolbarDecorator.createDecorator(argumentsList)
                .setAddAction { addArgument() }
                .setRemoveAction { removeSelectedArgument() }
                .setEditAction { editSelectedArgument() }
                .setAddActionName("Add Argument")
                .setRemoveActionName("Remove Argument")
                .setEditActionName("Edit Argument")
                .setPreferredSize(JBUI.size(400, 120))
                .createPanel()
        }

        private fun createEnvironmentPanel(): JComponent {
            return ToolbarDecorator.createDecorator(envVarsList)
                .setAddAction { addEnvironmentVariable() }
                .setRemoveAction { removeSelectedEnvironmentVariable() }
                .setEditAction { editSelectedEnvironmentVariable() }
                .setAddActionName("Add Variable")
                .setRemoveActionName("Remove Variable")
                .setEditActionName("Edit Variable")
                .setPreferredSize(JBUI.size(400, 120))
                .createPanel()
        }

        private fun addArgument() {
            val dialog = SingleInputDialog("Add Argument", "Argument:", "")
            if (dialog.showAndGet()) {
                val argument = dialog.inputValue.trim()
                if (argument.isNotEmpty()) {
                    details.arguments.add(argument)
                    argumentsListModel.add(argument)
                }
            }
        }

        private fun removeSelectedArgument() {
            val selectedIndex = argumentsList.selectedIndex
            if (selectedIndex >= 0) {
                val removed = details.arguments.removeAt(selectedIndex)
                argumentsListModel.remove(removed)
            }
        }

        private fun editSelectedArgument() {
            val selectedIndex = argumentsList.selectedIndex
            if (selectedIndex >= 0) {
                val currentValue = details.arguments[selectedIndex]
                val dialog = SingleInputDialog("Edit Argument", "Argument:", currentValue)
                if (dialog.showAndGet()) {
                    val newValue = dialog.inputValue.trim()
                    if (newValue.isNotEmpty() && newValue != currentValue) {
                        details.arguments[selectedIndex] = newValue
                        argumentsListModel.setElementAt(newValue, selectedIndex)
                    }
                }
            }
        }

        private fun addEnvironmentVariable() {
            val dialog = EnvironmentVariableDialog("Add Environment Variable", "", "")
            if (dialog.showAndGet()) {
                val name = dialog.variableName.trim()
                val value = dialog.variableValue.trim()
                if (name.isNotEmpty()) {
                    details.environmentVariables[name] = value
                    envVarsListModel.add("$name=$value")
                }
            }
        }

        private fun removeSelectedEnvironmentVariable() {
            val selectedIndex = envVarsList.selectedIndex
            if (selectedIndex >= 0) {
                val selectedItem = envVarsListModel.getElementAt(selectedIndex)
                val key = selectedItem.substringBefore('=')
                details.environmentVariables.remove(key)
                envVarsListModel.remove(selectedIndex)
            }
        }

        private fun editSelectedEnvironmentVariable() {
            val selectedIndex = envVarsList.selectedIndex
            if (selectedIndex >= 0) {
                val selectedItem = envVarsListModel.getElementAt(selectedIndex)
                val parts = selectedItem.split('=', limit = 2)
                val currentName = parts[0]
                val currentValue = if (parts.size > 1) parts[1] else ""

                val dialog = EnvironmentVariableDialog(
                    "Edit Environment Variable",
                    currentName,
                    currentValue
                )
                if (dialog.showAndGet()) {
                    val newName = dialog.variableName.trim()
                    val newValue = dialog.variableValue.trim()

                    if (newName.isNotEmpty()) {
                        details.environmentVariables.remove(currentName)
                        details.environmentVariables[newName] = newValue
                        envVarsListModel.setElementAt("$newName=$newValue", selectedIndex)
                    }
                }
            }
        }

        private class ServerEditorListCellRenderer(private val cellIcon: Icon) : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: JList<*>?,
                value: Any?,
                index: Int,
                isSelected: Boolean,
                cellHasFocus: Boolean
            ): Component {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
                icon = cellIcon
                text = value?.toString() ?: ""
                return this
            }
        }
    }
}