[![logo](https://raw.githubusercontent.com/omnifaces/omnihai/refs/heads/main/logo-96x96.png)](https://github.com/omnifaces/omnihai)

# OmniHai vs LangChain4J vs Spring AI vs Jakarta Agentic AI

## Philosophy

| Aspect | OmniHai | LangChain4J | Spring AI | Jakarta Agentic |
|--------|--------|-------------|-----------|-----------------|
| **Target Runtime** | Any Java (Jakarta EE / MicroProfile integration built in) | Any Java | Spring | Jakarta EE |
| **Philosophy** | Minimal, focused utility | Comprehensive toolkit | Spring integration | Standard specification for agent workflows |
| **Dependencies** | JSON-P only (CDI/EL/MP-config optional) | Multiple modules | Spring framework | `jakarta.ai.agent` API plus an implementation |
| **Learning Curve** | Low | Medium-High | Medium (if Spring-familiar) | Medium (workflow annotations) |

## Feature Comparison

| Feature | OmniHai | LangChain4J | Spring AI |
|---------|--------|-------------|-----------|
| **Chat/Completion** | ✅ | ✅ | ✅ |
| **Streaming** | ✅ | ✅ | ✅ |
| **Structured Outputs** | ✅ | ✅ | ✅ |
| **File Attachments** | ✅ | ✅ | ✅ |
| **Function Calling** | ✅ (schema-based) | ✅ | ✅ |
| **RAG Support** | ❌ | ✅ (extensive) | ✅ |
| **Vector Stores** | ❌ | ✅ (many) | ✅ (many) |
| **Embeddings** | ❌ | ✅ | ✅ |
| **Image Analysis** | ✅ | ✅ | ✅ |
| **Image Generation** | ✅ | ✅ | ✅ |
| **Audio Transcription** | ✅ (native + fallback) | ✅ | ✅ |
| **Audio Generation (TTS)** | ✅ | ✅ | ✅ |
| **Video Analysis** | ✅ | ✅ | ✅ |
| **Video Generation** | ✅ | ❌ | ❌ |
| **Content Moderation** | ✅ (native + fallback) | ❌ (via chat) | ❌ (via chat) |
| **Classification** | ✅ | ❌ (via chat) | ❌ (via chat) |
| **Translation** | ✅ | ❌ (via chat) | ❌ (via chat) |
| **Proofreading** | ✅ | ❌ (via chat) | ❌ (via chat) |
| **Summarization** | ✅ | ❌ (via chat) | ❌ (via chat) |
| **Memory/History** | ✅ | ✅ | ✅ |
| **Token Usage Tracking** | ✅ | ✅ | ✅ |
| **Web Search** | ✅ (built-in) | ✅ | ✅ |
| **Agents** | ➖ (single tool loop, no orchestration) | ✅ | ✅ |
| **Prompt Templates** | ❌ | ✅ | ✅ |

Jakarta Agentic AI is deliberately absent from that table: it standardizes the workflow around an AI call rather than the call itself, so a feature-by-feature comparison would be comparing different layers. See below.

## Jakarta Agentic AI

Jakarta Agentic AI standardizes the shape of an agent workflow, not the provider call.

An agent is a CDI bean annotated `@Agent` in package `jakarta.ai.agent`. A `@Trigger` method starts the workflow from a CDI event, `@Decision` methods determine how it progresses, `@Action` methods carry out a step, `@Outcome` marks completion, `@HandleException` handles failures, and `@WorkflowScoped` gives one CDI context per execution. The AI itself is reached through an injectable `LargeLanguageModel` facade, described by the specification as deliberately minimal, with parameterized queries in the style of Jakarta Persistence. Version 1.0.0-M1 supports linear workflows; conditional ones are planned. It does not initially seek inclusion in the Jakarta EE Platform or any profile.

That makes it complementary to OmniHai rather than an alternative to it:

| Aspect | OmniHai | Jakarta Agentic AI |
|--------|---------|--------------------|
| **Layer** | The provider call | The workflow around it |
| **Who decides the next step** | The AI, from the tools you registered | You, in `@Decision` and `@Action` methods |
| **LLM surface** | Ten providers, streaming, attachments, cost, moderation, transcription | Minimal facade by design |
| **Form** | A library you depend on | A specification you code against, plus an implementation |

An `@Action` method is free to call an injected OmniHai `AIService`, which is probably the most useful way to read the two together: the specification decides which step runs, OmniHai performs the call that step needs.

## Provider Support

| Provider | OmniHai | LangChain4J | Spring AI |
|----------|--------|-------------|-----------|
| OpenAI | ✅ | ✅ | ✅ |
| Anthropic | ✅ | ✅ | ✅ |
| Google AI | ✅ | ✅ | ✅ |
| xAI (Grok) | ✅ | ❌ (via OpenAI) | ❌ (via OpenAI) |
| Mistral | ✅ | ✅ | ✅ |
| Meta AI | ✅ | ❌ (via OpenAI) | ❌ (via OpenAI) |
| Azure OpenAI | ✅ | ✅ | ✅ |
| OpenRouter | ✅ | ❌ (via OpenAI) | ❌ (via OpenAI) |
| Hugging Face | ✅ | ✅ | ✅ |
| Ollama | ✅ | ✅ | ✅ |
| AWS Bedrock | ❌ | ✅ | ✅ |

## CDI Integration

| Aspect | OmniHai | LangChain4J-CDI | Spring AI |
|--------|--------|-----------------|-----------|
| **Injection Style** | `@Inject @AI(...)` | `@Inject` + config | `@Autowired` + beans |
| **Qualifier-based** | ✅ | ❌ | ❌ |
| **EL Support** | ✅ `#{...}`, `${...}` | ❌ | ❌ (SpEL, different) |
| **MP Config Support** | ✅ `${config:...}` | ✅ (properties-based) | ❌ (SpEL, different) |

## Where OmniHai Shines

- Ultra-lightweight - No external HTTP library, just [`java.net.http.HttpClient`](https://docs.oracle.com/en/java/javase/21/docs/api/java.net.http/java/net/http/HttpClient.html). Minimal deps. Transparent gzip compression for reduced bandwidth.
- Built-in text utilities - Summarization, translation, transcription, proofreading, key point extraction, classification, moderation as first-class features (not "build your own prompt")
- Structured outputs - Get typed Java objects directly from AI responses: `service.chat(message, MyRecord.class)`
- File attachments - Send documents, images, and other files alongside chat messages with help of `ChatInput`
- Web search - Access up-to-date internet information via `service.webSearch(query)` or `ChatOptions.newBuilder().webSearch().build()`, with optional location context for localized results
- Token usage tracking - Track input, cached input, output, and reasoning tokens per call via `ChatOptions.getLastUsage()`
- Cost calculation - Attach a `ChatPricing` to `ChatOptions` and read the per-call `ChatCost` from `ChatOptions.getLastCost()`
- Budget cap - Set a cumulative-cost cap alongside pricing; exceeding it aborts the next call with `AIBudgetExceededException`
- Reasoning effort control - Dial reasoning spend with `ChatOptions.newBuilder().reasoningEffort(...)` across providers that support it
- Portable JSON serialization - `ChatOptions.toJson()` / `ChatOptions.fromJson(String)` for session stores, databases, or cross-service transport
- Native CDI with EL - `@AI(apiKey = "#{config.openaiKey}")` with expression resolution
- MicroProfile Config - `@AI(apiKey = "${config:openai.key}")` with expression resolution
- 10 providers out of the box - Including Ollama for local/offline
- Caller-owned conversation memory - History lives in `ChatOptions`, not in the service. No server-side session state, no memory leaks, no lifecycle management. The caller controls it. Sliding window keeps context manageable, and uploaded file references are tracked across turns.
- Automatic file cleanup - Uploaded files on provider servers are cleaned up after 2 days in a fire-and-forget background task, preventing stale file accumulation.
- Tool use on every provider - Annotate a method with `@AITool`, hand the object to `ai.withTools(...)`, and the AI can call it. Rides on structured output, so it behaves identically on all ten providers, groups narrow the schema itself, and tools run on your thread inside your transaction.
- Clean exception hierarchy - Specific exceptions per HTTP status

## Where OmniHai is Intentionally Simpler

No embeddings, RAG, vector stores, or agent orchestration. This isn't a gap - it's a design choice. OmniHai is a utility library, not a framework.

[Tool use](https://github.com/omnifaces/omnihai/blob/main/README.md#tool-use) is the one thing on that list which turned out not to be framework territory: it rides on structured output, so it costs no per-provider machinery and works everywhere. Native function calling with per-tool argument schemas, parallel tool calls and forced tool choice remains out.

## Positioning

| Library | Analogy |
|---------|---------|
| **LangChain4J** | Full kitchen with every appliance |
| **Spring AI** | Full kitchen, Spring-branded appliances |
| **Jakarta Agentic AI** | Kitchen building code, for the order of the steps |
| **OmniHai** | Sharp chef's knife - does a few things very well |

OmniHai fills a different niche. For apps that need:

- Multi-provider chat with easy switching
- Text analysis (summarize, translate, proofread, classify, moderate)
- Web search with optional location context
- Image analysis (describe, generate alt text)
- Audio analysis (transcribe) and generation (text-to-speech)
- Token usage tracking and cost calculation for budget monitoring
- Minimal dependencies
- Pure Jakarta EE / MicroProfile APIs, no framework

...without needing RAG pipelines, agent frameworks, or vector stores, OmniHai is arguably the better choice. Less to learn, less to break, fewer dependencies.

Jakarta Agentic AI standardizes a layer above this one, so the two compose rather than compete; its `@Action` methods can call an OmniHai `AIService`.

## Is OmniHai smaller than e.g. LangChain4J?

Yes, significantly:
- OmniHai JAR: ~337 KB vs LangChain4J: ~5-10 MB (*per* AI provider!) — at least 15x smaller when using only one AI provider
- 115 source files, ~24,000 lines (\~10,400 actual code, \~10,700 javadoc, rest is blank lines)
- Zero external runtime dependencies — uses JDK's native `java.net.http.HttpClient` directly without any SDKs
- Only one required dependency: Jakarta JSON-P (which Jakarta EE and MicroProfile runtimes already have)
- Other dependencies are optional: CDI, EL and/or MP Config APIs (which Jakarta EE resp. MicroProfile runtimes already have)
- On plain Java SE that one dependency is the entire footprint

## Is it faster?

Likely yes for startup and per-request overhead:
- No classpath scanning or proxy generation at startup
- Minimal reflection — only used once during service instantiation, not per-request
- No abstraction layers around HTTP — direct `java.net.http.HttpClient` usage
- Simple interface dispatch, no dynamic proxies
- Services are stateless and cached via `ConcurrentHashMap`

## Does it produce less GC garbage?

The design strongly suggests yes:
- No intermediate JSON object materialization — uses path extraction directly on `JsonObject`
- Conservative allocation patterns — no framework overhead creating wrapper objects
- Native `java.net.http.HttpClient` — has better GC characteristics than third-party HTTP libraries
- Simple POJOs and builders — no reflection-based bean creation at runtime
- Stateless services — all state lives in method parameters, no per-request object graphs

## When to Choose Each

**Choose OmniHai when:**
- You need a lean, focused solution without pulling in a framework
- Your use case is straightforward chat, translation, summarization, proofreading, classification, or moderation
- You want minimal dependencies and a small footprint
- You prefer simplicity over feature completeness

**Choose LangChain4J when:**
- You're building complex AI agents with tool calling and orchestration
- You need Retrieval-Augmented Generation (RAG) or vector stores
- You want the most comprehensive feature set
- You're not tied to a specific framework

**Choose Spring AI when:**
- You're already in the Spring ecosystem
- You need tight Spring Boot integration
- You want auto-configuration and starters
- Your team is Spring-proficient

**Choose Jakarta Agentic AI when:**
- You want the workflow itself standardized, with vendor-neutral portability across implementations
- Your steps are authored by you rather than chosen by the AI
- You want a per-execution CDI scope around the whole workflow
- You can live with a milestone specification and its implementations still settling

As said, OmniHai is "a sharp chef's knife — does a few things very well" rather than being a full framework.

Bottom line: If you need a lightweight utility for AI chat/text operations without framework overhead, OmniHai is dramatically smaller and should be faster with less GC pressure. If you need RAG or agent pipelines, LangChain4J's / Spring AI's larger footprint comes with those capabilities.

Back to the [README](https://github.com/omnifaces/omnihai/blob/main/README.md).
