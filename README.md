# LocalAgent

LocalAgent is an autonomous Android Accessibility Service agent tailored for API levels 26–28 (Android 8/9, low-RAM hardware such as the Tecno Camon i Click with 4 GB RAM).

## Current Capabilities

### 1. 100% Offline Multi-Tier Fallback Execution Engine
- **Layer 1 (Intent Contract Fast-Path):** Intercepts Alarm, Clock, and Share tasks (`AlarmClock.ACTION_SET_TIMER`, `AlarmClock.ACTION_SET_ALARM`, `Intent.ACTION_SEND`) using native system intent contracts with `EXTRA_SKIP_UI = true`.
- **Layer 2 (Dynamic App Indexer & Launcher):** Dynamically indexes launcher apps via `PackageManager.queryIntentActivities`, applies fuzzy package matching, caches to `/Download/LocalAgent/app_capabilities.json`, and launches target packages with `FLAG_ACTIVITY_REORDER_TO_FRONT`.
- **Layer 3 (Universal Node Crawl & 3-Level Action Engine):**
  - **3-Level Tap (`UniversalTapEngine`):** Direct `ACTION_CLICK` $\rightarrow$ Parent climbing up to 4 levels $\rightarrow$ Physical coordinate bounds tap.
  - **3-Level Text Injector (`UniversalTextInjector`):** `ACTION_SET_TEXT` $\rightarrow$ Clipboard `ACTION_PASTE` $\rightarrow$ 600ms long-press + System Context Menu 'Paste' tap.
  - **Generic UI Primitives (`GenericUIOperator`):** Keyword matching, symbol sequence tapping, action confirmation, and leaf text harvesting.
- **Layer 4 (Universal Scroll & Reveal Engine):** `ACTION_SCROLL_FORWARD` on scrollable containers with physical coordinate swipe fallback (`UniversalScrollEngine`).
- **Layer 5 (Generic Spatial Grid Matrix):** Normalized 18:9 (720x1440) UI UX anchors (`FAB_ADD`, `TOP_SEARCH`, `SUBMIT_ENTER`, `NAV_BACK`, `CENTER_ACTION`) for Canvas/Flutter/Game layouts.

### 2. Bedrock System Defense & Hardware Integration
- **HiOS & Low-RAM Memory Defense:** `ServiceProtector` creates high-priority foreground notification channel `localagent_core` with `PRIORITY_HIGH` and `startForeground(1001, notification)` to prevent task freezing on Tecno HiOS 3.3 / Android 8.1.
- **Crash Recovery & Watchdog:** `CrashHandler` intercepts uncaught exceptions, writes stack dumps to `/Download/LocalAgent/crash_dump.log`, clears execution locks, and gracefully restarts `MainActivity`.
- **Accessibility Watchdog:** Companion singleton `LocalAgentService.instance` watchdog detects zombie service states in `MainActivity.onResume()` and prompts single-tap re-binding.
- **Dual Flashlight Hardware Controller:** Enumerates camera IDs by lens facing to toggle rear quad-LED flash and front dual-LED flash independently.
- **Wave-to-Listen Proximity Sensor Hook:** `HardwareSensorManager` registers proximity sensor events (hover < 3 cm for > 800ms) to trigger hands-free voice commands via `VoiceEngine`.

### 3. Cyberpunk Mission Control Deck (`MainActivity`)
- Native `android.app.Activity` dashboard with 3 top tabs (`[COMMAND]`, `[MEMORY]`, `[SYSTEM]`), RAM budget gauge, persistent storage controls, 1-tap permission shortcuts, and a 220dp auto-scrolling telemetry terminal (`tvCompactTerminalLog`).

---

## Limitations

1. **Target API Level Range:** Optimized for Android 8.0–9.0 (API 26–28). Features requiring Android 10+ scoped APIs require compatibility shims.
2. **Screen Coordinate Normalization:** Spatial grid anchors default to 18:9 HD+ (720x1440) screen aspect ratios. Devices with radically different aspect ratios (e.g. 4:3 tablets) rely on DOM node tree traversal before coordinate fallbacks.
3. **HiOS Vendor Customizations:** Battery optimization and auto-start whitelist shortcuts require initial user confirmation in HiOS Phone Master settings.

---

## Future Scope

1. **On-Device Small Language Model (SLM) Integration:** Integrate local quantised GGUF / ONNX neural models for complex multi-step reasoning without cloud API dependencies.
2. **Adaptive Local Graph Learning:** Automatically build visual UI transition graphs saved in `/Download/LocalAgent/local_rules.json` that generalize across app version updates.
3. **Multi-Language Offline Speech Engine:** Expand `VoiceEngine` with offline Vosk/Whisper.cpp STT models for fully private voice command processing.
4. **Cross-Device Spatial Calibration:** Dynamically query display metrics at runtime to adapt spatial grid coordinate anchors across arbitrary tablet and foldable display aspect ratios.

---

## Setup & Execution

### 1. Build Debug APK
```bash
./gradlew assembleDebug
```
Output location: `app/build/outputs/apk/debug/app-debug.apk`

### 2. Run Unit Tests
```bash
./gradlew test
```

### 3. ADB Command Line Automation
```bash
# Execute Goal
adb shell am broadcast -a com.localagent.EXECUTE_GOAL --es goal_text "calculate 45 * 8"

# Emergency Abort
adb shell am broadcast -a com.example.localagent.ACTION_KILL_SWITCH
```
