# Reliable background rest timer on Android

Research for [issue #7](https://github.com/TylerGimpel/GymTrackingApp/issues/7). Target: `minSdk 26`, native Kotlin/Compose, a 30 s – 5 min user-visible countdown that must fire with the app backgrounded and the screen off.

Primary sources are `developer.android.com`, `source.android.com`, AOSP javadoc on `android.googlesource.com`, and Google Play policy on `support.google.com`. Anything from a non-primary source is labelled as such. Where official pages contradict each other, both numbers are given rather than reconciled.

---

## 1. Recommendation in one paragraph

**Schedule an `AlarmManager` exact alarm as the single source of truth for when the rest ends; treat every other mechanism as decoration.** Concretely: on "start rest", compute a deadline in `SystemClock.elapsedRealtime()`, call `setExactAndAllowWhileIdle(ELAPSED_REALTIME_WAKEUP, deadline, pendingIntent)` (upgrading to `setAlarmClock()` for long rests or when the user opts into maximum reliability), and post an ongoing notification whose countdown is rendered by the system rather than by your process. When the alarm fires, a `BroadcastReceiver` posts a high-importance notification on an alarm-stream channel. The in-app Compose countdown is a *view* derived from the same deadline, never the thing that decides when the timer ends — it is recomputed from the wall clock on every resume.

This is the only design where the timer surviving does not depend on your process surviving.

---

## 2. Mechanism assessment

### 2.1 In-process timer (`CountDownTimer`, coroutine `delay`)

**Verdict: correct for the visible countdown while the app is on screen. Unusable as the timing authority.**

A backgrounded app's process is a *cached process*, and the documentation is explicit that the system kills these freely: "A cached process is one that is not currently needed, so the system is free to kill it as needed when resources like memory are needed elsewhere", and a well-running system "regularly kills the cached apps as needed" ([Processes and app lifecycle](https://developer.android.com/guide/components/activities/process-lifecycle)). The same page adds that "Starting in Android 13, an app process may receive limited or no execution time until it enters one of the above active lifecycle states."

Even with the process alive, Doze "Ignores wake locks" ([Doze and App Standby](https://developer.android.com/training/monitoring-device-state/doze-standby)), so a ticking coroutine has no guarantee the CPU stays out of suspend. A user-space tick is not a clock.

Practical consequence for this app: derive the UI countdown from `deadlineElapsedRealtime - SystemClock.elapsedRealtime()` and recompute on `ON_RESUME`. Never accumulate elapsed time in a variable.

### 2.2 WorkManager

**Verdict: wrong tool. Rejected.**

Doze "Doesn't let JobScheduler run (which includes `WorkManager` tasks)" ([Doze and App Standby](https://developer.android.com/training/monitoring-device-state/doze-standby)). WorkManager schedules with "flexible scheduling windows" and "adheres to power-saving features and best practices like Doze mode" — which is exactly the behaviour a rest timer must *not* have. The task-scheduling guide states plainly that WorkManager "is also not a general solution for all work that requires immediate execution", and points elsewhere for this case: "Unlike WorkManager's regular workers, AlarmManager's exact alarms wake a device from Doze mode… Only use it for precise alarms or notifications such as calendar events, not for recurring background work" ([Task scheduling](https://developer.android.com/develop/background-work/background-tasks/persistent)).

Expedited work does not rescue it: it is for "tasks that are important to the user and which complete within a few minutes", is quota-limited per standby bucket (Active: "Up to 30 mins in a rolling 24h period"; Restricted: "Up to 5 minutes in a rolling 24h window" — [Power management resource limits](https://developer.android.com/topic/performance/power/power-details)), and still carries no start-time guarantee.

This is the trap the ticket calls out: the "recommended default" of the background-work guide ([Background tasks](https://developer.android.com/develop/background-work/background-tasks) — "In most cases, your best option for running background tasks is to use WorkManager") is written for deferrable jobs. A rest timer is a deadline, not a job.

### 2.3 Foreground service

**Verdict: useful as a liveness/priority aid, but on API 34+ there is no clean service type for it, and it does not solve timing. Optional second layer, not the primary.**

What it genuinely buys you:

- Process importance rises from cached to **visible**: "It has a Service that is running as a foreground service, through `Service.startForeground()`" ([Processes and app lifecycle](https://developer.android.com/guide/components/activities/process-lifecycle)).
- The app is promoted to the **Active** standby bucket — "App runs a long-running foreground service" is listed as a promotion trigger ([App Standby Buckets](https://developer.android.com/topic/performance/appstandby)) — and the Active bucket has "No execution limits" on alarms ([Power management resource limits](https://developer.android.com/topic/performance/power/power-details)).
- A persistent, user-visible countdown notification is already required by the FGS contract.

What it does not buy you: exemption from CPU suspend. Doze ignores wake locks, so an FGS-hosted `delay()` loop is still not a clock. Keep the alarm.

The API 34 problem. "If your app targets Android 14 (API level 34) or higher, it must specify at least one foreground service type for each foreground service within your app", and omitting it raises `MissingForegroundServiceTypeException` on `startForeground()` ([Foreground service types are required](https://developer.android.com/about/versions/14/changes/fgs-types-required)). None of the defined types describe a rest timer. The two candidates:

| Candidate | Fit | Blocking problem |
|---|---|---|
| `shortService` | No permission required | "Approximately 3 minutes", then `Service.onTimeout()`; if not stopped "the app receives an ANR". Also "Cannot start other foreground services" and no sticky support. A 5-minute rest does not fit. ([Foreground service types](https://developer.android.com/develop/background-work/services/fgs/service-types)) |
| `specialUse` | No permission required; no time limit | Requires a `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` manifest property **and** a Play Console declaration; "All foreground service types are subject to review" ([Play: foreground service and full-screen intent requirements](https://support.google.com/googleplay/android-developer/answer/13392821)) |

Also relevant if you do use one:

- Starting an FGS from the background throws `ForegroundServiceStartNotAllowedException` on API 31+ — **but one of the listed exemptions is "Your app invokes an exact alarm to complete an action that the user requests"** ([Restrictions on starting a foreground service from the background](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start)). So the alarm receiver *is* allowed to start an FGS, e.g. to play a looping alert until dismissed.
- You have "a few seconds" after `startForegroundService()` to call `ServiceCompat.startForeground()` or you get `ForegroundServiceDidNotStartInTimeException` ([Troubleshoot foreground services](https://developer.android.com/develop/background-work/services/fgs/troubleshooting)).
- The FGS notification must be `PRIORITY_LOW` or higher ([Launch a foreground service](https://developer.android.com/develop/background-work/services/fgs/launch)).
- Android 12+ may delay the FGS notification by 10 s; opt out with `setForegroundServiceBehavior(FOREGROUND_SERVICE_IMMEDIATE)` — "display of the notification will be immediate even if the default behavior would be to defer visibility for a short time" (AOSP `Notification.java`).
- On Android 14 the user can swipe away an ongoing notification: "The behavior of `FLAG_ONGOING_EVENT` has changed to make such notifications actually dismissable by the user" ([Android 14 behaviour changes — all apps](https://developer.android.com/about/versions/14/behavior-changes-all)). It stays non-dismissible only when the phone is locked or under "Clear all".

**Recommendation:** ship v1 without a foreground service. Add a `specialUse` FGS later only if telemetry shows alarm-only delivery failing, and only with the Play declaration done. The alarm carries the correctness; the FGS would only be buying standby-bucket insurance.

### 2.4 AlarmManager — the recommended mechanism

Two viable calls.

**`setExactAndAllowWhileIdle()`** — "allowed to execute even when the system is in low-power idle modes" (AOSP `AlarmManager.java`). This is the default choice.

**`setAlarmClock()`** — strictly more reliable: "Alarms set with `setAlarmClock()` continue to fire normally. The system exits Doze shortly before those alarms fire" ([Doze and App Standby](https://developer.android.com/training/monitoring-device-state/doze-standby)); "the system never adjusts their delivery time… leaves low-power modes if necessary to deliver the alarms" ([Schedule alarms](https://developer.android.com/develop/background-work/services/alarms/schedule)). It is not subject to the while-idle throttle. The cost is user-visible: the system "will typically also use the information supplied here to tell the user about this upcoming alarm if appropriate" (AOSP `AlarmManager.java`) — i.e. a status-bar alarm icon and an entry any app can read via `getNextAlarmClock()`. For a 90-second rest that reads as noise; for a 5-minute rest it is arguably correct.

**Use `ELAPSED_REALTIME_WAKEUP`, not `RTC_WAKEUP`.** The alarms guide recommends elapsed-real-time alarms for anything based on "the passage of time", since they are unaffected by time zone or locale changes: "We recommend that you use an 'elapsed real time' alarm if you can" ([Schedule alarms](https://developer.android.com/develop/background-work/services/alarms)).

**Reboot:** "By default, all alarms are canceled when a device shuts down." For a 30 s – 5 min timer this is a non-issue (nobody reboots mid-set); do **not** add `RECEIVE_BOOT_COMPLETED` for this feature.

#### The while-idle throttle — the sharpest constraint, and three official numbers

Three first-party pages give three different figures for how often a while-idle alarm may fire:

| Source | Stated limit |
|---|---|
| [Doze and App Standby](https://developer.android.com/training/monitoring-device-state/doze-standby) | "Neither `setAndAllowWhileIdle()` nor `setExactAndAllowWhileIdle()` can fire alarms more than once per nine minutes, per app." |
| [Power management resource limits](https://developer.android.com/topic/performance/power/power-details) | While-idle alarms, screen off and Doze active: "Limited to 7 per hour" |
| AOSP `AlarmManager.java` javadoc | "Under normal system operation, it will not dispatch these alarms more than about every minute" |

**This matters for this app specifically.** A lifter resting 90 s between sets starts ~20–30 timers per hour. Under the most restrictive published number that is 3× over quota. The mitigating facts:

- Deep Doze requires the device to be "unplugged and stationary for a period of time, with the screen off" — a phone in a pocket during a workout is moving, and significant motion is what keeps deep Doze at bay. A phone parked on a bench between sets is *not* moving, and that is the realistic failure scenario.
- Only **deep** Doze throttles alarms. Light Doze (Android 7.0+, screen off and unplugged, no stationary requirement) "restricts network access and defers jobs and syncs" — jobs and syncs, not alarms ([Android 7.0 behaviour changes](https://developer.android.com/about/versions/nougat/android-7.0-changes)). This is why WorkManager is worse than an alarm even before deep Doze engages.
- `setAlarmClock()` is not subject to the while-idle quota at all.

**Design consequence:** make `setAlarmClock()` the escape hatch. Either use it unconditionally for rests ≥ 2 min, or expose a "maximum reliability" setting that switches all rest timers to it and accepts the status-bar alarm icon.

#### Standby buckets stack on top

Alarm frequency is also capped by App Standby bucket ([Power management resource limits](https://developer.android.com/topic/performance/power/power-details)):

| Bucket | Alarm limit |
|---|---|
| Active | No execution limits |
| Working set | Limited to 10 per hour |
| Frequent | Limited to 2 per hour |
| Rare | Limited to 1 per hour |
| Restricted | "One alarm per day, either an exact alarm or an inexact alarm" |

Normal use is fine: the user taps "start rest" inside the app, and "User launches an activity" promotes the app to **Active** ([App Standby Buckets](https://developer.android.com/topic/performance/appstandby)). Also "When the device is charging, there are no execution limits for all standby buckets".

The dangerous bucket is **Restricted**, which an app is forced into after "No user interaction for 8 days" on Android 13+. A lapsed user returning after a two-week break is therefore the single most likely person to see a dead timer — on their *first* session back, before any activity launch has promoted them. Note the exemption list for forced-Restricted includes "apps with `USE_EXACT_ALARM`… permissions", which is a real, if awkward, argument for that permission.

---

## 3. Doze and App Standby: what actually applies here

Doze is entered when "the user leaves a device unplugged and stationary for a period of time, with the screen off". Its restrictions ([Doze and App Standby](https://developer.android.com/training/monitoring-device-state/doze-standby)):

1. Suspends network access
2. **Ignores wake locks**
3. **Defers standard AlarmManager alarms, including `setExact()` and `setWindow()`, to the next maintenance window**
4. No Wi-Fi scans
5. No sync adapters
6. **No JobScheduler (and therefore no WorkManager)**

Maintenance windows: "the system runs all pending syncs, jobs, and alarms" — and "Over time, the system schedules maintenance windows less frequently." A non-while-idle alarm therefore has *unbounded* lateness, not merely bounded lateness. This is the "silently never fires" failure the ticket is worried about.

Exempt paths, per the same page: foreground services where "the user expects the system to execute immediately or without interruption", `setAlarmClock()` alarms, and high-priority FCM.

Note the app's own state does not matter much: **a foregrounded-then-backgrounded app is subject to device-level Doze exactly like any other app.** There is no grace period conferred by having been in the foreground a moment ago. What being recently-foregrounded buys you is the *Active standby bucket*, which relaxes the per-bucket alarm cap but not the device-wide while-idle throttle.

---

## 4. Permissions, and how intrusive each is

### 4.1 `SCHEDULE_EXACT_ALARM` vs `USE_EXACT_ALARM` (API 31+)

| | `SCHEDULE_EXACT_ALARM` | `USE_EXACT_ALARM` |
|---|---|---|
| Introduced | Android 12 (API 31) | Android 13 (API 33) |
| Granted by | User, via Settings → Special app access → Alarms & reminders | System, automatically on install |
| User-revocable | Yes | **No** |
| Pre-granted to fresh installs | **No** (targeting API 33+) | Yes |
| Play review | None | **Yes — narrow policy** |

Source: [Schedule alarms](https://developer.android.com/develop/background-work/services/alarms/schedule).

The Android 14 change is the one that bites: `SCHEDULE_EXACT_ALARM` "is no longer being pre-granted to most newly installed apps targeting Android 13 and higher (will be set to denied by default)" ([Exact alarms denied by default](https://developer.android.com/about/versions/14/changes/schedule-exact-alarms)). Worse for returning users: "If a user transfers app data to a device running Android 14 through a backup-and-restore operation, the `SCHEDULE_EXACT_ALARM` permission will be denied on the new device."

So the flow is mandatory, not optional:

1. Call `alarmManager.canScheduleExactAlarms()` before every schedule.
2. If false, send the user to `ACTION_REQUEST_SCHEDULE_EXACT_ALARM` with an in-app explanation first.
3. Re-check in `onResume()`.
4. Register for `AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED`.
5. Degrade *visibly* when denied — see §6.

`canScheduleExactAlarms()` also returns true for apps "on the device's power-save exemption list" (AOSP `AlarmManager.java`), which is a useful second route.

**The `USE_EXACT_ALARM` Play policy question.** The acceptable use is: "The app is an alarm or timer app. The app is a calendar app that shows event notifications", and "Apps that request this restricted permission are subject to review, and those that do not meet the acceptable use case criteria will be disallowed from publishing on Google Play" ([Play: exact alarm permission](https://support.google.com/googleplay/android-developer/answer/13161072)).

Honest read: the policy says "alarm **or timer** app", and a rest timer is literally a timer — but this app's *core functionality* is workout logging, and the policy is framed around core functionality. Declaring `USE_EXACT_ALARM` is a defensible-but-contestable position that risks a publishing block, and Play's own alternative guidance is "You should evaluate if using `SCHEDULE_EXACT_ALARM` as an alternative is an option."

**Recommendation:** ship `SCHEDULE_EXACT_ALARM` only. It costs a settings round-trip the first time, and that round-trip is a fair thing to ask for a feature the user explicitly wants. Revisit `USE_EXACT_ALARM` only if the rest timer becomes a headline feature you are willing to defend in review — its side benefit (exemption from forced-Restricted bucket) is real but not worth a store rejection.

If you ever declare both, gate the old one: `<uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM" android:maxSdkVersion="32" />`.

### 4.2 `POST_NOTIFICATIONS` (API 33+)

Runtime permission; notifications "are off by default on newly-installed apps" targeting API 33+ ([Notification runtime permission](https://developer.android.com/develop/ui/views/notifications/notification-permission)).

Two facts worth internalising:

- "Apps don't need to request the `POST_NOTIFICATIONS` permission in order to launch a foreground service. However, apps must include a notification when they start a foreground service" — **but** "if the user denies the notification permission, they still see notices related to foreground services in the Task Manager but don't see them in the notification drawer." An FGS is therefore not a way around a denied permission; it just makes the failure quieter.
- Timing is yours to choose when targeting 33+. The right moment for this app is the first time the user starts a rest timer, with one line of context. Check `NotificationManager.areNotificationsEnabled()` before every alert.

Intrusiveness: low, one dialog, well-understood by users. This is the cheapest of the three.

### 4.3 Foreground service permissions (API 34+)

`FOREGROUND_SERVICE` plus the type-specific permission. "When the app attempts to promote a service to the foreground, the system checks for the appropriate permissions and throws `SecurityException` if the app is missing any" ([Launch a foreground service](https://developer.android.com/develop/background-work/services/fgs/launch)). Neither `shortService` nor `specialUse` needs a runtime permission — the cost of `specialUse` is the Play Console declaration and review, not a permission prompt.

### 4.4 `USE_FULL_SCREEN_INTENT` — recommend against

On API 34+ "apps that are allowed to use this permission are limited to those that provide calling and alarms only. The Google Play Store revokes default `USE_FULL_SCREEN_INTENT` permissions for any apps that don't fit this profile" ([Android 14 behaviour changes](https://developer.android.com/about/versions/14/behavior-changes-14)); since 22 Jan 2025 only declared-and-approved calling/alarm apps get it by default ([Play: FGS and full-screen intent requirements](https://support.google.com/googleplay/android-developer/answer/13392821)). Everyone else must route the user through `ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT` after checking `NotificationManager.canUseFullScreenIntent()`.

A rest timer does not need to seize the screen. A heads-up `IMPORTANCE_HIGH` notification with an alarm-stream sound achieves the same outcome at a fraction of the permission cost. Skip it.

### 4.5 `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` — do not use the direct dialog

Two intents exist ([Doze and App Standby](https://developer.android.com/training/monitoring-device-state/doze-standby)):

- `ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS` — available to any app, opens the system list. **Safe to use.**
- `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` — direct one-tap exemption dialog, but "Google Play policies prohibit apps from requesting direct exemption from Power Management features—Doze and App Standby—in Android 6.0 and above unless the core function of the app is adversely affected." The acceptable-use table covers instant messaging, safety apps, task automation and peripheral-device companions. A rest timer is not on it. **Do not ship this one.**

Use `isIgnoringBatteryOptimizations()` to *read* the state and tailor your in-app guidance, and `ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS` to point the user at the list.

---

## 5. OEM battery optimisation

This is the part with the weakest primary sourcing, and the report should say so plainly.

**What Android officially guarantees.** App Standby, Doze and the quota tables above are AOSP behaviour. Being on the power-save exemption list lifts them: apps on that list are "always allowed to call `setExact()` or `setExactAndAllowWhileIdle()`" ([Exact alarms denied by default](https://developer.android.com/about/versions/14/changes/schedule-exact-alarms)), and per the standby-bucket docs, apps on the Doze exemption list are exempt from bucket-based restrictions.

**What OEMs add on top.** Samsung, Xiaomi, OnePlus/Oppo and others ship additional process-killing layers beyond AOSP. I could not retrieve the verbatim text of CDD §3.5.1 "Application Restriction" — both the Android 14 and Android 16 CDD pages truncated before that section on fetch — so I am not going to quote constraints I have not read. **Open item: pull CDD §3.5.1 directly and confirm what OEMs are contractually forbidden from doing to apps the user has exempted.** The CDD is the only lever that would let you argue an OEM behaviour is a compatibility bug rather than a fact of life.

**What the community source says.** [dontkillmyapp.com](https://dontkillmyapp.com/) — a community-maintained benchmark, *not* an official Google or OEM statement — ranks aggressiveness roughly: Huawei, Xiaomi, OnePlus, Samsung, Meizu, Asus worst; Nokia/HMD, HTC and Google/Pixel best. Samsung's "sleeping apps"/"deep sleeping apps" and Xiaomi's MIUI battery saver plus autostart gating are the named mechanisms. Its developer advice is to request battery-optimisation exemption and direct users to the device settings pages.

**What this app can realistically do.**

1. Detect via `PowerManager.isIgnoringBatteryOptimizations()`. If false, show a one-time, dismissible card — *not* a blocking wall — the first time a rest timer completes late or on first timer use.
2. Deep-link with `ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS`. That intent is documented and safe.
3. OEM-specific deep links (Samsung's `Never sleeping apps`, Xiaomi's Autostart, Oppo/OnePlus' `Allow background activity`) are **undocumented component names that change between OS versions**. If you ship them, wrap each in `try/catch` on `ActivityNotFoundException`/`SecurityException` and fall back to the generic battery settings screen. Do not let a missing OEM activity crash the app.
4. Instrument it. Record `scheduledDeadline` vs `actualFireTime` on every alarm, bucket it by `Build.MANUFACTURER`, and let real data — not folklore — decide whether to escalate to `setAlarmClock()` or a `specialUse` FGS.
5. Accept that a user-initiated **force stop** cancels everything. There is no app-side defence against "Force stop"; some OEM "deep sleep" states are functionally equivalent.

---

## 6. Delivering the alert

### 6.1 Channel

Create one dedicated channel, e.g. `rest_timer_alert`, at `IMPORTANCE_HIGH` — "Makes a sound and appears as a heads-up notification" ([Create and manage notification channels](https://developer.android.com/develop/ui/views/notifications/channels)).

**Channel settings are immutable after creation:** "Once you submit the channel to the `NotificationManager`, you can't change the importance level", and per the AOSP `NotificationChannel` javadoc, `setSound`, `setImportance`, `enableVibration`/`setVibrationPattern` and `setBypassDnd` are "only modifiable before the channel is submitted to `NotificationManager#createNotificationChannel()`". Get the id-versioning strategy right up front (`rest_timer_alert_v1`, `_v2`, …) because changing the sound later means a new channel, and deleted channels still linger in the user's settings UI.

Also set `setCategory(Notification.CATEGORY_ALARM)` — the AOSP javadoc for that constant is literally "Notification category: alarm or timer", which is exactly this. It feeds system ranking and filtering.

### 6.2 Sound when the device is on silent

Attach explicit audio attributes to the channel:

```kotlin
val attrs = AudioAttributes.Builder()
    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
    .setUsage(AudioAttributes.USAGE_ALARM)
    .build()
channel.setSound(alertUri, attrs)
```

`CONTENT_TYPE_SONIFICATION` + `USAGE_ALARM` maps to `STREAM_ALARM` ([AOSP audio attributes](https://source.android.com/docs/core/audio/attributes)), so the alert is governed by the **alarm** volume rather than the ringer/notification volume. A phone on silent or vibrate normally still has a non-zero alarm volume — that is what makes this the right usage for a gym timer.

The honest caveat: the AOSP audio-attributes page documents the stream mapping and says usage guides "volume or routing decisions", but it does **not** document silent-mode or DND behaviour, so treat the silent-mode benefit as a strong inference from the stream mapping rather than a documented guarantee, and verify on device. A user with alarm volume at zero gets nothing, and you cannot fix that.

Vibration: `setVibrationPattern(...)` on the channel, `VIBRATE` permission (normal, no prompt). Ship a pattern regardless of sound — a phone in a pocket transmits vibration better than sound.

### 6.3 Do Not Disturb

Two paths, and the recommendation is the first:

**Rely on DND's built-in alarm allowance.** DND policy has a `PRIORITY_CATEGORY_ALARMS` category ("Alarms are prioritized" — AOSP `NotificationManager.java`), which is on by default in stock Android. Combined with a `USAGE_ALARM` channel sound and `CATEGORY_ALARM`, a rest timer lands in the category DND is designed to let through. A user who has explicitly disallowed alarms in DND has made a choice you should respect.

**Explicit bypass — available but heavy.** `NotificationChannel.setBypassDnd(true)` "Sets whether or not notifications posted to this channel can interrupt the user in `INTERRUPTION_FILTER_PRIORITY` mode", and it requires Do Not Disturb policy access, checked via `NotificationManager.isNotificationPolicyAccessGranted()` and granted only by the user through `ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS` (AOSP `NotificationChannel.java`, `NotificationManager.java`). That is a broad, alarming-sounding grant ("allow this app to override Do Not Disturb") for a rest timer. It must also be set before `createNotificationChannel`, so retrofitting it means a new channel.

**Is a rest-timer alert a legitimate DND bypass?** Arguably yes in principle — it is a user-requested, user-anticipated alarm, which is precisely what DND's alarm category exists for. But asking for global Notification Policy Access is disproportionate when the alarm category already covers the case. Offer explicit bypass, if at all, as an opt-in toggle in settings for users who report missed timers, never on first run.

Note also that on API 34+ the platform steers apps away from mutating global policy toward `AutomaticZenRule` — another reason not to go down this road for a timer.

### 6.4 Rendering the countdown without a running process

The system can draw the ticking countdown for you. On the ongoing "resting" notification:

```kotlin
.setWhen(deadlineWallClockMillis)
.setUsesChronometer(true)
.setChronometerCountDown(true)   // API 24+, safe at minSdk 26
.setOngoing(true)
```

`setUsesChronometer` controls "whether `when` should be shown as a count-up timer (specifically a `Chronometer`) instead of a timestamp", and `setChronometerCountDown` makes it "count down instead of counting up" (AOSP `Notification.java`). SystemUI does the ticking; your process can be dead the whole time. **This removes the main reason people reach for a foreground service.**

Note `setOngoing(true)` no longer makes it undismissable on Android 14 (§2.3) — the alarm still fires if the user swipes the notification away, so make sure swiping does not cancel the alarm, and offer an explicit "Skip rest" action that does.

### 6.5 Notification trampolines

When the alert notification is tapped, "your app cannot call `startActivity()` inside of a service or broadcast receiver" on API 31+ ([Android 12 behaviour changes](https://developer.android.com/about/versions/12/behavior-changes-12)). Build the `PendingIntent` directly against the target Activity. Same rule for the "Skip"/"+30 s" action buttons, which should target a `BroadcastReceiver` that does work but never launches UI indirectly.

---

## 7. Concrete design

```
User taps "Start rest (90s)"
  │
  ├─ deadline = SystemClock.elapsedRealtime() + 90_000
  │  (persisted to DataStore/Room so a process death is recoverable)
  │
  ├─ canScheduleExactAlarms()?
  │     yes → setExactAndAllowWhileIdle(ELAPSED_REALTIME_WAKEUP, deadline, pi)
  │           (or setAlarmClock(...) for rests ≥ 2 min / "max reliability" setting)
  │     no  → set(ELAPSED_REALTIME_WAKEUP, ...) as a best-effort fallback
  │           + persistent in-app banner: "Rest timer may be late — enable
  │             Alarms & reminders" → ACTION_REQUEST_SCHEDULE_EXACT_ALARM
  │
  ├─ post ongoing notification, channel rest_timer_progress (IMPORTANCE_LOW),
  │  setWhen(deadline-as-wall-clock) + chronometer countdown,
  │  actions: [Skip] [+30s]
  │
  └─ Compose UI renders countdown from the same deadline, recomputed on resume

Alarm fires → BroadcastReceiver
  ├─ cancel the progress notification
  ├─ post alert notification: channel rest_timer_alert (IMPORTANCE_HIGH,
  │  USAGE_ALARM sound, vibration pattern, CATEGORY_ALARM,
  │  contentIntent = PendingIntent to the workout Activity directly)
  └─ optional: start a specialUse FGS to loop the alert until dismissed
     (permitted from the background under the exact-alarm exemption)
```

Keep `onReceive()` trivial — post the notification and return. Anything longer belongs in a service started from the receiver.

Permissions to declare:

```xml
<uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.VIBRATE" />
<!-- only if the FGS layer ships: -->
<!-- <uses-permission android:name="android.permission.FOREGROUND_SERVICE" /> -->
```

No `RECEIVE_BOOT_COMPLETED`, no `USE_FULL_SCREEN_INTENT`, no `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`.

---

## 8. Failure modes, stated honestly

| # | Failure | Likelihood | Mitigation | Residual risk |
|---|---|---|---|---|
| 1 | `SCHEDULE_EXACT_ALARM` denied — the default for fresh installs targeting API 33+ on Android 14+, and after a backup-restore | **High** | `canScheduleExactAlarms()` on every schedule; in-context request; listen for the state-changed broadcast | Users who decline get inexact alarms deferred to a Doze maintenance window — unbounded lateness. Must be surfaced in the UI, not swallowed |
| 2 | While-idle alarm quota exceeded (7/hour, or once per 9 min per the Doze page) with sets ~90 s apart and the phone stationary on a bench | **Medium** | `setAlarmClock()` for longer rests or via a reliability setting; the Active standby bucket while the app is in use | Genuinely unresolvable with `setExactAndAllowWhileIdle` alone; `setAlarmClock` costs a status-bar alarm icon |
| 3 | App in the **Restricted** bucket (8 days of no interaction) → one alarm per day | Medium for returning users | Launching an activity promotes to Active — but the promotion may land after the first timer is scheduled | First session back after a long layoff is the most likely to fail. Schedule the alarm *after* the activity is resumed, not during cold-start work |
| 4 | `POST_NOTIFICATIONS` denied | Medium | Ask in context at first timer use; `areNotificationsEnabled()` before every alert | No visible alert at all. Fall back to an in-app alert + sound if the app happens to be foreground; otherwise the timer silently succeeds and the user never knows |
| 5 | DND on with alarms disallowed, or alarm volume at 0 | Low–medium | `USAGE_ALARM` stream + vibration + `CATEGORY_ALARM`; optional opt-in DND bypass | Cannot be fixed without Notification Policy Access, which is disproportionate |
| 6 | OEM process killer (Samsung/Xiaomi/OnePlus) | Medium, device-dependent | Alarm lives in the system, not your process, so a plain process kill is survivable; battery-optimisation exemption prompt; OEM deep links behind try/catch | User-initiated **force stop** cancels all alarms. Nothing defends against it |
| 7 | Relying on an in-process tick | High if built naively | Deadline-based recomputation from `elapsedRealtime()` | None if the architecture rule is followed — but easy to regress. Enforce it in review |
| 8 | `shortService` FGS overruns its ~3 min and ANRs | High if used for 5-min rests | Don't use `shortService` for this | — |
| 9 | Play review rejects `USE_EXACT_ALARM` or a `specialUse` FGS declaration | Medium if attempted | Ship `SCHEDULE_EXACT_ALARM` only in v1 | Deferred, not eliminated, if the FGS layer is added later |
| 10 | Doze suspends the CPU despite a foreground service (wake locks are ignored in Doze) | Certain, if assumed otherwise | Never treat an FGS as a Doze exemption for timing | — |

---

## 9. How to verify (the "works on the emulator, dies on a Samsung" problem)

```bash
# force deep Doze
adb shell dumpsys deviceidle force-idle
adb shell dumpsys deviceidle unforce

# inspect scheduled alarms and while-idle quota state
adb shell dumpsys alarm | grep -A20 <package>

# drive standby buckets
adb shell am set-standby-bucket <package> restricted   # 45 = restricted
adb shell am get-standby-bucket <package>

# revoke the exact-alarm special access to test the denied path
adb shell cmd appops set <package> SCHEDULE_EXACT_ALARM deny

# battery-optimisation exemption state
adb shell dumpsys deviceidle whitelist
```

Automated tests cannot cover this. The acceptance bar is a real Samsung and a real Xiaomi, screen off, phone left stationary for 30+ minutes to force deep Doze, then a sequence of 60 s / 90 s / 5 min rests with fire-time deltas logged.

---

## 10. Open items

- **CDD §3.5.1 "Application Restriction"** — not retrieved (both CDD pages truncated on fetch). Worth reading in full: it determines whether aggressive OEM killing of an exempted app is a compatibility violation you can report, or just reality.
- **Silent-mode behaviour of a `USAGE_ALARM` channel sound** — the stream mapping is documented, the silent-mode outcome is not. Verify empirically on Samsung One UI and MIUI, which both customise volume handling.
- **Which of the three published while-idle quota figures is current** — resolve by measuring `dumpsys alarm` under forced Doze rather than by reading more docs.

---

## Sources

All primary unless marked.

- [Optimize for Doze and App Standby](https://developer.android.com/training/monitoring-device-state/doze-standby)
- [Schedule alarms](https://developer.android.com/develop/background-work/services/alarms) · [Set exact vs inexact alarms](https://developer.android.com/develop/background-work/services/alarms/schedule)
- [Exact alarms are denied by default (Android 14)](https://developer.android.com/about/versions/14/changes/schedule-exact-alarms)
- [Power management resource limits](https://developer.android.com/topic/performance/power/power-details) · [App Standby Buckets](https://developer.android.com/topic/performance/appstandby)
- [Foreground services overview](https://developer.android.com/develop/background-work/services/fgs) · [types](https://developer.android.com/develop/background-work/services/fgs/service-types) · [launch](https://developer.android.com/develop/background-work/services/fgs/launch) · [background-start restrictions](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start) · [troubleshooting](https://developer.android.com/develop/background-work/services/fgs/troubleshooting)
- [Foreground service types are required (Android 14)](https://developer.android.com/about/versions/14/changes/fgs-types-required)
- [Background tasks overview](https://developer.android.com/develop/background-work/background-tasks) · [Task scheduling / WorkManager](https://developer.android.com/develop/background-work/background-tasks/persistent)
- [Notification runtime permission](https://developer.android.com/develop/ui/views/notifications/notification-permission) · [Notification channels](https://developer.android.com/develop/ui/views/notifications/channels)
- [Processes and app lifecycle](https://developer.android.com/guide/components/activities/process-lifecycle)
- Behaviour changes: [Android 12](https://developer.android.com/about/versions/12/behavior-changes-12) · [Android 14 (targeting)](https://developer.android.com/about/versions/14/behavior-changes-14) · [Android 14 (all apps)](https://developer.android.com/about/versions/14/behavior-changes-all) · [Android 15](https://developer.android.com/about/versions/15/behavior-changes-15) · [Android 16](https://developer.android.com/about/versions/16/behavior-changes-16) · [Android 7.0](https://developer.android.com/about/versions/nougat/android-7.0-changes)
- AOSP javadoc: [`AlarmManager.java`](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/apex/jobscheduler/framework/java/android/app/AlarmManager.java) · [`NotificationChannel.java`](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/app/NotificationChannel.java) · [`NotificationManager.java`](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/app/NotificationManager.java) · [`Notification.java`](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/app/Notification.java)
- [AOSP audio attributes](https://source.android.com/docs/core/audio/attributes)
- Google Play policy: [exact alarm permission](https://support.google.com/googleplay/android-developer/answer/13161072) · [foreground service and full-screen intent requirements](https://support.google.com/googleplay/android-developer/answer/13392821)
- *Community source (not official):* [dontkillmyapp.com](https://dontkillmyapp.com/)
