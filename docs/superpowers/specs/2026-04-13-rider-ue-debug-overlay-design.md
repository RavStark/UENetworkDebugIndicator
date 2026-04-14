# Rider Unreal Engine Debug Context Overlay — Design Spec

## Summary

A JetBrains Rider plugin that displays a small, opaque color-coded badge in the bottom-right corner of the text editor when the debugger is paused at a breakpoint in an Unreal Engine project. The badge indicates whether the current debug context is **Server**, **Client**, or **Listen Server** by evaluating the UE global `GPlayInEditorContextString`.

## Requirements

- Show a badge overlay in the **bottom-right corner** of the active text editor
- Only visible **when stopped at a breakpoint** — hidden when running or not debugging
- Detect context by evaluating `{,,UnrealEditor-Engine.dll}::GPlayInEditorContextString` via the debugger's expression evaluator
- Map the result to simplified color-coded labels:
  - **SERVER** — Red background
  - **CLIENT** — Blue background
  - **LISTEN SERVER** — Gold/Yellow background
  - **UNKNOWN** — Gray background (fallback)
- Opaque, compact badge style with white text
- Toggle on/off via a menu action (Debug menu)
- Target: Rider 2025.x (IntelliJ Platform 2025.1+), Windows, native C++ debugger

## Architecture

### Components

#### 1. DebugSessionListener

Listens for debug session lifecycle events via `XDebuggerManager`:
- **Session paused:** Triggers expression evaluation, then updates the overlay
- **Session resumed:** Hides the overlay
- **Session stopped:** Hides the overlay and cleans up

Registered as a project-level listener.

#### 2. ContextEvaluator

Evaluates `{,,UnrealEditor-Engine.dll}::GPlayInEditorContextString` through the current debug session.

**C++ Debugger Specifics:**
- The expression uses **MSVC debugger syntax** (`{,,module}::symbol`) — this only works with Rider's native C++ debugger on Windows
- Rider's C++ debugger on Windows uses the Visual Studio native debug engine under the hood
- The plugin must obtain the evaluator from the C++ debug process specifically, not the generic `XDebugSession` evaluator — Rider exposes C++ debugging via `CidrDebugProcess` (from the CLion/Rider C++ debugger subsystem)
- **Implementation spike required:** During implementation, the first step should be to verify the exact API path for evaluating a C++ expression in a paused Rider debug session. Candidate APIs:
  - `XDebugSession` → `getDebugProcess()` → cast to Rider's C++ debug process → evaluate
  - `XStackFrame.evaluateExpression()` on the current stack frame
  - Rider's own `EvaluateExpressionAction` internals (how the "Evaluate Expression" dialog works)
- The implementation should mimic what happens when a user types the expression into Rider's "Evaluate Expression" dialog (Ctrl+Alt+F8 / Alt+F8) — that's the known working path

**Behavior:**
- Evaluates the expression on the debugger's background thread
- Parses the result string and maps it to a `DebugContext` enum
- Returns `UNKNOWN` if the expression fails or returns an unexpected value
- Fails silently if the symbol doesn't exist (not a UE project, or DLL not loaded)

#### 3. IndicatorOverlay

A Swing `JLabel` positioned absolutely within the active editor's `JScrollPane`.

**Visual properties:**
- Font: Bold, ~11px monospace
- Padding: 4px horizontal, 2px vertical
- Corner radius: 4px (custom `paintComponent`)
- Margin: 8px from bottom-right edge of editor viewport
- Opaque background, white text

**Color mapping:**

| Context       | Label           | Background   | Text  |
|---------------|-----------------|--------------|-------|
| Server        | `SERVER`        | #CC3333 (Red)| White |
| Client        | `CLIENT`        | #3366CC (Blue)| White |
| Listen Server | `LISTEN SERVER` | #CC9900 (Gold)| White |
| Unknown       | `UNKNOWN`       | #666666 (Gray)| White |

**Lifecycle management:**
- Attaches to the active editor's `JScrollPane` when an editor gains focus
- Detaches when the editor loses focus or closes
- Repositions on editor resize via `ComponentListener` on the scroll pane
- Uses `FileEditorManagerListener` to track active editor changes

#### 4. ToggleIndicatorAction

The menu action registered in `plugin.xml` under `DebugMainMenu`.

- Toggles a project-level boolean stored in a `PersistentStateComponent`
- When disabled, the overlay is never shown regardless of debug state
- Toggling off mid-debug hides the overlay immediately
- State persists across IDE restarts

### Data Flow

```
Debugger pauses at breakpoint
  -> DebugSessionListener.onSessionPaused()
  -> ContextEvaluator.evaluate(session)        [background thread]
  -> Result string mapped to DebugContext enum
  -> SwingUtilities.invokeLater:
       -> IndicatorOverlay.show(context)        [EDT]
       -> JLabel text + background color set
       -> Overlay positioned in active editor

Debugger resumes or stops
  -> DebugSessionListener.onSessionResumed/Stopped()
  -> SwingUtilities.invokeLater:
       -> IndicatorOverlay.hide()               [EDT]
```

## Error Handling & Edge Cases

- **Not a UE project:** Expression evaluation fails — overlay stays hidden, no error shown
- **Not a C++ debug session:** If the active debug session is not using the native C++ debugger (e.g., C# debugging), skip evaluation — overlay stays hidden
- **DLL not loaded yet:** `UnrealEditor-Engine.dll` may not be loaded at early breakpoints — evaluation fails silently, overlay hidden
- **Expression returns empty/null:** Treated as `UNKNOWN` context
- **Multiple debug sessions:** Use the session that triggered the pause event
- **Editor splits:** Overlay attaches to whichever editor pane is focused; moves when focus changes
- **Toggle off mid-debug:** Overlay hides immediately; no further evaluations until re-enabled
- **Editor resize/scroll:** Overlay repositions via `ComponentListener`

## Platform Constraints

- **Windows only** — the MSVC expression syntax `{,,DLL}::Symbol` is specific to the Windows native debugger
- If cross-platform support is desired in the future, the expression syntax would need to change for LLDB on macOS/Linux (e.g., using LLDB expression evaluation syntax instead)

## Build & Packaging

- **Build system:** Gradle with `intellij-platform-plugin`
- **Language:** Kotlin
- **Target platform:** IntelliJ 2025.1+ (Rider 2025.x)
- **Plugin dependencies:** `com.intellij.modules.platform`, `com.intellij.modules.lang`, `com.intellij.modules.xdebugger`
- **Output:** Single `.zip` plugin artifact, installable via "Install Plugin from Disk"
- **Migration note:** Current project uses `.iml` module setup — implementation will migrate to Gradle-based build

## File Structure (Planned)

```
src/main/kotlin/com/unrealdebugger/
  DebugContext.kt              -- Enum: SERVER, CLIENT, LISTEN_SERVER, UNKNOWN
  ContextEvaluator.kt          -- Expression evaluation logic
  IndicatorOverlay.kt          -- JLabel overlay component + positioning
  DebugSessionListener.kt      -- XDebugger session event listener
  ToggleIndicatorAction.kt     -- Menu toggle action
  IndicatorSettings.kt         -- PersistentStateComponent for toggle state
src/main/resources/META-INF/
  plugin.xml                   -- Plugin descriptor
build.gradle.kts               -- Gradle build configuration
settings.gradle.kts            -- Gradle settings
```
