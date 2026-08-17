package app.alpha.ui.logic

/**
 * Runtime permissions for onboarding, by SDK level. Plain strings so the
 * selection is JVM-testable; values mirror android.Manifest.permission.
 */
object OnboardingPermissions {
    const val BLUETOOTH_SCAN = "android.permission.BLUETOOTH_SCAN"
    const val BLUETOOTH_CONNECT = "android.permission.BLUETOOTH_CONNECT"
    const val ACCESS_FINE_LOCATION = "android.permission.ACCESS_FINE_LOCATION"
    const val POST_NOTIFICATIONS = "android.permission.POST_NOTIFICATIONS"

    /**
     * API 31+ uses the dedicated BLE permissions (scan is neverForLocation);
     * below that, BLE scanning requires fine location. Notifications became a
     * runtime permission at API 33.
     */
    fun required(sdkInt: Int): List<String> = buildList {
        if (sdkInt >= 31) {
            add(BLUETOOTH_SCAN)
            add(BLUETOOTH_CONNECT)
        } else {
            add(ACCESS_FINE_LOCATION)
        }
        if (sdkInt >= 33) add(POST_NOTIFICATIONS)
    }
}
