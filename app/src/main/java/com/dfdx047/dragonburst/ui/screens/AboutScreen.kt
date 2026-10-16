package com.dfdx047.dragonburst.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.dfdx047.dragonburst.BuildConfig
import com.dfdx047.dragonburst.DragonBurstApp
import com.dfdx047.dragonburst.R
import com.dfdx047.dragonburst.ui.LauncherViewModel
import com.dfdx047.dragonburst.ui.UpdateState
import com.dfdx047.dragonburst.ui.components.SectionCard
import com.dfdx047.dragonburst.ui.theme.LogoStyle
import com.dfdx047.dragonburst.ui.components.gradientText

@Composable
fun AboutScreen(vm: LauncherViewModel) {
    val context = LocalContext.current
    fun open(url: String) = context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(painterResource(R.drawable.ic_launcher_foreground), null, tint = Color.Unspecified, modifier = Modifier.size(140.dp))
        Text(
            "DRAGON BURST",
            style = LogoStyle,
            color = Color.White,
            modifier = Modifier.gradientText(
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    listOf(com.dfdx047.dragonburst.ui.theme.DfColors.Gold, com.dfdx047.dragonburst.ui.theme.DfColors.Ember),
                ),
            ),
        )
        Text(stringResource(R.string.about_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.about_developer, DragonBurstApp.DEVELOPER), style = MaterialTheme.typography.titleMedium)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { open(DragonBurstApp.GITHUB_URL) }) {
                Icon(Icons.Filled.Code, null)
                Spacer(Modifier.width(6.dp))
                Text("GitHub")
            }
            OutlinedButton(onClick = { vm.checkUpdates(manual = true) }, enabled = vm.updateState != UpdateState.CHECKING) {
                if (vm.updateState == UpdateState.CHECKING) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Filled.SystemUpdate, null)
                }
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.about_check_updates))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { open(DragonBurstApp.DISCORD_URL) }) { Text("Discord") }
            OutlinedButton(onClick = { open(DragonBurstApp.YOUTUBE_URL) }) { Text("YouTube") }
            OutlinedButton(onClick = { open(DragonBurstApp.KOFI_URL) }) { Text("Ko-fi") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { vm.openGpuNotice() }) {
                Text(stringResource(R.string.about_gpu_notice))
            }
            OutlinedButton(onClick = { vm.exportLogs(context) }, enabled = vm.busy == null) {
                Text(stringResource(R.string.export_logs))
            }
        }
        if (vm.updateState == UpdateState.AVAILABLE) {
            TextButton(onClick = { vm.showUpdateDialog = true }) {
                Text(stringResource(R.string.about_update_available, vm.updateInfo?.version ?: ""))
            }
        }

        SectionCard(stringResource(R.string.about_project_title), Icons.Filled.Favorite, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.about_project_text), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { open(DragonBurstApp.ORIGINAL_PROJECT_URL) }) {
                Text("iExplosiveRage/DBZ-Burst-Limit-Recompiled")
            }
            Text(stringResource(R.string.about_credits), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.about_legal), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
