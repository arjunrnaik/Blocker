package com.lockweb.app.data

import com.lockweb.app.blocklist.BlocklistManager
import com.lockweb.app.vpn.LockWebVpnService
import com.lockweb.corefilter.DomainNormalizer
import com.lockweb.corefilter.ParseResult
import com.lockweb.corefilter.RuleMode
import com.lockweb.corefilter.RuleSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext

sealed interface AddRuleResult {
    data class Success(val entity: BlockRuleEntity) : AddRuleResult
    data class InvalidDomain(val reason: String) : AddRuleResult
    data object Duplicate : AddRuleResult
}

/**
 * Single source of truth for all blocking rules.
 * Merges Room user rules with the OISD adult blocklist and pushes the compiled
 * immutable RuleSet to LockWebVpnService.
 */
class RuleRepository(
    private val blockRuleDao: BlockRuleDao,
    private val settingsRepository: SettingsRepository,
    private val blocklistManager: BlocklistManager,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {

    val userRules: Flow<List<BlockRuleEntity>> = blockRuleDao.getAllRules()

    init {
        // Automatically recompile and push when user rules or adult blocklist toggle changes
        combine(
            blockRuleDao.getAllRules(),
            settingsRepository.isAdultBlocklistEnabled
        ) { userEntities, adultEnabled ->
            Pair(userEntities, adultEnabled)
        }.onEach { (userEntities, adultEnabled) ->
            compileAndPush(userEntities, adultEnabled)
        }.launchIn(scope)
    }

    suspend fun addRule(
        rawInput: String,
        mode: RuleMode = RuleMode.SUBDOMAINS,
        note: String? = null
    ): AddRuleResult = withContext(Dispatchers.IO) {
        val parseResult = DomainNormalizer.parseInput(rawInput)
        val normalizedHost = when (parseResult) {
            is ParseResult.Success -> parseResult.normalizedHost
            is ParseResult.InvalidCharacters -> return@withContext AddRuleResult.InvalidDomain("Invalid characters in domain")
            is ParseResult.EmptyLabel -> return@withContext AddRuleResult.InvalidDomain("Domain cannot contain empty labels")
            is ParseResult.LabelTooLong -> return@withContext AddRuleResult.InvalidDomain("Domain label exceeds 63 characters")
            is ParseResult.DomainTooLong -> return@withContext AddRuleResult.InvalidDomain("Domain exceeds 253 characters")
            is ParseResult.SingleLabelDomain -> return@withContext AddRuleResult.InvalidDomain("Single-label domains are not allowed")
            is ParseResult.PublicSuffixOnly -> return@withContext AddRuleResult.InvalidDomain("Bare public suffixes cannot be blocked")
            is ParseResult.IpAddressLiteral -> return@withContext AddRuleResult.InvalidDomain("IP address literals are not supported")
            is ParseResult.MalformedInput -> return@withContext AddRuleResult.InvalidDomain("Malformed URL or domain: ${parseResult.reason}")
        }

        val existing = blockRuleDao.findByHost(normalizedHost)
        if (existing != null) {
            return@withContext AddRuleResult.Duplicate
        }

        val entity = BlockRuleEntity(
            normalizedHost = normalizedHost,
            mode = mode.name,
            createdAt = System.currentTimeMillis(),
            note = note?.take(100)
        )
        val id = blockRuleDao.insertRule(entity)
        AddRuleResult.Success(entity.copy(id = id))
    }

    suspend fun deleteRule(id: Long): Boolean = withContext(Dispatchers.IO) {
        blockRuleDao.deleteRule(id) > 0
    }

    suspend fun deleteRuleByHost(host: String): Boolean = withContext(Dispatchers.IO) {
        blockRuleDao.deleteRuleByHost(host) > 0
    }

    suspend fun importRules(rules: List<BlockRuleEntity>): Int = withContext(Dispatchers.IO) {
        val inserted = blockRuleDao.insertAll(rules)
        inserted.count { it > 0 }
    }

    suspend fun recompileAndPush() = withContext(Dispatchers.IO) {
        val userEntities = blockRuleDao.getAllRulesList()
        val adultEnabled = kotlinx.coroutines.flow.first(settingsRepository.isAdultBlocklistEnabled)
        compileAndPush(userEntities, adultEnabled)
    }

    private suspend fun compileAndPush(
        userEntities: List<BlockRuleEntity>,
        adultEnabled: Boolean
    ) = withContext(Dispatchers.IO) {
        val userBlockRules = userEntities.map { it.toBlockRule() }
        val blocklistDomains = if (adultEnabled) {
            blocklistManager.loadDomains()
        } else {
            emptyList()
        }

        val compileResult = RuleSet.compile(
            userRules = userBlockRules,
            blocklistDomains = blocklistDomains
        )

        LockWebVpnService.updateRuleSet(compileResult.ruleSet)
    }
}
