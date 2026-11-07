package com.lockweb.app.blocklist

import android.content.Context
import com.lockweb.corefilter.BlocklistParser
import java.io.File
import java.io.FileOutputStream
import java.io.StringReader

sealed interface BlocklistValidationResult {
    data class Valid(val domains: List<String>) : BlocklistValidationResult
    data class Invalid(val reason: String) : BlocklistValidationResult
}

/**
 * Manages local storage, snapshot extraction, and atomic updates for the OISD NSFW blocklist.
 */
class BlocklistManager(private val context: Context) {

    companion object {
        const val OISD_SNAPSHOT_ASSET = "oisd_nsfw_snapshot.txt"
        const val BLOCKLIST_FILENAME = "oisd_nsfw.txt"
        const val BLOCKLIST_DIR = "blocklists"
        const val OISD_UPDATE_URL = "https://nsfw.oisd.nl/domainswild"

        /**
         * Pure function validating downloaded blocklist content against truncation or corruption.
         */
        fun validateBlocklistContent(
            newContent: String,
            currentFileSizeBytes: Long
        ): BlocklistValidationResult {
            if (newContent.isBlank()) {
                return BlocklistValidationResult.Invalid("Downloaded content is empty")
            }

            val newSizeBytes = newContent.toByteArray(Charsets.UTF_8).size.toLong()

            // If an existing file exists and has size > 100 bytes, ensure new download is >= 50% of its size
            if (currentFileSizeBytes > 100L && newSizeBytes < (currentFileSizeBytes / 2)) {
                return BlocklistValidationResult.Invalid(
                    "Downloaded size ($newSizeBytes bytes) is suspiciously small (< 50% of current $currentFileSizeBytes bytes)"
                )
            }

            val parsedDomains = BlocklistParser.parse(StringReader(newContent))
            if (parsedDomains.isEmpty()) {
                return BlocklistValidationResult.Invalid("No valid domains parsed from blocklist")
            }

            return BlocklistValidationResult.Valid(parsedDomains)
        }
    }

    private val blocklistDir: File
        get() = File(context.filesDir, BLOCKLIST_DIR).apply { mkdirs() }

    val blocklistFile: File
        get() = File(blocklistDir, BLOCKLIST_FILENAME)

    /**
     * Ensures the bundled snapshot asset is copied to internal storage on first run.
     */
    fun ensureSnapshotCopied(): File {
        val file = blocklistFile
        if (!file.exists() || file.length() == 0L) {
            try {
                context.assets.open(OISD_SNAPSHOT_ASSET).use { input ->
                    FileOutputStream(file).use { output ->
                        input.copyTo(output)
                    }
                }
            } catch (e: Exception) {
                // If asset copy fails, create an empty file
                file.createNewFile()
            }
        }
        return file
    }

    /**
     * Atomically replaces the current blocklist file with newly validated content.
     */
    fun atomicUpdate(newContent: String): Boolean {
        val currentSize = if (blocklistFile.exists()) blocklistFile.length() else 0L
        val validation = validateBlocklistContent(newContent, currentSize)
        if (validation !is BlocklistValidationResult.Valid) {
            return false
        }

        val tempFile = File(blocklistDir, "$BLOCKLIST_FILENAME.tmp")
        return try {
            tempFile.writeText(newContent, Charsets.UTF_8)
            // Atomic rename
            tempFile.renameTo(blocklistFile)
        } catch (_: Exception) {
            tempFile.delete()
            false
        }
    }

    /**
     * Loads and parses all domains from the current blocklist file.
     */
    fun loadDomains(): List<String> {
        val file = ensureSnapshotCopied()
        if (!file.exists() || file.length() == 0L) return emptyList()

        return file.bufferedReader().use { reader ->
            BlocklistParser.parse(reader)
        }
    }
}
