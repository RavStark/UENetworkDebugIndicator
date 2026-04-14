package com.unrealdebugger

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

class ToggleIndicatorAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val settings = IndicatorSettings.getInstance(project)
        settings.isEnabled = !settings.isEnabled

        if (!settings.isEnabled) {
            IndicatorOverlay.getInstance(project).hide()
        }
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        e.presentation.isEnabled = project != null
        if (project != null) {
            val settings = IndicatorSettings.getInstance(project)
            e.presentation.text = if (settings.isEnabled)
                "Disable UE Debug Indicator"
            else
                "Enable UE Debug Indicator"
        }
    }

    override fun getActionUpdateThread() = ActionUpdateThread.BGT
}
