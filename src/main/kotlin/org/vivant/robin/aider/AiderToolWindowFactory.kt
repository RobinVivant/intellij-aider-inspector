package org.vivant.robin.aider

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea

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
    val content = ContentFactory.getInstance().createContent(
        JBScrollPane(
            JBTextArea(output).apply {
                isEditable = false
            }
        ),
        "Aider Output",
        false
    )

    toolWindow.contentManager.removeAllContents(true)
    toolWindow.contentManager.addContent(content)
    toolWindow.show()
}
