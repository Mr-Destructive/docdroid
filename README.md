# DocDroid

A local Android app that uses **Cactus Needle** (26M parameters) to route natural language requests to document manipulation tools. No cloud APIs — everything runs on-device.

## How it works

```
User: "Merge report.pdf and appendix.pdf"
  ↓
Needle (26M params, Cactus CQ4, ARM NEON) → {"name":"merge_pdfs","arguments":{...}}
  ↓
Tool Harness → iText PDF merge on Android
  ↓
"Done! Merged 2 PDFs into report.pdf (14 pages, 234 KB)"
```

## Features (MVP)

| Tool | Command example |
|------|----------------|
| `merge_pdfs` | "Combine these two PDFs" |
| `split_pdf` | "Split by pages 1-5,6-10" |
| `rotate_pdf` | "Rotate page 3 by 90 degrees" |
| `extract_text` | "Get all the text from this contract" |
| `extract_images` | "Pull out the photos from this PDF" |
| `compress_pdf` | "Shrink this PDF for email" |
| `add_watermark` | "Stamp CONFIDENTIAL on every page" |
| `images_to_pdf` | "Convert these screenshots to a PDF" |

## Architecture

- **Cactus Needle** — 26M-param function-calling model, CQ4 quantized (~16MB), runs via JNI on ARM64
- **Tool Harness** — Kotlin dispatcher: JSON → tool function → result
- **Document Tools** — PDFBox Android + Android PDF APIs, native execution
- **UI** — Jetpack Compose chat interface

## Prerequisites

- Android Studio Ladybug (2024.2+) or later
- Android NDK (r26+)
- CMake 3.10+
- JDK 17+

## Build instructions

### Step 1: Build the Cactus native library

You need a Linux/macOS machine with Rust installed.

```bash
# Clone and set up Cactus
git clone https://github.com/cactus-compute/cactus.git
cd cactus
source ./setup

# Build for Android (arm64-v8a)
cactus build --android
```

This produces `android/libcactus_engine.so`.

### Step 2: Copy native library to DocDroid

```bash
cp cactus/android/libcactus_engine.so docdroid/app/src/main/jniLibs/arm64-v8a/
```

### Step 3: Download the Needle CQ4 model

```bash
# Option A: Using Cactus CLI
cactus download Cactus-Compute/needle

# Option B: From HuggingFace directly
pip install huggingface_hub
python3 -c "
from huggingface_hub import hf_hub_download, snapshot_download
snapshot_download('Cactus-Compute/needle', allow_patterns=['needle-cq4*'], local_dir='.')
"
unzip needle-cq4.zip -d docdroid/app/src/main/assets/needle/
```

The `assets/needle/` directory should contain:
```
assets/needle/
├── model.bin
├── config.json
└── ...
```

### Step 4: Build the APK

```bash
cd docdroid
./gradlew assembleDebug
```

### Step 5: Install on device

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

## Project structure

```
app/src/main/java/com/docdroid/
├── DocDroidApp.kt           ← Application init (PDFBox)
├── MainActivity.kt           ← Entry point
├── engine/
│   ├── CactusJNI.kt          ← JNI bridge to libcactus_engine.so
│   └── NeedleEngine.kt       ← High-level Needle wrapper
├── harness/
│   ├── ToolDefinitions.kt    ← 8-tool JSON schema for Needle
│   ├── ToolRegistry.kt       ← JSON dispatcher
│   ├── ToolResult.kt         ← Success/Error sealed class
│   └── tools/
│       ├── PdfMerge.kt
│       ├── PdfSplit.kt
│       ├── PdfRotate.kt
│       ├── PdfTextExtract.kt
│       ├── PdfImageExtract.kt
│       ├── PdfCompress.kt
│       ├── PdfWatermark.kt
│       └── ImagesToPdf.kt
├── model/
│   └── Message.kt            ← Chat message data class
└── ui/
    ├── theme/Theme.kt
    ├── screens/ChatScreen.kt
    ├── components/
    │   ├── MessageBubble.kt
    │   └── InputBar.kt
    └── viewmodel/
        └── ChatViewModel.kt  ← Orchestrates: input → Needle → harness → response
```

## Adding new tools

1. Create a new class in `harness/tools/` implementing the `Tool` interface:

```kotlin
class MyNewTool(private val context: Context) : Tool {
    override suspend fun execute(argsJson: String): ToolResult {
        // Parse args, do work, return result
        return ToolResult.Success("Done!", outputPath)
    }
}
```

2. Add the tool definition to `ToolDefinitions.kt`:

```kotlin
mapOf(
    "name" to "my_new_tool",
    "description" to "What this tool does.",
    "parameters" to mapOf(...)
)
```

3. Register it in `ToolRegistry.kt`:

```kotlin
"my_new_tool" to MyNewTool(context),
```

## Performance

- Model load: ~200-500ms on modern phones
- Routing latency: ~1-5s (ARM NEON, single core)
- Tool execution: varies by operation (PDF merge ~100ms for small files)

## License

MIT
