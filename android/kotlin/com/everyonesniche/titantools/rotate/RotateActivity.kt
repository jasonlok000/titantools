package com.everyonesniche.titantools.rotate

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Toast

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
            Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
            close()
        }
    }

    private fun close() {
        finishAndRemoveTask()
        @Suppress("DEPRECATION") overridePendingTransition(0, 0)
    }
}
