package com.m3upro

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortalScreen(vm: MainVm, onBack: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var mac by remember { mutableStateOf("") }
    var d1 by remember { mutableStateOf("") }
    var d2 by remember { mutableStateOf("") }
    var sn by remember { mutableStateOf("") }
    val macOk = Regex("^([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$").matches(mac.trim())

    Scaffold(topBar = {
        TopAppBar(title = { Text("MAC portal login") },
            navigationIcon = { IconButton(onBack) { Icon(Icons.Default.ArrowBack, "Back") } })
    }) { pad ->
        Column(Modifier.padding(pad).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Name (optional)") }, singleLine = true)
            OutlinedTextField(url, { url = it }, Modifier.fillMaxWidth(), label = { Text("Portal URL") },
                placeholder = { Text("http://host:port/c/") }, singleLine = true)
            OutlinedTextField(mac, { mac = it.uppercase() }, Modifier.fillMaxWidth(), label = { Text("MAC address") },
                placeholder = { Text("00:1A:79:XX:XX:XX") }, singleLine = true,
                isError = mac.isNotEmpty() && !macOk,
                supportingText = { if (mac.isNotEmpty() && !macOk) Text("Format: 00:1A:79:AA:BB:CC") })
            OutlinedTextField(d1, { d1 = it }, Modifier.fillMaxWidth(), label = { Text("Device ID 1") }, singleLine = true)
            OutlinedTextField(d2, { d2 = it }, Modifier.fillMaxWidth(), label = { Text("Device ID 2") }, singleLine = true)
            OutlinedTextField(sn, { sn = it }, Modifier.fillMaxWidth(), label = { Text("Serial number") }, singleLine = true)
            Button(
                onClick = { vm.portalLogin(name.trim(), PortalCfg(url.trim(), mac.trim(), d1.trim(), d2.trim(), sn.trim()), onBack) },
                enabled = url.isNotBlank() && macOk, modifier = Modifier.fillMaxWidth().height(52.dp)
            ) { Text("Login & import channels") }
            Text("After import the account expiry is shown on the playlist card, and every channel can be checked.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun XtreamDialog(vm: MainVm, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var s by remember { mutableStateOf("") }
    var u by remember { mutableStateOf("") }
    var p by remember { mutableStateOf("") }
    var ext by remember { mutableStateOf("m3u8") }
    var movies by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Xtream Codes login") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name (optional)") }, singleLine = true)
                OutlinedTextField(s, { s = it }, label = { Text("Server URL") }, placeholder = { Text("http://host:port") }, singleLine = true)
                OutlinedTextField(u, { u = it }, label = { Text("Username") }, singleLine = true)
                OutlinedTextField(p, { p = it }, label = { Text("Password") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation())
                Text("Stream format", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(ext == "m3u8", { ext = "m3u8" }, { Text("m3u8 (HLS)") })
                    FilterChip(ext == "ts", { ext = "ts" }, { Text("ts") })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(movies, { movies = it })
                    Text("Include movies (VOD)")
                }
            }
        },
        confirmButton = {
            TextButton({ onDismiss(); vm.xtreamLogin(name.trim(), s.trim(), u.trim(), p.trim(), ext, movies) },
                enabled = s.isNotBlank() && u.isNotBlank() && p.isNotBlank()) { Text("Login") }
        },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } }
    )
}
