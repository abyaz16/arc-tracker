# Arc Tracker — Android

Native Kotlin + Jetpack Compose. A personal operating system for tasks, habits, goals and focus.

The matching **web app** lives at `E:\Tracker` (React + Vite) and shares this design system, domain model and insight rules. Full architecture docs are in `E:\Tracker\docs\`.

The interface is a soft-pastel system: a warm off-white canvas, white slab cards with no borders, and four tints that carry meaning rather than decoration — **sky** for what you are looking at now, **sage** for settled, **butter** for the one action, **coral** for attention. Text is the same near-black on every tint, so colour never costs legibility. Navigation is a floating pill of circles over the content, with the active one filled in butter.

**Reminders** are real local notifications, off until you switch them on. A master switch plus separate ones for tasks and habits live in Settings; a habit carries its own reminder time, set when you edit it. Built on `AlarmManager` and `NotificationManager` — no WorkManager, no dependency added. The decision of *what* to remind about lives in `domain/Reminders.kt` as a pure function so it can be tested on the JVM; the `notify/` package only carries it out. **How it arrives** is a choice, because the two are genuinely different things. *Notification* is quiet, needs no extra permission, and rides an inexact alarm so it can land a few minutes late. *Alarm* rings with the device's alarm sound, vibrates, and fires on the minute — which on Android 12+ needs the "alarms and reminders" permission, granted in system settings. Notification is the default: an app that starts ringing before being asked to is an app you uninstall. If exact alarms are not permitted, Alarm style still rings but degrades to inexact rather than going silent.

**Settings** is reached from the avatar in the top bar: your name, wake and wind-down times, theme, first day of the week, what the database holds, and starting over. It offers only what it actually changes — there is no reduced-motion switch, because honouring one would mean gating every animation in the app, and a toggle that does nothing is worse than none.

**A new install starts empty, not seeded.** Demo data on first run makes every number on every screen a fiction until you notice and clear it, and an insight engine reporting on invented tasks is worse than one reporting nothing. Insights draws nothing derived until there is something to derive it from, and offers the demo set from its empty state.

**Light and dark** are both first-class. The button in the top bar steps System → Light → Dark → System; the choice is stored in `Settings.theme` and survives a restart, and "System" follows the device. Every text-on-background pairing in the palette clears WCAG AA (4.5:1) in *both* themes — including the two that invert, the toast and the active nav circle, which is why `accentOnInverse` exists as a separate token from `accent`.

```bash
gradlew assembleDebug        # -> app/build/outputs/apk/debug/app-debug.apk
gradlew installDebug         # to a connected device or emulator
gradlew testDebugUnitTest    # 50 domain tests
```

`minSdk 26` (Android 8.0) so `java.time` is available without core-library desugaring.

---

## Zero added dependencies

This app uses only what the template already had: Compose BOM, Activity Compose, Material3, core-ktx. Nothing else was added.

| Need | Usual answer | What this does instead | Why |
|---|---|---|---|
| Reminders | WorkManager | `AlarmManager` + a `BroadcastReceiver`, rebuilt on boot | Framework APIs; WorkManager is a dependency and overkill for a clock alarm |
| Persistence | Room, or kotlinx.serialization | Hand-written codec over `org.json`, one file in `filesDir` | Schema is small and stable; avoids a KSP/plugin setup and its version coupling |
| State | ViewModel + StateFlow | Process-singleton `Store` holding Compose `mutableStateOf` | No lifecycle artifacts in the catalog; survives rotation without one |
| Navigation | navigation-compose | `AnimatedContent` over a tab string | Five tabs and a sheet stack don't need a routing library |
| Icons | material-icons-extended | `PathParser` over the same SVG path data as the web app | A few KB instead of a few MB, and the two clients look identical |
| Charts | Vico, MPAndroidChart | `Canvas` drawing in `ui/Charts.kt` | Full control over the design system's chart rules |
| Async | coroutines artifacts | `Handler` debounce + single-thread executor for file writes | Nothing to resolve, nothing to conflict |

The result is a build with no version-resolution risk and an 11.6 MB debug APK.

---

## Structure

```
app/src/main/java/com/example/mytracker/
├─ MainActivity.kt          entry point
├─ App.kt                   nav shell, floating tab bar, sheet routing, toasts
├─ data/
│   ├─ Models.kt            domain model
│   ├─ Json.kt              defensive org.json codec
│   ├─ Store.kt             single source of truth + debounced atomic writes
│   └─ Seed.kt              demo data
├─ domain/
│   ├─ Dates.kt             the ONLY calendar arithmetic (java.time)
│   ├─ Stats.kt             streaks, rollups, goal progress, priority score
│   └─ Insights.kt          the insight engine and its guards
├─ ui/
│   ├─ theme/               Color.kt · Type.kt · Theme.kt (tokens, no Material You)
│   ├─ Icons.kt             monoline set via PathParser
│   ├─ Components.kt        Card, StatTile, Chip, Segmented, Ring, Capsule, Bar, buttons
│   ├─ Charts.kt            Sparkline, BarChart, LineChart, RankedBars, Heatmap
│   └─ TaskRow.kt           swipe-to-complete / swipe-for-actions
└─ screens/
    ├─ HomeScreen.kt  TasksScreen.kt  HabitsScreen.kt
    ├─ GoalsScreen.kt InsightsScreen.kt FocusScreen.kt
    └─ Sheets.kt            all bottom sheets
```

Data lives in `filesDir/arc.json`, written on a 200ms debounce via write-then-rename so a crash mid-write cannot truncate it. Nothing leaves the device; the app requests no permissions and makes no network calls.

---

## Decisions worth knowing

- **Dates.** A "day" is a local `yyyy-MM-dd` String, an instant is epoch millis, and all arithmetic goes through `domain/Dates.kt` using `java.time`. Tested across both DST boundaries.
- **Streaks are never stored** — always derived from immutable habit logs, so backfilling recomputes history correctly.
- **"X times a week" habits** are measured against the weekly quota, stop asking once it's met, have no missed days, and count streaks in weeks (`3w` in the UI).
- **Derived goals** compute progress from habit logs or completed tasks; there is nothing to update by hand.
- **Focus timing is wall-clock**, recomputed every tick rather than accumulated, so backgrounding or doze cannot make it drift.
- **No Material You.** Dynamic colour would let the wallpaper recolour charts, breaking the rule that a colour always means the same thing.
- **A card tint is a meaning, not a mood.** `CardTone` has six values and each is used for one thing; `Tint` bundles the four shades (base / soft / deep / ink) a family needs so a card, its track, its arc and its text never drift apart.
- **Priority is one palette, declared once** (`ui/TaskRow.kt: priorityColor`) and always drawn in the same order, so the ranking still reads for anyone who cannot separate the hues.

---

## What this build deliberately does not do

**Screen time, app usage, app blocking and sleep are absent — not faked.** There is no simulated data anywhere in this app.

Adding them for real means:

| Feature | What it needs | Reality |
|---|---|---|
| Screen time, per-app usage, launch/unlock counts | `UsageStatsManager` + `PACKAGE_USAGE_STATS` (a Settings toggle, not a runtime dialog) | Genuinely works on Android, and is the platform's strength |
| App blocking | — | **No consumer Android API blocks another app.** `DevicePolicyManager.setPackagesSuspended` needs device-owner; an AccessibilityService risks Play removal. The honest approach is a foreground service polling usage plus a full-screen interruption the user can dismiss — *interrupting*, not *enforcing* |
| Sleep | Health Connect `SleepSessionRecord` + `READ_SLEEP` | Works, but only if a watch or another app writes the data; manual entry must stay the baseline |

Rather than ship a convincing-looking fake, the Insights screen states plainly what the build cannot see. `E:\Tracker\docs\04-platform-capabilities.md` has the full analysis, including the inverse on iOS — where blocking genuinely works at OS level and screen-time figures cannot be read back into the app at all.

Focus sessions record leaving the app as an interruption, which is the honest signal available without any special permission.

---

## Tests

`app/src/test/.../DomainTest.kt` — 20 tests over date arithmetic across DST, streak derivation, the weekly-quota model, seed coherence, derived goal progress, rollup invariants, priority ordering, and the insight engine's guards (no causal language, no output below the minimum sample, nothing at all from an empty database).
