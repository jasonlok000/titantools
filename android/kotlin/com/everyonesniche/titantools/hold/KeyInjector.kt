package com.everyonesniche.titantools.hold

import android.os.IBinder
import android.os.SystemClock
import android.view.InputDevice
import android.view.InputEvent
import android.view.KeyCharacterMap
import android.view.KeyEvent
import com.everyonesniche.titantools.shizuku.ShizukuBinder
import com.everyonesniche.titantools.shizuku.Shizuku
import java.lang.reflect.Method

/**
 * Types whole key presses through the system input service, as Shizuku's user (which may inject events), from Hold Symbols
 * 0.3.1. Events go through the normal input pipeline, so the selected keyboard app still receives them like typed keys. Calls
 * are synchronous: when one returns, the event is queued ahead of the next physical key.
 */
object KeyInjector {
    private const val MODE_ASYNC = 0
    private val virtual: KeyCharacterMap = KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD)
    @Volatile private var manager: Any? = null
    @Volatile private var inject: Method? = null

    fun ready(): Boolean {
        if (manager != null && inject != null && Shizuku.running()) return true
        return connect()
    }

    @Synchronized
    private fun connect(): Boolean {
        if (!Shizuku.allowed()) {
            manager = null
            inject = null
            return false
        }
        return try {
            val input = Class.forName("android.os.ServiceManager").getMethod("getService", String::class.java)
                .invoke(null, "input") as IBinder? ?: return false
            val stub = Class.forName("android.hardware.input.IInputManager\$Stub")
            manager = stub.getMethod("asInterface", IBinder::class.java).invoke(null, ShizukuBinder(input))
            // Found on the interface: Stub.Proxy's method is on Android's blocked list, IInputManager's is not.
            inject = Class.forName("android.hardware.input.IInputManager").getMethod("injectInputEvent", InputEvent::class.java, Int::class.javaPrimitiveType)
            true
        } catch (error: Throwable) {
            manager = null
            inject = null
            false
        }
    }

    /** One whole press of [keyCode]. */
    fun press(keyCode: Int, metaState: Int = 0): Boolean {
        val now = SystemClock.uptimeMillis()
        return send(arrayOf(event(now, KeyEvent.ACTION_DOWN, keyCode, metaState), event(now, KeyEvent.ACTION_UP, keyCode, metaState)))
    }

    /** Types [c] with the standard virtual key map, with Shift when it needs it, as `input text` does. */
    fun type(c: Char): Boolean {
        val events = virtual.getEvents(charArrayOf(c)) ?: return false
        if (events.isEmpty()) return false
        val now = SystemClock.uptimeMillis()
        return send(Array(events.size) { event(now, events[it].action, events[it].keyCode, events[it].metaState) })
    }

    private fun event(time: Long, action: Int, code: Int, meta: Int) =
        KeyEvent(time, time, action, code, 0, meta, KeyCharacterMap.VIRTUAL_KEYBOARD, 0, 0, InputDevice.SOURCE_KEYBOARD)

    private fun send(events: Array<KeyEvent>): Boolean {
        val m = manager ?: return false
        val i = inject ?: return false
        var sent = 0
        try {
            for (e in events) {
                if (i.invoke(m, e, MODE_ASYNC) != true) break
                sent++
            }
        } catch (error: Throwable) {
            manager = null
            inject = null
        }
        if (sent in 1 until events.size && events[sent - 1].action == KeyEvent.ACTION_DOWN) {
            // Never leave a typed key down.
            try { i.invoke(m, KeyEvent.changeAction(events[sent - 1], KeyEvent.ACTION_UP), MODE_ASYNC) } catch (ignored: Throwable) {}
        }
        return sent == events.size
    }
}
