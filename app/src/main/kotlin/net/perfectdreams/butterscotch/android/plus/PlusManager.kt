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

    private val _status = MutableStateFlow(Status.UNKNOWN)
    val status: StateFlow<Status> = _status.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _features = mutableSetOf<Feature>()

    val isPlus: Boolean
        get() = _status.value == Status.PLUS

    fun hasFeature(feature: Feature): Boolean {
        return feature in _features
    }

    fun setFree() {
        _status.value = Status.FREE
        _features.clear()
        _isLoading.value = false
    }

    fun setPlus() {
        _status.value = Status.PLUS

        _features.clear()
        _features.addAll(Feature.entries)

        _isLoading.value = false
    }

    fun setLoading() {
        _status.value = Status.UNKNOWN
        _features.clear()
        _isLoading.value = true
    }

    fun grantFeature(feature: Feature) {
        _features.add(feature)
    }

    fun revokeFeature(feature: Feature) {
        _features.remove(feature)
    }

    fun clearFeatures() {
        _features.clear()
    }
}
