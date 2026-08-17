package app.alpha.ui.logic

import kotlin.test.Test
import kotlin.test.assertEquals

class PermissionsTest {

    @Test
    fun `api 26-30 needs fine location for ble scanning`() {
        assertEquals(
            listOf(OnboardingPermissions.ACCESS_FINE_LOCATION),
            OnboardingPermissions.required(26),
        )
        assertEquals(
            listOf(OnboardingPermissions.ACCESS_FINE_LOCATION),
            OnboardingPermissions.required(30),
        )
    }

    @Test
    fun `api 31-32 needs the dedicated ble permissions`() {
        assertEquals(
            listOf(
                OnboardingPermissions.BLUETOOTH_SCAN,
                OnboardingPermissions.BLUETOOTH_CONNECT,
            ),
            OnboardingPermissions.required(31),
        )
    }

    @Test
    fun `api 33 plus adds runtime notifications`() {
        assertEquals(
            listOf(
                OnboardingPermissions.BLUETOOTH_SCAN,
                OnboardingPermissions.BLUETOOTH_CONNECT,
                OnboardingPermissions.POST_NOTIFICATIONS,
            ),
            OnboardingPermissions.required(34),
        )
    }
}
