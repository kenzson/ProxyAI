package ee.carlrobert.codegpt.settings.mcp

import com.intellij.openapi.options.Configurable
import ee.carlrobert.codegpt.settings.mcp.form.McpForm
import javax.swing.JComponent

class McpConfigurable : Configurable {

    private lateinit var component: McpForm

    override fun getDisplayName(): String {
        return "ProxyAI: MCP Servers"
    }

    override fun createComponent(): JComponent {
        component = McpForm()
        return component.createPanel()
    }

    override fun isModified(): Boolean = component.isModified()

    override fun apply() {
        component.applyChanges()
    }

    override fun reset() {
        component.resetChanges()
    }
}