package com.tylergimpel.gymtracker.ui

import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import com.tylergimpel.gymtracker.ui.theme.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.tylergimpel.gymtracker.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Picks a calendar day.
 *
 * **Read and written in UTC, deliberately, and never in the device's zone.** The picker deals in
 * milliseconds while what is being chosen is a day on a calendar; converting through the local zone
 * files a late evening on the following day, which is the exact error the session's own midnight
 * rule exists to avoid. One implementation, because getting this wrong is invisible until a session
 * or a weigh-in turns up on a day nobody was there.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DayPickerDialog(
    date: LocalDate,
    onPick: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val picker = rememberDatePickerState(
        initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    picker.selectedDateMillis?.let {
                        onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                },
                enabled = picker.selectedDateMillis != null,
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    ) {
        DatePicker(state = picker)
    }
}
