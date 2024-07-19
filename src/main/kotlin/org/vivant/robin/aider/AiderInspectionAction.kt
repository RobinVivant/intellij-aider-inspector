package org.vivant.robin.aider

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ex.InspectionToolWrapper
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper
import com.intellij.profile.codeInspection.InspectionProjectProfileManager
import com.intellij.psi.PsiManager

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
                problems.addAll(descriptors)
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

        // TODO: Implement sending to aider
        sendToAider(formattedProblems)
    }

    private fun sendToAider(problems: String) {
        // TODO: Implement this method to send problems to aider
        println("Sending to aider: $problems")
    }
}
