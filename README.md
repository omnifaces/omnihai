[![Maven](https://img.shields.io/maven-central/v/org.omnifaces/omnihai)](https://search.maven.org/artifact/org.omnifaces/omnihai)
[![Javadoc](https://javadoc.io/badge/org.omnifaces/omnihai.svg)](https://javadoc.io/doc/org.omnifaces/omnihai) 

[![logo](https://raw.githubusercontent.com/omnifaces/omnihai/refs/heads/main/logo-96x96.png)](https://github.com/omnifaces/omnihai)

# OmniHai

*One API, any AI*

A unified Java AI utility library, with first-class Jakarta EE and MicroProfile integration.

## Overview

OmniHai provides a single, consistent API to interact with multiple AI providers. It achieves that by interacting with their REST API endpoints directly.

## Contents

- [Minimum Requirements](#minimum-requirements)
- [Installation](#installation) — [servlet containers](#servlet-containers), [plain Java SE](#plain-java-se), [CDI on plain Java SE](#cdi-on-plain-java-se)
- [Supported Providers](#supported-providers)
- [Quick Start](#quick-start) — [programmatic](#programmatic-configuration), [CDI](#cdi-integration), [multi-provider aggregation](#multi-provider-aggregation)
- [Features](#features) — [chat](#chat), [token usage](#token-usage-tracking), [cost](#cost-calculation), [reasoning effort](#reasoning-effort), [structured outputs](#structured-outputs), [web search](#web-search), [text analysis](#text-analysis), [classification](#classification), [moderation](#content-moderation), [modality support](#modality-support), [images](#image-analysis-and-generation), [audio](#audio-transcription-and-generation), [video analysis](#video-analysis), [video generation](#video-generation)
- [Custom Providers](#custom-providers) and [Custom Handlers](#custom-handlers)
- [Service Wrapper](#service-wrapper) — [resilience](#resilience)
- [Tool Use](#tool-use) — [declaring programmatically](#declaring-tools-programmatically), [grouping](#grouping-tools), [authorizing](#authorizing-tool-calls), [bounding and observing the loop](#bounding-and-observing-the-loop), [owning the loop](#owning-the-loop)
- [Where OmniHai Fits](#where-omnihai-fits)
- [License](#license), [Links](#links), [Credits](#credits)

## Minimum Requirements

- Java 17
- A JSON-P implementation

That is the only hard requirement. CDI, EL and MP Config are optional and only needed for the `@AI` injection features. Jakarta EE 11 and MicroProfile 7 runtimes already provide all four, so nothing needs to be added there.

## Installation

```xml
<dependency>
    <groupId>org.omnifaces</groupId>
    <artifactId>omnihai</artifactId>
    <version>1.7.1</version>
</dependency>
```
That's all for Jakarta EE / MicroProfile runtimes. No additional dependencies needed.

### Servlet containers

On a bare servlet container such as Tomcat, add JSON-P and optionally CDI / MP Config:

```xml
<!-- JSON-P implementation (required) -->
<dependency>
    <groupId>org.eclipse.parsson</groupId>
    <artifactId>parsson</artifactId>
    <version>1.1.9</version>
</dependency>

<!-- CDI implementation (optional, for @AI injection) -->
<dependency>
    <groupId>org.jboss.weld.servlet</groupId>
    <artifactId>weld-servlet-shaded</artifactId>
    <version>6.0.4.Final</version>
</dependency>

<!-- MP Config implementation (optional, for ${config:...} resolution in @AI attributes) -->
<dependency>
    <groupId>io.smallrye.config</groupId>
    <artifactId>smallrye-config</artifactId>
    <version>3.18.1</version>
</dependency>
```

### Plain Java SE

OmniHai runs on plain Java SE too. The programmatic API needs nothing but a JSON-P implementation:

```xml
<dependency>
    <groupId>org.eclipse.parsson</groupId>
    <artifactId>parsson</artifactId>
    <version>1.1.9</version>
</dependency>
```

```java
AIService service = AIConfig.of(AIProvider.OLLAMA, null).createService();
String response = service.chat("What is Jakarta EE?");
```

The `@AI` injection features work on Java SE as well, via Weld SE. See [CDI on plain Java SE](#cdi-on-plain-java-se).

### CDI on plain Java SE

Add Weld SE, the standalone CDI implementation, next to JSON-P:

```xml
<dependency>
    <groupId>org.jboss.weld.se</groupId>
    <artifactId>weld-se-core</artifactId>
    <version>6.0.4.Final</version>
</dependency>
```

Weld only discovers beans in archives which carry a `META-INF/beans.xml`, so add one to your own module:

```xml
<beans
    xmlns="https://jakarta.ee/xml/ns/jakartaee"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="https://jakarta.ee/xml/ns/jakartaee https://jakarta.ee/xml/ns/jakartaee/beans_4_1.xsd"
    version="4.1" bean-discovery-mode="annotated"
>
</beans>
```

Then bootstrap the container, and inject with `@AI` exactly as in a container:

```java
public static void main(String[] args) {
    try (var container = SeContainerInitializer.newInstance().initialize()) {
        container.select(Chatbot.class).get().run(); // Chatbot being your own bean with an @AI injection point
    }
}
```

`@AITool` methods on CDI beans are discovered and invoked exactly as in a container as well.
EL expressions in `@AI` attributes additionally need Weld's EL module `org.jboss.weld.module:weld-web` plus an EL implementation such as `org.glassfish.expressly:expressly`, as `weld-se-core` ships neither.
MicroProfile Config expressions additionally need `io.smallrye.config:smallrye-config` and a `META-INF/microprofile-config.properties`.

## Supported Providers

| Provider | Default Model | API Key Required | Available Models |
|----------|---------------|------------------|------------------|
| OpenAI | gpt-5.6-terra | [Yes](https://platform.openai.com/api-keys) | [List](https://developers.openai.com/api/docs/models) |
| Anthropic | claude-sonnet-5 | [Yes](https://platform.claude.com/settings/keys) | [List](https://platform.claude.com/docs/en/about-claude/models/overview) |
| Google AI | gemini-3.7-flash | [Yes](https://aistudio.google.com/app/api-keys) | [List](https://ai.google.dev/gemini-api/docs/models) |
| xAI | grok-4.6 | [Yes](https://console.x.ai) | [List](https://docs.x.ai/developers/models) |
| Mistral | mistral-medium-3-5 | [Yes](https://admin.mistral.ai/organization/api-keys) | [List](https://docs.mistral.ai/models) |
| Meta AI | muse-spark-1.3 | [Yes](https://dev.meta.ai/api-keys) | [List](https://dev.meta.ai/docs/models) |
| Azure OpenAI | gpt-5.6-terra | [Yes](https://portal.azure.com) | [List](https://ai.azure.com/catalog) |
| OpenRouter | deepseek/deepseek-v4-pro | [Yes](https://openrouter.ai/workspaces/default/keys) | [List](https://openrouter.ai/models) |
| Hugging Face | google/gemma-4-31B-it | [Yes](https://huggingface.co/settings/tokens) | [List](https://huggingface.co/models) |
| Ollama | gemma4 | No (localhost) | [List](https://ollama.com/library) |
| Custom | - | - | - |

## Quick Start

### Programmatic Configuration

```java
// Create a service instance
AIService service = AIConfig.of(AIProvider.OPENAI, "your-openai-api-key").createService();

// Simple chat
String response = service.chat("What is Jakarta EE?");
```

### CDI Integration

```java
@Inject
@AI(provider = AIProvider.ANTHROPIC, apiKey = "your-anthropic-api-key")
private AIService claude;

// EL `#{...}` and MicroProfile Config `${config:...}` expressions both resolve in every attribute
@Inject
@AI(provider = AIProvider.GOOGLE,
    apiKey = "${config:google.api-key}",
    model = "#{configBean.geminiModel}",
    prompt = "You are a helpful assistant specialized in Jakarta EE.")
private AIService jakartaExpert;
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
Path document = Path.of("report.pdf");
Path image = Path.of("chart.png");

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

History is a sliding window, defaulting to 20 messages (10 conversational turns), evicting the oldest when the limit is exceeded; `withMemory(50)` sizes it otherwise.
Feed `history(...)` an existing `getHistory()` to carry a conversation over to a differently configured `ChatOptions`, e.g. one with another system prompt or temperature.

The instance is `Serializable`, so it can be saved and restored across sessions in an HTTP session or a database.
For portable storage — REST payloads, JSON columns, audit logs, cross-service transport — use the explicit JSON form:

```java
String json = options.toJson();     // serialize: options + history, no lastUsage
ChatOptions restored = ChatOptions.fromJson(json); // rehydrate: always mutable
```

File attachments are tracked in history too: their references are preserved across turns, so a follow-up question can still reach the PDF uploaded two turns ago, and they are evicted along with the message which slides out of the window.
Uploaded files on the provider's servers are cleaned up in the background after 2 days, so nothing accumulates; only files uploaded by OmniHai are touched.
This requires the AI provider to support a files API, which is currently the case for OpenAI, Anthropic, Google AI, xAI, Mistral, and OpenRouter.

### Token Usage Tracking

Track token consumption for cost monitoring and optimization:

```java
ChatOptions options = ChatOptions.DEFAULT.copy(); // Default ones are immutable, so you need a copy.
service.chat("Explain quantum computing", options);

ChatUsage usage = options.getLastUsage();
int total = usage.totalTokens(); // inputTokens() + outputTokens()
```

`cachedInputTokens()` is the subset of the input tokens which the provider's prompt cache served, and `reasoningTokens()` the subset of the output tokens which a reasoning-capable model spent on thinking.
A `ChatUsage` is available after each chat call which the provider reports usage for; values it leaves out are `-1`.

### Cost Calculation

Turn token usage into an actual cost by attaching a `ChatPricing` configuration to your `ChatOptions`. Prices are expressed *per one million tokens* to match how providers publish their rate sheets. Look up the current rate for your chosen model and pass those numbers in:

```java
// Example rates — always use the provider's current rate sheet
ChatPricing pricing = new ChatPricing(
    new BigDecimal("3.00"),  // input price per 1M tokens
    new BigDecimal("0.30"),  // cached-input price per 1M tokens (optional)
    new BigDecimal("15.00"), // output price per 1M tokens (includes reasoning)
    Currency.getInstance("USD"));

ChatOptions options = ChatOptions.newBuilder()
    .pricing(pricing)
    .build();

String response = service.chat("Explain quantum computing", options);

ChatCost cost = options.getLastCost(); // inputCost(), cachedInputCost(), outputCost(), totalCost(), currency()
```

`ChatPricing.of(input, output)` and `ChatPricing.of(input, cached, output)` are convenience factories which skip the currency, and a `null` `cachedInputTokenPrice` bills cached tokens at `inputTokenPrice`.
Any `ChatUsage` can be priced ad-hoc with `usage.calculateCost(pricing)`.

Note: `ChatPricing` models a simplified three-tier scheme (base input, cached input, output). Provider-specific billing axes such as Anthropic's 5-minute / 1-hour cache-*write* premium are not modeled and may cause under-counting for heavy explicit-prompt-caching workloads. For strict accuracy, reconcile against the provider's own billing API.

#### Budget cap

Pair a pricing with a cumulative-cost cap to stop runaway spend on a given `ChatOptions` instance:

```java
ChatOptions options = ChatOptions.newBuilder()
    .pricing(pricing, new BigDecimal("1.00")) // hard stop at $1.00
    .build();

while (hasMoreWork()) {
    try {
        service.chat(next(), options);
    } catch (AIBudgetExceededException e) {
        log.warn("Spent {} of {} {} — stopping", e.getTotalCost(), e.getMaxTotalCost(), e.getCurrency());
        break;
    }
}
```

The cap is checked *before* each call using the accumulated `ChatOptions.getTotalCost()`. It is a **soft** ceiling: the call that pushes the running total at or over the cap still completes; the next call is refused with `AIBudgetExceededException`. Call `options.resetBudget()` to zero the counter and start a fresh window on the same instance, switch to a different `ChatOptions` instance, or even fail over to a different `AIService` (e.g. a cheaper model) to continue.

### Reasoning Effort

Models that expose reasoning (e.g. GPT-5, Claude extended thinking, Gemini thinking, Grok reasoning) let you tune how many tokens to spend on internal reasoning:

```java
ChatOptions options = ChatOptions.newBuilder()
    .reasoningEffort(ReasoningEffort.HIGH)
    .build();

String answer = service.chat("Prove the Pythagorean theorem.", options);
```

Available levels: `AUTO` (default, defers to the provider), `NONE` (disable reasoning where supported), `LOW` (~20% of budget), `MEDIUM` (~50% of budget), `HIGH` (~80% of budget), and `XHIGH` (~95% of budget). Providers that don't support a given level map to the closest equivalent.

### Structured Outputs

Get typed Java objects directly from AI responses:

```java
// Define your response structure as a record (or bean)
record ProductReview(String sentiment, int rating, List<String> pros, List<String> cons) {}

// Get a typed response in one call
ProductReview review = service.chat("Analyze this review: " + reviewText, ProductReview.class);
```

Every typed method has a `ChatOptions` overload, so a system prompt and a temperature apply here as they do to a plain chat.

Under the hood, OmniHai generates a JSON schema from the class, instructs the AI to return conforming JSON, and parses the response back into the typed object.
You can also do this manually if you need more control:

```java
JsonObject schema = JsonSchemaHelper.buildJsonSchema(ProductReview.class);
ChatOptions options = ChatOptions.newBuilder().jsonSchema(schema).build();
String responseJson = service.chat("Analyze this review: " + reviewText, options);
ProductReview review = JsonSchemaHelper.fromJson(responseJson, ProductReview.class);
```

`JsonSchemaHelper` supports primitive types, strings, enums, temporals, collections, arrays, maps, nested types, and `Optional` fields (which are excluded from `"required"` in JSON schema).

### Web Search

Enable the AI to access up-to-date information from the internet via built-in "web search" tool.
You can enable web search via `ChatOptions.newBuilder().webSearch()` when calling `chat(…)` or you can use the dedicated `webSearch(…)` methods: 

```java
// Basic web search
String response = service.webSearch("What is the current stock price of Tesla?");

// Structured output from web search
record StockPrice(String ticker, BigDecimal price, String currencyCode) {}
StockPrice price = service.webSearch("What is the current stock price of Tesla?", StockPrice.class);

// Localized web search
Location miami = new Location("US", "Florida", "Miami");
String response = service.webSearch("What is the current weather?", miami);

// Async variant
CompletableFuture<String> future = service.webSearchAsync("Latest news about AI regulations");
```

With custom system prompt and temperature via chat(…) method:
```java
ChatOptions options = ChatOptions.newBuilder()
    .systemPrompt("""
        You are a financial analyst.
        When retrieving data, prioritize official exchange websites like NASDAQ or NYSE over secondary sources like Yahoo Finance or Google Finance.
    """)
    .temperature(0.2)
    .webSearch() // or webSearch(location) with non-null location
    .build();

StockPrice nvidiaPrice = service.chat("What is the current stock price of Nvidia?", options, StockPrice.class);
```

### Text Analysis

```java
String summary = service.summarize(longText, 100);        // max 100 words
List<String> points = service.extractKeyPoints(text, 5);  // max 5 points
String lang = service.detectLanguage(text);               // ISO 639-1 code
String spanish = service.translate(text, null, "es");     // null source language auto-detects
String french = service.translate(text, "en", "fr");
String corrected = service.proofread(text);               // fixes grammar and spelling, preserves meaning and style
```

### Classification

```java
// Pick exactly one label
ClassificationResult result = service.classify(ticket, "billing", "shipping", "technical");
route(result.label()); // Never anything else than one of the offered labels.

// Fall back to a human when the AI is unsure
if (result.confidence() < 0.7) {
    queueForReview(ticket);
}

// Score every label on its own merit, best fitting one first
List<ClassificationResult> results = service.classifyAll(ticket, "billing", "shipping", "technical");

// Tag with all labels which apply, which may be several or none
List<String> tags = results.stream().filter(r -> r.confidence() > 0.5).map(ClassificationResult::label).toList();
```

Use `classify(…)` to route a text to one destination and `classifyAll(…)` to tag it, rank the labels, or find out that none of them fits.
In `classify(…)` the labels are the only values the AI may answer with, so it always picks one even when the fit is poor, and the confidence is what tells that apart.
In `classifyAll(…)` each label is scored independently, so the scores are not divided among them.

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

### Modality Support

Not every model does every modality, so `supportsModality` gives a hint about the configured one, to skip a call which would fail, to disable a button, or to pick another provider:

```java
if (service.supportsModality(AIModality.VIDEO_ANALYSIS)) {
    return service.analyzeVideo(video, "Summarize this");
}
```

Most providers derive the answer from the model name and version. OpenRouter and Hugging Face instead publish the input and output modalities per routed model, which OmniHai looks up rather than guesses: of the OpenRouter models accepting video, not one carries "video" in its name. Each listing is fetched at most once a day per endpoint and shared by every service instance on it, so the first call blocks on one HTTP request per listing and the rest are answered from memory; when a listing cannot be obtained, matching the model name is the fallback. OpenRouter omits its video generators from the default listing, so the one under `output_modalities=video` is fetched and cached next to it, which makes its first call two requests rather than one.

It answers about the service rather than about the model alone: a modality which no operation of the service can perform is reported as unsupported, whatever the provider publishes about the model.

It remains a hint, not a guarantee, and can be wrong in both directions: a published listing goes stale, a name-matched guess is a guess, and a provider may still refuse a call it advertises, for this key, this region or this moment. The only guarantee is to make the call and handle its failure — `UnsupportedOperationException` when the implementation itself does not serve the modality, and an `AIException` when the AI provider rejects it:

```java
try {
    return service.analyzeVideo(video, "Summarize this");
}
catch (UnsupportedOperationException | AIException e) {
    return fallbackService.analyzeVideo(video, "Summarize this");
}
```

### Image Analysis and Generation

```java
// Analyze, from bytes or straight from a source path
byte[] imageBytes = Files.readAllBytes(imagePath);
String description = service.analyzeImage(imageBytes, "Describe the product");
String described = service.analyzeImage(Path.of("product.png"), "Describe the product");
String altText = service.generateAltText(imageBytes);

// Generate, optionally with options
byte[] image = service.generateImage("A sunset over mountains");
byte[] office = service.generateImage("A modern office",
    GenerateImageOptions.newBuilder()
        .size("1024x1024")
        .build());
```

### Audio Transcription and Generation

```java
String transcription = service.transcribe(Path.of("audio.mp3"));

// Text-to-speech, to bytes or straight to a file (the file name is yours to pick; the format depends on the AI provider)
byte[] audio = service.generateAudio("Hello, welcome to OmniHai!");
service.generateAudio("Hello, welcome to OmniHai!", Path.of("greeting.mp3"));

// With options (allowable options depend on AI provider)
byte[] tuned = service.generateAudio("Hello!",
    GenerateAudioOptions.newBuilder()
        .voice("breeze")
        .speed(1.5)
        .outputFormat("wav")
        .build());
```

Some providers transcribe by attaching the audio to a chat completion. Others have a dedicated endpoint, which needs a model that serves it: `gpt-4o-transcribe` on OpenAI, `muse-voice-transcribe-1.0` on Meta AI, Voxtral on Mistral and Whisper on Hugging Face. The Meta AI one accepts a mono 16-bit PCM WAV at 16 or 24 kHz alone, so OmniHai converts the audio to that format first; audio which the Java Sound API cannot read, such as MP3, is refused before the call.

OpenAI honors the output format and defaults to MP3. Gemini and OpenRouter emit bare PCM which OmniHai prepends a WAV header to, so they answer WAV whichever format was asked for, and a file named `.mp3` would hold a WAV. Use `MimeType.guessMimeType(audio)` to learn what actually came back, as the audio generation IT does.

### Video Analysis

```java
// Analyze video
String description = service.analyzeVideo(Path.of("match.mp4"), "When does the goal happen?");

// With options (sample 2 frames per second of the second minute only)
String sampled = service.analyzeVideo(Path.of("match.mp4"), "When does the goal happen?",
    AnalyzeVideoOptions.newBuilder()
        .fps(2)
        .startOffset(Duration.ofMinutes(1))
        .endOffset(Duration.ofMinutes(2))
        .build());
```

Video input is accepted by Gemini and by the video-capable models routed through OpenRouter, which [modality support](#modality-support) tells apart. The sampling options are honored by Gemini only, as OpenRouter takes the video as a plain data URI with nowhere to put them.

### Video Generation

Video generation is the only operation which does not fit in one request: the AI provider answers the submission with a job id within seconds, then takes minutes to produce the video, and never calls back.
`generateVideo` therefore hands back a handle on the job as soon as it is accepted, and `generateVideoAsync` waits for the video itself.

```java
// Submit and walk away; returns at once, PENDING
VideoGeneration video = service.generateVideo("Sunrise over the colorful houses of Willemstad");
String jobId = video.jobId();

VideoGeneration.Status status = video.status(); // Pure getter, performs no I/O, free to call from a render pass
VideoGeneration.Status refreshedStatus = video.refresh().status(); // Caller-driven poll, exactly one request

// Revive the job from its id in a later request, possibly after a restart or on another node
VideoGeneration revived = service.findVideoGeneration(jobId);

if (revived.refresh().status() == VideoGeneration.Status.COMPLETED) {
    revived.writeTo(Path.of("cat.mp4")); // And writeTo(OutputStream)
}
```

```java
// Or wait for the video; the library polls meanwhile
service.generateVideo("Sunrise over the colorful houses of Willemstad", Path.of("curacao.mp4"));

// Or wait without blocking the calling thread
CompletableFuture<Void> written = service.generateVideoAsync("A calico cat", Path.of("cat.mp4"));

// Or wait for the handle itself, which carries the terminal status next to the video
CompletableFuture<VideoGeneration> pending = service.generateVideoAsync("A calico cat");
pending.thenAccept(video -> video.writeTo(Path.of("cat.mp4")));

// With options (allowable options depend on AI provider)
VideoGeneration video = service.generateVideo("A calico cat",
    GenerateVideoOptions.newBuilder()
        .aspectRatio("9:16")
        .resolution("720p")
        .seconds(8)
        .pollInterval(Duration.ofSeconds(30))
        .maxWait(Duration.ofMinutes(10))
        .build());
```

The handle is serializable and carries the job id, so a web application can submit in one request and poll from later ones.
A deserialized handle can still be read but no longer polled; hand its `jobId()` to `findVideoGeneration` to get a pollable one back.
The library polls at `pollInterval`, five seconds by default, and stops as soon as the future returned by `generateVideoAsync` or `completion()` is completed or canceled, so a handle nobody watches costs nothing.
A job which has not finished within `maxWait`, five minutes by default, fails the future rather than polling on, so a job the AI provider never finishes cannot block a caller forever. Raise it for a resolution or AI provider which takes longer.

Video generation is offered by Google (Veo), xAI (Grok Imagine) and OpenRouter, which routes generators of several labs. It is absent on OpenAI and Azure OpenAI, which both retire Sora without a successor to route to, and on Anthropic, Mistral, Meta, Hugging Face and Ollama, where [modality support](#modality-support) reports `VIDEO_GENERATION` as unsupported. Each AI provider states sizing in its own vocabulary, so `aspectRatio` as `16:9`, `size` as `{width}x{height}` and `resolution` as `720p` are reconciled per provider: setting a size recalculates the aspect ratio and setting an aspect ratio resets the size, so the two can never contradict each other. The aspect ratio always reaches the request, defaulting to landscape. A `size`, `resolution` or duration left at its default is omitted, so that the AI provider applies its own.

Generated videos are hosted by the AI provider for about a day and are then deleted, upon which the status becomes `EXPIRED`. Some AI providers host them on a separate host and hand out a pre-signed URL; OmniHai downloads such a URL without the API key.

All methods have async variants returning `CompletableFuture` (e.g., `chatAsync`, `summarizeAsync`, `translateAsync`, `proofreadAsync`, `classifyAsync`, `moderateContentAsync`, `analyzeImageAsync`, `generateImageAsync`, `transcribeAsync`, `generateAudioAsync`, `analyzeVideoAsync`, `generateVideoAsync`, etc.).

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
AIService service = AIConfig.of(AIProvider.OPENAI, "your-openai-api-key").withStrategy(strategy).createService();
```

### CDI Integration

```java
@Inject
@AI(provider = OPENAI, apiKey = "#{config.openaiApiKey}", textHandler = TrackingTextHandler.class)
private AIService trackedService;
```

## Service Wrapper

`AIServiceWrapper` is an abstract decorator base class that lets you wrap any `AIService` and intercept specific methods. All methods delegate to the wrapped service by default, so you only override what you need — useful for cost tracking, caching, auditing, or A/B testing between providers.

Note that `AIServiceWrapper` delegates each overload straight to the identical overload on the wrapped service, so overriding a single method (e.g. `chatAsync(ChatInput, ChatOptions)`) intercepts *only* that exact overload — calls made through other overloads (`chat(String)`, `summarize(...)`, the image/audio methods, …) bypass it. To intercept every operation uniformly, extend `InterceptingAIServiceWrapper` and implement its two `intercept` / `interceptAsync` hooks instead.

### Resilience

OmniHai ships two ready-to-use resilience decorators built on `InterceptingAIServiceWrapper`, so they apply across the entire service surface — chat, image, audio, moderation, etc. — synchronous and asynchronous alike.

**Retry.** `RetryingAIService` retries transient failures (HTTP 429 rate limit, HTTP 503 unavailable, transient I/O) with exponential backoff and full jitter:

```java
AIService resilient = new RetryingAIService(service); // 3 attempts, sensible defaults

AIService tuned = RetryingAIService.newBuilder(service)
    .maxAttempts(5)
    .initialBackoff(Duration.ofSeconds(1))
    .maxBackoff(Duration.ofSeconds(20))
    .maxDuration(Duration.ofMinutes(1))
    .build();
```

On the CDI path the same policy is available on the qualifier itself:

```java
@Inject @AI(apiKey = "#{keys.openai}", maxAttempts = 3)
private AIService gpt;
```

`maxAttempts` counts the initial attempt plus retries, exactly as on the builder, so the default of 1 means a single attempt and hence no retrying.
Anything below 1 is rejected at injection time rather than silently producing a service that never retries.
Backoff, duration and the retry condition are not expressible as annotation constants, so reach for the builder when you need to tune those.
Injection points sharing the same provider configuration still share one underlying service; only the decorator around it differs.

**Failover.** `FailoverAIService` tries a primary service, then falls back to alternates in order on those same transient failures. It has no annotation form, as it needs several fully configured services rather than a single value:

```java
@Inject @AI(apiKey = "#{keys.openai}")
private AIService gpt;

@Inject @AI(provider = ANTHROPIC, apiKey = "#{keys.anthropic}")
private AIService claude;

AIService resilient = new FailoverAIService(gpt, claude);
String response = resilient.chat("Explain the Jakarta EE security model.");
```

Being pure decorators, they compose — retry each provider before failing over to the next:

```java
AIService resilient = new FailoverAIService(
    new RetryingAIService(gpt),
    new RetryingAIService(claude));
```

By default both trigger on rate limiting, service unavailability, and transient I/O, but never on deterministic errors such as a bad request or authentication failure (an alternate provider or a retry would fail the same way). Customize the condition via `RetryingAIService.Builder.retryOn(...)` or `FailoverAIService.Builder.failoverOn(...)`.

From the caller's perspective each is just an `AIService`.

#### Streaming

Re-attempting a `chatStream(…)` replays it from the start, which would leave the token consumer holding a duplicated prefix. Both decorators therefore re-attempt a stream only while it is safe to do so:

- Fails **before** any token was emitted → retried or failed over transparently. Nothing was delivered, so nothing can be duplicated.
- Fails **after** one or more tokens were emitted → the operation is abandoned with a terminal `AIStreamAbortedException` (the original failure is its cause), rather than silently corrupting what you accumulated.

To opt into restarting a partially consumed stream, pass a `ResettableConsumer` instead of a plain `Consumer<String>`. It is notified right before each new attempt, so it can discard the stale prefix:

```java
StringBuilder response = new StringBuilder();

service.chatStream(message, ResettableConsumer.of(
    token -> response.append(token),
    (cause, attempt) -> response.setLength(0))); // previous attempt's tokens are stale
```

Note that `chatStream` is the only operation with this hazard; every other operation delivers its result once, atomically, on completion.

Conversation memory composes safely with both decorators: a memory-enabled `ChatOptions` records the user message before the request is sent (file attachments are uploaded and anchored to it while the payload is built), but re-recording the same user message replaces it rather than appending. A retried or failed-over chat therefore leaves exactly one user message in the history, and only the successful attempt's file references survive.

## Tool Use

Let the AI call your own methods before it answers. Annotate them with `@AITool` and hand the object over:

```java
@ApplicationScoped
public class OrderTools {

    @Inject
    private OrderService orders;

    @ReadOnly
    @AITool("Looks up a single order by id")
    public String findOrder(@AIToolParam(value = "The order id", name = "orderId") long orderId) {
        return orders.findById(orderId).map(Order::toSummary).orElse("No order found with that id.");
    }

    @AITool("Lists the orders placed by a customer")
    public String listOrders(@AIToolParam(value = "The customer email", name = "email") String email) {
        var summaries = orders.listByEmail(email).stream().map(Order::toSummary).collect(joining("\n"));
        return summaries.isEmpty() ? "No orders found for that email." : summaries;
    }

    @AITool("Issues a refund for an order")
    public Refund refundOrder(@AIToolParam(value = "The order id", name = "orderId") long orderId) {
        return orders.refund(orderId);
    }

}
```

```java
@Inject @AI(apiKey = "#{keys.openai}")
private AIService gpt;

AIService agent = gpt.withTools(orderTools);
String answer = agent.chat("Where is order 42?");
```

On the CDI path the tools are named on the qualifier itself, and the classes are resolved as beans:

```java
@Inject
@AI(apiKey = "#{keys.openai}", tools = OrderTools.class, toolGroup = ReadOnly.class,
    maxToolCalls = 4, maxAttempts = 3)
private AIService agent;
```

The tool beans must be normal-scoped, e.g. `@ApplicationScoped` or `@RequestScoped`, so that they observe their own scope and their own interceptors on every call; a `@Dependent` one is rejected at injection time. Retrying and tool calling compose in the order described under [bounding the loop](#bounding-and-observing-the-loop) regardless of the order you write the attributes in. The observer is not expressible as an annotation constant, so it remains programmatic.

Each turn the AI either names a tool or answers. A named tool is invoked with its arguments converted to the declared parameter types, the return value is fed back, and the next turn begins, until the AI answers or the tool call cap is exhausted. Tools work the same on every provider, as they ride on the same provider-enforced structured outputs as `chat(message, MyRecord.class)` rather than on provider-native function calling.

The tool name is derived from the method signature, so `findOrder` of `OrderTools` becomes `OrderTools_findOrder` and overloads of it carry their parameter types as `OrderTools_findOrder_long` and `OrderTools_findOrder_String`, and the manifest handed to the AI is generated from the descriptions. A tool may return anything; what the AI is handed is its `toString()`, so return something which reads as an answer. A record does nicely, a bare `true` does not. Tools are listed in a stable order, so that the manifest is identical from one call to the next and the provider's prompt cache keeps hitting.

The declaring class must be `public`, and `@AIToolParam` needs an explicit `name` unless you compile with `-parameters`. Both are checked when the tools are registered, not when the AI first calls one. An injected bean is a container proxy whose methods carry no parameter names of their own, so the class behind the proxy is what gets scanned; hand `ToolRegistry.newBuilder().add(group, declaringClass, instance)` the class explicitly if your container's proxies are not recognized.

Providers differ in how strictly they enforce the schema, so whatever shape the AI puts its arguments in is accepted as long as it unambiguously carries a name and a value.

### Declaring Tools Programmatically

`ToolRegistry` is the programmatic counterpart of the `@AITool` annotation, in the same way `AIConfig` is the programmatic counterpart of the `@AI` qualifier. A method reference cannot carry a name, a description or its parameter names, as those are erased, so state them:

```java
ToolRegistry tools = ToolRegistry.newBuilder()
    .add("FIND_ORDER_BY_ID", "Looks up a single order by id", orders::findById, ToolParam.of(long.class, "orderId", "The order id"))
    .add("LIST_OPEN_ORDERS", "Lists all open orders", orders::listOpen) // no arguments to describe
    .add(shippingTools)                                                 // mixes fine with annotated objects
    .build();

AIService agent = aiService.withTools(tools);
```

A tool taking one or two parameters keeps their Java types, so the lambda receives a real `Long` rather than a string. For three or more, pass the parameters as a list and a function taking an `Object[]`, whose values arrive in the order you declared them.

### Grouping Tools

The unit of grouping is the object you hand over, and `withTools` is varargs, so a class is a toolset. For subsets which cut across classes, such as read-only versus mutating, tag the methods with your own annotation declared as an `@AIToolGroup`:

```java
@AIToolGroup
@Retention(RUNTIME)
@Target(METHOD)
public @interface ReadOnly {}
```

```java
AIService tier1      = aiService.withTools(ReadOnly.class, orderTools); // OrderTools_findOrder only
AIService supervisor = aiService.withTools(orderTools, shippingTools);  // everything
```

Narrowing applies to the generated response schema rather than to a check afterwards, so `tier1` cannot name `OrderTools_refund` at all: the token is not in the grammar the model decodes against.

### Authorizing Tool Calls

Grouping decides which tools exist, not which data they may return. The AI picks the arguments, and it picks them from everything it has read: the question, and every tool result before it. An `orderId` is therefore a value which arrived from outside, exactly like a request parameter, and a lookup by id or by email which does not check who is asking lets any caller read any caller's data. Keep that check in the service the tool delegates to rather than in the tool itself, so that it holds for the backing bean and the REST resource too, and let it return nothing rather than throw, so that the AI reports "no order found" instead of confirming that the order exists:

```java
@ApplicationScoped
public class OrderService {

    @Inject
    private SecurityContext security;

    @Inject
    private OrderRepository repository;

    public Optional<Order> findById(long orderId) {
        return repository.findById(orderId).filter(order -> mayRead(order.getCustomer().getEmail()));
    }

    public List<Order> listByEmail(String email) {
        return mayRead(email) ? repository.findByEmail(email) : emptyList();
    }

    @Transactional
    public Refund refund(long orderId) {
        if (!security.isCallerInRole("SUPPORT")) {
            throw new SecurityException("Refunding requires the SUPPORT role.");
        }

        return repository.refund(orderId);
    }

    private boolean mayRead(String email) {
        var caller = security.getCallerPrincipal();
        return security.isCallerInRole("SUPPORT") || (caller != null && caller.getName().equalsIgnoreCase(email));
    }

}
```

The security context is in scope because the synchronous `chat` methods invoke tools on the calling thread, as described under [bounding the loop](#bounding-and-observing-the-loop). The role check is programmatic because `@RolesAllowed` is enforced by the EJB container only; nothing applies it to a CDI bean. Dropping a parameter the caller has no business choosing is stronger still: a `listMyOrders()` deriving the email from the principal leaves nothing in the schema to aim at, and the email-taking variant then belongs in a support-only tool group.

### Bounding and Observing the Loop

The tool call cap bounds latency and spend, and defaults to five. The turn after the last permitted call is offered no tool at all: the schema for that turn enumerates only the answer, so a model which keeps reaching for tools is denied the tokens to name one rather than merely asked to stop, which is what a weaker model ignores. `AIToolIterationException` is therefore the backstop for a provider which does not enforce the schema, and means the conversation is not converging rather than that a tool failed. On the typed methods the cap forces the typed answer instead of throwing, as the call which produces it carries your own schema and offers no tool to begin with. An observer receives every tool call after it ran, which is the audit point for logging and metrics. It cannot gate a call, as the tool has already executed by then; see [owning the loop](#owning-the-loop) when you need approval before a tool runs:

```java
AIService agent = new ToolCallingAIService(aiService, ToolRegistry.of(orderTools), 4, invocation -> {
    if (invocation.hasFailed()) {
        log.warn("Tool {} failed: {}", invocation.toolName(), invocation.failure().getMessage());
    }
    else {
        log.info("Tool {} called with {}", invocation.toolName(), invocation.arguments());
    }
});
```

A tool which throws, or whose arguments cannot be converted, is reported back to the AI rather than aborting the call, so it can correct itself and try again. This self-correction is the main thing the loop buys you over a single call. An `Error` is not reported back but rethrown, as it is not something the AI can work around. What the AI is told about a failure is deliberately bounded: an argument it got wrong is quoted back verbatim, while an exception thrown by the tool itself is reduced to "the call did not complete" and logged, so that a stack trace, a SQL error or a constraint message cannot reach the AI and be repeated to a user. The observer still receives the exception.

The synchronous `chat` methods invoke tools on the calling thread, so they observe its transaction, scope and security context. The asynchronous ones invoke them on whichever thread completes the provider call, where none of that applies — use the synchronous ones for tools which touch a database or a scoped bean.

Two things to know before pointing this at a memory-enabled `ChatOptions`. Every turn is a chat call of its own, so each tool call and result lands in the conversation history, and one tool-heavy question can fill the sliding window; give the loop its own options when the history should hold only questions and answers. And whatever a tool returns is fed to the AI as text, so a tool returning user-supplied data — an order note, a ticket body — is an indirect prompt-injection channel like any other untrusted input. Keep tools which act on the world out of the set, and let a human confirm what the AI proposes.

Compose with the resilience decorators in this order, so that a retry re-attempts a single provider call rather than replaying the whole loop and every side effect it already caused:

```java
AIService agent = new RetryingAIService(aiService).withTools(orderTools);
```

Tool use and a typed result each want the one response schema a provider call carries, so they take a turn each. The loop runs on its own schema, and the turn which would have answered is asked again for your type instead, with every tool result still in front of it:

```java
public record Delivery(String carrier, LocalDate estimated) {}

Delivery delivery = agent.chat("Where is order 42?", Delivery.class);
```

That costs one extra call, and only when you ask for a type: five tool calls take six provider calls, and seven when the conversation has memory, as the answer it records is then a call of its own. Streaming cannot be combined with tools at all, as the tool the AI picks is only known once its reply is complete, so `chatStream(...)` on a tool calling service throws `UnsupportedOperationException` rather than quietly answering without the tools.

Other limits: the AI calls one tool per turn rather than several at once, there is no way to force a specific tool, and arguments are converted from strings rather than typed per tool on the wire. Reach for LangChain4J or Spring AI when you need native function calling with per-tool argument schemas.

### Owning the Loop

The loop above runs to completion inside `chat(...)`, which leaves three things out of reach: approving a tool call *before* it runs, suspending a run and resuming it elsewhere, and using a different model for different turns. The observer cannot do the first, as it is notified once the tool has already executed.

Writing the loop yourself gets all three, at the cost of the prompt handling the built-in one does for you. Structured outputs are enforced provider-side, so the model's choice is reliably parseable:

```java
public enum Action { FIND_ORDER, REFUND, ANSWER }

public record AgentStep(Action action, Map<String, String> arguments, Optional<String> answer) {}

public String handle(String question, ChatOptions options) {
    String input = question;

    for (int iteration = 0; iteration < MAX_ITERATIONS; iteration++) {
        AgentStep step = ai.chat(input, options, AgentStep.class);

        if (step.action() == ANSWER) {
            return step.answer().orElseThrow();
        }

        if (needsApproval(step)) {
            approvals.save(ticketId, options.toJson()); // Park it; no thread and no session is held.
            return "Escalated for approval.";
        }

        input = "TOOL RESULT (%s): %s".formatted(step.action(), execute(step));
    }

    throw new IllegalStateException("Did not converge within " + MAX_ITERATIONS + " turns");
}
```

Because the conversation lives in `ChatOptions` rather than in the service, the parked run resumes in another request, on another node:

```java
ChatOptions resumed = ChatOptions.fromJson(approvals.load(ticketId));
String reply = ai.chat("The supervisor approved. Send the reply.", resumed);
```

The same shape lets a cheap model pick the tool and a capable one write the final answer, by calling a different `AIService` per turn.

What you give up is everything `@AITool` does for you: the manifest is prose which drifts from the methods it describes, arguments arrive as strings you convert and validate yourself, and the instructions which keep a weaker model from re-calling a tool it already called, or from spending its last turn on another call, are yours to write. Prefer `withTools(...)` unless you need to interrupt the loop.

## Where OmniHai Fits

| Library | Analogy |
|---------|---------|
| **LangChain4J** | Full kitchen with every appliance |
| **Spring AI** | Full kitchen, Spring-branded appliances |
| **Jakarta Agentic AI** | Kitchen building code, for the order of the steps |
| **OmniHai** | Sharp chef's knife - does a few things very well |

OmniHai is a utility library, not a framework.
It covers the provider call itself - chat, text analysis, web search, images, audio, video, tool use, usage and cost - across ten providers behind one API, with JSON-P as its only hard dependency.
Embeddings, RAG, vector stores and agent orchestration are out of scope by design; reach for LangChain4J or Spring AI when you need those.
Jakarta Agentic AI standardizes the layer above this one, so the two compose rather than compete: its `@Action` methods can call an OmniHai `AIService`.

See [OmniHai vs LangChain4J vs Spring AI vs Jakarta Agentic AI](https://github.com/omnifaces/omnihai/blob/main/COMPARISON.md) for the feature-by-feature tables, footprint numbers and a when-to-choose-each guide.


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
- [Blog post: OmniHai 1.2: OmniHai finds its voice](https://balusc.omnifaces.org/2026/02/omnihai-finds-its-voice.html)
- [Blog post: OmniHai 1.3: OmniHai goes online](https://balusc.omnifaces.org/2026/03/omnihai-goes-online.html)
- [Blog post: OmniHai 1.4: OmniHai counts the cost](https://balusc.omnifaces.org/2026/04/omnihai-counts-cost.html)
- [Blog post: OmniHai 1.5: OmniHai grows a backbone](https://balusc.omnifaces.org/2026/07/omnihai-grows-backbone.html)
- [Blog post: OmniHai 1.6: OmniHai gets hands](https://balusc.omnifaces.org/2026/08/omnihai-gets-hands.html)
- [Blog post: OmniHai 1.7: OmniHai gets motion](https://balusc.omnifaces.org/2026/08/omnihai-gets-motion.html)

## Credits

This README is ~90% generated by [Claude Code](https://claude.com/product/claude-code) :)


