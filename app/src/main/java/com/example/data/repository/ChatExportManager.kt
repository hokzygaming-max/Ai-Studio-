package com.example.data.repository

import com.example.core.security.SecureKeyStore
import com.example.domain.model.*
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

object ChatExportManager {

    private val dateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)

    fun exportConversationMarkdown(
        conversation: Conversation,
        messages: List<ChatMessage>,
        knownSecrets: List<String>
    ): String {
        val raw = buildString {
            appendLine("# ${conversation.title}")
            appendLine()
            appendLine("- Exported from AI Studio on ${dateFmt.format(Date())}")
            if (conversation.modelKey != null) appendLine("- Model: ${conversation.modelKey.substringAfter("::")}")
            if (conversation.systemPrompt.isNotBlank()) {
                appendLine("- System Instructions: ${conversation.systemPrompt}")
            }
            appendLine()
            appendLine("---")
            appendLine()
            messages.forEach { msg ->
                val speaker = when (msg.role) {
                    MessageRole.USER -> "User"
                    MessageRole.ASSISTANT -> "Assistant (${msg.modelUsed.ifBlank { "AI" }})"
                    MessageRole.SYSTEM -> "System"
                }
                appendLine("### $speaker")
                if (msg.attachments.isNotEmpty()) {
                    appendLine("*Attachments: ${msg.attachments.joinToString { it.fileName }}*")
                }
                appendLine()
                appendLine(msg.content)
                if (msg.citations.isNotEmpty()) {
                    appendLine()
                    appendLine("**Sources:**")
                    msg.citations.forEach { c ->
                        appendLine("- [${c.index}] ${c.title}: ${c.url}")
                    }
                }
                appendLine()
            }
        }
        return SecureKeyStore.redactSecrets(raw, knownSecrets)
    }

    fun exportConversationsJson(
        conversations: List<Pair<Conversation, List<ChatMessage>>>,
        knownSecrets: List<String>
    ): String {
        val root = JSONObject().apply {
            put("app", "AI Studio")
            put("formatVersion", 1)
            put("exportedAt", System.currentTimeMillis())
            put("note", "API keys and credentials are never exported.")
        }
        val convArray = JSONArray()
        conversations.forEach { (conv, msgs) ->
            val msgArray = JSONArray()
            msgs.forEach { m ->
                msgArray.put(JSONObject().apply {
                    put("id", m.id)
                    put("role", m.role.name)
                    put("content", m.content)
                    put("modelUsed", m.modelUsed)
                    put("tokenInfo", m.tokenInfo)
                    put("createdAt", m.createdAt)
                })
            }
            convArray.put(JSONObject().apply {
                put("id", conv.id)
                put("title", conv.title)
                put("systemPrompt", conv.systemPrompt)
                put("modelKey", conv.modelKey)
                put("isPinned", conv.isPinned)
                put("isArchived", conv.isArchived)
                put("createdAt", conv.createdAt)
                put("updatedAt", conv.updatedAt)
                put("messages", msgArray)
            })
        }
        root.put("conversations", convArray)
        return SecureKeyStore.redactSecrets(root.toString(2), knownSecrets)
    }

    fun parseImportedConversationsJson(jsonString: String): List<Pair<Conversation, List<ChatMessage>>> {
        val root = JSONObject(jsonString)
        val arr = root.optJSONArray("conversations") ?: JSONArray()
        val result = mutableListOf<Pair<Conversation, List<ChatMessage>>>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val newConvId = UUID.randomUUID().toString()
            val conv = Conversation(
                id = newConvId,
                title = obj.optString("title").ifBlank { "Imported Chat" },
                systemPrompt = obj.optString("systemPrompt"),
                modelKey = obj.optString("modelKey").ifBlank { null },
                isPinned = obj.optBoolean("isPinned", false),
                isArchived = obj.optBoolean("isArchived", false),
                createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                updatedAt = System.currentTimeMillis()
            )
            val msgsArr = obj.optJSONArray("messages") ?: JSONArray()
            val msgs = List(msgsArr.length()) { mIdx ->
                val mObj = msgsArr.getJSONObject(mIdx)
                ChatMessage(
                    id = UUID.randomUUID().toString(),
                    conversationId = newConvId,
                    role = runCatching { MessageRole.valueOf(mObj.optString("role")) }.getOrDefault(MessageRole.USER),
                    content = mObj.optString("content"),
                    modelUsed = mObj.optString("modelUsed"),
                    tokenInfo = mObj.optString("tokenInfo"),
                    createdAt = mObj.optLong("createdAt", System.currentTimeMillis() + mIdx)
                )
            }
            result.add(conv to msgs)
        }
        return result
    }

    fun exportProjectJson(
        project: ProjectWorkspace,
        files: List<ProjectFile>,
        knownSecrets: List<String>
    ): String {
        val root = JSONObject().apply {
            put("app", "AI Studio Project")
            put("formatVersion", 1)
            put("name", project.name)
            put("description", project.description)
            put("instructions", project.instructions)
            put("selectedSkillIds", JSONArray(project.selectedSkillIds))
            put("files", JSONArray().apply {
                files.forEach { f ->
                    put(JSONObject().apply {
                        put("path", f.path)
                        put("content", f.content)
                        put("mimeType", f.mimeType)
                    })
                }
            })
        }
        return SecureKeyStore.redactSecrets(root.toString(2), knownSecrets)
    }

    fun parseImportedProjectJson(jsonString: String): Pair<ProjectWorkspace, List<ProjectFile>> {
        val root = JSONObject(jsonString)
        val projectId = UUID.randomUUID().toString()
        val skillArr = root.optJSONArray("selectedSkillIds") ?: JSONArray()
        val skills = List(skillArr.length()) { skillArr.optString(it) }.filter { it.isNotBlank() }
        val project = ProjectWorkspace(
            id = projectId,
            name = root.optString("name").ifBlank { "Imported Project" },
            description = root.optString("description"),
            instructions = root.optString("instructions"),
            selectedSkillIds = skills
        )
        val filesArr = root.optJSONArray("files") ?: JSONArray()
        val files = List(filesArr.length()) { idx ->
            val o = filesArr.getJSONObject(idx)
            val content = o.optString("content")
            ProjectFile(
                id = UUID.randomUUID().toString(),
                projectId = projectId,
                path = o.optString("path").ifBlank { "file_$idx.txt" },
                content = content,
                mimeType = o.optString("mimeType").ifBlank { "text/plain" },
                sizeBytes = content.toByteArray().size.toLong()
            )
        }
        return project to files
    }
}
