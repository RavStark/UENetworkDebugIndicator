package com.unrealdebugger

import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebugSessionListener
import com.intellij.xdebugger.XDebuggerManagerListener

class DebugSessionListener(private val project: Project) : XDebuggerManagerListener {

    private var isPaused = false
    private var editorListener: FileEditorManagerListener? = null

    override fun processStarted(debugProcess: XDebugProcess) {
        val session = debugProcess.session

        session.addSessionListener(object : XDebugSessionListener {
            override fun sessionPaused() {
                isPaused = true
                val settings = IndicatorSettings.getInstance(project)
                if (!settings.isEnabled) return

                ContextEvaluator.evaluate(session) { context ->
                    val overlay = IndicatorOverlay.getInstance(project)
                    overlay.show(context)
                }

                startTrackingEditorChanges()
            }

            override fun sessionResumed() {
                isPaused = false
                stopTrackingEditorChanges()
                IndicatorOverlay.getInstance(project).hide()
            }

            override fun sessionStopped() {
                isPaused = false
                stopTrackingEditorChanges()
                IndicatorOverlay.getInstance(project).hide()
            }
        })
    }

    private fun startTrackingEditorChanges() {
        if (editorListener != null) return

        editorListener = object : FileEditorManagerListener {
            override fun selectionChanged(event: FileEditorManagerEvent) {
                if (isPaused) {
                    IndicatorOverlay.getInstance(project).moveToActiveEditor()
                }
            }
        }

        project.messageBus.connect()
            .subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, editorListener!!)
    }

    private fun stopTrackingEditorChanges() {
        editorListener = null
    }
}
