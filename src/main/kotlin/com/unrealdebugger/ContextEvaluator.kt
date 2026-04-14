package com.unrealdebugger

import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator
import com.intellij.xdebugger.frame.XValue
import com.intellij.xdebugger.frame.XValueNode
import com.intellij.xdebugger.frame.XValuePlace
import com.intellij.xdebugger.frame.presentation.XValuePresentation
import javax.swing.Icon

object ContextEvaluator {

    private const val EXPRESSION =
        "{,,UnrealEditor-Engine.dll}::GPlayInEditorContextString"

    /**
     * Evaluates the UE play-in-editor context string on the given debug session.
     * Calls [callback] on a background thread with the parsed result.
     * Calls [callback] with UNKNOWN on any failure.
     */
    fun evaluate(session: XDebugSession, callback: (DebugContext) -> Unit) {
        val frame = session.currentStackFrame
        if (frame == null) {
            callback(DebugContext.UNKNOWN)
            return
        }
        val evaluator = frame.evaluator
        if (evaluator == null) {
            callback(DebugContext.UNKNOWN)
            return
        }

        evaluator.evaluate(
            EXPRESSION,
            object : XDebuggerEvaluator.XEvaluationCallback {
                override fun evaluated(result: XValue) {
                    extractStringValue(result) { value ->
                        callback(DebugContext.fromString(value))
                    }
                }

                override fun errorOccurred(errorMessage: String) {
                    callback(DebugContext.UNKNOWN)
                }
            },
            frame.sourcePosition
        )
    }

    /**
     * Extracts the string representation from an XValue by requesting its presentation.
     */
    private fun extractStringValue(value: XValue, callback: (String?) -> Unit) {
        value.computePresentation(object : XValueNode {
            override fun setPresentation(
                icon: Icon?,
                type: String?,
                valueStr: String,
                hasChildren: Boolean
            ) {
                // valueStr contains the evaluated result as a display string.
                // Strip surrounding quotes if present (debugger may return "\"Server\"").
                val cleaned = valueStr
                    .trim()
                    .removeSurrounding("\"")
                    .removeSurrounding("'")
                    .removeSurrounding("L\"", "\"") // Wide string prefix
                callback(cleaned)
            }

            override fun setPresentation(
                icon: Icon?,
                presentation: XValuePresentation,
                hasChildren: Boolean
            ) {
                // Fallback: render the presentation to text
                val sb = StringBuilder()
                presentation.renderValue(object : XValuePresentation.XValueTextRenderer {
                    override fun renderValue(value: String) { sb.append(value) }
                    override fun renderStringValue(value: String) { sb.append(value) }
                    override fun renderNumericValue(value: String) { sb.append(value) }
                    override fun renderKeywordValue(value: String) { sb.append(value) }
                    override fun renderValue(value: String, key: XValuePresentation.TextAttributesKey) { sb.append(value) }
                    override fun renderStringValue(value: String, additionalSpecialCharsToHighlight: String?, maxLength: Int) { sb.append(value) }
                    override fun renderComment(comment: String) {}
                    override fun renderSpecialSymbol(symbol: String) {}
                    override fun renderError(error: String) {}
                })
                val cleaned = sb.toString()
                    .trim()
                    .removeSurrounding("\"")
                    .removeSurrounding("'")
                callback(cleaned)
            }

            override fun setFullValueEvaluator(fullValueEvaluator: com.intellij.xdebugger.frame.XFullValueEvaluator) {}
            override fun isObsolete(): Boolean = false
        }, XValuePlace.TOOLTIP)
    }
}
