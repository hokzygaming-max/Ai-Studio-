package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.core.security.SecureKeyStore
import com.example.data.repository.ChatExportManager
import com.example.domain.coding.CodingAgentEngine
import com.example.domain.coding.CodingScaffoldType
import com.example.domain.model.*
import com.example.domain.provider.*
import com.example.domain.skills.BuiltInSkillsSeed
import com.example.domain.skills.SkillManager
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AiStudioDomainTests {

    @Test
    fun `skill manager parses frontmatter and auto discovers relevant skills`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = SkillManager(context)
        val raw = """
            ---
            name: "Rust Systems"
            category: "Languages"
            description: "Memory safe Rust development with tokio and serde"
            ---
            # Rust Systems
            Prefer Result and ownership lifetimes cleanly.
        """.trimIndent()

        val parsed = manager.parseSkillMd(raw)
        assertEquals("Rust Systems", parsed.name)
        assertEquals("Languages", parsed.category)
        assertTrue(parsed.instructionsBody.contains("Prefer Result"))

        val builtIns = BuiltInSkillsSeed.defaultSkills()
        assertEquals(16, builtIns.size)
        val matched = manager.selectActiveSkills(
            userPrompt = "Help me build a Jetpack Compose Android screen with Kotlin",
            allSkills = builtIns,
            manualSkillIds = emptyList(),
            autoDiscover = true
        )
        assertTrue(matched.any { it.name == "Android Development" })
        assertTrue(matched.any { it.name == "Kotlin Development" })
    }

    @Test
    fun `export redacts known secrets and round trips json`() {
        val secret = "sk-test-secret-1234567890"
        val conv = Conversation(id = "c1", title = "Debug Session")
        val msg = ChatMessage(
            id = "m1",
            conversationId = "c1",
            role = MessageRole.USER,
            content = "My key was accidentally pasted: $secret"
        )
        val json = ChatExportManager.exportConversationsJson(listOf(conv to listOf(msg)), listOf(secret))
        assertFalse(json.contains(secret))
        assertTrue(json.contains("[REDACTED]"))

        val imported = ChatExportManager.parseImportedConversationsJson(json)
        assertEquals(1, imported.size)
        assertEquals("Debug Session", imported.first().first.title)
        assertEquals(1, imported.first().second.size)
    }

    @Test
    fun `coding agent extracts proposed files and runs static checks`() {
        val aiOutput = """
            Here is the updated file:
            ```kotlin path=src/App.kt
            fun greet(): String {
                return "Hello"
            }
            ```
        """.trimIndent()
        val proposed = CodingAgentEngine.extractProposedFiles(aiOutput)
        assertEquals(1, proposed.size)
        assertEquals("src/App.kt", proposed.first().path)

        val scaffold = CodingAgentEngine.buildScaffoldFiles("p1", CodingScaffoldType.REACT_TS, "Demo")
        val report = CodingAgentEngine.runSafeWorkspaceDiagnostics(scaffold)
        assertTrue(report.contains("PASS package.json"))
        assertTrue(report.contains("all", ignoreCase = true))
    }

    @Test
    fun `all 17 providers map to valid adapters and secure key store works`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val vault = SecureKeyStore(context)
        vault.putSecret("provider_openai", "sk-example-token-999")
        assertTrue(vault.hasSecret("provider_openai"))
        assertEquals("sk-example-token-999", vault.getSecret("provider_openai"))
        vault.deleteSecret("provider_openai")
        assertFalse(vault.hasSecret("provider_openai"))

        ProviderType.entries.forEach { type ->
            val adapter = ProviderAdapterFactory.forProvider(type)
            assertNotNull(adapter)
        }
    }
}
