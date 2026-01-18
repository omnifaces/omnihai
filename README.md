# OmniAI

A unified Java AI utility library for Jakarta EE applications.

## Overview

OmniAI provides a single, consistent API to interact with multiple AI providers.

## Requirements

- Java 17+
- Jakarta EE 11 (JSON-P required, CDI optional, EL optional)

## Installation

```xml
<dependency>
    <groupId>org.omnifaces</groupId>
    <artifactId>omniai</artifactId>
    <version>1.0-SNAPSHOT</version>
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

## Supported Providers

| Provider | Default Model | API Key Required | Available Models |
|----------|---------------|------------------|------------------|
| OpenAI | gpt-5-mini | [Yes](https://platform.openai.com/api-keys) | [List](https://platform.openai.com/docs/models) |
| Anthropic | claude-sonnet-4-5-20250929 | [Yes](https://platform.claude.com/settings/keys) | [List](https://platform.claude.com/docs/en/about-claude/models/overview) |
| Google AI | gemini-2.5-flash | [Yes](https://aistudio.google.com/app/api-keys) | [List](https://ai.google.dev/gemini-api/docs/models) |
| xAI | grok-4-1-fast-reasoning | [Yes](https://console.x.ai) | [List](https://docs.x.ai/docs/models) |
| Meta Llama | Llama-4-Scout-17B-16E-Instruct-FP8 | [Yes](https://llama.developer.meta.com/join-waitlist) | [List](https://llama.developer.meta.com/docs/models/) |
| Azure OpenAI | gpt-5-mini | [Yes](https://portal.azure.com) | [List](https://ai.azure.com/catalog) |
| OpenRouter | google/gemma-3-27b-it:free | [Yes](https://openrouter.ai/settings/keys) | [List](https://openrouter.ai/models) |
| Ollama | llama3.2 | No (localhost) | [List](https://ollama.com/library) |
| Custom | - | - | - |

## Quick Start

### Programmatic Configuration

```java
// Create a service instance
var service = AIConfig.of(AIProvider.ANTHROPIC, "your-api-key").createService();

// Simple chat
String response = service.chat("What is Jakarta EE?");

// Chat with options
String response = service.chat("Explain microservices",
    new ChatOptions.Builder()
        .systemPrompt("You are a helpful software architect.")
        .temperature(0.7)
        .maxTokens(500)
        .build());
```

### CDI Integration

```java
@Inject
@AI(provider = AIProvider.ANTHROPIC, apiKey = "your-api-key")
private AIService claude;

// Use EL expressions for dynamic configuration
@Inject
@AI(provider = AIProvider.OPENAI,
    apiKey = "#{initParam['com.example.OPENAI_KEY']}",
    model = "gpt-5")
private AIService gpt;

// With custom system prompt
@Inject
@AI(provider = AIProvider.GOOGLE,
    apiKey = "#{configBean.googleApiKey}",
    prompt = "You are a helpful assistant specialized in Jakarta EE.")
private AIService jakartaExpert;
```

## Features

### Chat

```java
// Synchronous
String response = service.chat("Hello!");

// Asynchronous
CompletableFuture<String> future = service.chatAsync("Hello!");
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
    new ModerationOptions.Builder()
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
    new GenerateImageOptions.Builder()
        .size("1024x1024")
        .build());
```

## Custom Providers

Implement `AIService` and use the fully qualified class name as the provider:

```java
var service = AIConfig.of("com.example.MyCustomAIService", "api-key").createService();
```

## License

[Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0)

## Links

- [OmniFaces](https://omnifaces.org)
- [GitHub](https://github.com/omnifaces/omniai)
