# ImageAnalyzer

An experimental Android app for exploring what **edge AI** (on-device AI) can actually do.

You pick a photo, and the app analyses it entirely on the device to judge whether it looks safe to post publicly on social media — no server round trip, no API key.

> **This is a proof of concept, not a product.** The goal is to find out what an on-device model can manage, how long you wait for it, and where it falls down — not to build a content moderation tool you can trust.

## What it does

- Picks an image through the Android Photo Picker (no storage permission needed)
- Decodes and displays it, with file name, dimensions and size
- Analyses it on device with **Gemini Nano** at the press of a button, returning a fixed-format report:

```
CONTENT:      one sentence describing what is in the photo
PEOPLE:       how many recognisable faces
VISIBLE TEXT: text that could identify a person or place
CONCERNS:     privacy or sensitivity concerns
VERDICT:      SAFE / REVIEW / AVOID - short reason
```

- Shows download progress the first time the model is needed
- Logs decode time, model status, input token count and inference latency through Timber

## Device requirements

| | |
|---|---|
| Device | **A physical device with Gemini Nano support** |
| minSdk | 35 |
| Emulator | **Not supported** |
| Bootloader | Must not be unlocked |

Supported devices include the Pixel 9/10/11 series, Samsung Galaxy S25/S26 series, and selected Honor, OnePlus, OPPO, vivo and Xiaomi models. See the [ML Kit GenAI docs](https://developers.google.com/ml-kit/genai) for the full list.

On an unsupported device the app still picks and displays images fine, but the analysis reports that Gemini Nano is unavailable.

Developed and tested against a Pixel 11 Pro Fold.

## What you *don't* need

Worth spelling out, because it is easy to assume otherwise:

- **No API key**
- **No Firebase project**, no `google-services.json`
- **No runtime permission requests** — the library merges in `INTERNET`, `ACCESS_NETWORK_STATE` and `aicore.service.BIND_SERVICE`, but these are all normal permissions, so nothing prompts the user
- **No per-use cost** — inference runs on the device's own hardware

The model is managed and updated by the system AICore service, and is never bundled into the APK.

## Architecture

MVVM with Koin.

```
data/
  AnalyzableImage.kt                 Model: an inference-ready Bitmap plus file details
  ImageRepository.kt                 Interface + ContentResolver decoding implementation
  ShareCheckRepository.kt            Interface + ShareCheckStatus
  GeminiNanoShareCheckRepository.kt   Gemini Nano implementation
di/
  AppModule.kt                       Koin module
ui/home/
  HomeUiState.kt                     UI state
  HomeViewModel.kt                   StateFlow + viewModelScope
  HomeScreen.kt                      HomeScreen (stateful) / HomeContent (stateless)
```

A few deliberate choices:

**Inference runs in `viewModelScope`**, not `rememberCoroutineScope()`. Rotating the screen must not throw away an inference that has been running for several seconds.

**`ShareCheckRepository` is an interface.** Swapping in a MediaPipe or LiteRT backend means changing one binding in `AppModule.kt`; the ViewModel and UI are untouched.

**The prompt is hardcoded** in `GeminiNanoShareCheckRepository.PROMPT`. Users cannot edit it and there is no text field for it — the app asks the question, the user only supplies the image.

**The ViewModel never touches Compose types.** It holds a `Bitmap`; the conversion to `ImageBitmap` stays in the composable.

**`HomeContent` is stateless** so `@Preview` can render it without a Koin graph.

## Build and run

```bash
./gradlew :app:assembleDebug

ADB=~/Library/Android/sdk/platform-tools/adb
$ADB install -r app/build/outputs/apk/debug/app-debug.apk
```

## Reading the logs

Timber is only planted in debug builds. Filtering by PID is the simplest approach:

```bash
$ADB logcat --pid=$($ADB shell pidof -s com.weiting.imageanalyzer)
```

The lines worth watching (shape only — these are not measured values):

```
I  decoded to <W>x<H> (ARGB_8888, <N> KB) in <N> ms
I  AICore feature status = AVAILABLE
I  input = <N> tokens (device limit <N>)
I  inference finished in <N> ms, 1 candidate(s), <N> chars, finishReason=STOP
```

`input = N tokens` is the **fixed prefill cost**, driven mostly by the image rather than the prompt. It is the latency floor that shortening the answer cannot reduce.

`finishReason=MAX_TOKENS` means the answer was cut off by `maxOutputTokens` and the limit needs raising.

AICore's own logs can also be useful:

```bash
$ADB logcat -s AiCore:V ML:V
```

## Known limitations

- **This is not content moderation.** A small on-device model both misses things and raises false alarms. Treat the output as something to make the user think, not a verdict to act on. The ML Kit GenAI additional terms also put responsibility for safety squarely on the developer.
- **English output only.** Producing an English and Traditional Chinese report in a single inference pass was tried; Nano's Chinese quality and format stability were not good enough, so it went back to English.
- **`genai-prompt` is still beta** (`1.0.0-beta4`) and is not covered by any SLA or deprecation policy.
- **The image is lost if the process is killed.** The ViewModel survives rotation, but there is no `SavedStateHandle` persistence — and the Photo Picker grant would not survive process death anyway.
- **The first analysis waits on a model download**, and it is a substantial one.

## Why these technologies

Recorded so the research does not have to be repeated:

**Gemini Nano via the ML Kit GenAI Prompt API** — the current choice.
Lets you ask your own question about an image, with the model managed by the system, no growth in APK size and no key to manage. The cost is limited device support and beta status.

**ML Kit GenAI Image Description API.**
Returns one short caption, English only. It cannot make a suitability judgement — it will not tell you that an ID card is visible in the photo.

**MediaPipe LLM Inference with Gemma 3n.**
Full control over the prompt and the model, and no dependency on AICore, so it reaches far more devices. The cost is owning a multi-gigabyte model file (`adb push` during development, self-hosted download in production). This is the next stop if the device restriction becomes the blocker.

**LiteRT (formerly TensorFlow Lite).**
MediaPipe runs on top of it; they are not competing choices. Using it directly only makes sense when bringing your own trained model, and means handling tensor pre- and post-processing yourself.

## Next steps

- Switch to `generateContentStream()`. Total time is unchanged, but text appearing progressively feels far faster.
- Move to structured output with `@Generable` / `@Guide` annotations (requires KSP and `genai-schema-compiler`) so `VERDICT` can drive the UI directly.
- Add a Koin `checkModules()` test to validate the dependency graph at unit-test time instead of crashing at startup.
- Measure whether lowering `MAX_EDGE_PX` actually changes `input = N tokens`, to find out if image size really affects latency.
