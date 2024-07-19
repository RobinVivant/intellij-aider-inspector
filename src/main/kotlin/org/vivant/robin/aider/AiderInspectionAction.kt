package org.vivant.robin.aider

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.profile.codeInspection.InspectionProjectProfileManager
import com.intellij.psi.PsiManager
import java.io.BufferedReader
import java.io.InputStreamReader

class AiderInspectionAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project: Project = e.project ?: return
        val file: VirtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return

        val psiFile = PsiManager.getInstance(project).findFile(file) ?: return
        val inspectionManager = InspectionManager.getInstance(project)

        val problems = mutableListOf<ProblemDescriptor>()

        // Get all enabled inspections
        val profile = InspectionProjectProfileManager.getInstance(project).currentProfile
        val tools = profile.getAllEnabledInspectionTools(project)

        // Run all enabled inspections
        for (toolWrapper in tools) {
            if (toolWrapper is LocalInspectionToolWrapper) {
                val tool = (toolWrapper as LocalInspectionToolWrapper).tool
                val descriptors = tool.checkFile(psiFile, inspectionManager, false)
                descriptors?.let { problems.addAll(it) }
            }
        }

        if (problems.isEmpty()) {
            // No issues found
            return
        }

        // Format problems for aider
        val formattedProblems = problems.joinToString("\n") { problem ->
            "${problem.psiElement.containingFile.name}:${problem.lineNumber}: ${problem.descriptionTemplate}"
        }

        sendToAider(project, formattedProblems)
    }

    private fun sendToAider(project: Project, problems: String) {
        val toolWindowManager = ToolWindowManager.getInstance(project)
        var toolWindow = toolWindowManager.getToolWindow("Aider Output")

        if (toolWindow == null) {
            // If the tool window doesn't exist, register it
            toolWindow = toolWindowManager.registerToolWindow("Aider Output", true, ToolWindowAnchor.RIGHT)
            AiderToolWindowFactory().createToolWindowContent(project, toolWindow)
        }

        // Show the tool window
        toolWindow.show()

        // Run aider in a background task
        com.intellij.openapi.progress.ProgressManager.getInstance().run(
            object : com.intellij.openapi.progress.Task.Backgroundable(project, "Running Aider Inspection") {
                override fun run(indicator: com.intellij.openapi.progress.ProgressIndicator) {
                    val process = ProcessBuilder("bash", "-c", "echo \"$problems\" | aider --no-auto-commits")
                        .redirectErrorStream(true)
                        .start()

                    val reader = BufferedReader(InputStreamReader(process.inputStream))
                    val output = StringBuilder()

                    reader.useLines { lines ->
                        lines.forEach { line ->
                            output.append(line).append("\n")
                            indicator.text = "Processing: $line"
                        }
                    }

                    process.waitFor()

                    // Update the tool window content
                    com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
                        AiderToolWindowFactory.updateContent(toolWindow, output.toString())
                    }
                }
            }
        )
    }
}
