[![Maven](https://img.shields.io/maven-central/v/org.omnifaces/omniai)](https://search.maven.org/artifact/org.omnifaces/omniai)
[![Javadoc](http://javadoc.io/badge/org.omnifaces/omniai.svg)](http://javadoc.io/doc/org.omnifaces/omniai) 

# OmniAI

A unified Java AI utility library for Jakarta EE applications.

## Overview

OmniAI provides a single, consistent API to interact with multiple AI providers. It achieves that by interacting with their REST API endpoints directly.

## Requirements

- Java 17+
- Jakarta EE 11 (JSON-P required, CDI optional, EL optional)

## Installation

```xml
<dependency>
    <groupId>org.omnifaces</groupId>
    <artifactId>omniai</artifactId>
    <version>1.0-M2</version>
</dependency>
```

On non-Jakarta EE containers such as Tomcat, you'll need to add JSON-P and optionally CDI dependencies:

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
```

You can also use it on Java SE, you'll still need the JSON-P implementation, but you cannot use the CDI annotation.

## Supported Providers (in 1.0-M2)

| Provider | Default Model | API Key Required | Available Models |
|----------|---------------|------------------|------------------|
| OpenAI | gpt-5-mini | [Yes](https://platform.openai.com/api-keys) | [List](https://platform.openai.com/docs/models) |
| Anthropic | claude-sonnet-4-5-20250929 | [Yes](https://platform.claude.com/settings/keys) | [List](https://platform.claude.com/docs/en/about-claude/models/overview) |
| Google AI | gemini-2.5-flash | [Yes](https://aistudio.google.com/app/api-keys) | [List](https://ai.google.dev/gemini-api/docs/models) |
| xAI | grok-4-1-fast-reasoning | [Yes](https://console.x.ai) | [List](https://docs.x.ai/docs/models) |
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
AIService service = AIConfig.of(AIProvider.ANTHROPIC, "your-api-key").createService();

// Simple chat
String response = service.chat("What is Jakarta EE?");
```

### CDI Integration

```java
@Inject
@AI(provider = AIProvider.ANTHROPIC, apiKey = "your-api-key")
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

// With different model
@Inject
@AI(provider = AIProvider.XAI,
    apiKey = "#{configBean.xaiApiKey}",
    model = "grok-2-image-1212")
private AIService imageGenerator;
```

### Multi-Provider Aggregation

Need diverse perspectives? OmniAI makes it easy to query multiple providers and combine their responses:

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

## Features (in 1.0-M2)

### Chat

```java
// Synchronous
String response = service.chat("Hello!");

// Asynchronous
CompletableFuture<String> future = service.chatAsync("Hello!");

// With options
String response = service.chat("Explain microservices",
    ChatOptions.newBuilder()
        .systemPrompt("You are a helpful software architect.")
        .temperature(0.7)
        .maxTokens(500)
        .build());

// Streaming
service.chatStream(message, options, token -> {
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

### Text Analysis

```java
// Summarize text
String summary = service.summarize(longText, 100); // max 100 words

// Extract key points
List<String> points = service.extractKeyPoints(text, 5); // max 5 points
```

### Translation

```java
// Translate with auto-detection
String translated = service.translate(text, null, "es");

// Translate from specific language
String translated = service.translate(text, "en", "fr");

// Detect language
String lang = service.detectLanguage(text); // Returns ISO 639-1 code
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

All methods have async variants returning `CompletableFuture` (e.g., `summarizeAsync`, `translateAsync`, `generateImageAsync`, etc.).

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
AIStrategy strategy = new AIStrategy(TrackingTextHandler.class, null);
AIService service = AIConfig.of("your-api-key").withStrategy(strategy).createService();
```

### CDI Integration

```java                                                                                                                                                                                                                                              
@Inject
@AI(provider = OPENAI, apiKey = "#{config.openaiApiKey}", textHandler = TrackingTextHandler.class)
private AIService trackedService;
```

## OmniAI vs LangChain4J vs Spring AI vs Jakarta Agentic

### Philosophy

| Aspect | OmniAI | LangChain4J | Spring AI | Jakarta Agentic |
|--------|--------|-------------|-----------|-----------------|
| **Target Runtime** | Jakarta EE | Any Java | Spring | Jakarta EE |
| **Philosophy** | Minimal, focused utility | Comprehensive toolkit | Spring integration | Standard specification |
| **Dependencies** | JSON-P only (CDI/EL optional) | Multiple modules | Spring framework | TBD (in development) |
| **Learning Curve** | Low | Medium-High | Medium (if Spring-familiar) | TBD |

### Feature Comparison (in 1.0-M2)

| Feature | OmniAI | LangChain4J | Spring AI | Jakarta Agentic |
|---------|--------|-------------|-----------|-----------------|
| **Chat/Completion** | ✅ | ✅ | ✅ | ✅ (planned) |
| **Streaming** | ✅ | ✅ | ✅ | TBD |
| **Function Calling** | ❌ | ✅ | ✅ | TBD |
| **RAG Support** | ❌ | ✅ (extensive) | ✅ | TBD |
| **Vector Stores** | ❌ | ✅ (many) | ✅ (many) | TBD |
| **Embeddings** | ❌ | ✅ | ✅ | TBD |
| **Image Analysis** | ✅ | ✅ | ✅ | TBD |
| **Image Generation** | ✅ | ✅ | ✅ | TBD |
| **Content Moderation** | ✅ (native + fallback) | ❌ | ❌ | TBD |
| **Translation** | ✅ | ❌ (via chat) | ❌ (via chat) | TBD |
| **Summarization** | ✅ | ❌ (via chat) | ❌ (via chat) | TBD |
| **Memory/History** | ❌ | ✅ | ✅ | TBD |
| **Agents** | ❌ | ✅ | ✅ | ✅ (core focus) |
| **Prompt Templates** | ❌ | ✅ | ✅ | TBD |

### Provider Support (in 1.0-M2)

| Provider | OmniAI | LangChain4J | Spring AI |
|----------|--------|-------------|-----------|
| OpenAI | ✅ | ✅ | ✅ |
| Anthropic | ✅ | ✅ | ✅ |
| Google AI | ✅ | ✅ | ✅ |
| xAI (Grok) | ✅ | ❌ | ❌ |
| Mistral | ✅ | ✅ | ✅ |
| Meta AI | ✅ | ❌ | ❌ |
| Azure OpenAI | ✅ | ✅ | ✅ |
| OpenRouter | ✅ | ❌ | ❌ |
| Hugging Face | ✅ | ✅ | ✅ |
| Ollama | ✅ | ✅ | ✅ |
| AWS Bedrock | ❌ | ✅ | ✅ |

### CDI Integration

| Aspect | OmniAI | LangChain4J-CDI | Spring AI |
|--------|--------|-----------------|-----------|
| **Injection Style** | `@Inject @AI(...)` | `@Inject` + config | `@Autowired` + beans |
| **EL Support** | ✅ `#{...}` expressions | ❌ | ❌ (SpEL, different) |
| **MP Config Support** | ✅ `${config:...}` | ❌ | ❌ (SpEL, different) |
| **Zero Config** | ❌ | ❌ | ❌ |
| **Qualifier-based** | ✅ | ❌ | ❌ |

### Where OmniAI Shines

- Ultra-lightweight - No external HTTP library, just [`java.net.http.HttpClient`](https://docs.oracle.com/en/java/javase/21/docs/api/java.net.http/java/net/http/HttpClient.html). Minimal deps.
- Built-in text utilities - Summarization, translation, key point extraction, moderation as first-class features (not "build your own prompt")
- Native CDI with EL - `@AI(apiKey = "#{config.key}")` with expression resolution
- 8 providers out of the box - Including Ollama for local/offline
- Clean exception hierarchy - Specific exceptions per HTTP status

### Where OmniAI is Intentionally Simpler

No tools, embeddings, RAG, memory, or agents. This isn't a gap - it's a design choice. OmniAI is a utility library, not a framework.

### Positioning

| Library | Analogy |
|---------|---------|
| **LangChain4J** | Full kitchen with every appliance |
| **Spring AI** | Full kitchen, Spring-branded appliances |
| **Jakarta Agentic** | Kitchen building code (specification) |
| **OmniAI** | Sharp chef's knife - does a few things very well |

OmniAI fills a different niche. For apps that need:

- Multi-provider chat with easy switching
- Text analysis (summarize, translate, moderate)
- Minimal dependencies
- Pure Jakarta EE / CDI

...without needing RAG pipelines, agent frameworks, or vector stores, OmniAI is arguably the better choice. Less to learn, less to break, fewer dependencies.

If Jakarta Agentic matures, OmniAI could potentially be a lightweight implementation of parts of that spec, or remain a complementary "just the essentials" alternative.

### Is OmniAI smaller than e.g. LangChain4J?

Yes, significantly:
- OmniAI 1.0-M2 JAR: 110 KB vs LangChain4J: 2+ MB (with dependencies) — roughly 20x smaller
- 54 source files, ~7,300 lines of code (~2,900 actual code, rest is javadocs/comments)
- Zero runtime dependencies — uses JDK's native `java.net.http.HttpClient` directly
- Only optional provided dependencies: Jakarta JSON-P, CDI, and EL APIs (which Jakarta EE servers already have)

### Is it faster?

Likely yes for startup and per-request overhead:
- No classpath scanning or proxy generation at startup
- Minimal reflection — only used once during service instantiation, not per-request
- No abstraction layers around HTTP — direct `java.net.http.HttpClient` usage
- Simple interface dispatch, no dynamic proxies
- Services are stateless and cached via ConcurrentHashMap

### Does it produce less GC garbage?

The design strongly suggests yes:
- No intermediate JSON object materialization — uses path extraction directly on JsonObject
- Conservative allocation patterns — no framework overhead creating wrapper objects
- Native `java.net.http.HttpClient` — has better GC characteristics than third-party HTTP libraries
- Simple POJOs and builders — no reflection-based bean creation at runtime
- Stateless services — all state lives in method parameters, no per-request object graphs

### When to Choose Each

**Choose OmniAI when:**
- You need a lightweight, focused solution for Jakarta EE
- Your use case is straightforward chat, translation, summarization, or moderation
- You want minimal dependencies and a small footprint
- You prefer simplicity over feature completeness

**Choose LangChain4J when:**
- You're building complex AI agents with tools
- You need conversation memory management
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

As said, OmniAI is "a sharp chef's knife — does a few things very well" rather than being a full framework.

Bottom line: If you need a lightweight utility for AI chat/text operations in Jakarta EE without framework overhead, OmniAI is dramatically smaller and should be faster with less GC pressure. If you need streaming, RAG, or agent pipelines, LangChain4J's / Spring AI's larger footprint comes with those capabilities.

## License

[Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0)

## Links

- [OmniFaces](https://omnifaces.org)
- [GitHub](https://github.com/omnifaces/omniai)
- [Blog post: One API, any AI](https://balusc.omnifaces.org/2026/01/one-api-any-ai.html)
- [Blog post: Real-time AI, your way](https://balusc.omnifaces.org/2026/01/real-time-ai-your-way.html)

## Credits

This README is ~90% generated by [Claude Code](https://claude.com/product/claude-code) :)


