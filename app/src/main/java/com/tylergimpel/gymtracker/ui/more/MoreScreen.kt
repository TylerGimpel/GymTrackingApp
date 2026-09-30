package com.tylergimpel.gymtracker.ui.more

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.tylergimpel.gymtracker.R
import com.tylergimpel.gymtracker.ui.theme.AppCard
import com.tylergimpel.gymtracker.ui.theme.AppTopBar
import com.tylergimpel.gymtracker.ui.theme.CardRow
import com.tylergimpel.gymtracker.ui.theme.RowDivider

/** More: everything that is not today, the calendar or the routine (#133). */
@Composable
fun MoreScreen(
    onOpenExercises: () -> Unit,
    onOpenBodyweight: () -> Unit,
    onOpenPlates: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Scaffold(topBar = { AppTopBar(stringResource(R.string.tab_more)) }) { insets ->
        Column(
            Modifier.fillMaxSize().padding(insets).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        ) {
            AppCard(Modifier.fillMaxWidth()) {
                CardRow(stringResource(R.string.more_exercises), onClick = onOpenExercises)
                RowDivider()
                CardRow(stringResource(R.string.more_weigh_ins), onClick = onOpenBodyweight)
                RowDivider()
                CardRow(stringResource(R.string.more_plates), onClick = onOpenPlates)
                RowDivider()
                CardRow(stringResource(R.string.more_profile), onClick = onOpenProfile)
                RowDivider()
                CardRow(stringResource(R.string.more_settings), onClick = onOpenSettings)
            }
        }
    }
}
