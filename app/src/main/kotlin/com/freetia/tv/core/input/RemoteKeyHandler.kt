package com.freetia.tv.core.input

import android.view.KeyEvent

sealed class RemoteEvent {
    object Up : RemoteEvent()
    object Down : RemoteEvent()
    object Left : RemoteEvent()
    object Right : RemoteEvent()
    object OK : RemoteEvent()
    object Back : RemoteEvent()
    object Home : RemoteEvent()
    data class Number(val digit: Int) : RemoteEvent()
    object ColorRed : RemoteEvent()
    object ColorGreen : RemoteEvent()
    object ColorYellow : RemoteEvent()
    object ColorBlue : RemoteEvent()
}

class RemoteKeyHandler {
    fun handleKeyEvent(keyCode: Int): RemoteEvent? {
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> RemoteEvent.Up
            KeyEvent.KEYCODE_DPAD_DOWN -> RemoteEvent.Down
            KeyEvent.KEYCODE_DPAD_LEFT -> RemoteEvent.Left
            KeyEvent.KEYCODE_DPAD_RIGHT -> RemoteEvent.Right
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> RemoteEvent.OK
            KeyEvent.KEYCODE_BACK -> RemoteEvent.Back
            KeyEvent.KEYCODE_HOME -> RemoteEvent.Home
            KeyEvent.KEYCODE_0 -> RemoteEvent.Number(0)
            KeyEvent.KEYCODE_1 -> RemoteEvent.Number(1)
            KeyEvent.KEYCODE_2 -> RemoteEvent.Number(2)
            KeyEvent.KEYCODE_3 -> RemoteEvent.Number(3)
            KeyEvent.KEYCODE_4 -> RemoteEvent.Number(4)
            KeyEvent.KEYCODE_5 -> RemoteEvent.Number(5)
            KeyEvent.KEYCODE_6 -> RemoteEvent.Number(6)
            KeyEvent.KEYCODE_7 -> RemoteEvent.Number(7)
            KeyEvent.KEYCODE_8 -> RemoteEvent.Number(8)
            KeyEvent.KEYCODE_9 -> RemoteEvent.Number(9)
            KeyEvent.KEYCODE_PROG_RED -> RemoteEvent.ColorRed
            KeyEvent.KEYCODE_PROG_GREEN -> RemoteEvent.ColorGreen
            KeyEvent.KEYCODE_PROG_YELLOW -> RemoteEvent.ColorYellow
            KeyEvent.KEYCODE_PROG_BLUE -> RemoteEvent.ColorBlue
            else -> null
        }
    }

    companion object {
        const val DOUBLE_BACK_TIMEOUT_MS = 2000L
        const val NUMBER_ENTRY_TIMEOUT_MS = 2000L
    }
}
