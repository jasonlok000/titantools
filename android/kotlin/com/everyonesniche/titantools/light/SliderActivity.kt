package com.everyonesniche.titantools.light

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import com.everyonesniche.titantools.core.Prefs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.roundToInt

/**
 * The keyboard light's two sliders, as a small pop-up with Android's own SeekBar (Ink has no slider that can be dragged).
 * Brightness: 4–100%, saved as you drag, and the keys light at that level while you do (LightService.preview). Dark room:
 * 0–100% of a log scale from 0.5 to 200 lux, with the room's reading right now under it.
 */
class SliderActivity : Activity() {
    private val main = Handler(Looper.getMainLooper())
    private lateinit var room: TextView
    private var threshold = 0

    private val roomTick = object : Runnable {
        override fun run() {
            val lux = LightService.instance?.roomLux ?: Float.NaN
            room.text = if (lux.isFinite()) {
                val now = luxPercent(lux)
                "Your room now: $now%, ${if (now < threshold) "dark" else "bright"}"
            } else "Your room now: shown while the keyboard light is on."
            main.postDelayed(this, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val kind = intent.getStringExtra(EXTRA_KIND) ?: KIND_LEVEL
        val brightness = kind == KIND_LEVEL
        val dim = kind == KIND_DIM
        val title = when (kind) { KIND_DARK -> "Dark room below"; KIND_DIM -> "Extra dim strength"; else -> "Brightness" }
        val pad = (20 * resources.displayMetrics.density).toInt()
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        fun text(size: Float, muted: Boolean = false) = TextView(this).apply {
            textSize = size
            if (muted) setTextColor(Color.GRAY)
            layout.addView(this)
        }
        text(20f).text = title
        val value = text(28f)
        val bar = SeekBar(this)
        layout.addView(bar, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            .apply { topMargin = pad / 2; bottomMargin = pad / 2 })
        text(15f, true).text = when (kind) {
            KIND_LEVEL -> "How bright the keys are when lit: 100% is the brightest the keyboard goes. While you drag, the keys light at that level so you can see it."
            KIND_DIM -> "How much Extra dim darkens the screen when Night mode turns on. While Night mode is on, it changes as you drag."
            else -> "How dark the room must be: the keys count it as dark below this. Lower means darker."
        }
        room = text(15f)
        room.visibility = if (kind == KIND_DARK) TextView.VISIBLE else TextView.GONE
        val done = Button(this).apply {
            text = "Done"
            setOnClickListener { finish() }
        }
        layout.addView(done, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            .apply { gravity = Gravity.END; topMargin = pad / 2 })
        setContentView(layout)
        // Full width (a dialog is narrow by default), so the bar is easy to drag.
        window.setLayout(android.view.WindowManager.LayoutParams.MATCH_PARENT, android.view.WindowManager.LayoutParams.WRAP_CONTENT)
        setTitle(title)

        if (brightness) {
            bar.min = 4
            bar.max = 100
            bar.progress = (Prefs.level(this) / 2.55f).roundToInt().coerceIn(4, 100)
        } else if (dim) {
            bar.min = 0
            bar.max = 100
            bar.progress = Prefs.of(this).getInt(Prefs.NIGHT_DIM_LEVEL, 50).coerceIn(0, 100)
        } else {
            bar.min = 0
            bar.max = 100
            bar.progress = luxPercent(Prefs.darkLux(this))
        }
        threshold = bar.progress
        value.text = "${bar.progress}%"
        bar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                value.text = "$progress%"
                if (!fromUser) return
                threshold = progress
                val editor = Prefs.of(this@SliderActivity).edit()
                if (dim) {
                    Prefs.of(this@SliderActivity).edit().putInt(Prefs.NIGHT_DIM_LEVEL, progress).apply()
                    // Live only while Night mode has Extra dim on: the phone's own strength, through Shizuku, off the main thread.
                    if (com.everyonesniche.titantools.quick.NightMode.active(this@SliderActivity) &&
                        com.everyonesniche.titantools.shizuku.Shizuku.allowed()) {
                        Thread { try { com.everyonesniche.titantools.shizuku.Shell.putSecure("reduce_bright_colors_level", progress) } catch (ignored: Exception) {} }.start()
                    }
                    return
                }
                if (brightness) {
                    val level = (progress * 2.55f).roundToInt().coerceIn(LightLevels.MIN, 255)
                    LightService.instance?.preview(level)
                    editor.putInt(Prefs.LIGHT_LEVEL_VALUE, level).remove(Prefs.LIGHT_LEVEL).remove(Prefs.LIGHT_LEVEL_NAME)
                } else {
                    editor.putFloat(Prefs.LIGHT_DARK_LUX, percentLux(progress))
                }
                editor.apply()
                LightService.sync(this@SliderActivity)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
        })
        if (kind == KIND_DARK) main.post(roomTick)
    }

    override fun onDestroy() {
        main.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    companion object {
        const val EXTRA_KIND = "kind"
        const val KIND_LEVEL = "level"
        const val KIND_DARK = "dark"
        const val KIND_DIM = "dim"
        private const val LUX_MIN = 0.5f
        private val LUX_SPAN = ln(200f / LUX_MIN)

        /** The same scale as the screen's (lib/titantools.ts). */
        fun luxPercent(lux: Float): Int = (ln(maxOf(lux, LUX_MIN) / LUX_MIN) / LUX_SPAN * 100).roundToInt().coerceIn(0, 100)
        fun percentLux(percent: Int): Float = ((LUX_MIN * exp(percent / 100f * LUX_SPAN)) * 10).roundToInt() / 10f
    }
}
