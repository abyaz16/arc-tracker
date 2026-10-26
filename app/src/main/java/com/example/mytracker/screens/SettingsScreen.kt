package com.example.mytracker.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import com.example.mytracker.data.Db
import com.example.mytracker.data.Store
import com.example.mytracker.data.ReminderStyle
import com.example.mytracker.data.ThemeMode
import com.example.mytracker.domain.D
import com.example.mytracker.domain.Reminders
import com.example.mytracker.notify.Notifier
import com.example.mytracker.ui.CButton
import com.example.mytracker.ui.CCard
import com.example.mytracker.ui.CToggle
import com.example.mytracker.ui.CardTone
import com.example.mytracker.ui.Eyebrow
import com.example.mytracker.ui.Ic
import com.example.mytracker.ui.IconBtn
import com.example.mytracker.ui.PageHead
import com.example.mytracker.ui.Segmented
import com.example.mytracker.ui.SettingDivider
import com.example.mytracker.ui.SettingRow
import com.example.mytracker.ui.T
import com.example.mytracker.ui.theme.C
import com.example.mytracker.ui.theme.CadenceType
import com.example.mytracker.ui.theme.Space

/**
 * Settings is reached from the avatar in the top bar, not from the nav pill —
 * it is somewhere you visit occasionally, and the five tabs are places you work.
 *
 * It only offers what it actually changes. There is no reduced-motion switch
 * here because honouring it would mean gating every animation in the app, and a
 * toggle that does nothing is worse than none.
 */
@Composable
fun SettingsScreen(store: Store, db: Db, onBack: () -> Unit) {
    val s = db.settings
    var confirmErase by remember { mutableStateOf(false) }
    val ctx = LocalContext.current

    // re-read on every recomposition: the user can change this in system
    // settings while the app is open, and the screen should tell the truth
    val permitted = Notifier.canPost(ctx)
    val canBeExact = Notifier.canScheduleExact(ctx)
    val pendingCount = remember(db) { Reminders.plan(db, System.currentTimeMillis()).size }

    // Android 13+ asks before an app may notify at all
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        store.updateSettings { st -> st.copy(remindersEnabled = granted) }
        if (!granted) store.showToast("Notifications are off for this app")
    }
    val askPermission = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !permitted) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else if (!permitted) {
            store.showToast("Turn on notifications for Arc Tracker in system settings")
        } else {
            store.updateSettings { st -> st.copy(remindersEnabled = true) }
        }
    }

    LazyColumn(
        Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            start = Space.gutter, end = Space.gutter, top = Space.s7, bottom = 120.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(Space.s4),
    ) {
        item {
            PageHead(
                title = "Settings",
                subtitle = "Yours, and local to this phone",
                action = { IconBtn(Ic.CHEVRON_LEFT, "Back", onBack) },
            )
        }

        // ---- you ----
        item {
            Eyebrow("You")
            Spacer(Modifier.height(Space.s4))
            CCard {
                Field("NAME", "Used to greet you on Home. It never leaves this phone.") {
                    CInput(
                        value = s.name,
                        onChange = { store.updateSettings { st -> st.copy(name = it.take(40)) } },
                        placeholder = "Your name",
                    )
                }
                Spacer(Modifier.height(Space.s5))
                Row(horizontalArrangement = Arrangement.spacedBy(Space.s4)) {
                    Column(Modifier.weight(1f)) {
                        Field("WAKE") {
                            CInput(
                                value = s.wakeTime,
                                onChange = { store.updateSettings { st -> st.copy(wakeTime = it.take(5)) } },
                                placeholder = "07:00",
                            )
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        Field("WIND DOWN") {
                            CInput(
                                value = s.sleepTime,
                                onChange = { store.updateSettings { st -> st.copy(sleepTime = it.take(5)) } },
                                placeholder = "23:00",
                            )
                        }
                    }
                }
                Spacer(Modifier.height(Space.s4))
                T(
                    "Your day runs ${D.formatTime(s.wakeTime)} to ${D.formatTime(s.sleepTime)}. Home greets " +
                        "you against this window and, after wind-down, reviews the day instead of pushing it. " +
                        "These two times shape wording and nothing else — this build cannot measure when you " +
                        "actually sleep.",
                    CadenceType.caption, C.textMuted,
                )
            }
        }

        // ---- appearance ----
        item {
            Spacer(Modifier.height(Space.s5))
            Eyebrow("Appearance")
            Spacer(Modifier.height(Space.s4))
            CCard {
                Field("THEME") {
                    Segmented(
                        options = ThemeMode.entries.map { it.key to it.label },
                        value = s.theme.key,
                        onChange = { k -> store.updateSettings { st -> st.copy(theme = ThemeMode.from(k)) } },
                    )
                }
                Spacer(Modifier.height(Space.s4))
                SettingRow(
                    title = "Week starts on Monday",
                    hint = "Moves weekly habit quotas, week-counted streaks and the heatmap together.",
                    control = {
                        CToggle(
                            checked = s.weekStartsMonday,
                            onChange = { v -> store.updateSettings { st -> st.copy(weekStartsMonday = v) } },
                            label = "Week starts on Monday",
                        )
                    },
                )
            }
        }

        // ---- reminders ----
        item {
            Spacer(Modifier.height(Space.s5))
            Eyebrow("Reminders")
            Spacer(Modifier.height(Space.s4))
            CCard {
                SettingRow(
                    title = "Remind me",
                    hint = "Local notifications only. Nothing is sent anywhere, and nothing " +
                        "runs in the background when this is off.",
                    control = {
                        CToggle(
                            checked = s.remindersEnabled,
                            onChange = { want ->
                                if (want) askPermission() else {
                                    store.updateSettings { st -> st.copy(remindersEnabled = false) }
                                }
                            },
                            label = "Remind me",
                        )
                    },
                )
                if (s.remindersEnabled) {
                    SettingDivider()
                    SettingRow(
                        title = "Tasks",
                        hint = "At the time a task is due. Tasks without a time are never used.",
                        control = {
                            CToggle(
                                checked = s.remindTasks,
                                onChange = { v -> store.updateSettings { st -> st.copy(remindTasks = v) } },
                                label = "Remind me about tasks",
                            )
                        },
                    )
                    SettingDivider()
                    SettingRow(
                        title = "Habits",
                        hint = "At each habit's own reminder time, set when you edit it. " +
                            "A habit that is already done, or whose weekly quota is met, stays quiet.",
                        control = {
                            CToggle(
                                checked = s.remindHabits,
                                onChange = { v -> store.updateSettings { st -> st.copy(remindHabits = v) } },
                                label = "Remind me about habits",
                            )
                        },
                    )
                    SettingDivider()
                    Spacer(Modifier.height(Space.s4))
                    Field(
                        "HOW IT ARRIVES",
                        hint = if (s.reminderStyle == ReminderStyle.ALARM) {
                            "Rings with your device's alarm sound, vibrates, and fires on the minute."
                        } else {
                            "A quiet notification. Needs no extra permission, and may land a few " +
                                "minutes late."
                        },
                    ) {
                        Segmented(
                            options = ReminderStyle.entries.map { it.key to it.label },
                            value = s.reminderStyle.key,
                            onChange = { k ->
                                val picked = ReminderStyle.from(k)
                                store.updateSettings { st -> st.copy(reminderStyle = picked) }
                                if (picked == ReminderStyle.ALARM && !canBeExact) {
                                    store.showToast("Allow alarms for Arc Tracker to ring on the minute")
                                }
                            },
                        )
                    }
                }
            }
            if (s.remindersEnabled && s.reminderStyle == ReminderStyle.ALARM && !canBeExact) {
                Spacer(Modifier.height(Space.s4))
                CCard(variant = CardTone.QUIET, compact = true) {
                    T(
                        "Android has not given this app permission to set exact alarms, so these " +
                            "still ring but may be a few minutes late.",
                        CadenceType.caption, C.textMuted,
                    )
                    Spacer(Modifier.height(Space.s4))
                    CButton(
                        "Allow exact alarms",
                        {
                            Notifier.exactAlarmSettingsIntent(ctx)?.let { ctx.startActivity(it) }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        variant = "secondary",
                        small = true,
                    )
                }
            }
            if (s.remindersEnabled && !permitted) {
                Spacer(Modifier.height(Space.s4))
                T(
                    "Android is currently blocking notifications for this app, so nothing will " +
                        "arrive. Turn them on for Arc Tracker in the system settings.",
                    CadenceType.caption, C.overdue,
                )
            } else if (s.remindersEnabled) {
                Spacer(Modifier.height(Space.s4))
                T(
                    "$pendingCount scheduled over the next ${Reminders.HORIZON_HOURS} hours. " +
                        "Delivery is approximate by a few minutes — an exact alarm needs a " +
                        "permission a habit nudge does not justify asking for.",
                    CadenceType.caption, C.textMuted,
                )
            }
        }

        // ---- what this holds ----
        item {
            Spacer(Modifier.height(Space.s5))
            Eyebrow("What this holds")
            Spacer(Modifier.height(Space.s4))
            CCard(variant = CardTone.QUIET, compact = true) {
                val rows = listOf(
                    "Tasks" to db.tasks.size,
                    "Habits" to db.habits.size,
                    "Habit logs" to db.habitLogs.size,
                    "Goals" to db.goals.size,
                    "Focus sessions" to db.sessions.size,
                    "Recorded reasons" to db.failures.size,
                )
                rows.forEachIndexed { i, (label, n) ->
                    if (i > 0) SettingDivider()
                    SettingRow(title = label, control = { T("$n", CadenceType.bodyStrong) })
                }
            }
            Spacer(Modifier.height(Space.s4))
            T(
                "Everything lives in a single file on this phone. There is no account and no server, " +
                    "which also means uninstalling deletes it.",
                CadenceType.caption, C.textMuted,
            )
        }

        // ---- start over ----
        item {
            Spacer(Modifier.height(Space.s5))
            Eyebrow("Start over")
            Spacer(Modifier.height(Space.s4))
            CCard {
                CButton(
                    if (confirmErase) "Tap again to erase everything" else "Erase everything",
                    {
                        if (!confirmErase) {
                            confirmErase = true
                        } else {
                            store.reset()
                            confirmErase = false
                            store.showToast("All data cleared")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    variant = if (confirmErase) "danger" else "ghost",
                )
                Spacer(Modifier.height(Space.s4))
                T(
                    "Erasing cannot be undone and no backup is kept for you.",
                    CadenceType.caption, C.textMuted,
                )
            }
        }

        // ---- about ----
        item {
            Spacer(Modifier.height(Space.s5))
            CCard(variant = CardTone.QUIET, compact = true, icon = Ic.INFO, title = "Arc Tracker · 0.1.0") {
                T(
                    "No network call is made by this app, so there is nothing here to sign in to or opt " +
                        "out of. Screen time, app usage, app blocking and sleep are absent rather than " +
                        "faked — this build does not request the OS permissions that would make them real.",
                    CadenceType.caption, C.textMuted,
                )
            }
        }
    }
}
