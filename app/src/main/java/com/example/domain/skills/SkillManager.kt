package com.example.domain.skills

import android.content.Context
import android.net.Uri
import com.example.domain.model.Skill
import com.example.domain.model.SkillPermission
import com.example.domain.model.SkillSupportingFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class SkillManager(private val context: Context) {

    fun parseSkillMd(
        rawMarkdown: String,
        fallbackName: String = "Imported Skill",
        supportingFiles: List<SkillSupportingFile> = emptyList(),
        isBuiltIn: Boolean = false
    ): Skill {
        var name = fallbackName
        var description = ""
        var category = "Custom"
        val permissions = mutableSetOf(SkillPermission.READ_ATTACHMENTS)
        var body = rawMarkdown.trim()

        val trimmed = rawMarkdown.trimStart()
        if (trimmed.startsWith("---")) {
            val endIdx = trimmed.indexOf("\n---", startIndex = 3)
            if (endIdx > 0) {
                val frontmatter = trimmed.substring(3, endIdx)
                body = trimmed.substring(endIdx + 4).trim()
                frontmatter.lines().forEach { line ->
                    val colon = line.indexOf(':')
                    if (colon > 0) {
                        val key = line.substring(0, colon).trim().lowercase()
                        val value = line.substring(colon + 1).trim().trim('"', '\'')
                        when (key) {
                            "name" -> if (value.isNotBlank()) name = value
                            "description" -> if (value.isNotBlank()) description = value
                            "category" -> if (value.isNotBlank()) category = value
                            "permissions" -> {
                                if (value.contains("write", true)) permissions.add(SkillPermission.WRITE_PROJECT_FILES)
                                if (value.contains("search", true) || value.contains("network", true)) {
                                    permissions.add(SkillPermission.NETWORK_SEARCH)
                                }
                                if (value.contains("exec", true) || value.contains("script", true)) {
                                    permissions.add(SkillPermission.EXECUTE_SANDBOX)
                                }
                            }
                        }
                    }
                }
            }
        }

        if (description.isBlank()) {
            description = body.lines()
                .firstOrNull { it.isNotBlank() && !it.startsWith("#") }
                ?.take(140)
                ?: "Custom SKILL.md instruction module."
        }

        val hasScripts = supportingFiles.any { it.isScript }
        if (hasScripts) {
            permissions.add(SkillPermission.EXECUTE_SANDBOX)
        }

        return Skill(
            id = "skill_${UUID.randomUUID()}",
            name = name,
            description = description,
            category = category,
            skillMdContent = rawMarkdown,
            instructionsBody = body,
            supportingFiles = supportingFiles,
            requiredPermissions = permissions.toList(),
            isEnabled = true,
            isBuiltIn = isBuiltIn,
            isTrusted = isBuiltIn || !hasScripts
        )
    }

    suspend fun importFromUri(uri: Uri, fileName: String): Result<List<Skill>> = withContext(Dispatchers.IO) {
        runCatching {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: error("Unable to read selected file.")

            if (fileName.endsWith(".zip", ignoreCase = true)) {
                importSkillsFromZip(bytes, fileName)
            } else {
                val md = String(bytes, StandardCharsets.UTF_8)
                val baseName = fileName.substringBeforeLast('.').ifBlank { "Custom Skill" }
                listOf(parseSkillMd(md, fallbackName = baseName))
            }
        }
    }

    private fun importSkillsFromZip(zipBytes: ByteArray, archiveName: String): List<Skill> {
        val entries = mutableMapOf<String, String>()
        ZipInputStream(zipBytes.inputStream()).use { zis ->
            var entry = zis.nextEntry
            var totalBytes = 0L
            while (entry != null) {
                val cleanName = entry.name.replace('\\', '/').trimStart('/')
                if (!entry.isDirectory && !cleanName.contains("..") && !cleanName.startsWith("__MACOSX")) {
                    val data = zis.readBytes()
                    totalBytes += data.size
                    if (totalBytes > 10 * 1024 * 1024) {
                        error("Skill ZIP exceeds 10 MB safety limit.")
                    }
                    if (data.size <= 512 * 1024) {
                        entries[cleanName] = String(data, StandardCharsets.UTF_8)
                    }
                }
                entry = zis.nextEntry
            }
        }

        val skillMdEntries = entries.keys.filter {
            it.equals("SKILL.md", ignoreCase = true) || it.endsWith("/SKILL.md", ignoreCase = true)
        }

        if (skillMdEntries.isEmpty()) {
            val firstMd = entries.entries.firstOrNull { it.key.endsWith(".md", ignoreCase = true) }
                ?: error("No SKILL.md or Markdown instructions found in $archiveName.")
            val others = entries.filterKeys { it != firstMd.key }.map { (path, content) ->
                SkillSupportingFile(path, content, isScript = isScriptPath(path))
            }
            return listOf(parseSkillMd(firstMd.value, archiveName.substringBeforeLast('.'), others))
        }

        return skillMdEntries.map { skillMdPath ->
            val folderPrefix = skillMdPath.substringBeforeLast("SKILL.md", "")
            val supporting = entries.filterKeys { path ->
                path != skillMdPath && (folderPrefix.isEmpty() || path.startsWith(folderPrefix))
            }.map { (path, content) ->
                val rel = if (folderPrefix.isNotEmpty()) path.removePrefix(folderPrefix) else path
                SkillSupportingFile(rel, content, isScript = isScriptPath(rel))
            }
            val fallback = folderPrefix.trimEnd('/').substringAfterLast('/').ifBlank {
                archiveName.substringBeforeLast('.')
            }
            parseSkillMd(entries[skillMdPath].orEmpty(), fallback, supporting)
        }
    }

    fun exportSkillToZipBytes(skill: Skill): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            zos.putNextEntry(ZipEntry("SKILL.md"))
            zos.write(skill.skillMdContent.toByteArray(StandardCharsets.UTF_8))
            zos.closeEntry()

            skill.supportingFiles.forEach { file ->
                val safePath = file.relativePath.replace("..", "").trimStart('/')
                if (safePath.isNotBlank()) {
                    zos.putNextEntry(ZipEntry(safePath))
                    zos.write(file.content.toByteArray(StandardCharsets.UTF_8))
                    zos.closeEntry()
                }
            }
        }
        return baos.toByteArray()
    }

    /**
     * Automatically selects up to 3 most relevant enabled skills for a user prompt,
     * combined with any manually pinned skills for the conversation/project.
     */
    fun selectActiveSkills(
        userPrompt: String,
        allSkills: List<Skill>,
        manualSkillIds: List<String>,
        autoDiscover: Boolean
    ): List<Skill> {
        val manual = allSkills.filter { it.id in manualSkillIds }
        if (!autoDiscover || userPrompt.isBlank()) return manual

        val tokens = userPrompt.lowercase()
            .split(Regex("[^a-z0-9.+_-]+"))
            .filter { it.length >= 2 }
            .toSet()

        val autoMatched = allSkills
            .filter { it.isEnabled && it.id !in manualSkillIds }
            .map { skill ->
                val haystack = "${skill.name} ${skill.category} ${skill.description}".lowercase()
                var score = 0
                for (tok in tokens) {
                    if (haystack.contains(tok)) score += if (tok.length >= 4) 3 else 1
                }
                if (tokens.any { skill.name.lowercase().contains(it) && it.length >= 3 }) score += 4
                skill to score
            }
            .filter { it.second >= 3 }
            .sortedByDescending { it.second }
            .take(3)
            .map { it.first }

        return (manual + autoMatched).distinctBy { it.id }
    }

    fun buildCombinedSystemPrompt(
        baseSystemPrompt: String,
        projectInstructions: String,
        activeSkills: List<Skill>,
        searchContext: String = ""
    ): String = buildString {
        if (baseSystemPrompt.isNotBlank()) {
            appendLine(baseSystemPrompt.trim())
        }
        if (projectInstructions.isNotBlank()) {
            appendLine("\n## Project Workspace Instructions")
            appendLine(projectInstructions.trim())
        }
        if (activeSkills.isNotEmpty()) {
            appendLine("\n## Active Skills")
            activeSkills.forEach { skill ->
                appendLine("\n### Skill: ${skill.name} (${skill.category})")
                appendLine(skill.instructionsBody.trim())
                val safeRefs = skill.supportingFiles.filter { !it.isScript }.take(2)
                safeRefs.forEach { ref ->
                    appendLine("\nReference (${ref.relativePath}):")
                    appendLine(ref.content.take(2500))
                }
            }
        }
        if (searchContext.isNotBlank()) {
            appendLine("\n## Live Web Search Context")
            appendLine(searchContext.trim())
        }
    }.trim()

    private fun isScriptPath(path: String): Boolean {
        val ext = path.substringAfterLast('.', "").lowercase()
        return ext in setOf("sh", "bash", "py", "js", "ts", "ps1", "bat", "cmd", "rb", "pl")
    }
}
