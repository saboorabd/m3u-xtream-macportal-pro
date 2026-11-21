package com.m3upro

import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SettingsScreen(vm: MainVm) {
    val s by vm.settings.collectAsState()
    val ctx = LocalContext.current
    val clip = LocalClipboardManager.current
    var crash by remember { mutableStateOf(ctx.getSharedPreferences("crash", 0).getString("last", "") ?: "") }
    var showLog by remember { mutableStateOf(false) }
    val header = "M3U Tools Pro v$APP_VERSION • Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) • ${Build.MANUFACTURER} ${Build.MODEL}\n"

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text("Settings", style = MaterialTheme.typography.headlineSmall) }

        item {
            SoftCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Channel checker", style = MaterialTheme.typography.titleMedium)
                    Text("Timeout per channel", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(5, 8, 15, 25).forEach { t -> FilterChip(s.timeout == t, { vm.setSettings(s.copy(timeout = t)) }, { Text("${t}s") }) }
                    }
                    Text("Parallel checks (higher = faster, uses more data)", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(10, 20, 40, 80).forEach { t -> FilterChip(s.concurrency == t, { vm.setSettings(s.copy(concurrency = t)) }, { Text("$t") }) }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Thorough HLS check", style = MaterialTheme.typography.bodyLarge)
                            Text("Also downloads one video segment, so a playlist that exists but has no video is marked dead.",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(s.thorough, { vm.setSettings(s.copy(thorough = it)) })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Check every channel on app start", style = MaterialTheme.typography.bodyLarge)
                            Text("Off by default (uses data).", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(s.deep, { vm.setSettings(s.copy(deep = it)) })
                    }
                }
            }
        }

        item {
            SoftCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Diagnostics", style = MaterialTheme.typography.titleMedium)
                    Text("If something does not work, copy the log and send it. Passwords and tokens are masked.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (crash.isNotBlank()) {
                        Text("The app crashed last time.", color = Pal.bad, style = MaterialTheme.typography.labelLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button({ clip.setText(AnnotatedString(header + crash)); vm.message.value = "Crash report copied" }) { Text("Copy crash report") }
                            TextButton({
                                ctx.getSharedPreferences("crash", 0).edit().remove("last").apply(); crash = ""
                            }) { Text("Dismiss") }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button({ clip.setText(AnnotatedString(header + DebugLog.dump())); vm.message.value = "Log copied" }) { Text("Copy debug log") }
                        OutlinedButton({ showLog = true }) { Text("View") }
                        TextButton({ DebugLog.clear(); vm.message.value = "Log cleared" }) { Text("Clear") }
                    }
                }
            }
        }

        item {
            SoftCard(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    LogoMark(52.dp)
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text("M3U Tools Pro", style = MaterialTheme.typography.titleMedium)
                        Text("Version $APP_VERSION  •  No ads  •  No billing  •  Your data stays on this phone",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }

    if (showLog) AlertDialog(onDismissRequest = { showLog = false }, title = { Text("Debug log") },
        text = {
            Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
                Text(DebugLog.dump().ifBlank { "(empty)" }, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
            }
        },
        confirmButton = { TextButton({ showLog = false }) { Text("Close") } })
}
