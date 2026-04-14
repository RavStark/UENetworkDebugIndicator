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
