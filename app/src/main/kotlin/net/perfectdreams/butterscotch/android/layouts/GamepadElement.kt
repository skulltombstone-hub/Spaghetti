package net.perfectdreams.butterscotch.android.layouts

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class GamepadElement {
    /**
     * The X position of the element on the overlay (percentage, 0..1, based on the overlay width)
     */
    abstract val positionX: Double
    /**
     * The Y position of the element on the overlay (percentage, 0..1, based on the overlay height)
     */
    abstract val positionY: Double
    /**
     * The scale (X and Y) of the element (scaled by min(overlayWidth, overlayHeight))
     */
    abstract val scale: Double
    /**
     * The opacity of the element
     */
    abstract val opacity: Double

    @Serializable
    @SerialName("Key")
    data class Key(
        override val positionX: Double,
        override val positionY: Double,
        override val scale: Double,
        override val opacity: Double,

        /**
         * The label of the button. If null falls back to a value derived from the [binding].
         */
        val label: String?,
        val type: KeyTrigger,
        val binding: InputBinding
    ) : GamepadElement()

    @Serializable
    @SerialName("Joystick")
    data class Joystick(
        override val positionX: Double,
        override val positionY: Double,
        override val scale: Double,
        override val opacity: Double,
        val up: InputBinding,
        val down: InputBinding,
        val left: InputBinding,
        val right: InputBinding,
    ) : GamepadElement()

    @Serializable
    @SerialName("AnalogJoystick")
    data class AnalogJoystick(
        override val positionX: Double,
        override val positionY: Double,
        override val scale: Double,
        override val opacity: Double,
        val stick: GamepadStick
    ) : GamepadElement()
}