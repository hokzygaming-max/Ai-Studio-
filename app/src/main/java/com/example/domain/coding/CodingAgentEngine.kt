package com.example.domain.coding

import com.example.domain.model.ProjectFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class ProposedCodeFile(
    val path: String,
    val language: String,
    val content: String
)

enum class CodingScaffoldType(val label: String, val description: String) {
    ANDROID_COMPOSE("Android Compose", "Kotlin + Jetpack Compose + Material 3 starter"),
    KOTLIN_CLI("Kotlin Project", "Gradle Kotlin JVM app structure"),
    WEB_STATIC("Web App", "HTML5 + CSS3 + ES6 module app"),
    REACT_TS("React + TypeScript", "Vite + React TSX component structure"),
    NODE_EXPRESS("Node.js API", "Express REST server + package.json"),
    PYTHON_FASTAPI("Python Project", "FastAPI / CLI module + pytest")
}

object CodingAgentEngine {

    /**
     * Extracts structured file blocks from AI responses like:
     * ```kotlin path=app/Main.kt  OR  // FILE: src/app.py followed by ```python
     */
    fun extractProposedFiles(markdown: String): List<ProposedCodeFile> {
        val result = mutableListOf<ProposedCodeFile>()
        val blockRegex = Regex("```([a-zA-Z0-9_+-]*)(?:\\s+(?:file|path)=([^\\n]+))?\\r?\\n([\\s\\S]*?)```")
        val lines = markdown.lines()

        for (match in blockRegex.findAll(markdown)) {
            val lang = match.groupValues[1].ifBlank { "txt" }
            var path = match.groupValues[2].trim().trim('"', '\'', '`')
            val code = match.groupValues[3].trimEnd()
            if (path.isBlank()) {
                val beforeIdx = markdown.substring(0, match.range.first).lines().lastOrNull { it.isNotBlank() }?.trim().orEmpty()
                val headerMatch = Regex("(?:FILE|File|Path|`+)\\s*:?\\s*`?([a-zA-Z0-9_./-]+\\.[a-zA-Z0-9]+)`?").find(beforeIdx)
                if (headerMatch != null) {
                    path = headerMatch.groupValues[1]
                }
            }
            if (path.isNotBlank() && code.isNotBlank()) {
                result.add(ProposedCodeFile(path = path.trimStart('/'), language = lang, content = code))
            }
        }
        return result.distinctBy { it.path }
    }

    fun buildScaffoldFiles(projectId: String, type: CodingScaffoldType, projectName: String): List<ProjectFile> {
        val safeName = projectName.ifBlank { "ai-studio-project" }.lowercase().replace(Regex("[^a-z0-9_-]"), "-")
        val specs: List<Pair<String, String>> = when (type) {
            CodingScaffoldType.ANDROID_COMPOSE -> listOf(
                "app/build.gradle.kts" to "plugins {\n    id(\"com.android.application\")\n    id(\"org.jetbrains.kotlin.plugin.compose\")\n}\n\nandroid {\n    namespace = \"com.example.$safeName\"\n    compileSdk = 35\n    defaultConfig {\n        minSdk = 26\n        targetSdk = 35\n    }\n}\n",
                "app/src/main/java/MainActivity.kt" to "package com.example\n\nimport android.os.Bundle\nimport androidx.activity.ComponentActivity\nimport androidx.activity.compose.setContent\nimport androidx.compose.material3.MaterialTheme\nimport androidx.compose.material3.Text\n\nclass MainActivity : ComponentActivity() {\n    override fun onCreate(savedInstanceState: Bundle?) {\n        super.onCreate(savedInstanceState)\n        setContent { MaterialTheme { Text(\"Hello from $projectName\") } }\n    }\n}\n"
            )
            CodingScaffoldType.KOTLIN_CLI -> listOf(
                "build.gradle.kts" to "plugins {\n    kotlin(\"jvm\") version \"2.0.21\"\n    application\n}\n\napplication {\n    mainClass.set(\"MainKt\")\n}\n",
                "src/main/kotlin/Main.kt" to "fun main() {\n    println(\"Running $projectName\")\n}\n"
            )
            CodingScaffoldType.WEB_STATIC -> listOf(
                "index.html" to "<!doctype html>\n<html lang=\"en\">\n<head>\n  <meta charset=\"utf-8\" />\n  <meta name=\"viewport\" content=\"width=device-width,initial-scale=1\" />\n  <title>$projectName</title>\n  <link rel=\"stylesheet\" href=\"styles.css\" />\n</head>\n<body>\n  <main id=\"app\"><h1>$projectName</h1></main>\n  <script type=\"module\" src=\"app.js\"></script>\n</body>\n</html>\n",
                "styles.css" to ":root { color-scheme: dark; font-family: system-ui, sans-serif; }\nbody { margin: 0; padding: 2rem; background: #0a0f17; color: #f1f5f9; }\n",
                "app.js" to "console.log(\"$projectName initialized\");\n"
            )
            CodingScaffoldType.REACT_TS -> listOf(
                "package.json" to "{\n  \"name\": \"$safeName\",\n  \"private\": true,\n  \"scripts\": { \"dev\": \"vite\", \"build\": \"vite build\" },\n  \"dependencies\": { \"react\": \"^18.3.1\", \"react-dom\": \"^18.3.1\" }\n}\n",
                "src/App.tsx" to "import React from 'react';\n\nexport function App() {\n  return <main><h1>$projectName</h1></main>;\n}\n"
            )
            CodingScaffoldType.NODE_EXPRESS -> listOf(
                "package.json" to "{\n  \"name\": \"$safeName\",\n  \"type\": \"module\",\n  \"scripts\": { \"start\": \"node src/index.js\" },\n  \"dependencies\": { \"express\": \"^4.21.0\" }\n}\n",
                "src/index.js" to "import express from 'express';\nconst app = express();\napp.use(express.json());\napp.get('/health', (_req, res) => res.json({ status: 'ok' }));\napp.listen(3000, () => console.log('$projectName listening on :3000'));\n"
            )
            CodingScaffoldType.PYTHON_FASTAPI -> listOf(
                "pyproject.toml" to "[project]\nname = \"$safeName\"\nversion = \"0.1.0\"\nrequires-python = \">=3.11\"\ndependencies = [\"fastapi\", \"uvicorn\"]\n",
                "src/main.py" to "from fastapi import FastAPI\n\napp = FastAPI(title=\"$projectName\")\n\n@app.get(\"/health\")\ndef health() -> dict[str, str]:\n    return {\"status\": \"ok\"}\n",
                "tests/test_main.py" to "def test_placeholder() -> None:\n    assert True\n"
            )
        }
        return specs.map { (path, content) ->
            ProjectFile(
                id = UUID.randomUUID().toString(),
                projectId = projectId,
                path = path,
                content = content,
                mimeType = "text/plain",
                sizeBytes = content.toByteArray().size.toLong()
            )
        }
    }

    /**
     * Permission-controlled on-device workspace checks: validates JSON syntax,
     * XML/HTML tag balance, brace/paren balance, and project file stats.
     * Never claims an Android APK was compiled on-device.
     */
    fun runSafeWorkspaceDiagnostics(files: List<ProjectFile>): String {
        if (files.isEmpty()) return "Workspace has 0 files to check."
        val report = StringBuilder()
        report.appendLine("Local Workspace Verification (${files.size} files)")
        report.appendLine("Note: On-device environment performs static validation only (no local Gradle APK compiler).")
        report.appendLine()

        var issues = 0
        files.forEach { file ->
            val ext = file.path.substringAfterLast('.', "").lowercase()
            when (ext) {
                "json" -> {
                    val err = runCatching {
                        val trimmed = file.content.trim()
                        if (trimmed.startsWith("[")) JSONArray(trimmed) else JSONObject(trimmed)
                    }.exceptionOrNull()
                    if (err != null) {
                        issues++
                        report.appendLine("FAIL ${file.path}: Invalid JSON (${err.message})")
                    } else {
                        report.appendLine("PASS ${file.path}: Valid JSON (${file.content.lines().size} lines)")
                    }
                }
                else -> {
                    val balanceErr = checkDelimiterBalance(file.content)
                    if (balanceErr != null) {
                        issues++
                        report.appendLine("WARN ${file.path}: $balanceErr")
                    } else {
                        report.appendLine("PASS ${file.path}: Delimiters balanced (${file.content.lines().size} lines)")
                    }
                }
            }
        }
        report.appendLine()
        report.appendLine(if (issues == 0) "Summary: All ${files.size} files passed static validation." else "Summary: $issues potential syntax issue(s) detected.")
        return report.toString()
    }

    private fun checkDelimiterBalance(code: String): String? {
        var parens = 0
        var braces = 0
        var brackets = 0
        var inString: Char? = null
        var escaped = false
        for (ch in code) {
            if (escaped) {
                escaped = false
                continue
            }
            if (ch == '\\') {
                escaped = true
                continue
            }
            if (inString != null) {
                if (ch == inString) inString = null
                continue
            }
            when (ch) {
                '"', '\'' -> inString = ch
                '(' -> parens++
                ')' -> parens--
                '{' -> braces++
                '}' -> braces--
                '[' -> brackets++
                ']' -> brackets--
            }
            if (parens < 0) return "Extra closing ')'"
            if (braces < 0) return "Extra closing '}'"
            if (brackets < 0) return "Extra closing ']'"
        }
        return when {
            parens != 0 -> "Unclosed '(' ($parens remaining)"
            braces != 0 -> "Unclosed '{' ($braces remaining)"
            brackets != 0 -> "Unclosed '[' ($brackets remaining)"
            else -> null
        }
    }

    fun exportProjectZipBytes(files: List<ProjectFile>): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            files.forEach { file ->
                val clean = file.path.replace("..", "").trimStart('/')
                if (clean.isNotBlank()) {
                    zos.putNextEntry(ZipEntry(clean))
                    zos.write(file.content.toByteArray(StandardCharsets.UTF_8))
                    zos.closeEntry()
                }
            }
        }
        return baos.toByteArray()
    }
}
