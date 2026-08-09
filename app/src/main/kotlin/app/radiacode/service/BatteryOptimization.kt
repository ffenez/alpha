package app.radiacode.service

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings

/**
 * Battery-optimization exemption helper. Continuous BLE measurement dies under
 * Doze without it (ADR 001 field experience). The UI decides when to fire the
 * intent; this only builds it.
 */
object BatteryOptimization {

    fun isExempt(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    /** Direct per-app exemption dialog; requires REQUEST_IGNORE_BATTERY_OPTIMIZATIONS. */
    @SuppressLint("BatteryLife")
    fun buildRequestIntent(context: Context): Intent = Intent(
        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
        Uri.parse("package:${context.packageName}"),
    )
}
