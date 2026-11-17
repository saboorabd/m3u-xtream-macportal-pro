package com.m3upro

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortalScreen(vm: MainVm, init: Smart?, onBack: () -> Unit) {
    var paste by remember { mutableStateOf("") }
    var auto by remember { mutableStateOf(true) }
    var go by remember { mutableIntStateOf(0) }
    var name by remember { mutableStateOf(init?.name?.takeIf { it.startsWith("Portal") } ?: "") }
    var url by remember { mutableStateOf(init?.url ?: "") }
    var mac by remember { mutableStateOf(init?.mac ?: "") }
    var sn by remember { mutableStateOf(init?.sn ?: "") }
    var d1 by remember { mutableStateOf(init?.d1 ?: "") }
    var d2 by remember { mutableStateOf(init?.d2 ?: "") }
    val clip = LocalClipboardManager.current
    val macOk = Regex("^([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$").matches(mac.trim())

    fun fill(s: Smart) {
        if (s.url.isNotBlank()) url = s.url
        if (s.mac.isNotBlank()) mac = s.mac
        if (s.sn.isNotBlank()) sn = s.sn
        if (s.d1.isNotBlank()) d1 = s.d1
        if (s.d2.isNotBlank()) d2 = s.d2
    }
    fun onPaste(t: String) {
        val jump = t.length - paste.length
        paste = t
        val s = SmartDetect.detect(t)
        if (s.kind == "portal") {
            fill(s)
            if (auto && jump >= 15 && s.complete) go++
        }
    }

    // pasted a full block => fetch automatically (after the fields were filled)
    LaunchedEffect(go) {
        if (go > 0) {
            delay(350)
            vm.portalLogin(name.trim(), PortalCfg(url.trim(), mac.trim(), d1.trim(), d2.trim(), sn.trim()), onBack)
        }
    }

    Scaffold(topBar = {
        TopAppBar(title = { Text("MAC portal") }, navigationIcon = { IconButton(onBack) { Icon(Icons.Default.ArrowBack, "Back") } })
    }) { pad ->
        Column(Modifier.padding(pad).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Paste all details", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text("Paste the whole block: URL, MAC, serial, device IDs. The fields below fill themselves.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(paste, { onPaste(it) }, Modifier.fillMaxWidth().padding(top = 8.dp),
                        placeholder = { Text("Mac url: http://…\nMac address: 00:1A:79:…\nSerial no: …\nDevice id 1: …\nDevice id 2: …") },
                        minLines = 3, maxLines = 6, shape = RoundedCornerShape(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                        OutlinedButton({ clip.getText()?.text?.let { onPaste(it) } }) { Text("Paste") }
                        Spacer(Modifier.width(12.dp))
                        Checkbox(auto, { auto = it })
                        Text("Fetch automatically", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Name (optional)") }, singleLine = true)
            OutlinedTextField(url, { url = it }, Modifier.fillMaxWidth(), label = { Text("Portal URL") },
                placeholder = { Text("http://host:port/c/") }, singleLine = true)
            OutlinedTextField(mac, { mac = it.uppercase() }, Modifier.fillMaxWidth(), label = { Text("MAC address") },
                placeholder = { Text("00:1A:79:XX:XX:XX") }, singleLine = true,
                isError = mac.isNotEmpty() && !macOk,
                supportingText = { if (mac.isNotEmpty() && !macOk) Text("Format: 00:1A:79:AA:BB:CC") })
            OutlinedTextField(sn, { sn = it }, Modifier.fillMaxWidth(), label = { Text("Serial number") }, singleLine = true)
            OutlinedTextField(d1, { d1 = it }, Modifier.fillMaxWidth(), label = { Text("Device ID 1") }, singleLine = true)
            OutlinedTextField(d2, { d2 = it }, Modifier.fillMaxWidth(), label = { Text("Device ID 2") }, singleLine = true)
            Button(
                onClick = { vm.portalLogin(name.trim(), PortalCfg(url.trim(), mac.trim(), d1.trim(), d2.trim(), sn.trim()), onBack) },
                enabled = url.isNotBlank() && macOk, modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(16.dp)
            ) { Text("Fetch channels", fontWeight = FontWeight.Bold) }
            Text("Account expiry is shown on the playlist card. Use \"Get real stream links\" in the editor to turn portal channels into direct URLs.",
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
                OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Name (optional)") }, singleLine = true)
                OutlinedTextField(s, { s = it }, Modifier.fillMaxWidth(), label = { Text("Server URL") }, placeholder = { Text("http://host:port") }, singleLine = true)
                OutlinedTextField(u, { u = it }, Modifier.fillMaxWidth(), label = { Text("Username") }, singleLine = true)
                OutlinedTextField(p, { p = it }, Modifier.fillMaxWidth(), label = { Text("Password") }, singleLine = true,
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
