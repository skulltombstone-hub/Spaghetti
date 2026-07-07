package net.perfectdreams.butterscotch.android.runtime

data class RuntimeCapabilities(

        val supportsTouch: Boolean = true,

            val supportsKeyboard: Boolean = true,

                val supportsMouse: Boolean = false,

                    val supportsGamepad: Boolean = true,

                        val supportsVirtualControls: Boolean = true,

                            val supportsSaveSlots: Boolean = true
)
