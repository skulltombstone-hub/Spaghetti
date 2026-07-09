package net.perfectdreams.butterscotch.android.plus

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object PlusManager {

    enum class Status {
        UNKNOWN,
        FREE,
        PLUS
    }

    enum class Feature {
        CLOUD_SYNC,
        THEMES,
        CUSTOM_COVERS,
        EXPERIMENTAL_FEATURES,
        EARLY_ACCESS,
        ONLINE_LIBRARY,
        MULTI_DEVICE_SYNC
    }

    data class PlusState(
        val status: Status = Status.UNKNOWN,
        val features: Set<Feature> = emptySet(),
        val isLoading: Boolean = true
    ) {
        val isPlus: Boolean
            get() = status == Status.PLUS

        fun hasFeature(feature: Feature): Boolean {
            return feature in features
        }
    }

    private val _state = MutableStateFlow(PlusState())

    val state: StateFlow<PlusState> =
        _state.asStateFlow()

    val currentState: PlusState
        get() = _state.value

    val isPlus: Boolean
        get() = currentState.isPlus

    fun hasFeature(feature: Feature): Boolean {
        return currentState.hasFeature(feature)
    }

    internal fun update(
        state: PlusState
    ) {
        _state.value = state
    }
}
