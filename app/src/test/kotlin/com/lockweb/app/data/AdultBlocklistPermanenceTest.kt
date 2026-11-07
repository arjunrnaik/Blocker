package com.lockweb.app.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier

class AdultBlocklistPermanenceTest {

    @Test
    fun settingsRepository_hasNoMethodToDisableAdultBlocklist() {
        val clazz = SettingsRepository::class.java
        val methods = clazz.declaredMethods

        // 1. Verify enable method exists
        val enableMethod = methods.find { it.name == "enableAdultBlocklistPermanently" }
        assertNotNull("enableAdultBlocklistPermanently must exist", enableMethod)

        // 2. Verify NO disable or toggle-off method exists anywhere on SettingsRepository
        for (method in methods) {
            val name = method.name.lowercase()
            assertFalse(
                "SettingsRepository must never declare a disable method for adult blocklist: ${method.name}",
                name.contains("disable") && name.contains("adult")
            )
            assertFalse(
                "SettingsRepository must never declare a toggle method for adult blocklist: ${method.name}",
                name.contains("toggle") && name.contains("adult")
            )
            if (name.contains("setadultblocklist")) {
                // If any setter exists, it must not accept a boolean parameter to set false
                val booleanParam = method.parameterTypes.any { it == Boolean::class.javaPrimitiveType || it == java.lang.Boolean::class.java }
                assertFalse("No setter should take a boolean allowing disabling: ${method.name}", booleanParam)
            }
        }
    }

    @Test
    fun adultBlocklistKey_isNeverAssignedFalseInRepositoryCode() {
        // Read SettingsRepository source code to verify KEY_ADULT_BLOCKLIST_ENABLED is never assigned false
        val sourceFile = listOf(
            java.io.File("src/main/kotlin/com/lockweb/app/data/SettingsRepository.kt"),
            java.io.File("app/src/main/kotlin/com/lockweb/app/data/SettingsRepository.kt")
        ).find { it.exists() }
        if (sourceFile != null) {
            val content = sourceFile.readText()
            assertFalse(
                "KEY_ADULT_BLOCKLIST_ENABLED must never be assigned false in source code",
                content.contains("KEY_ADULT_BLOCKLIST_ENABLED] = false")
            )
        }
    }
}
