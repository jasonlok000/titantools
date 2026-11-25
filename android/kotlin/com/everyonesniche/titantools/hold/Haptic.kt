package com.everyonesniche.titantools.hold

import android.content.Context
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings

/**
 * The same feel as TitanKeys' haptics (its Haptics.kt families): its own waveforms at the default strength, never the phone's
 * ready-made effects, and silent while the phone's touch feedback is off.
 */
object Haptic {
    /** Short then long, rising: done. A hold's action ran. */
    val CONFIRM = listOf(15L, 40L, 35L)
    /** A quiet swell: a held letter became its symbol. */
    val CHANGE = listOf(12L, 35L, 22L, 35L, 12L)

    fun play(context: Context, pattern: List<Long>) {
        val vibrator = context.getSystemService(Vibrator::class.java) ?: return
        if (!vibrator.hasVibrator()) return
        if (Settings.System.getInt(context.contentResolver, Settings.System.HAPTIC_FEEDBACK_ENABLED, 1) == 0) return
        val effect = if (pattern.size == 1) VibrationEffect.createOneShot(pattern[0], VibrationEffect.DEFAULT_AMPLITUDE)
            else VibrationEffect.createWaveform(longArrayOf(0) + pattern.toLongArray(), -1)
        vibrator.vibrate(effect, VibrationAttributes.Builder().setUsage(VibrationAttributes.USAGE_HARDWARE_FEEDBACK).build())
    }
}
