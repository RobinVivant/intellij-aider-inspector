package org.vivant.robin.aider

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

class AiderToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        // Initial content creation is not needed here
    }

    companion object {
        fun updateContent(toolWindow: ToolWindow, output: String) {
            val content = ContentFactory.getInstance().createContent(
                com.intellij.ui.components.JBScrollPane(
                    com.intellij.ui.components.JBTextArea(output).apply {
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
    }
}
