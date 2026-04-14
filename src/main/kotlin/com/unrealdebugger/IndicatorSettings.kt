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
