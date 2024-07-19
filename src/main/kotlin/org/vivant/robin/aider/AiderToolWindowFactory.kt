package org.vivant.robin.aider

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.content.ContentFactory

class AiderToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val content = ContentFactory.getInstance().createContent(
            JBScrollPane(
                JBTextArea("Welcome to Aider Inspector").apply {
                    isEditable = false
                }
            ),
            "Aider Output",
            false
        )
        toolWindow.contentManager.addContent(content)
    }
}

fun updateToolWindowContent(toolWindow: ToolWindow, output: String) {
    val existingContent = toolWindow.contentManager.getContent(0)
    if (existingContent != null) {
        val component = existingContent.component
        if (component is JBScrollPane) {
            val textArea = component.viewport.view as? JBTextArea
            if (textArea != null) {
                textArea.text = output
                return
            }
        }
    }

    // If we couldn't update the existing content, create a new one
    val newContent = ContentFactory.getInstance().createContent(
        JBScrollPane(
            JBTextArea(output).apply {
                isEditable = false
            }
        ),
        "Aider Output",
        false
    )

    toolWindow.contentManager.removeAllContents(true)
    toolWindow.contentManager.addContent(newContent)
    toolWindow.show()
}
