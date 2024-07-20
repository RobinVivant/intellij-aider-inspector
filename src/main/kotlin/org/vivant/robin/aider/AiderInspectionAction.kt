package org.vivant.robin.aider

import com.intellij.codeInspection.InspectionEngine
import com.intellij.codeInspection.InspectionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.profile.codeInspection.InspectionProjectProfileManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import java.io.BufferedReader
import java.io.InputStreamReader

class AiderInspectionAction : AnAction() {
    private val LOG = Logger.getInstance(AiderInspectionAction::class.java)

    override fun update(e: AnActionEvent) {
        val project = e.project
        val editor = e.getData(CommonDataKeys.EDITOR)
        e.presentation.isEnabledAndVisible = project != null && editor != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        LOG.info("AiderInspectionAction triggered")
        val project: Project = e.project ?: run {
            LOG.warn("Project is null")
            return
        }
        val editor: Editor = e.getData(CommonDataKeys.EDITOR) ?: run {
            LOG.warn("Editor is null")
            return
        }
        val psiFile: PsiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.document) ?: run {
            LOG.warn("PsiFile is null")
            return
        }

        val problems = mutableListOf<String>()

        try {
            // Get all enabled inspections
            val profile = InspectionProjectProfileManager.getInstance(project).currentProfile
            val tools = profile.getAllTools()
                .filter { it.isEnabled }
                .map { it.tool }

            LOG.info("Running ${tools.size} enabled inspections")

            // Run all enabled inspections
            val inspectionManager = InspectionManager.getInstance(project)
            val problemDescriptors = tools.flatMap { tool ->
                InspectionEngine.runInspectionOnFile(psiFile, tool, inspectionManager.createNewGlobalContext())
            }

            problems.addAll(problemDescriptors.mapNotNull { problem ->
                problem.psiElement?.let { element ->
                    "${psiFile.name}:${editor.document.getLineNumber(element.textRange.startOffset) + 1}: ${problem.descriptionTemplate}"
                }
            })

            LOG.info("Found ${problems.size} problems")

            if (problems.isEmpty()) {
                updateToolWindow(project, "No issues found in the current file.")
                return
            }

            // Format problems for aider
            val formattedProblems = problems.joinToString("\n")

            sendToAider(project, formattedProblems)
        } catch (e: Exception) {
            LOG.error("Error during inspection", e)
            Messages.showErrorDialog(project, "An error occurred during inspection: ${e.message}", "Inspection Error")
        }
    }

    private fun sendToAider(project: Project, problems: String) {
        val toolWindow = getToolWindow(project) ?: return

        // Show the tool window
        toolWindow.show()

        // Run aider in a background task
        com.intellij.openapi.progress.ProgressManager.getInstance().run(
            object : com.intellij.openapi.progress.Task.Backgroundable(project, "Running Aider Inspection") {
                override fun run(indicator: com.intellij.openapi.progress.ProgressIndicator) {
                    try {
                        LOG.info("Starting Aider process")
                        val process = ProcessBuilder("bash", "-c", "echo \"$problems\" | aider --no-auto-commits")
                            .redirectErrorStream(true)
                            .start()

                        val reader = BufferedReader(InputStreamReader(process.inputStream))
                        val output = StringBuilder()

                        reader.useLines { lines ->
                            lines.forEach { line ->
                                output.append(line).append("\n")
                                indicator.text = "Processing: $line"
                                LOG.info("Aider output: $line")
                            }
                        }

                        val exitCode = process.waitFor()
                        if (exitCode != 0) {
                            LOG.warn("Aider process exited with code $exitCode")
                            output.append("Aider process exited with code $exitCode")
                        }

                        // Update the tool window content
                        com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
                            updateToolWindowContent(toolWindow, output.toString())
                        }
                    } catch (e: Exception) {
                        LOG.error("Error running Aider", e)
                        com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
                            updateToolWindowContent(toolWindow, "Error: ${e.message}")
                        }
                    }
                }
            }
        )
    }

    private fun getToolWindow(project: Project): ToolWindow? {
        val toolWindowManager = ToolWindowManager.getInstance(project)
        return toolWindowManager.getToolWindow("Aider Output")?.also {
            it.show()
        } ?: run {
            LOG.error("Aider Output tool window not found")
            Messages.showErrorDialog(project, "Aider Output tool window not found", "Error")
            null
        }
    }

    private fun updateToolWindow(project: Project, message: String) {
        val toolWindow = getToolWindow(project) ?: return
        updateToolWindowContent(toolWindow, message)
    }

    private fun updateToolWindowContent(toolWindow: ToolWindow, content: String) {
        val contentManager = toolWindow.contentManager
        val factory = com.intellij.ui.content.ContentFactory.SERVICE.getInstance()
        val newContent = factory.createContent(
            com.intellij.ui.components.JBScrollPane(
                com.intellij.ui.components.JBTextArea(content).apply {
                    isEditable = false
                }
            ), "", false
        )
        contentManager.removeAllContents(true)
        contentManager.addContent(newContent)
    }
}
