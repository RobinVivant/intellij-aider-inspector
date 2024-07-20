package org.vivant.robin.aider

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.content.ContentFactory
import java.awt.BorderLayout
import javax.swing.JPanel

class AiderToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = JPanel(BorderLayout())

        // Create the play button
        val actionManager = ActionManager.getInstance()
        val action = actionManager.getAction("org.vivant.robin.aider.AiderInspectionAction")
        val toolbar = actionManager.createActionToolbar(ActionPlaces.TOOLBAR, DefaultActionGroup(action), true)
        panel.add(toolbar.component, BorderLayout.NORTH)

        // Add the text area
        val textArea = JBTextArea("Welcome to Aider Inspector").apply {
            isEditable = false
        }
        panel.add(JBScrollPane(textArea), BorderLayout.CENTER)

        val content = ContentFactory.getInstance().createContent(panel, "Aider Output", false)
        toolWindow.contentManager.addContent(content)
    }
}

fun updateToolWindowContent(project: Project, toolWindow: ToolWindow, output: String) {
    val content = toolWindow.contentManager.getContent(0)
    if (content != null) {
        val panel = content.component as? JPanel
        if (panel != null) {
            val scrollPane = panel.getComponent(1) as? JBScrollPane
            val textArea = scrollPane?.viewport?.view as? JBTextArea
            if (textArea != null) {
                textArea.text = output
                return
            }
        }
    }

    // If we couldn't update the existing content, create a new one
    val panel = JPanel(BorderLayout())

    val actionManager = ActionManager.getInstance()
    val action = actionManager.getAction("org.vivant.robin.aider.AiderInspectionAction")
    val toolbar = actionManager.createActionToolbar(ActionPlaces.TOOLBAR, DefaultActionGroup(action), true)
    panel.add(toolbar.component, BorderLayout.NORTH)

    val textArea = JBTextArea(output).apply {
        isEditable = false
    }
    panel.add(JBScrollPane(textArea), BorderLayout.CENTER)

    val newContent = ContentFactory.getInstance().createContent(panel, "Aider Output", false)

    toolWindow.contentManager.removeAllContents(true)
    toolWindow.contentManager.addContent(newContent)
    toolWindow.show()
}
