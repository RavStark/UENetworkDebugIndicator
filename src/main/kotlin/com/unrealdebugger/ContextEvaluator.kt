package com.unrealdebugger

import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator
import com.intellij.xdebugger.frame.XValue
import com.intellij.xdebugger.frame.XValueNode
import com.intellij.xdebugger.frame.XValuePlace
import com.intellij.xdebugger.frame.presentation.XValuePresentation
import com.intellij.openapi.editor.colors.TextAttributesKey
import java.awt.Color
import javax.swing.Icon

object ContextEvaluator {

    private const val EXPRESSION =
        "{,,UnrealEditor-Engine.dll}::GPlayInEditorContextString"

    /**
     * Evaluates the UE play-in-editor context string on the given debug session.
     * Calls [callback] on a background thread with the parsed result.
     * Calls [callback] with UNKNOWN on any failure.
     */
    data class Result(val text: String, val color: Color)

    private val COLOR_CLIENT = Color(0x33, 0x99, 0x33)
    private val COLOR_SERVER = Color(0xCC, 0x33, 0x33)
    private val COLOR_DEFAULT = Color(0x66, 0x66, 0x66)

    fun evaluate(session: XDebugSession, callback: (Result) -> Unit) {
        val frame = session.currentStackFrame
        if (frame == null) {
            callback(Result("no frame", COLOR_DEFAULT))
            return
        }
        val evaluator = frame.evaluator
        if (evaluator == null) {
            callback(Result("no evaluator", COLOR_DEFAULT))
            return
        }

        evaluator.evaluate(
            EXPRESSION,
            object : XDebuggerEvaluator.XEvaluationCallback {
                override fun evaluated(result: XValue) {
                    extractStringValue(result) { value ->
                        val text = cleanRaw(value ?: "null")
                        val color = when {
                            text.contains("client", ignoreCase = true) -> COLOR_CLIENT
                            text.contains("server", ignoreCase = true) -> COLOR_SERVER
                            else -> COLOR_DEFAULT
                        }
                        callback(Result(text, color))
                    }
                }

                override fun errorOccurred(errorMessage: String) {
                    callback(Result("error: $errorMessage", COLOR_DEFAULT))
                }
            },
            frame.sourcePosition
        )
    }

    private fun cleanRaw(raw: String): String {
        return raw.trim()
            .let { if (it.startsWith("L\"") && it.endsWith("\"")) it.removePrefix("L\"").removeSuffix("\"") else it }
            .removeSurrounding("\"")
            .removeSurrounding("'")
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
                callback(valueStr)
            }

            override fun setPresentation(
                icon: Icon?,
                presentation: XValuePresentation,
                hasChildren: Boolean
            ) {
                val sb = StringBuilder()
                presentation.renderValue(object : XValuePresentation.XValueTextRenderer {
                    override fun renderValue(value: String) { sb.append(value) }
                    override fun renderStringValue(value: String) { sb.append(value) }
                    override fun renderNumericValue(value: String) { sb.append(value) }
                    override fun renderKeywordValue(value: String) { sb.append(value) }
                    override fun renderValue(value: String, key: TextAttributesKey) { sb.append(value) }
                    override fun renderStringValue(value: String, additionalSpecialCharsToHighlight: String?, maxLength: Int) { sb.append(value) }
                    override fun renderComment(comment: String) {}
                    override fun renderSpecialSymbol(symbol: String) {}
                    override fun renderError(error: String) {}
                })
                callback(sb.toString())
            }

            override fun setFullValueEvaluator(fullValueEvaluator: com.intellij.xdebugger.frame.XFullValueEvaluator) {}
            override fun isObsolete(): Boolean = false
        }, XValuePlace.TOOLTIP)
    }
}
