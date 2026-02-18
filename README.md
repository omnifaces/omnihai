[![Maven](https://img.shields.io/maven-central/v/org.omnifaces/omnihai)](https://search.maven.org/artifact/org.omnifaces/omnihai)
[![Javadoc](https://javadoc.io/badge/org.omnifaces/omnihai.svg)](https://javadoc.io/doc/org.omnifaces/omnihai) 

[![logo](https://raw.githubusercontent.com/omnifaces/omnihai/refs/heads/main/logo-96x96.png)](https://github.com/omnifaces/omnihai)

# OmniHai

A unified Java AI utility library for Jakarta EE or MicroProfile applications.

## Overview

OmniHai provides a single, consistent API to interact with multiple AI providers. It achieves that by interacting with their REST API endpoints directly.

## Minimum Requirements

- Java 17
- Jakarta EE 10 or MicroProfile 7 (JSON-P required, CDI optional, EL optional, MP Config optional)

## Installation

```xml
<dependency>
    <groupId>org.omnifaces</groupId>
    <artifactId>omnihai</artifactId>
    <version>1.1</version>
</dependency>
```

On non-Jakarta EE / non-MicroProfile runtimes such as Tomcat, you'll need to manually add JSON-P and optionally CDI / MP Config dependencies:

```xml
<!-- JSON-P implementation (required) -->
<dependency>
    <groupId>org.eclipse.parsson</groupId>
    <artifactId>parsson</artifactId>
    <version>1.1.7</version>
</dependency>

<!-- CDI implementation (optional, for @AI injection) -->
<dependency>
    <groupId>org.jboss.weld.servlet</groupId>
    <artifactId>weld-servlet-shaded</artifactId>
    <version>6.0.4.Final</version>
</dependency>

<!-- MP Config implementation (optional, for ${config:...} resolution in @AI attributes) -->
<dependency>
    <groupId>smallrye.config</groupId>
    <artifactId>smallrye-config</artifactId>
    <version>3.15.1</version>
</dependency>
```

You can technically also use it on plain Java SE, you'll still need the JSON-P implementation, but you cannot use the CDI annotation.

## Supported Providers

| Provider | Default Model | API Key Required | Available Models |
|----------|---------------|------------------|------------------|
| OpenAI | gpt-5.2-2025-12-11 | [Yes](https://platform.openai.com/api-keys) | [List](https://platform.openai.com/docs/models) |
| Anthropic | claude-sonnet-4-5-20250929 | [Yes](https://platform.claude.com/settings/keys) | [List](https://platform.claude.com/docs/en/about-claude/models/overview) |
| Google AI | gemini-2.5-flash | [Yes](https://aistudio.google.com/app/api-keys) | [List](https://ai.google.dev/gemini-api/docs/models) |
| xAI | grok-4-1-fast-reasoning | [Yes](https://console.x.ai) | [List](https://docs.x.ai/developers/models) |
| Mistral | mistral-medium-2508 | [Yes](https://console.mistral.ai/home?workspace_dialog=apiKeys) | [List](https://docs.mistral.ai/getting-started/models) |
| Meta AI | Llama-4-Scout-17B-16E-Instruct-FP8 | [Yes](https://llama.developer.meta.com/join-waitlist) | [List](https://llama.developer.meta.com/docs/models/) |
| Azure OpenAI | gpt-5-mini | [Yes](https://portal.azure.com) | [List](https://ai.azure.com/catalog) |
| OpenRouter | deepseek/deepseek-v3.2 | [Yes](https://openrouter.ai/settings/keys) | [List](https://openrouter.ai/models) |
| Hugging Face | google/gemma-3-27b-it | [Yes](https://huggingface.co/settings/tokens) | [List](https://huggingface.co/models) |
| Ollama | gemma3 | No (localhost) | [List](https://ollama.com/library) |
| Custom | - | - | - |

## Quick Start

### Programmatic Configuration

```java
// Create a service instance
AIService service = AIConfig.of("your-openai-api-key").createService();

// Simple chat
String response = service.chat("What is Jakarta EE?");
```

### CDI Integration

```java
@Inject
@AI(provider = AIProvider.ANTHROPIC, apiKey = "your-anthropic-api-key")
private AIService claude;

// Use EL expressions for dynamic configuration
@Inject
@AI(provider = AIProvider.OPENAI,
    apiKey = "#{initParam['com.example.OPENAI_API_KEY']}")
private AIService gpt;

// With MicroProfile config expressions and custom system prompt
@Inject
@AI(provider = AIProvider.GOOGLE,
    apiKey = "${config:google.api-key}",
    prompt = "You are a helpful assistant specialized in Jakarta EE.")
private AIService jakartaExpert;

// With different model than default
@Inject
@AI(provider = AIProvider.XAI,
    apiKey = "#{configBean.xaiApiKey}",
    model = "grok-2-image-1212")
private AIService imageGenerator;
```

### Multi-Provider Aggregation

Need diverse perspectives? OmniHai makes it easy to query multiple providers and combine their responses:

```java
@Inject @AI(apiKey = "#{config.openaiApiKey}")
private AIService gpt;

@Inject @AI(provider = GOOGLE, apiKey = "#{config.googleApiKey}")
private AIService gemini;

@Inject @AI(provider = XAI, apiKey = "#{config.xaiApiKey}")
private AIService grok;

public String getConsensusAnswer(String question) {
    var responses = Stream.of(gpt, gemini, grok)
        .parallel()
        .map(ai -> ai.chat(question))
        .toList();

    return gpt.summarize(String.join("\n\n", responses), 200);
}
```

This pattern is useful for reducing bias, cross-validating answers, or getting a balanced summary from multiple AI perspectives.

## Features

### Chat

Synchronous:
```java
String response = service.chat("Hello!");
```

Asynchronous:
```java
CompletableFuture<String> future = service.chatAsync("Hello!");
```

With options:
```java
String response = service.chat("Explain microservices",
    ChatOptions.newBuilder()
        .systemPrompt("You are a helpful software architect.")
        .temperature(0.5)
        .maxTokens(500)
        .build());
```

Streaming:
```java
service.chatStream(message, token -> {
    // handle partial response
    System.out.print(token);
}).exceptionally(e -> {
    // handle exception
    System.out.println("\n\nError occurred: " + e);
}).thenRun(() -> {
    // handle completion
    System.out.println("\n\n");
});
```

With file attachments:
```java
byte[] document = Files.readAllBytes(Path.of("report.pdf"));
byte[] image = Files.readAllBytes(Path.of("chart.png"));

ChatInput input = ChatInput.newBuilder()
    .message("Compare these files")
    .attach(document, image)
    .build();

String response = service.chat(input);
```

Multi-turn conversation with memory:
```java
ChatOptions options = ChatOptions.newBuilder()
    .systemPrompt("You are a helpful assistant.")
    .withMemory()
    .build();

String response1 = service.chat("My name is Bob.", options);
String response2 = service.chat("What is my name?", options); // AI remembers: "Bob"

// Access conversation history
List<ChatInput.Message> history = options.getHistory();
```

History is maintained as a sliding window, defaulting to 20 messages (10 conversational turns). Oldest messages are automatically evicted when the limit is exceeded. You can customize the window size:
```java
ChatOptions options = ChatOptions.newBuilder()
    .withMemory(50) // Keep up to 50 messages (25 turns)
    .build();
```

File attachments are automatically tracked in history. When you upload files in a memory-enabled chat, their references are preserved across turns so the AI can continue referencing them:
```java
ChatOptions options = ChatOptions.newBuilder()
    .withMemory()
    .build();

ChatInput input = ChatInput.newBuilder()
    .message("Analyze this PDF")
    .attach(Files.readAllBytes(Path.of("report.pdf")))
    .build();

String analysis = service.chat(input, options);
String followUp = service.chat("What's on page 2?", options); // AI still has access to the PDF
```

When messages slide out of the window, their associated file references are evicted as well. Uploaded files on the provider's servers are automatically cleaned up in the background after 2 days, preventing stale file accumulation. Only files uploaded by OmniHai are cleaned up.

Note: file tracking in history requires the AI provider to support a files API. This is currently the case for OpenAI(-compatible) providers, Anthropic, and Google AI.

### Structured Outputs

Get typed Java objects directly from AI responses:

```java
// Define your response structure as a record (or bean)
record ProductReview(String sentiment, int rating, List<String> pros, List<String> cons) {}

// Get a typed response in one call
ProductReview review = service.chat("Analyze this review: " + reviewText, ProductReview.class);
```

With options:
```java
ChatOptions options = ChatOptions.newBuilder()
    .systemPrompt("You are a product review analyzer.")
    .temperature(0.3)
    .build();
ProductReview review = service.chat("Analyze this review: " + reviewText, options, ProductReview.class);
```

Under the hood, OmniHai generates a JSON schema from the class, instructs the AI to return conforming JSON, and parses the response back into the typed object. 
You can also do this manually if you need more control:

```java
JsonObject schema = JsonSchemaHelper.buildJsonSchema(ProductReview.class);
ChatOptions options = ChatOptions.newBuilder().jsonSchema(schema).build();
String responseJson = service.chat("Analyze this review: " + reviewText, options);
ProductReview review = JsonSchemaHelper.fromJson(responseJson, ProductReview.class);
```

`JsonSchemaHelper` supports primitive types, strings, enums, temporals, collections, arrays, maps, nested types, and `Optional` fields (which are excluded from `"required"` in JSON schema).

### Text Analysis

```java
// Summarize text
String summary = service.summarize(longText, 100); // max 100 words

// Extract key points
List<String> points = service.extractKeyPoints(text, 5); // max 5 points
```

### Translation and Proofreading

```java
// Detect language
String lang = service.detectLanguage(text); // Returns ISO 639-1 code

// Translate with auto-detection
String translated = service.translate(text, null, "es");

// Translate from specific language
String translated = service.translate(text, "en", "fr");

// Proofread text (fix grammar and spelling, preserve meaning and style)
String corrected = service.proofread(text);
```

### Content Moderation

```java
// Basic moderation
ModerationResult result = service.moderateContent(userInput);
if (result.isFlagged()) {
    // Handle violation
}

// Custom moderation options
ModerationResult result = service.moderateContent(content,
    ModerationOptions.newBuilder()
        .categories(Category.HATE, Category.VIOLENCE)
        .threshold(0.8)
        .build());
```

### Image Analysis

```java
// Analyze image
byte[] imageBytes = Files.readAllBytes(imagePath);
String description = service.analyzeImage(imageBytes, "Describe the product");

// Generate alt text
String altText = service.generateAltText(imageBytes);
```

### Image Generation

```java
// Generate image
byte[] image = service.generateImage("A sunset over mountains");

// With options
byte[] image = service.generateImage("A modern office",
    GenerateImageOptions.newBuilder()
        .size("1024x1024")
        .build());
```

### Audio Transcription

```java
// Transcribe audio
byte[] audioBytes = Files.readAllBytes(audioPath);
String transcription = service.transcribe(audioBytes);
```

All methods have async variants returning `CompletableFuture` (e.g., `chatAsync`, `summarizeAsync`, `translateAsync`, `proofreadAsync`, `generateImageAsync`, `transcribeAsync`, etc.).

## Custom Providers

Implement `AIService` or extend `BaseAIService` or even `OpenAIService`, etc.

### Programmatic Configuration

```java
AIService service = AIConfig.of(MyCustomAIService.class, "api-key").createService();
```

### CDI Integration

```java
@Inject
@AI(serviceClass = MyCustomAIService.class, apiKey = "#{config.apiKey}")
private AIService custom;
```

## Custom Handlers

You can customize how requests are built and responses are parsed by providing custom handler implementations.

```java
// Custom OpenAI text handler for request tracking
public class TrackingTextHandler extends OpenAITextHandler {
    @Override
    public JsonObject buildChatPayload(AIService service, ChatInput input, ChatOptions options, boolean streaming) {
        return Json.createObjectBuilder(super.buildChatPayload(service, input, options, streaming))
            .add("user", getCurrentUserId())
            .build();
    }
}
```

### Programmatic Configuration

```java
AIStrategy strategy = AIStrategy.of(TrackingTextHandler.class);
AIService service = AIConfig.of("your-api-key").withStrategy(strategy).createService();
```

### CDI Integration

```java                                                                                                                                                                                                                                              
@Inject
@AI(provider = OPENAI, apiKey = "#{config.openaiApiKey}", textHandler = TrackingTextHandler.class)
private AIService trackedService;
```

## OmniHai vs LangChain4J vs Spring AI vs Jakarta Agentic

### Philosophy

| Aspect | OmniHai | LangChain4J | Spring AI | Jakarta Agentic |
|--------|--------|-------------|-----------|-----------------|
| **Target Runtime** | Jakarta EE / MicroProfile | Any Java | Spring | Jakarta EE |
| **Philosophy** | Minimal, focused utility | Comprehensive toolkit | Spring integration | Standard specification |
| **Dependencies** | JSON-P only (CDI/EL/MP-config optional) | Multiple modules | Spring framework | TBD (in development) |
| **Learning Curve** | Low | Medium-High | Medium (if Spring-familiar) | TBD |

### Feature Comparison

| Feature | OmniHai | LangChain4J | Spring AI | Jakarta Agentic |
|---------|--------|-------------|-----------|-----------------|
| **Chat/Completion** | ✅ | ✅ | ✅ | ✅ (planned) |
| **Streaming** | ✅ | ✅ | ✅ | TBD |
| **Structured Outputs** | ✅ | ✅ | ✅ | TBD |
| **File Attachments** | ✅ | ✅ | ✅ | TBD |
| **Function Calling** | ❌ | ✅ | ✅ | TBD |
| **RAG Support** | ❌ | ✅ (extensive) | ✅ | TBD |
| **Vector Stores** | ❌ | ✅ (many) | ✅ (many) | TBD |
| **Embeddings** | ❌ | ✅ | ✅ | TBD |
| **Image Analysis** | ✅ | ✅ | ✅ | TBD |
| **Image Generation** | ✅ | ✅ | ✅ | TBD |
| **Audio Transcription** | ✅ (native + fallback) | ✅ | ✅ | TBD |
| **Content Moderation** | ✅ (native + fallback) | ❌ (via chat) | ❌ (via chat) | TBD |
| **Translation** | ✅ | ❌ (via chat) | ❌ (via chat) | TBD |
| **Proofreading** | ✅ | ❌ (via chat) | ❌ (via chat) | TBD |
| **Summarization** | ✅ | ❌ (via chat) | ❌ (via chat) | TBD |
| **Memory/History** | ✅ | ✅ | ✅ | TBD |
| **Agents** | ❌ | ✅ | ✅ | ✅ (core focus) |
| **Prompt Templates** | ❌ | ✅ | ✅ | TBD |

### Provider Support

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

### CDI Integration

| Aspect | OmniHai | LangChain4J-CDI | Spring AI |
|--------|--------|-----------------|-----------|
| **Injection Style** | `@Inject @AI(...)` | `@Inject` + config | `@Autowired` + beans |
| **Qualifier-based** | ✅ | ❌ | ❌ |
| **EL Support** | ✅ `#{...}`, `${...}` | ❌ | ❌ (SpEL, different) |
| **MP Config Support** | ✅ `${config:...}` | ❌ | ❌ (SpEL, different) |

### Where OmniHai Shines

- Ultra-lightweight - No external HTTP library, just [`java.net.http.HttpClient`](https://docs.oracle.com/en/java/javase/21/docs/api/java.net.http/java/net/http/HttpClient.html). Minimal deps. Transparent gzip compression for reduced bandwidth.
- Built-in text utilities - Summarization, translation, transcription, proofreading, key point extraction, moderation as first-class features (not "build your own prompt")
- Structured outputs - Get typed Java objects directly from AI responses: `service.chat(message, MyRecord.class)`
- File attachments - Send documents, images, and other files alongside chat messages with help of `ChatInput`
- Native CDI with EL - `@AI(apiKey = "#{config.openaiKey}")` with expression resolution
- MicroProfile Config - `@AI(apiKey = "${config:openai.key}")` with expression resolution
- 10 providers out of the box - Including Ollama for local/offline
- Caller-owned conversation memory - History lives in `ChatOptions`, not in the service. No server-side session state, no memory leaks, no lifecycle management. The caller controls it. Sliding window keeps context manageable, and uploaded file references are tracked across turns.
- Automatic file cleanup - Uploaded files on provider servers are cleaned up after 2 days in a fire-and-forget background task, preventing stale file accumulation.
- Clean exception hierarchy - Specific exceptions per HTTP status

### Where OmniHai is Intentionally Simpler

No tools, embeddings, RAG, or agents. This isn't a gap - it's a design choice. OmniHai is a utility library, not a framework.

### Positioning

| Library | Analogy |
|---------|---------|
| **LangChain4J** | Full kitchen with every appliance |
| **Spring AI** | Full kitchen, Spring-branded appliances |
| **Jakarta Agentic** | Kitchen building code (specification) |
| **OmniHai** | Sharp chef's knife - does a few things very well |

OmniHai fills a different niche. For apps that need:

- Multi-provider chat with easy switching
- Text analysis (summarize, translate, proofread, moderate)
- Image analysis (describe, generate alt text)
- Audio analysis (transcribe)
- Minimal dependencies
- Pure Jakarta EE / MicroProfile

...without needing RAG pipelines, agent frameworks, or vector stores, OmniHai is arguably the better choice. Less to learn, less to break, fewer dependencies.

If Jakarta Agentic matures, OmniHai could potentially be a lightweight implementation of parts of that spec, or remain a complementary "just the essentials" alternative.

### Is OmniHai smaller than e.g. LangChain4J?

Yes, significantly:
- OmniHai JAR: ~175 KB vs LangChain4J: ~5-10 MB (*per* AI provider!) — at least 35x smaller
- 73 source files, ~11,000 lines of code (\~4,600 actual code, rest is javadoc)
- Zero external runtime dependencies — uses JDK's native `java.net.http.HttpClient` directly without any SDKs
- Only one required dependency: Jakarta JSON-P (which Jakarta EE and MicroProfile runtimes already have)
- Other dependencies are optional: CDI, EL and/or MP Config APIs (which Jakarta EE resp. MicroProfile runtimes already have)

### Is it faster?

Likely yes for startup and per-request overhead:
- No classpath scanning or proxy generation at startup
- Minimal reflection — only used once during service instantiation, not per-request
- No abstraction layers around HTTP — direct `java.net.http.HttpClient` usage
- Simple interface dispatch, no dynamic proxies
- Services are stateless and cached via `ConcurrentHashMap`

### Does it produce less GC garbage?

The design strongly suggests yes:
- No intermediate JSON object materialization — uses path extraction directly on `JsonObject`
- Conservative allocation patterns — no framework overhead creating wrapper objects
- Native `java.net.http.HttpClient` — has better GC characteristics than third-party HTTP libraries
- Simple POJOs and builders — no reflection-based bean creation at runtime
- Stateless services — all state lives in method parameters, no per-request object graphs

### When to Choose Each

**Choose OmniHai when:**
- You need a lean, focused solution for Jakarta EE or MicroProfile
- Your use case is straightforward chat, translation, summarization, proofreading, or moderation
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

**Choose Jakarta Agentic when:**
- You need a standard specification (once finalized)
- You want vendor-neutral portability
- You're building agentic workflows
- You can wait for the specification to mature

As said, OmniHai is "a sharp chef's knife — does a few things very well" rather than being a full framework.

Bottom line: If you need a lightweight utility for AI chat/text operations in Jakarta EE or MicroProfile without framework overhead, OmniHai is dramatically smaller and should be faster with less GC pressure. If you need RAG or agent pipelines, LangChain4J's / Spring AI's larger footprint comes with those capabilities.

## License

[Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0)

## Links

- [OmniHai](https://omnihai.org)
- [OmniFaces](https://omnifaces.org)
- [GitHub](https://github.com/omnifaces/omnihai)
- [Blog post: OmniAI 1.0-M1: One API, any AI](https://balusc.omnifaces.org/2026/01/one-api-any-ai.html)
- [Blog post: OmniAI 1.0-M2: Real-time AI, your way](https://balusc.omnifaces.org/2026/01/real-time-ai-your-way.html)
- [Blog post: OmniHai 1.0 released!](https://balusc.omnifaces.org/2026/02/omnihai-10-released.html)
- [Blog post: OmniHai 1.1: OmniHai grows ears](https://balusc.omnifaces.org/2026/02/omnihai-grows-ears.html)

## Credits

This README is ~90% generated by [Claude Code](https://claude.com/product/claude-code) :)


