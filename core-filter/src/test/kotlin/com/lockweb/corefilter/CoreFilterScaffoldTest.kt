package com.lockweb.corefilter

import org.junit.Assert.assertEquals
import org.junit.Test

class CoreFilterScaffoldTest {
    @Test
    fun moduleName_isCorrect() {
        assertEquals("core-filter", CoreFilterPlaceholder.MODULE_NAME)
    }
}
