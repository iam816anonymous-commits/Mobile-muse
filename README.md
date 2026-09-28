# LocalAgent

LocalAgent is an autonomous Android Accessibility Service agent targeting API levels 26–28 (Android 8/9).

## Features
- **Launcher Activity Controller**: Lightweight launcher (`MainActivity`) inheriting from `android.app.Activity` with programmatic UI layout.
- **Accessibility Service**: Implements `LocalAgentService` with `canRetrieveWindowContent` and `canPerformGestures`.
- **Content Scraping & Result Extraction**: Monitors screen text stabilization (1500ms timeout) via `ContentScraper` and logs structured output via `Log.i("LocalAgentResult", resultText)`.
- **Broadcast Result Callbacks**: Sends system broadcast `com.localagent.GOAL_COMPLETED` with string extras `goal_text`, `status` ('SUCCESS'/'FAILURE'), and `result_data`.
- **State Management & Circuit Breaker**: `TaskStateManager` enforces task tracking and a strict 15-action circuit breaker limit.
- **Safety Kill Switch & Emergency Abort**:
  - Broadcast receiver (`com.example.localagent.ACTION_KILL_SWITCH`).
  - Double-pressing `KEYCODE_VOLUME_DOWN` within 500ms.
  - Tapping the 32dp floating HUD overlay.
- **Zero-Login AI Bridge & Serializer**: Lightweight `HttpURLConnection` wrapper sending compact JSON screen node representations to Gemini / external endpoints.
- **Offline Rule Graph & Caching**: Screen fingerprinting (`ScreenHasher`) and rule graph (`RuleLedger` -> `local_rules.json`) enabling 100% offline action replay.
- **Reliable Gestures & Fallback**: `GestureExecutor` for synthetic taps/swipes with automatic bounding-center coordinate fallback when `performAction(ACTION_CLICK)` fails.
- **Memory Optimization**: Memory pressure `onTrimMemory` handler, 7-level depth capping, invisible node filtering, and strict `.recycle()` calls on all traversed `AccessibilityNodeInfo` objects.

## First-Time Setup

1. **Launch App**: Open `LocalAgent` from the Android app drawer/home screen.
2. **Enable Accessibility Service**:
   - Tap **Open Accessibility Settings** in the controller screen.
   - Locate and enable `LocalAgent` under Installed Services.

## Real-Time Output Monitoring via ADB Logcat

To view extracted answers and agent action logs in real-time:
```bash
adb logcat -s LocalAgentResult:I
```

## Command-Line Automation via ADB Shell

You can dynamically trigger goals and emergency stops on a connected device or emulator via ADB shell broadcasts:

### 1. Execute Goal Broadcast
```bash
adb shell am broadcast -a com.localagent.EXECUTE_GOAL --es goal_text "Ask Gemini about quantum computing"
```
```bash
adb shell am broadcast -a com.localagent.EXECUTE_GOAL --es goal_text "Search latest tech news on Chrome"
```
```bash
adb shell am broadcast -a com.localagent.EXECUTE_GOAL --es goal_text "Play Kotlin Android tutorial on YouTube"
```

### 2. Terminal One-Liner (Send Goal and Monitor Output)
```bash
adb shell am broadcast -a com.localagent.EXECUTE_GOAL --es goal_text "Search latest space news on Chrome" && adb logcat -s LocalAgentResult:I
```

### 3. Emergency Kill Switch Broadcast
```bash
adb shell am broadcast -a com.example.localagent.ACTION_KILL_SWITCH
```

## Building the Debug APK
```bash
./gradlew assembleDebug
```
Output APK location:
`app/build/outputs/apk/debug/app-debug.apk`

## Running Tests
```bash
./gradlew test
```
