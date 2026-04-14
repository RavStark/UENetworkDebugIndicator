# Rider UE Debug Context Overlay — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a Rider plugin that shows a color-coded "SERVER" / "CLIENT" / "LISTEN SERVER" badge in the bottom-right of the text editor when the debugger is paused at a breakpoint in an Unreal Engine project.

**Architecture:** Floating Swing `JLabel` overlay positioned on the editor's content component. A project-level debug session listener detects breakpoint pauses, evaluates `{,,UnrealEditor-Engine.dll}::GPlayInEditorContextString` via the `XDebuggerEvaluator` API, maps the result to a colored label, and shows the overlay. The overlay hides on resume/stop.

**Tech Stack:** Kotlin, IntelliJ Platform SDK 2025.1, Rider 2025.x, Gradle with `intellij-platform-gradle-plugin` 2.x

**Spec:** `docs/superpowers/specs/2026-04-13-rider-ue-debug-overlay-design.md`

---

## File Structure

```
UENetworkDebugIndicator/
├── build.gradle.kts                                    # Gradle build config (Rider SDK, Kotlin, plugin packaging)
├── settings.gradle.kts                                 # Gradle settings (project name, plugin repos)
├── gradle.properties                                   # JVM args, plugin metadata
├── gradle/wrapper/
│   └── gradle-wrapper.properties                       # Gradle wrapper config
├── src/
│   ├── main/
│   │   ├── kotlin/com/unrealdebugger/
│   │   │   ├── DebugContext.kt                         # Enum: SERVER, CLIENT, LISTEN_SERVER, UNKNOWN + colors + parsing
│   │   │   ├── IndicatorSettings.kt                    # PersistentStateComponent for toggle state
│   │   │   ├── IndicatorOverlay.kt                     # JLabel badge + positioning on editor content component
│   │   │   ├── ContextEvaluator.kt                     # Evaluates GPlayInEditorContextString via XDebuggerEvaluator
│   │   │   ├── DebugSessionListener.kt                 # XDebuggerManagerListener — wires evaluator + overlay
│   │   │   └── ToggleIndicatorAction.kt                # AnAction to toggle the indicator on/off
│   │   └── resources/META-INF/
│   │       └── plugin.xml                              # Plugin descriptor (services, listeners, actions)
│   └── test/
│       └── kotlin/com/unrealdebugger/
│           └── DebugContextTest.kt                     # Unit tests for string-to-enum parsing
```

---

### Task 1: Gradle Build Setup & Project Restructure

Migrate from the current `.iml`-based project to a Gradle build with the IntelliJ Platform Gradle Plugin 2.x targeting Rider 2025.1.

**Files:**
- Create: `build.gradle.kts`
- Create: `settings.gradle.kts`
- Create: `gradle.properties`
- Create: `gradle/wrapper/gradle-wrapper.properties`
- Delete: `UENetworkDebugIndicator.iml`
- Delete: `src/Main.kt`
- Delete: `src/main/kotlin/UnrealDebugIndicatorPlugin.kt`
- Move: `src/resources/META-INF/plugin.xml` → `src/main/resources/META-INF/plugin.xml`

- [ ] **Step 1: Create `settings.gradle.kts`**

```kotlin
// settings.gradle.kts
plugins {
    id("org.jetbrains.intellij.platform.settings") version "2.2.1"
}

rootProject.name = "UENetworkDebugIndicator"
```

- [ ] **Step 2: Create `build.gradle.kts`**

```kotlin
// build.gradle.kts
plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.25"
    id("org.jetbrains.intellij.platform") version "2.2.1"
}

group = "com.krafton"
version = "1.0.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        rider("2025.1")
        instrumentationTools()
    }
    testImplementation("junit:junit:4.13.2")
}

kotlin {
    jvmToolchain(21)
}

tasks {
    patchPluginXml {
        sinceBuild.set("251")
        untilBuild.set("251.*")
    }
}
```

- [ ] **Step 3: Create `gradle.properties`**

```properties
# gradle.properties
org.gradle.jvmargs=-Xmx2g -XX:MaxMetaspaceSize=512m
```

- [ ] **Step 4: Create `gradle/wrapper/gradle-wrapper.properties`**

```properties
# gradle/wrapper/gradle-wrapper.properties
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-8.10-bin.zip
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
```

- [ ] **Step 5: Move plugin.xml to Gradle-standard location**

Move `src/resources/META-INF/plugin.xml` to `src/main/resources/META-INF/plugin.xml`. Delete the now-empty `src/resources/` directory.

- [ ] **Step 6: Clean up old project files**

Delete these files that are no longer needed:
- `UENetworkDebugIndicator.iml` (Gradle manages module config)
- `src/Main.kt` (default Kotlin template, not part of plugin)
- `src/main/kotlin/UnrealDebugIndicatorPlugin.kt` (stub with broken import)

- [ ] **Step 7: Generate Gradle wrapper and verify build**

Run from the project root (requires Gradle 8.x installed, or download wrapper manually):

```bash
gradle wrapper
```

Then verify the project syncs:

```bash
./gradlew dependencies
```

Expected: Gradle downloads the Rider SDK and resolves all dependencies without errors. If `gradle` is not installed, download `gradle-wrapper.jar` from a Gradle distribution zip and place it in `gradle/wrapper/`.

- [ ] **Step 8: Commit**

```bash
git add build.gradle.kts settings.gradle.kts gradle.properties gradle/
git add src/main/resources/META-INF/plugin.xml
git rm UENetworkDebugIndicator.iml src/Main.kt src/main/kotlin/UnrealDebugIndicatorPlugin.kt
git commit -m "chore: migrate to Gradle build with IntelliJ Platform Plugin 2.x"
```

---

### Task 2: DebugContext Enum + Unit Test

Create the enum that maps debug context strings to labels and colors. This is pure logic with no IDE dependencies — write a unit test first.

**Files:**
- Create: `src/main/kotlin/com/unrealdebugger/DebugContext.kt`
- Create: `src/test/kotlin/com/unrealdebugger/DebugContextTest.kt`

- [ ] **Step 1: Write the failing test**

Create `src/test/kotlin/com/unrealdebugger/DebugContextTest.kt`:

```kotlin
package com.unrealdebugger

import org.junit.Assert.assertEquals
import org.junit.Test
import java.awt.Color

class DebugContextTest {

    @Test
    fun parseServer() {
        assertEquals(DebugContext.SERVER, DebugContext.fromString("Server"))
    }

    @Test
    fun parseClient() {
        assertEquals(DebugContext.CLIENT, DebugContext.fromString("Client"))
    }

    @Test
    fun parseListenServer() {
        assertEquals(DebugContext.LISTEN_SERVER, DebugContext.fromString("Listen Server"))
    }

    @Test
    fun parseCaseInsensitive() {
        assertEquals(DebugContext.SERVER, DebugContext.fromString("server"))
        assertEquals(DebugContext.CLIENT, DebugContext.fromString("CLIENT"))
        assertEquals(DebugContext.LISTEN_SERVER, DebugContext.fromString("listen server"))
    }

    @Test
    fun parseUnknownString() {
        assertEquals(DebugContext.UNKNOWN, DebugContext.fromString("SomethingElse"))
    }

    @Test
    fun parseEmptyString() {
        assertEquals(DebugContext.UNKNOWN, DebugContext.fromString(""))
    }

    @Test
    fun parseNull() {
        assertEquals(DebugContext.UNKNOWN, DebugContext.fromString(null))
    }

    @Test
    fun serverHasRedBackground() {
        assertEquals(Color(0xCC, 0x33, 0x33), DebugContext.SERVER.backgroundColor)
    }

    @Test
    fun clientHasBlueBackground() {
        assertEquals(Color(0x33, 0x66, 0xCC), DebugContext.CLIENT.backgroundColor)
    }

    @Test
    fun listenServerHasGoldBackground() {
        assertEquals(Color(0xCC, 0x99, 0x00), DebugContext.LISTEN_SERVER.backgroundColor)
    }

    @Test
    fun unknownHasGrayBackground() {
        assertEquals(Color(0x66, 0x66, 0x66), DebugContext.UNKNOWN.backgroundColor)
    }

    @Test
    fun labelsAreUppercase() {
        assertEquals("SERVER", DebugContext.SERVER.label)
        assertEquals("CLIENT", DebugContext.CLIENT.label)
        assertEquals("LISTEN SERVER", DebugContext.LISTEN_SERVER.label)
        assertEquals("UNKNOWN", DebugContext.UNKNOWN.label)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
./gradlew test
```

Expected: Compilation error — `DebugContext` class does not exist.

- [ ] **Step 3: Implement DebugContext**

Create `src/main/kotlin/com/unrealdebugger/DebugContext.kt`:

```kotlin
package com.unrealdebugger

import java.awt.Color

enum class DebugContext(
    val label: String,
    val backgroundColor: Color,
) {
    SERVER("SERVER", Color(0xCC, 0x33, 0x33)),
    CLIENT("CLIENT", Color(0x33, 0x66, 0xCC)),
    LISTEN_SERVER("LISTEN SERVER", Color(0xCC, 0x99, 0x00)),
    UNKNOWN("UNKNOWN", Color(0x66, 0x66, 0x66));

    companion object {
        fun fromString(value: String?): DebugContext {
            if (value.isNullOrBlank()) return UNKNOWN
            return when (value.trim().lowercase()) {
                "server" -> SERVER
                "client" -> CLIENT
                "listen server" -> LISTEN_SERVER
                else -> UNKNOWN
            }
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

```bash
./gradlew test
```

Expected: All 12 tests pass.

- [ ] **Step 5: Commit**

```bash
git add src/main/kotlin/com/unrealdebugger/DebugContext.kt src/test/kotlin/com/unrealdebugger/DebugContextTest.kt
git commit -m "feat: add DebugContext enum with color mapping and string parsing"
```

---

### Task 3: IndicatorSettings Service

Create a `PersistentStateComponent` project service that stores the toggle state (enabled/disabled). Register it in `plugin.xml`.

**Files:**
- Create: `src/main/kotlin/com/unrealdebugger/IndicatorSettings.kt`
- Modify: `src/main/resources/META-INF/plugin.xml`

- [ ] **Step 1: Create IndicatorSettings**

Create `src/main/kotlin/com/unrealdebugger/IndicatorSettings.kt`:

```kotlin
package com.unrealdebugger

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project

@Service(Service.Level.PROJECT)
@State(
    name = "UEDebugIndicatorSettings",
    storages = [Storage("ueDebugIndicator.xml")]
)
class IndicatorSettings : PersistentStateComponent<IndicatorSettings.State> {

    data class State(var isEnabled: Boolean = true)

    private var state = State()

    var isEnabled: Boolean
        get() = state.isEnabled
        set(value) { state.isEnabled = value }

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
    }

    companion object {
        fun getInstance(project: Project): IndicatorSettings =
            project.getService(IndicatorSettings::class.java)
    }
}
```

- [ ] **Step 2: Update plugin.xml — replace entire file**

Replace `src/main/resources/META-INF/plugin.xml` with:

```xml
<idea-plugin>
    <id>com.krafton.unreal-debug-indicator</id>
    <name>Unreal Engine Debug Context Indicator</name>
    <version>1.0.0</version>
    <vendor>Krafton</vendor>

    <description><![CDATA[
        Shows a color-coded badge (SERVER / CLIENT / LISTEN SERVER) in the bottom-right
        corner of the editor when debugging Unreal Engine 5, so you always know which
        network context your breakpoint is in.
    ]]></description>

    <depends>com.intellij.modules.platform</depends>
    <depends>com.intellij.modules.lang</depends>
    <depends>com.intellij.modules.xdebugger</depends>

    <!-- Actions will be added in Task 7 -->
</idea-plugin>
```

Note: The `@Service` annotation with `Service.Level.PROJECT` automatically registers the service — no XML entry needed in IntelliJ 2025.1+.

- [ ] **Step 3: Verify build**

```bash
./gradlew build
```

Expected: Build succeeds with no errors.

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/com/unrealdebugger/IndicatorSettings.kt src/main/resources/META-INF/plugin.xml
git commit -m "feat: add IndicatorSettings project service for toggle persistence"
```

---

### Task 4: IndicatorOverlay Component

Create the Swing badge component and the project service that manages attaching/detaching it to editors.

**Files:**
- Create: `src/main/kotlin/com/unrealdebugger/IndicatorOverlay.kt`

- [ ] **Step 1: Create IndicatorOverlay**

Create `src/main/kotlin/com/unrealdebugger/IndicatorOverlay.kt`:

```kotlin
package com.unrealdebugger

import com.intellij.openapi.components.Service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.VisibleAreaEvent
import com.intellij.openapi.editor.event.VisibleAreaListener
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import java.awt.Color
import java.awt.Component
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.SwingUtilities
import javax.swing.border.EmptyBorder

@Service(Service.Level.PROJECT)
class IndicatorOverlay(private val project: Project) {

    private val label = RoundedLabel()
    private var currentEditor: Editor? = null
    private var visibleAreaListener: VisibleAreaListener? = null
    private var resizeListener: ComponentAdapter? = null

    fun show(context: DebugContext) {
        SwingUtilities.invokeLater {
            label.text = " ${context.label} "
            label.background = context.backgroundColor
            label.foreground = Color.WHITE
            label.isOpaque = false  // We paint background ourselves for rounded corners
            label.isVisible = true

            val editor = getActiveEditor() ?: return@invokeLater
            attachToEditor(editor)
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
```

- [ ] **Step 2: Verify build**

```bash
./gradlew build
```

Expected: Build succeeds.

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/com/unrealdebugger/IndicatorOverlay.kt
git commit -m "feat: add IndicatorOverlay badge component with editor positioning"
```

---

### Task 5: ContextEvaluator

Create the expression evaluator that calls into the debugger to get `GPlayInEditorContextString`.

**Files:**
- Create: `src/main/kotlin/com/unrealdebugger/ContextEvaluator.kt`

**Important implementation note:** This component uses the `XDebuggerEvaluator` API from the IntelliJ Platform. The expression `{,,UnrealEditor-Engine.dll}::GPlayInEditorContextString` uses MSVC syntax which Rider's C++ debugger forwards to the native debug engine. The `XDebuggerEvaluator.evaluate()` method is the same API that powers the "Evaluate Expression" dialog.

If `XDebuggerEvaluator` does not work with Rider's C++ debugger (returns errors for all expressions), check:
1. Whether the evaluator is only available on specific `XStackFrame` subclasses from Rider's CIDR debugger
2. Whether Rider's debugger requires a different evaluation API path
3. Whether the `com.jetbrains.cidr.execution.debugger` plugin dependency is needed in `plugin.xml`

- [ ] **Step 1: Create ContextEvaluator**

Create `src/main/kotlin/com/unrealdebugger/ContextEvaluator.kt`:

```kotlin
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
```

- [ ] **Step 2: Verify build**

```bash
./gradlew build
```

Expected: Build succeeds. If compilation fails due to `XValueNode` interface changes in 2025.1 (additional abstract methods), implement the missing methods as no-ops. Check the SDK source for the exact interface definition:

```bash
./gradlew dependencies --configuration compileClasspath | grep xdebugger
```

Then find `XValueNode.class` in the Rider SDK jars to confirm the interface.

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/com/unrealdebugger/ContextEvaluator.kt
git commit -m "feat: add ContextEvaluator for GPlayInEditorContextString expression"
```

---

### Task 6: DebugSessionListener + Editor Tracking

Create the listener that ties everything together: detects debug pause/resume, triggers evaluation, and manages the overlay lifecycle including editor focus changes.

**Files:**
- Create: `src/main/kotlin/com/unrealdebugger/DebugSessionListener.kt`
- Modify: `src/main/resources/META-INF/plugin.xml`

- [ ] **Step 1: Create DebugSessionListener**

Create `src/main/kotlin/com/unrealdebugger/DebugSessionListener.kt`:

```kotlin
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
        // The message bus connection is scoped to the project and will be
        // disposed when the project closes. Setting the listener to null
        // ensures we don't leak references; the selectionChanged callback
        // checks isPaused so it becomes a no-op once the session ends.
        editorListener = null
    }
}
```

- [ ] **Step 2: Register the listener in plugin.xml**

Add the `<projectListeners>` block to `plugin.xml`, after the `<depends>` entries:

```xml
    <projectListeners>
        <listener class="com.unrealdebugger.DebugSessionListener"
                  topic="com.intellij.xdebugger.XDebuggerManagerListener"/>
    </projectListeners>
```

The full `plugin.xml` should now be:

```xml
<idea-plugin>
    <id>com.krafton.unreal-debug-indicator</id>
    <name>Unreal Engine Debug Context Indicator</name>
    <version>1.0.0</version>
    <vendor>Krafton</vendor>

    <description><![CDATA[
        Shows a color-coded badge (SERVER / CLIENT / LISTEN SERVER) in the bottom-right
        corner of the editor when debugging Unreal Engine 5, so you always know which
        network context your breakpoint is in.
    ]]></description>

    <depends>com.intellij.modules.platform</depends>
    <depends>com.intellij.modules.lang</depends>
    <depends>com.intellij.modules.xdebugger</depends>

    <projectListeners>
        <listener class="com.unrealdebugger.DebugSessionListener"
                  topic="com.intellij.xdebugger.XDebuggerManagerListener"/>
    </projectListeners>

    <!-- Actions will be added in Task 7 -->
</idea-plugin>
```

- [ ] **Step 3: Verify build**

```bash
./gradlew build
```

Expected: Build succeeds.

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/com/unrealdebugger/DebugSessionListener.kt src/main/resources/META-INF/plugin.xml
git commit -m "feat: add DebugSessionListener to wire evaluator and overlay on pause/resume"
```

---

### Task 7: ToggleIndicatorAction + Final plugin.xml

Create the toggle action and finalize the plugin descriptor.

**Files:**
- Create: `src/main/kotlin/com/unrealdebugger/ToggleIndicatorAction.kt`
- Modify: `src/main/resources/META-INF/plugin.xml`

- [ ] **Step 1: Create ToggleIndicatorAction**

Create `src/main/kotlin/com/unrealdebugger/ToggleIndicatorAction.kt`:

```kotlin
package com.unrealdebugger

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

    override fun getActionUpdateThread() = com.intellij.openapi.actionSystem.ActionUpdateThread.BGT
}
```

- [ ] **Step 2: Add action to plugin.xml**

Replace the `<!-- Actions will be added in Task 7 -->` comment in `plugin.xml` with:

```xml
    <actions>
        <action id="ToggleUEDebugIndicator"
                class="com.unrealdebugger.ToggleIndicatorAction"
                text="Toggle UE Debug Indicator"
                description="Toggle the Unreal Engine debug context indicator on/off">
            <add-to-group group-id="XDebugger.ToolWindow.TopToolbar3" anchor="last"/>
        </action>
    </actions>
```

The final complete `plugin.xml`:

```xml
<idea-plugin>
    <id>com.krafton.unreal-debug-indicator</id>
    <name>Unreal Engine Debug Context Indicator</name>
    <version>1.0.0</version>
    <vendor>Krafton</vendor>

    <description><![CDATA[
        Shows a color-coded badge (SERVER / CLIENT / LISTEN SERVER) in the bottom-right
        corner of the editor when debugging Unreal Engine 5, so you always know which
        network context your breakpoint is in.
    ]]></description>

    <depends>com.intellij.modules.platform</depends>
    <depends>com.intellij.modules.lang</depends>
    <depends>com.intellij.modules.xdebugger</depends>

    <projectListeners>
        <listener class="com.unrealdebugger.DebugSessionListener"
                  topic="com.intellij.xdebugger.XDebuggerManagerListener"/>
    </projectListeners>

    <actions>
        <action id="ToggleUEDebugIndicator"
                class="com.unrealdebugger.ToggleIndicatorAction"
                text="Toggle UE Debug Indicator"
                description="Toggle the Unreal Engine debug context indicator on/off">
            <add-to-group group-id="XDebugger.ToolWindow.TopToolbar3" anchor="last"/>
        </action>
    </actions>
</idea-plugin>
```

- [ ] **Step 3: Verify build**

```bash
./gradlew build
```

Expected: Build succeeds.

- [ ] **Step 4: Commit**

```bash
git add src/main/kotlin/com/unrealdebugger/ToggleIndicatorAction.kt src/main/resources/META-INF/plugin.xml
git commit -m "feat: add ToggleIndicatorAction and finalize plugin.xml"
```

---

### Task 8: Build Plugin & Manual Test

Build the distributable plugin zip and verify it works in Rider with a UE project.

**Files:**
- None (build + test only)

- [ ] **Step 1: Build the plugin zip**

```bash
./gradlew buildPlugin
```

Expected: Creates `build/distributions/UENetworkDebugIndicator-1.0.0.zip`

- [ ] **Step 2: Install in Rider**

1. Open Rider
2. Go to **Settings → Plugins → gear icon → Install Plugin from Disk**
3. Select `build/distributions/UENetworkDebugIndicator-1.0.0.zip`
4. Restart Rider

- [ ] **Step 3: Manual test — basic debug overlay**

1. Open a UE5 C++ project in Rider
2. Set a breakpoint in a function that runs during PIE (Play In Editor) on the **server** (e.g., `AGameMode::StartPlay`)
3. Start PIE in Dedicated Server mode from the UE Editor
4. Attach Rider's debugger to the server process, or use a debug launch config
5. When the breakpoint hits, verify:
   - A **red badge** with text **SERVER** appears in the bottom-right of the text editor
   - The badge has rounded corners, white text, and doesn't overlap the scrollbar
6. Resume execution — verify the badge disappears
7. Set a breakpoint in a client-side function (e.g., `APlayerController::BeginPlay`)
8. When it hits, verify a **blue CLIENT** badge appears

- [ ] **Step 4: Manual test — editor split behavior**

1. While paused at a breakpoint (badge visible), split the editor (right-click tab → Split Right)
2. Click into the other editor pane
3. Verify the badge moves to the focused editor pane

- [ ] **Step 5: Manual test — toggle action**

1. While paused (badge visible), go to the Debug toolbar area
2. Find and click "Disable UE Debug Indicator"
3. Verify the badge disappears immediately
4. Resume and hit another breakpoint
5. Verify the badge does NOT appear
6. Click "Enable UE Debug Indicator"
7. The badge won't appear until the next breakpoint pause — this is expected

- [ ] **Step 6: Manual test — non-UE project**

1. Open a non-UE C++ project (or any project)
2. Debug normally
3. Verify no badge appears and no errors in the IDE log (**Help → Show Log in Explorer** → check `idea.log` for exceptions from `com.unrealdebugger`)

- [ ] **Step 7: Commit any fixes from manual testing**

If any issues were found and fixed during testing:

```bash
git add -A
git commit -m "fix: address issues found during manual testing"
```

---

## Troubleshooting

### Expression evaluation returns errors for all expressions

The `XDebuggerEvaluator` obtained from `XStackFrame.getEvaluator()` may not support MSVC expression syntax directly. If this happens:

1. Check if Rider's C++ stack frames use a subclass of `XStackFrame` that requires a different evaluation path
2. Add `<depends>com.jetbrains.cidr.debugger</depends>` to `plugin.xml` and check if `CidrDebugProcess` or `LLDBDebugProcess` exposes a more direct evaluator
3. As a last resort, use Rider's action system to programmatically invoke the "Evaluate Expression" action (`EvaluateExpression`) with the expression string

### Badge renders behind code or is not visible

The `contentComponent.setComponentZOrder(label, 0)` call should ensure the label paints on top. If it doesn't:

1. Try using `JLayeredPane.getLayeredPaneAbove(contentComponent)` and adding the label at `JLayeredPane.POPUP_LAYER`
2. Or override `label.paint()` to explicitly call `paintComponent` after the parent renders

### GPlayInEditorContextString not found

This symbol only exists when the UE Editor process is running in PIE mode. If debugging a standalone game build or a shipping build, the symbol won't exist. The evaluator will call `errorOccurred` and the overlay will stay hidden — this is the expected behavior.
