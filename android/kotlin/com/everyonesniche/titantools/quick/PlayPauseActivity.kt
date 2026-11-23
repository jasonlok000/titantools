package com.everyonesniche.titantools.quick

import android.media.AudioManager
import android.os.SystemClock
import android.view.KeyEvent
import com.everyonesniche.titantools.core.Prefs

/** The "Play/Pause" launcher entry: sends play/pause to the app that last played something. No Shizuku. */
class PlayPauseActivity : QuickActivity() {
    override fun act(done: (String?) -> Unit) {
        if (!Prefs.on(this, Prefs.PLAY_ON)) return done("Play/Pause is off. Turn it on in Titan Tools.")
        send(this)
        done(null)
    }

    companion object {
        fun send(context: android.content.Context) {
            val audio = context.getSystemService(AudioManager::class.java) ?: return
            val time = SystemClock.uptimeMillis()
            audio.dispatchMediaKeyEvent(KeyEvent(time, time, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, 0))
            audio.dispatchMediaKeyEvent(KeyEvent(time, time, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, 0))
        }
    }
}
