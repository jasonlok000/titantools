package com.everyonesniche.titantools.rotate

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.view.WindowManager

/** The "Rotate" launcher entry, there while the Rotation tool is on: opening it flips the screen, says which way, and closes. */
class RotateActivity : Activity() {
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
        Rotation.flip(this) { message ->
            // After the screen has turned: a toast shown while it turns is dropped.
            com.everyonesniche.titantools.core.Feedback.say(this, message, ROTATE_SETTLE_MILLIS)
            close()
        }
    }

    private companion object {
        const val ROTATE_SETTLE_MILLIS = 700L
    }

    private fun close() {
        finishAndRemoveTask()
        @Suppress("DEPRECATION") overridePendingTransition(0, 0)
    }
}
