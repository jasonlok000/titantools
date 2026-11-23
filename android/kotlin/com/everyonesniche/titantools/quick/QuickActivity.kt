package com.everyonesniche.titantools.quick

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Toast

/**
 * A launcher entry with no screen, for Shortcut keys and the side button: opening it does one thing, says what in a short
 * toast, and closes at once. Each is there only while its tool is on (Shortcuts.sync).
 */
abstract class QuickActivity : Activity() {
    /** Does the thing, then calls [done] on the main thread with what to say, or null to say nothing. */
    protected abstract fun act(done: (String?) -> Unit)

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        @Suppress("DEPRECATION") overridePendingTransition(0, 0)
        window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        window.setLayout(1, 1)
        setContentView(View(this))
        if (state != null) {
            close()
            return
        }
        act { message ->
            if (message != null) Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
            close()
        }
    }

    private fun close() {
        finishAndRemoveTask()
        @Suppress("DEPRECATION") overridePendingTransition(0, 0)
    }
}
