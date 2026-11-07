package com.lockweb.app.di

import android.content.Context
import com.lockweb.app.blocklist.BlocklistManager
import com.lockweb.app.data.AppDatabase
import com.lockweb.app.data.RuleRepository
import com.lockweb.app.data.SettingsRepository
import com.lockweb.app.data.settingsDataStore
import com.lockweb.app.guard.GuardSessionManager
import com.lockweb.app.security.AndroidKeyStoreVault
import com.lockweb.app.security.AuthManager
import com.lockweb.app.security.FileCredentialStorage
import com.lockweb.app.security.FileRateLimitStore
import com.lockweb.app.security.InMemoryRateLimitStore
import com.lockweb.app.security.PasswordHasher
import com.lockweb.app.security.RateLimiter
import com.lockweb.app.security.RecoveryCodeManager

/**
 * Dependency injection container for LockWeb.
 * In accordance with MASTER_PROMPT.md Section 3: Lightweight manual DI without external DI frameworks.
 */
class AppContainer(private val context: Context) {

    val database: AppDatabase by lazy {
        AppDatabase.getInstance(context)
    }

    val settingsRepository: SettingsRepository by lazy {
        SettingsRepository(context.settingsDataStore)
    }

    val blocklistManager: BlocklistManager by lazy {
        BlocklistManager(context)
    }

    val ruleRepository: RuleRepository by lazy {
        RuleRepository(
            blockRuleDao = database.blockRuleDao(),
            settingsRepository = settingsRepository,
            blocklistManager = blocklistManager
        )
    }

    val passwordHasher: PasswordHasher by lazy {
        PasswordHasher()
    }

    val keyStoreVault: AndroidKeyStoreVault by lazy {
        AndroidKeyStoreVault()
    }

    val credentialStorage: FileCredentialStorage by lazy {
        FileCredentialStorage(context.filesDir)
    }

    val rateLimiter: RateLimiter by lazy {
        RateLimiter(FileRateLimitStore(context.filesDir))
    }

    val recoveryCodeManager: RecoveryCodeManager by lazy {
        RecoveryCodeManager(passwordHasher)
    }

    val authManager: AuthManager by lazy {
        AuthManager(
            passwordHasher = passwordHasher,
            keyStoreVault = keyStoreVault,
            storage = credentialStorage,
            rateLimiter = rateLimiter,
            recoveryCodeManager = recoveryCodeManager
        )
    }

    val guardSessionManager: GuardSessionManager by lazy {
        GuardSessionManager.instance
    }
}
