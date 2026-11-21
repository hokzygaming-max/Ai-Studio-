package com.example.domain.skills

import com.example.domain.model.Skill
import com.example.domain.model.SkillPermission
import com.example.domain.model.SkillSupportingFile
import com.example.domain.model.SystemPromptPreset

object BuiltInSkillsSeed {

    fun defaultPresets(): List<SystemPromptPreset> = listOf(
        SystemPromptPreset(
            id = "preset_general",
            name = "Balanced Assistant",
            content = "You are AI Studio, a clear, honest, and technically rigorous assistant. Provide concise, accurate answers and explain trade-offs transparently.",
            isDefault = true
        ),
        SystemPromptPreset(
            id = "preset_architect",
            name = "Senior Software Architect",
            content = "Act as a principal software architect. Prioritize clean modularity, security boundaries, edge-case handling, testability, and idiomatic code without unnecessary boilerplate."
        ),
        SystemPromptPreset(
            id = "preset_tutor",
            name = "Step-by-Step Mentor",
            content = "Explain concepts clearly from first principles using concrete examples, mental models, and short check-in steps."
        ),
        SystemPromptPreset(
            id = "preset_concise",
            name = "Ultra Concise",
            content = "Answer in the fewest words possible while remaining accurate and complete. Prefer bullet points and direct code snippets."
        )
    )

    fun defaultSkills(): List<Skill> = listOf(
        makeSkill(
            id = "skill_android_dev",
            name = "Android Development",
            category = "Mobile",
            description = "Modern Android architecture with Jetpack Compose, Material 3, Room, Coroutines, StateFlow, and edge-to-edge UI.",
            body = """
                ## Android Engineering Guidelines
                - Prefer Jetpack Compose with Material 3 components and `enableEdgeToEdge()`.
                - Use MVVM with `ViewModel`, `StateFlow`, and `collectAsStateWithLifecycle()`.
                - Persist structured local data with Room + KSP and preferences with DataStore.
                - Declare only least-privilege permissions; use Android Photo Picker or SAF instead of broad storage permissions.
                - Respect configuration changes, multi-window layouts, and 48dp touch targets.
            """.trimIndent(),
            template = SkillSupportingFile(
                relativePath = "templates/ComposeViewModelTemplate.kt",
                content = "class FeatureViewModel : ViewModel() {\n  private val _uiState = MutableStateFlow(UiState())\n  val uiState: StateFlow<UiState> = _uiState.asStateFlow()\n}"
            ),
            enabled = true
        ),
        makeSkill(
            id = "skill_kotlin_dev",
            name = "Kotlin Development",
            category = "Languages",
            description = "Idiomatic Kotlin, coroutines, Flow, sealed hierarchies, immutability, and null safety.",
            body = """
                ## Kotlin Best Practices
                - Favor immutable `val` properties, `data class`, and `sealed interface`/`sealed class` for state modeling.
                - Handle errors with `Result`, explicit domain states, or structured coroutine exception handling—never swallow `CancellationException`.
                - Use extension functions, `sequence` for lazy transformations, and `Dispatchers.IO` for blocking I/O.
            """.trimIndent(),
            enabled = true
        ),
        makeSkill(
            id = "skill_python_dev",
            name = "Python Development",
            category = "Languages",
            description = "Clean Python 3.11+ with type hints, FastAPI/CLI patterns, pytest, and virtual environments.",
            body = """
                ## Python Engineering Standards
                - Use full PEP 484 type annotations (`list[str]`, `dict[str, Any]`, `dataclass` or Pydantic v2 models).
                - Keep functions small, pure where possible, and testable with `pytest`.
                - Never use `shell=True` with untrusted inputs; validate paths and inputs explicitly.
            """.trimIndent()
        ),
        makeSkill(
            id = "skill_react_dev",
            name = "React Development",
            category = "Web",
            description = "Modern React with TypeScript, functional hooks, accessible components, and clean state flow.",
            body = """
                ## React + TypeScript Standards
                - Write strictly typed functional components with explicit prop interfaces.
                - Keep derived state computed during render instead of redundant `useEffect` synchronization.
                - Ensure semantic HTML, keyboard accessibility, and clean error/loading boundaries.
            """.trimIndent()
        ),
        makeSkill(
            id = "skill_web_dev",
            name = "Web Development",
            category = "Web",
            description = "Responsive HTML5, modern CSS Grid/Flexbox, progressive JavaScript/TypeScript, and web performance.",
            body = """
                ## Modern Web Standards
                - Build responsive mobile-first layouts using CSS custom properties, Flexbox, and Grid.
                - Sanitize untrusted content and avoid raw `innerHTML` injection.
                - Optimize accessibility (ARIA roles, contrast ratios, focus states) and Core Web Vitals.
            """.trimIndent()
        ),
        makeSkill(
            id = "skill_ui_ux",
            name = "UI/UX Design",
            category = "Design",
            description = "Visual hierarchy, spacing rhythm, Material 3 & dark-mode ergonomics, micro-interactions, and accessibility.",
            body = """
                ## UI/UX Design Craft
                - Establish crisp typographic hierarchy, 8dp spacing grids, and intentional focal contrast.
                - Eliminate dead-end controls, clutter, and low-contrast text.
                - Design clear empty states, loading feedback, and humane error recovery.
            """.trimIndent()
        ),
        makeSkill(
            id = "skill_threejs",
            name = "Three.js",
            category = "Graphics & 3D",
            description = "Interactive WebGL/Three.js scenes, PBR materials, lighting, shaders, camera rigs, and 60fps disposal.",
            body = """
                ## Three.js Architecture
                - Manage `WebGLRenderer`, `PerspectiveCamera`, `OrbitControls`, and resize observers cleanly.
                - Dispose geometries, materials, and textures on unmount to prevent GPU memory leaks.
                - Balance PBR lighting, environment maps, and instanced meshes for mobile frame rates.
            """.trimIndent()
        ),
        makeSkill(
            id = "skill_debugging",
            name = "Debugging",
            category = "Engineering",
            description = "Systematic root-cause analysis, stack-trace triage, reproduction steps, and minimal targeted fixes.",
            body = """
                ## Root-Cause Debugging Protocol
                - Reproduce and isolate the failing invariant before proposing edits.
                - Inspect stack traces, nullability boundaries, race conditions, and state mutations.
                - Explain why the bug occurs and verify edge cases around the fix.
            """.trimIndent(),
            enabled = true
        ),
        makeSkill(
            id = "skill_code_review",
            name = "Code Review",
            category = "Engineering",
            description = "Security, correctness, concurrency, readability, and performance code audits.",
            body = """
                ## Code Review Checklist
                - Check for security risks (hardcoded secrets, injection, path traversal, missing auth checks).
                - Identify subtle bugs, off-by-one errors, resource leaks, and unhandled error paths.
                - Provide concrete, actionable diff-ready improvements ordered by severity.
            """.trimIndent()
        ),
        makeSkill(
            id = "skill_game_dev",
            name = "Game Development",
            category = "Graphics & 3D",
            description = "Game loops, fixed-timestep physics, entity-component patterns, shaders, and input feel.",
            body = """
                ## Game Development Craft
                - Decouple fixed-timestep simulation (`dt`) from rendering interpolation.
                - Design responsive input buffering, state machines, and object pooling to avoid GC spikes.
            """.trimIndent()
        ),
        makeSkill(
            id = "skill_api_dev",
            name = "API Development",
            category = "Backend",
            description = "REST/GraphQL/SSE design, OpenAPI schemas, idempotency, pagination, rate limiting, and auth.",
            body = """
                ## API Engineering Standards
                - Design consistent resource endpoints, explicit HTTP status codes, and structured error envelopes.
                - Enforce input validation, pagination, rate limits, and least-privilege auth tokens.
            """.trimIndent()
        ),
        makeSkill(
            id = "skill_database_dev",
            name = "Database Development",
            category = "Backend",
            description = "Relational schema normalization, SQL indexing, Room migrations, query tuning, and transactions.",
            body = """
                ## Database Engineering
                - Model clear foreign keys, composite indexes for hot queries, and parameterized SQL only.
                - Plan backward-compatible schema migrations and atomic transaction boundaries.
            """.trimIndent()
        ),
        makeSkill(
            id = "skill_video_prod",
            name = "Video Production",
            category = "Creative",
            description = "Scriptwriting, shot lists, storyboards, pacing, audio mix cues, and editing workflows.",
            body = """
                ## Video Production Workflow
                - Structure deliverables into hook, narrative arc, visual B-roll cues, audio/VO script, and pacing beats.
                - Specify aspect ratio, framing, transitions, and color/lighting notes cleanly.
            """.trimIndent()
        ),
        makeSkill(
            id = "skill_image_prompt",
            name = "Image Prompt Engineering",
            category = "Creative",
            description = "Structured visual prompt crafting with composition, lighting, lens, materials, and style control.",
            body = """
                ## Visual Prompt Engineering
                - Specify subject, camera framing, lighting direction, palette, surface materials, and negative constraints.
                - Offer 2-3 tailored prompt variations (e.g., photoreal, editorial vector, architectural 3D).
            """.trimIndent()
        ),
        makeSkill(
            id = "skill_writing",
            name = "Writing Assistant",
            category = "Writing",
            description = "Clear technical documentation, essays, product copy, editing for clarity, tone, and structure.",
            body = """
                ## Editorial & Writing Standards
                - Prioritize clarity, active voice, concrete detail, and logical transitions.
                - Match the user's target audience and tone without fluff or clichés.
            """.trimIndent()
        ),
        makeSkill(
            id = "skill_research",
            name = "Research Assistant",
            category = "Research",
            description = "Structured synthesis, source-backed comparison matrices, hypothesis testing, and transparent uncertainty.",
            body = """
                ## Research Rigor
                - Separate verified facts from assumptions and clearly highlight gaps or uncertainty.
                - When web search results are provided, cite exact source URLs using `[n]` notation. Never invent citations.
            """.trimIndent()
        )
    )

    private fun makeSkill(
        id: String,
        name: String,
        category: String,
        description: String,
        body: String,
        template: SkillSupportingFile? = null,
        enabled: Boolean = false
    ): Skill {
        val skillMd = buildString {
            appendLine("---")
            appendLine("name: \"$name\"")
            appendLine("category: \"$category\"")
            appendLine("description: \"$description\"")
            appendLine("---")
            appendLine()
            appendLine("# $name")
            appendLine()
            append(body)
        }
        return Skill(
            id = id,
            name = name,
            description = description,
            category = category,
            skillMdContent = skillMd,
            instructionsBody = body,
            supportingFiles = listOfNotNull(template),
            requiredPermissions = listOf(SkillPermission.READ_ATTACHMENTS),
            isEnabled = enabled,
            isBuiltIn = true,
            isTrusted = true
        )
    }
}
