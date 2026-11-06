package app.aircons.util

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import app.aircons.service.AirConsAccessibilityService

data class BondedDevice(val address: String, val name: String)

fun hasBluetoothConnect(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.BLUETOOTH_CONNECT
    ) == PackageManager.PERMISSION_GRANTED
}

fun hasNotificationPermission(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS
    ) == PackageManager.PERMISSION_GRANTED
}

fun loadBondedDevices(context: Context): List<BondedDevice> {
    if (!hasBluetoothConnect(context)) return emptyList()
    return runCatching {
        val manager = context.getSystemService(BluetoothManager::class.java) ?: return emptyList()
        val adapter = manager.adapter ?: return emptyList()
        adapter.bondedDevices.orEmpty().mapNotNull { device ->
            val name = runCatching { device.name }.getOrNull() ?: return@mapNotNull null
            BondedDevice(device.address, name)
        }.sortedWith(
            compareByDescending<BondedDevice> { it.name.contains("AirPods", ignoreCase = true) }
                .thenBy { it.name.lowercase() }
        )
    }.getOrDefault(emptyList())
}

fun isAccessibilityEnabled(context: Context): Boolean {
    if (AirConsAccessibilityService.isRunning()) return true
    val enabled = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false
    return enabled.contains(context.packageName, ignoreCase = true) &&
        enabled.contains("AirConsAccessibilityService", ignoreCase = true)
}

fun openBluetoothSettings(context: Context) {
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

fun openAccessibilitySettings(context: Context) {
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

fun openNotificationSettings(context: Context) {
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
