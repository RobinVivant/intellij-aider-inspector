package org.vivant.robin.aider

import com.intellij.codeInspection.InspectionEngine
import com.intellij.codeInspection.InspectionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
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
import java.io.IOException
import java.io.InputStreamReader

class AiderInspectionAction : AnAction() {
    private val LOG = Logger.getInstance(AiderInspectionAction::class.java)

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.EDT
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        val editor = e.getData(CommonDataKeys.EDITOR)
        e.presentation.apply {
            text = "Run Aider Inspection"
            description = "Runs Aider inspection on the current file"
            isEnabledAndVisible = project != null && editor != null
        }
    }

    override fun actionPerformed(e: AnActionEvent) {
        LOG.info("AiderInspectionAction triggered")
        val project: Project = e.project ?: run {
            LOG.warn("Project is null")
            Messages.showErrorDialog("Project is null", "Error")
            return
        }
        Messages.showInfoMessage(project, "AiderInspectionAction triggered", "Debug Info")
        if (project.isDisposed) {
            LOG.warn("Project is disposed")
            Messages.showErrorDialog("The project has been closed.", "Error")
            return
        }
        val editor: Editor = e.getData(CommonDataKeys.EDITOR) ?: run {
            LOG.warn("Editor is null")
            Messages.showErrorDialog(project, "No active editor found.", "Error")
            return
        }
        val psiFile: PsiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.document) ?: run {
            LOG.warn("PsiFile is null")
            Messages.showErrorDialog(project, "Unable to get the current file.", "Error")
            return
        }

        com.intellij.openapi.application.ReadAction.nonBlocking<List<String>> {
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
            } catch (e: Exception) {
                LOG.error("Error during inspection", e)
                com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
                    Messages.showErrorDialog(
                        project,
                        "An error occurred during inspection: ${e.message}",
                        "Inspection Error"
                    )
                }
            }

            problems
        }.finishOnUiThread(com.intellij.openapi.application.ModalityState.defaultModalityState()) { problems ->
            if (problems.isEmpty()) {
                Messages.showInfoMessage(project, "No issues found in the current file.", "Aider Inspection")
                return@finishOnUiThread
            }

            // Format problems for aider
            val formattedProblems = problems.joinToString("\n")

            sendToAider(project, formattedProblems)
        }.inSmartMode(project)
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
                        indicator.text = "Starting Aider process"
                        indicator.isIndeterminate = false

                        val escapedProblems = problems.replace("\"", "\\\"").replace("$", "\\$")
                        val process = try {
                            ProcessBuilder("bash", "-c", "echo \"$escapedProblems\" | aider --no-auto-commits")
                                .redirectErrorStream(true)
                                .start()
                        } catch (e: IOException) {
                            LOG.error("Failed to start Aider process", e)
                            throw RuntimeException("Failed to start Aider process: ${e.message}")
                        }

                        val reader = BufferedReader(InputStreamReader(process.inputStream))
                        val output = StringBuilder()

                        reader.useLines { lines ->
                            lines.forEachIndexed { index, line ->
                                output.append(line).append("\n")
                                indicator.fraction = (index + 1).toDouble() / problems.split("\n").size
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
                            com.intellij.notification.Notifications.Bus.notify(
                                com.intellij.notification.Notification(
                                    "Aider Inspection",
                                    "Aider Inspection",
                                    "Aider inspection complete",
                                    com.intellij.notification.NotificationType.INFORMATION
                                ),
                                project
                            )
                        }
                    } catch (e: Exception) {
                        LOG.error("Error running Aider", e)
                        com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
                            when (e) {
                                is java.io.IOException -> {
                                    if (e.message?.contains("Cannot run program \"aider\"") == true) {
                                        updateToolWindowContent(
                                            toolWindow,
                                            "Error: Aider command not found. Please ensure Aider is installed and in your PATH."
                                        )
                                    } else {
                                        updateToolWindowContent(toolWindow, "Error: ${e.message}")
                                    }
                                }

                                else -> updateToolWindowContent(toolWindow, "Error: ${e.message}")
                            }
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


    private fun updateToolWindowContent(toolWindow: ToolWindow, content: String) {
        val contentManager = toolWindow.contentManager
        val factory = com.intellij.ui.content.ContentFactory.getInstance()
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
