# AI Studio

**AI Studio** (`com.aistudio.app`) is a free, open-source, multi-provider AI assistant and coding workspace built with Kotlin, Jetpack Compose, Material Design 3, Room, DataStore, OkHttp, and Android Keystore.

## Core Capabilities

1. **Multi-Provider AI System (16+ Providers + Custom/Local)**
   - Supports **OpenAI, Anthropic Claude, DeepSeek, Google Gemini, OpenRouter, Groq, Mistral AI, Together AI, Fireworks AI, Perplexity API, xAI, Cerebras, Cohere, Hugging Face, Ollama, Custom Local OpenAI-Compatible, and Custom OpenAI-Compatible APIs**.
   - Bring your own API keys stored exclusively in **Android Keystore AES-256-GCM** encrypted storage. Keys are never hardcoded, logged, or included in exports.
   - Real-time SSE / NDJSON streaming, connection testing, custom base URLs, custom headers, and provider-specific payload formatting.

2. **Model Manager**
   - Automatic model discovery where supported (`/models`, `/api/tags`) and manual model ID entry.
   - Model search, favorites, recently used tracking, default model selection, context window display, and capability flags.

3. **Conversational AI Chat**
   - Multiple local conversations with search, rename, pin, archive, delete, Markdown rendering, syntax-highlighted code blocks with 1-tap Copy, stop/continue/regenerate generation, message editing, and Markdown/JSON export & import.

4. **Multi-Format File Upload System**
   - Upload multiple files via Android Storage Access Framework & Photo Picker: **PDF, DOCX, TXT, MD, CSV, JSON, XML, HTML, source code files, and images**.
   - Local document extraction + native multimodal vision/PDF attachments where supported by the chosen model.

5. **SKILL.md Skills System**
   - Includes 16 built-in engineering & creative skills (`Android Development`, `Kotlin Development`, `Python Development`, `React Development`, `Web Development`, `UI/UX Design`, `Three.js`, `Debugging`, `Code Review`, `Game Development`, `API Development`, `Database Development`, `Video Production`, `Image Prompt Engineering`, `Writing Assistant`, `Research Assistant`).
   - Import/export `.md` and `.zip` skill packages, create/edit/toggle skills, automatic skill discovery, and explicit permission review before trusting imported skills containing scripts.

6. **AI Coding Agent & Project Workspaces**
   - Create isolated project workspaces with instructions, default models, linked skills, uploaded files, and scoped conversations.
   - Generate starter scaffolds (Android Compose, Kotlin, Web, React + TS, Node.js, Python FastAPI), edit/create/delete project files, apply AI-proposed file diffs directly, export project ZIPs, and run permission-controlled static workspace validation.

7. **Voice, Image Understanding & Optional Web Search**
   - Free on-device Android `SpeechRecognizer` (voice input) and `TextToSpeech` (read aloud with device voice selection).
   - Optional live web search using user-configured **Tavily, Brave Search, Serper, or self-hosted SearXNG** with verifiable clickable citations.

## Privacy & Open-Source Guarantee

- **100% Free & Open Source:** No subscriptions, no ads, no locked features, no mandatory account registration.
- Third-party API providers impose their own usage limits, rate limits, and billing terms.
- Local AI inference performance (Ollama / local OpenAI endpoints) depends on your device or host hardware and memory.

## Building

```bash
gradle :app:assembleDebug
gradle :app:testDebugUnitTest
```
