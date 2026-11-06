package com.lockweb.app

import org.junit.Assert.assertEquals
import org.junit.Test

class AppScaffoldTest {
    @Test
    fun packageName_isCorrect() {
        val expectedPackage = "com.lockweb.app"
        assertEquals("com.lockweb.app", expectedPackage)
    }
}
