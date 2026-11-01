package com.mediaplayer.viewmodel

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class EqualizerViewModel @Inject constructor() : ViewModel() {
    private val _customTargets = MutableStateFlow<Map<String, FloatArray>>(
        mapOf(
            "KZ Castor" to floatArrayOf(4.5f, 5.7f, 5.3f, 4.8f, 5.6f, 2.3f, 1.1f, 1.0f, -0.8f, -3.6f),
            "Moondrop Aria" to floatArrayOf(3.2f, 2.1f, 1.5f, 0.8f, 0.5f, 1.2f, 2.3f, 3.1f, 4.2f, 3.5f),
            "Sennheiser HD600" to floatArrayOf(0.5f, 0.2f, 0f, 0f, 0f, 0.5f, 1.2f, 1.8f, 2.5f, 3.0f),
            "Sony WH-1000XM5" to floatArrayOf(5.5f, 4.2f, 2.8f, 1.5f, 0.5f, 0.2f, 1.5f, 3.2f, 4.5f, 5.2f),
            "Apple AirPods Pro" to floatArrayOf(2.5f, 1.8f, 1.2f, 0.5f, 0.2f, 0.8f, 1.5f, 2.2f, 3.5f, 4.0f)
        )
    )
    val customTargets: StateFlow<Map<String, FloatArray>> = _customTargets.asStateFlow()

    private val _selectedTargetName = MutableStateFlow<String?>("KZ Castor")
    val selectedTargetName: StateFlow<String?> = _selectedTargetName.asStateFlow()

    private val _bandLevels = MutableStateFlow(floatArrayOf(4.5f, 5.7f, 5.3f, 4.8f, 5.6f, 2.3f, 1.1f, 1.0f, -0.8f, -3.6f))
    val bandLevels: StateFlow<FloatArray> = _bandLevels.asStateFlow()

    fun addCustomTarget(name: String, gains: FloatArray) {
        val updatedMap = _customTargets.value.toMutableMap()
        updatedMap[name] = gains
        _customTargets.value = updatedMap
        _selectedTargetName.value = name
    }

    fun selectTarget(name: String) {
        _selectedTargetName.value = name
        _customTargets.value[name]?.let {
            _bandLevels.value = it
        }
    }
    
    fun updateAllBands(gains: FloatArray) {
        _bandLevels.value = gains
    }
    
    fun updateBand(index: Int, level: Float) {
        val current = _bandLevels.value.copyOf()
        if (index in current.indices) {
            current[index] = level
            _bandLevels.value = current

            _selectedTargetName.value = "Custom"
            val updatedMap = _customTargets.value.toMutableMap()
            updatedMap["Custom"] = current
            _customTargets.value = updatedMap
        }
    }
}
