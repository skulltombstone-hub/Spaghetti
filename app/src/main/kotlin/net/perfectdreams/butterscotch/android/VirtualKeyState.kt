package net.perfectdreams.butterscotch.android

import net.perfectdreams.butterscotch.android.layouts.InputBinding

// Tracks which input bindings are currently held and forwards only edge transitions to JNI.
// Without this, a finger dragging across the joystick would re-fire "key down" on every pointer
// move event, which would spuriously re-trigger GameMaker's keyboard_check_pressed flag.
//
// Refcounting handles the case where two pointers happen to map to the same binding at once (e.g.
// two fingers both landing on the joystick area mapped to "up"): the binding stays down until *all*
// pointers release it. Matches what the WebKT reference frontend does with pressedRefs.
//
// Bindings are the digital InputBinding model (keyboard key or gamepad button). The data classes
// give us correct equals/hashCode, so they work directly as refcount/set keys.
class VirtualKeyState(val runner: ButterscotchDroidRunner) {
    private val refs = HashMap<InputBinding, Int>()

    // We do NOT need to use @Synchronized here because there is only one UI Thread, and we only dispatch it to a channel

    fun acquire(binding: InputBinding) {
        val newCount = (refs[binding] ?: 0) + 1
        refs[binding] = newCount
        if (newCount == 1) {
            dispatch(binding, isDown = true)
        }
    }

    fun release(binding: InputBinding) {
        val newCount = (refs[binding] ?: return) - 1
        if (newCount <= 0) {
            refs.remove(binding)
            dispatch(binding, isDown = false)
        } else {
            refs[binding] = newCount
        }
    }

    // Apply a new "currently-pressed" set for a single pointer, emitting only the delta.
    fun transition(oldKeys: Set<InputBinding>, newKeys: Set<InputBinding>) {
        if (oldKeys == newKeys) return
        for (k in oldKeys) if (k !in newKeys) release(k)
        for (k in newKeys) if (k !in oldKeys) acquire(k)
    }

    fun releaseAll() {
        for ((binding, _) in refs)
            dispatch(binding, isDown = false)
        refs.clear()
    }

    // Forward a binding edge to the runner. Keyboard goes through the existing key path. Gamepad
    // buttons have no host->runner transport yet (that needs a gamepad JNI bridge), so they are
    // dropped for now - the current default layout only uses keyboard bindings.
    private fun dispatch(binding: InputBinding, isDown: Boolean) {
        when (binding) {
            is InputBinding.Keyboard -> runner.onKey(binding.vk, isDown)
            is InputBinding.GamepadButton -> {} // TODO: wire once the gamepad axis/button JNI bridge exists
        }
    }
}