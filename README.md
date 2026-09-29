# LocalAgent: Autonomous On-Device Android AI Agent Framework

LocalAgent is a production-grade, 100% offline-first autonomous Android `AccessibilityService` agent written in Kotlin. It is specifically architected for **Android 8.0–9.0 (API 26–28)** on low-RAM hardware (e.g. 4 GB RAM, Helio P23, 720x1440 18:9 display hardware such as the Tecno Camon i Click running HiOS 3.3).

---

## Exhaustive File-by-File Architecture & Functionality Map

The codebase is organized into clear domain packages under `com.example.localagent`:

```
app/src/main/java/com/example/localagent/
├── LocalAgentService.kt            # Core AccessibilityService & Event Loop Controller
├── MainActivity.kt                 # Cyberpunk Mission Control Dashboard (3-Tab Activity)
├── MainApplication.kt             # Application Entry & Uncaught Exception Trap Registration
│
├── engine/                         # Autonomous Planning & UI Execution Engines
│   ├── AppAuditManager.kt         # Sequential device app audit & DOM classification loop
│   ├── AppIndexer.kt              # Dynamic launcher activity resolver & capability store
│   ├── AppLauncher.kt             # Foreground-guaranteed activity launch & UI minimize dispatcher
│   ├── AutonomousEngine.kt        # ReAct perception-action loop, timeout & loop detection
│   ├── BrowserAutomation.kt        # Dual-tier Chrome search & web chat execution engine
│   ├── DiagnosticRunner.kt        # End-to-end self-diagnostic test runner
│   ├── GenericUIOperator.kt       # High-level DOM UI primitives (keywords, sequence tap, harvest)
│   ├── LocalHeuristicEngine.kt    # On-device API-free heuristic matcher & multi-window root finder
│   ├── MotorActuator.kt           # Universal motor core (click, type, scroll, long-press)
│   ├── ObstacleDetector.kt        # System dialog / popup blocker interceptor
│   ├── PhotoViewerAutomation.kt   # Gallery / My Picture viewer, slideshow & deletion engine
│   ├── QueryFormulator.kt         # Unblocking search query generator for stalled states
│   ├── QueryPayloadSanitizer.kt   # Search query isolation regex cleaner
│   ├── RecorderAutomation.kt      # Sound recorder launch & record trigger automation
│   ├── SelfHealingResolver.kt     # Stall unblocking and self-healing action recovery
│   ├── SpatialGrid.kt             # Normalized 18:9 (720x1440) UI UX anchor matrix
│   ├── StallDetector.kt           # UI stall & fingerprint repetition detector
│   ├── TaskExecutionHub.kt        # Centralized goal dispatcher & skill router
│   ├── UniversalAppOperator.kt    # 5-layer cascading fallback task execution controller
│   ├── UniversalScrollEngine.kt   # Programmatic scroll & physical coordinate swipe fallback
│   ├── UniversalTapEngine.kt      # 3-level tap engine (direct -> parent climbing -> coordinate)
│   ├── UniversalTextInjector.kt   # 3-level text injector (SET_TEXT -> Clipboard -> Long-Press)
│   └── WebWorkflowLearner.kt      # Browser action transition recorder
│
├── gestures/                       # Low-Level Touch & Action Execution
│   ├── ActionExecutor.kt          # ACTION_CLICK with coordinate tap fallback
│   └── GestureExecutor.kt         # AccessibilityService.dispatchGesture wrapper (tap/swipe/long-press)
│
├── hud/                            # Visual On-Screen HUD & Pointer Indicators
│   ├── FloatingHudManager.kt      # 32dp floating status indicator & instant abort tap target
│   └── PointerIndicatorManager.kt # Real-time visual mouse cursor, click ripple pulse, & keystroke badge
│
├── intents/                        # System Intents & Zero-UI Hardware Controls
│   ├── AppCapabilityResolver.kt   # Dynamic intent resolver for notes, alarms, and clock apps
│   ├── CapabilityDomain.kt        # Enum constants for app domain classifications
│   ├── DeviceToolsManager.kt      # Dual flashlight (rear quad-LED / front dual-LED) & haptic control
│   ├── IntentContractBridge.kt    # Tier 1 fast-path intent contracts (AlarmClock, ACTION_SEND)
│   ├── IntentLauncher.kt          # Standard system app launchers (Chrome, YouTube, Camera)
│   └── SemanticIntentRouter.kt    # Semantic goal matching to platform intents
│
├── inventory/                      # Hardware Profiling & Wave-to-Listen Sensor Hook
│   ├── AppInventoryManager.kt     # Installed application database profiler
│   ├── EnvironmentProfiler.kt     # Hardware profiling (RAM budget, storage paths)
│   └── HardwareSensorManager.kt   # Proximity sensor 'Wave-to-Listen' hands-free voice trigger
│
├── memory/                         # Persistent Storage & Low-RAM Guardrails
│   ├── ActionRule.kt              # Action rule data class model (click, input, scroll, terminate)
│   ├── KnowledgeLedger.kt         # Answer cache store (/Download/LocalAgent/knowledge_ledger.json)
│   ├── MemoryGuard.kt             # LMK 250MB heap budget watchdog
│   ├── MemoryLedger.kt            # Step execution log recorder
│   ├── MemoryRehydrationManager.kt# Streamed JsonReader memory rehydration at boot
│   ├── RuleLedger.kt              # Offline rule transition graph (/Download/LocalAgent/local_rules.json)
│   ├── ScreenHasher.kt            # CRC32 screen fingerprint generator
│   ├── SelfReflectionEngine.kt    # Failure analysis & rule pruning engine
│   └── StorageManager.kt          # Storage directory resolver (/Download/LocalAgent/)
│
├── network/                        # Optional Cloud Reasoning Client
│   └── AiBridgeClient.kt          # Non-blocking HTTP Gemini Flash client with 4s timeout & local fallback
│
├── receiver/                       # System Broadcast Receivers & Goal Dispatching
│   ├── GoalBroadcastReceiver.kt   # External ADB broadcast trigger receiver
│   └── GoalDispatcher.kt          # Main broadcast goal receiver & execution orchestrator
│
├── routines/                       # E2E Test Routines
│   └── TestRoutines.kt            # Pre-configured test routines for Chrome, YouTube, and Camera
│
├── safety/                         # System Protection & Crash Interceptors
│   ├── CrashHandler.kt            # UncaughtExceptionHandler dumping stack traces to crash_dump.log
│   ├── KillSwitchReceiver.kt      # Immediate kill-switch broadcast receiver
│   ├── PermissionManager.kt       # Batch runtime permission requester & onboarding guide
│   └── ServiceProtector.kt        # Foreground notification channel manager (localagent_core)
│
├── scraper/                        # DOM Content Scraping
│   └── ContentScraper.kt          # Text stabilization & leaf node text harvester
│
├── serializer/                     # Token-Pruned Screen Representation
│   └── ScreenSerializer.kt        # Compact DOM node serializer (under 1500 tokens)
│
├── skills/                         # Specialized Application Skills
│   ├── CalculatorSkill.kt         # Real Calculator app automation with scientific expansion & numeric extraction
│   └── CameraSkill.kt             # Camera shutter & lens flip with 4s zombie recovery guard
│
├── state/                          # Task State Tracking
│   ├── AgentStatus.kt             # Enum statuses (IDLE, RUNNING, COMPLETED, FAILED, HALTED)
│   ├── TaskGoal.kt                # Task goal data class
│   └── TaskStateManager.kt        # Task step counter & 15-action circuit breaker
│
├── vision/                         # Multimodal Vision Bridges
│   ├── GeminiVisionBridge.kt      # JPEG compression & base64 Gemini vision analyzer
│   ├── ImageOptimizer.kt          # Bitmap scaling & recycling optimizer
│   └── LensLauncher.kt            # Google Lens intent launcher
│
└── voice/                          # Speech Synthesis & Recognition
    ├── VoiceCommandManager.kt     # SpeechRecognizer single-shot listener wrapper
    ├── VoiceEngine.kt             # Integrated TTS & STT manager
    └── VoiceSynthesizer.kt        # TextToSpeech engine wrapper
```

---

## Key Features & Capabilities

### 1. 5-Layer Universal Cascading Fallback Engine
LocalAgent uses a cascading execution pipeline that guarantees completion regardless of cloud availability:
- **Layer 1 (Intent Contract Fast-Path):** Intercepts Alarm, Clock, and Share tasks (`AlarmClock.ACTION_SET_TIMER`, `AlarmClock.ACTION_SET_ALARM`, `Intent.ACTION_SEND`) with `EXTRA_SKIP_UI = false` to visibly display running timers and alarms.
- **Layer 2 (Dynamic App Indexing & Foreground Surfacing):** Resolves target packages dynamically using `PackageManager.queryIntentActivities`, fires a `com.localagent.MINIMIZE_UI` broadcast to minimize `MainActivity`, and launches external activities with `FLAG_ACTIVITY_NEW_TASK or FLAG_ACTIVITY_CLEAR_TOP`.
- **Layer 3 (Universal Node Crawl & 3-Level Motor Actuation):**
  - **3-Level Tap (`UniversalTapEngine`):** Level 1 Direct `ACTION_CLICK` $\rightarrow$ Level 2 Parent Climbing up to 4 levels $\rightarrow$ Level 3 Physical bounds touch tap.
  - **3-Level Text Injector (`UniversalTextInjector`):** Level 1 `ACTION_SET_TEXT` $\rightarrow$ Level 2 Clipboard `ACTION_PASTE` $\rightarrow$ Level 3 Long-Press 600ms gesture + System Context Menu 'Paste' tap.
  - **Generic UI Primitives (`GenericUIOperator`):** Keyword label matching, token sequence tapping, action confirmation, and leaf node text harvesting.
- **Layer 4 (Universal Scroll & Reveal Engine):** Programmatic `ACTION_SCROLL_FORWARD` on scrollable containers with physical coordinate swipe fallback (`UniversalScrollEngine`).
- **Layer 5 (Generic Spatial Grid Matrix):** Normalized 18:9 (720x1440) UI UX anchors (`FAB_ADD`, `TOP_SEARCH`, `SUBMIT_ENTER`, `NAV_BACK`, `CENTER_ACTION`) for Canvas/Game layouts.

### 2. Visual Mouse Cursor & Keystroke Action HUD
- **On-Screen Mouse Cursor:** Renders a 42dp cyberpunk cyan arrow pointer (`PointerIndicatorManager`) positioned at exact touch coordinates during gestures.
- **Click Ripple Pulse:** Animates a yellow expanding pulse ring at touch points upon click dispatch.
- **Floating Keystroke Badge:** Displays a monospaced text badge (e.g. `⌨ 45 * 8`) near input fields in real-time during text entry.
- **Interactive Window Flags:** Declares and reinforces `FLAG_RETRIEVE_INTERACTIVE_WINDOWS`, `FLAG_INCLUDE_NOT_IMPORTANT_VIEWS`, and `FLAG_REPORT_VIEW_IDS` in `LocalAgentService.onServiceConnected()` for deep window inspection across vendor skins.

### 3. Bedrock System Defense & Hardware Tools
- **HiOS Memory Defense:** `ServiceProtector` creates high-priority foreground notification channel `localagent_core` with `PRIORITY_HIGH` and `startForeground(1001, notification)` to survive HiOS 3.3 background task freezing.
- **Uncaught Crash Handler:** `CrashHandler` intercepts unhandled exceptions, writes full stack traces to `/Download/LocalAgent/crash_dump.log`, resets execution locks, and gracefully restarts `MainActivity`.
- **Accessibility Watchdog:** Companion singleton `LocalAgentService.instance` watchdog detects zombie states in `MainActivity.onResume()` and prompts single-tap re-binding.
- **Dual Flashlight Hardware Controller:** Enumerates camera IDs by lens facing to independently toggle rear quad-LED flash and front dual-LED flash units via `CameraManager.setTorchMode()`.
- **Wave-to-Listen Proximity Sensor Hook:** `HardwareSensorManager` monitors proximity sensor events (hover < 3 cm for > 800ms + pull away) to trigger hands-free voice commands via `VoiceEngine`.

### 4. Specialized Application Automation Hub
- **Calculator (`CalculatorSkill`):** Launches real Calculator app, waits for active window package stabilization, expands scientific keypads for operators (`^`, `%`), and extracts finalized numeric displays matching `^[0-9,.]+$`.
- **Web Search (`BrowserAutomation`):** Sanitizes raw goal strings via `QueryPayloadSanitizer` and executes dual-tier search (Native Chrome `ACTION_WEB_SEARCH` $\rightarrow$ Direct Encoded URL fallback) and harvests headlines.
- **YouTube (`MediaAutomation`):** Launches `com.google.android.youtube` with search queries and clicks the first video thumbnail.
- **Notes (`NotesAutomation`):** Creates notes via `Intent.ACTION_SEND` or UI editor typing and saving.
- **Voice Recorder (`RecorderAutomation`):** Opens sound recorder and triggers recording via DOM node match or center-bottom coordinate fallback (`0.50f, 0.82f`).
- **Camera (`CameraSkill`):** Triggers photo capture with 4-second zombie state timeout guard, lens flip toggling, and dual shutter triggers (coordinate tap + hardware key injection).
- **Photo Viewer (`PhotoViewerAutomation`):** Handles image viewing, slideshow paging, toolbar reveals, and node/coordinate deletion fallbacks for Tecno HiOS gallery apps.

---

## Current System Limitations

1. **Target API Scope:** Optimized specifically for Android 8.0–9.0 (API 26–28). Running on Android 10+ requires adjustments for scoped storage and background activity launch restrictions.
2. **Spatial Coordinate Matrix:** Default coordinate anchors in `SpatialGrid` are calibrated for 18:9 HD+ (720x1440) displays. Non-18:9 aspect ratios rely on DOM node tree traversal before coordinate fallbacks.
3. **HiOS Vendor Settings:** Auto-start and Phone Master freeze whitelist shortcuts require initial 1-tap user confirmation in HiOS system settings.

---

## Future Roadmap

1. **On-Device Small Language Model (SLM):** Integrate quantised GGUF / ONNX neural models via NDK/C++ for complex multi-step reasoning without cloud API dependencies.
2. **Adaptive Local Graph Expansion:** Automatically build visual UI transition graphs saved in `/Download/LocalAgent/local_rules.json` that adapt dynamically across app version updates.
3. **Multi-Language Offline Speech Engine:** Expand `VoiceEngine` with offline Vosk/Whisper.cpp STT models for private voice command processing.
4. **Cross-Device Spatial Display Calibration:** Dynamically query display metrics at runtime to adapt spatial grid coordinate anchors across arbitrary tablet and foldable display aspect ratios.

---

## Architecture Improvement Guide

To further improve stability and performance on low-RAM hardware:

1. **Strict Node Recycling:** Always wrap `AccessibilityNodeInfo` traversals in `try ... finally { node.recycle() }` blocks to prevent memory leaks in the system accessibility framework.
2. **Asynchronous Execution:** Run all perception, node scanning, and motor execution cycles on `Dispatchers.Default` using `serviceScope.launch` to avoid main-thread ANR crashes.
3. **Re-entrant Lock Resets:** Always reset execution flags (`isProcessingGoal = false`, `AutonomousEngine.resetLocks()`) inside `finally` blocks to prevent zombie states.
4. **Persistent External Ledger Directory:** Keep persistent ledgers stored in `/Download/LocalAgent/` so learned workflow rules survive app uninstalls and re-installs.

---

## Setup & Execution Instructions

### 1. Build Debug APK
```bash
./gradlew assembleDebug
```
Output APK location: `app/build/outputs/apk/debug/app-debug.apk`

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
