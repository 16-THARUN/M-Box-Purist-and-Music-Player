package com.mediaplayer.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.PowerManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class DeviceState {
    OPTIMAL,      // Plugged in or battery > 50%
    LOW_BATTERY,  // Battery < 20%
    POWER_SAVER   // Power save mode enabled
}

@Singleton
class DeviceStateMonitor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val _stateFlow = MutableStateFlow(DeviceState.OPTIMAL)
    val stateFlow: StateFlow<DeviceState> = _stateFlow.asStateFlow()

    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            updateState()
        }
    }

    init {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        }
        context.registerReceiver(receiver, filter)
        updateState()
    }

    private fun updateState() {
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPct = level * 100 / scale.toFloat()
        
        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || 
                         status == BatteryManager.BATTERY_STATUS_FULL

        val isPowerSaveMode = powerManager.isPowerSaveMode

        _stateFlow.value = when {
            isPowerSaveMode -> DeviceState.POWER_SAVER
            batteryPct < 20f && !isCharging -> DeviceState.LOW_BATTERY
            else -> DeviceState.OPTIMAL
        }
    }
}
