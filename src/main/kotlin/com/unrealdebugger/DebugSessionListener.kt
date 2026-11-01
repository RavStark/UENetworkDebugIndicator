package com.unrealdebugger

import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.util.messages.MessageBusConnection
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XDebugSessionListener
import com.intellij.xdebugger.XDebuggerManagerListener

class DebugSessionListener(private val project: Project) : XDebuggerManagerListener {

    private var isPaused = false
    private var editorConnection: MessageBusConnection? = null

    override fun processStarted(debugProcess: XDebugProcess) {
        val session = debugProcess.session

        val sessionListener = object : XDebugSessionListener {
            override fun sessionPaused() {
                isPaused = true
                val settings = IndicatorSettings.getInstance(project)
                if (!settings.isEnabled) return

                ContextEvaluator.evaluate(session) { result ->
                    val overlay = IndicatorOverlay.getInstance(project)
                    overlay.show(result)
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
                session.removeSessionListener(this)
            }
        }
        session.addSessionListener(sessionListener)
    }

    private fun startTrackingEditorChanges() {
        if (editorConnection != null) return

        val connection = project.messageBus.connect()
        editorConnection = connection
        connection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, object : FileEditorManagerListener {
            override fun selectionChanged(event: FileEditorManagerEvent) {
                if (isPaused) {
                    IndicatorOverlay.getInstance(project).moveToActiveEditor()
                }
            }
        })
    }

    private fun stopTrackingEditorChanges() {
        editorConnection?.disconnect()
        editorConnection = null
    }
}
