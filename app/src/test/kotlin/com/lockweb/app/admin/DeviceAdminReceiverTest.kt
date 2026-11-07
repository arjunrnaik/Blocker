package com.lockweb.app.admin

import org.junit.Assert.assertFalse
import org.junit.Test

class DeviceAdminReceiverTest {

    @Test
    fun testDefaultState_isNotActive() {
        assertFalse(LockWebDeviceAdminReceiver.isAdminActive.value)
    }
}
