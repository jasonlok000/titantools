package com.everyonesniche.titantools.rotate

/** Rotation's choices, pure so the checks can run them. Rotations are Surface.ROTATION_0..3. */
object RotationRule {
    /** Portrait (0) goes to landscape (1); any landscape or upside-down goes back to portrait. */
    fun next(current: Int): Int = if (current == 0) 1 else 0

    fun landscape(rotation: Int): Boolean = rotation == 1 || rotation == 3

    fun label(rotation: Int): String = if (landscape(rotation)) "Landscape" else "Portrait"

    fun validRotation(value: Int?): Boolean = value != null && value in 0..3

    /** The phone's own auto-rotate (0 or 1) and rotation, saved when the tool was first turned on. */
    data class Saved(val autoRotate: Int?, val rotation: Int?)

    /** What goes back when the tool is turned off: (setting, value) pairs, only valid ones that differ from now. */
    fun restoreWrites(saved: Saved, autoRotateNow: Int?, rotationNow: Int?): List<Pair<String, Int>> {
        val writes = mutableListOf<Pair<String, Int>>()
        if (validRotation(saved.rotation) && saved.rotation != rotationNow) writes += "user_rotation" to saved.rotation!!
        if ((saved.autoRotate == 0 || saved.autoRotate == 1) && saved.autoRotate != autoRotateNow) writes += "accelerometer_rotation" to saved.autoRotate!!
        return writes
    }
}
