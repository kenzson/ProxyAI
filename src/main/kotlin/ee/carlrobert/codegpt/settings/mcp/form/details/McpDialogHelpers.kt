package ee.carlrobert.codegpt.settings.mcp.form.details

import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.*
import javax.swing.JComponent

class SingleInputDialog(
    title: String,
    private val label: String,
    initialValue: String
) : DialogWrapper(true) {
    
    private val inputField = JBTextField(initialValue)
    
    val inputValue: String
        get() = inputField.text
    
    init {
        this.title = title
        init()
    }
    
    override fun createCenterPanel(): JComponent {
        return panel {
            row(label) {
                cell(inputField)
                    .columns(COLUMNS_LARGE)
                    .focused()
            }
        }
    }
    
    override fun doValidate(): ValidationInfo? {
        return if (inputField.text.trim().isEmpty()) {
            ValidationInfo("Value cannot be empty", inputField)
        } else null
    }
}

class EnvironmentVariableDialog(
    title: String,
    initialName: String,
    initialValue: String
) : DialogWrapper(true) {
    
    private val nameField = JBTextField(initialName)
    private val valueField = JBTextField(initialValue)
    
    val variableName: String
        get() = nameField.text
    
    val variableValue: String
        get() = valueField.text
    
    init {
        this.title = title
        init()
    }
    
    override fun createCenterPanel(): JComponent {
        return panel {
            row("Variable name:") {
                cell(nameField)
                    .columns(COLUMNS_MEDIUM)
                    .focused()
            }
            row("Value:") {
                cell(valueField)
                    .columns(COLUMNS_MEDIUM)
            }
        }
    }
    
    override fun doValidate(): ValidationInfo? {
        return when {
            nameField.text.trim().isEmpty() -> ValidationInfo("Variable name cannot be empty", nameField)
            nameField.text.contains('=') -> ValidationInfo("Variable name cannot contain '=' character", nameField)
            else -> null
        }
    }
}