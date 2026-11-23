package com.everyonesniche.titantools.quick

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper
import com.everyonesniche.titantools.core.Status

/** The flashlight: the back camera's flash, through CameraManager.setTorchMode. No Shizuku. */
object Torch {
    private val main = Handler(Looper.getMainLooper())
    @Volatile var on = false
        private set
    private var camera: String? = null
    private var listening = false

    private val callback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(id: String, enabled: Boolean) {
            if (id != camera) return
            on = enabled
            Status.changed()
        }

        override fun onTorchModeUnavailable(id: String) {
            if (id == camera && on) {
                on = false
                Status.changed()
            }
        }
    }

    /** Finds the back flash and follows its state. False when the phone has none. */
    fun ready(context: Context): Boolean {
        val manager = context.getSystemService(CameraManager::class.java) ?: return false
        if (camera == null) camera = manager.cameraIdList.firstOrNull { id ->
            val c = manager.getCameraCharacteristics(id)
            c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true &&
                c.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
        } ?: manager.cameraIdList.firstOrNull { manager.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
        if (camera != null && !listening) {
            manager.registerTorchCallback(callback, main)
            listening = true
        }
        return camera != null
    }

    /** Turns the flashlight on or off. The torch callback answers at once on registering, so [on] is known by then. */
    fun toggle(context: Context, done: (String) -> Unit) {
        val app = context.applicationContext
        val fresh = !listening
        if (!ready(app)) {
            done("This phone has no flashlight.")
            return
        }
        main.postDelayed({
            try {
                val next = !on
                app.getSystemService(CameraManager::class.java).setTorchMode(camera!!, next)
                done(if (next) "Flashlight on" else "Flashlight off")
            } catch (error: Exception) {
                done("Couldn't use the flashlight: the camera may be busy.")
            }
        }, if (fresh) 150 else 0)
    }
}
