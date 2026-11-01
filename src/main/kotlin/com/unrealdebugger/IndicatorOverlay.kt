package com.unrealdebugger

import com.intellij.openapi.components.Service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.VisibleAreaListener
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import java.awt.Color
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.JLabel
import javax.swing.SwingUtilities
import javax.swing.border.EmptyBorder

@Service(Service.Level.PROJECT)
class IndicatorOverlay(private val project: Project) {

    private val label = RoundedLabel()
    private var currentEditor: Editor? = null
    private var visibleAreaListener: VisibleAreaListener? = null
    private var resizeListener: ComponentAdapter? = null

    fun show(result: ContextEvaluator.Result) {
        SwingUtilities.invokeLater {
            label.text = " ${result.text} "
            label.background = result.color
            label.foreground = Color.WHITE
            label.isOpaque = false  // We paint background ourselves for rounded corners
            label.isVisible = true

            val editor = getActiveEditor() ?: return@invokeLater
            attachToEditor(editor)
            label.size = label.preferredSize
            reposition()
        }
    }

    fun hide() {
        SwingUtilities.invokeLater {
            label.isVisible = false
            detachFromEditor()
        }
    }

    fun moveToActiveEditor() {
        if (!label.isVisible) return
        SwingUtilities.invokeLater {
            val editor = getActiveEditor() ?: return@invokeLater
            if (editor != currentEditor) {
                detachFromEditor()
                attachToEditor(editor)
                reposition()
            }
        }
    }

    private fun attachToEditor(editor: Editor) {
        if (currentEditor == editor && label.parent == editor.contentComponent) return
        detachFromEditor()

        currentEditor = editor
        val content = editor.contentComponent
        content.add(label)
        content.setComponentZOrder(label, 0) // Paint on top

        visibleAreaListener = VisibleAreaListener { reposition() }
        editor.scrollingModel.addVisibleAreaListener(visibleAreaListener!!)

        resizeListener = object : ComponentAdapter() {
            override fun componentResized(e: ComponentEvent) = reposition()
        }
        content.addComponentListener(resizeListener!!)
    }

    private fun detachFromEditor() {
        val editor = currentEditor ?: return

        visibleAreaListener?.let { editor.scrollingModel.removeVisibleAreaListener(it) }
        resizeListener?.let { editor.contentComponent.removeComponentListener(it) }
        editor.contentComponent.remove(label)
        editor.contentComponent.repaint()

        currentEditor = null
        visibleAreaListener = null
        resizeListener = null
    }

    private fun reposition() {
        val editor = currentEditor ?: return
        val content = editor.contentComponent
        val size = label.preferredSize
        val visibleRect = content.visibleRect

        val x = visibleRect.x + visibleRect.width - size.width - 8
        val y = visibleRect.y + visibleRect.height - size.height - 8
        label.setBounds(x, y, size.width, size.height)
        label.repaint()
    }

    private fun getActiveEditor(): Editor? {
        val fileEditor = FileEditorManager.getInstance(project).selectedEditor ?: return null
        return (fileEditor as? TextEditor)?.editor
    }

    companion object {
        fun getInstance(project: Project): IndicatorOverlay =
            project.getService(IndicatorOverlay::class.java)
    }

    private class RoundedLabel : JLabel() {
        init {
            font = Font(Font.MONOSPACED, Font.BOLD, 11)
            border = EmptyBorder(2, 4, 2, 4)
        }

        override fun paintComponent(g: Graphics) {
            val g2 = g.create() as Graphics2D
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.color = background
            g2.fillRoundRect(0, 0, width, height, 8, 8)
            g2.dispose()
            super.paintComponent(g)
        }
    }
}
