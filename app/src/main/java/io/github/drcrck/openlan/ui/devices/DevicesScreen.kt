package io.github.drcrck.openlan.ui.devices

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.drcrck.openlan.R
import io.github.drcrck.openlan.data.network.NetworkInfo
import io.github.drcrck.openlan.ui.common.CardShape
import io.github.drcrck.openlan.ui.common.Radar
import io.github.drcrck.openlan.ui.common.RoleTags
import io.github.drcrck.openlan.ui.common.SectionCard
import io.github.drcrck.openlan.ui.common.TypeAvatar
import io.github.drcrck.openlan.ui.common.labelRes
import io.github.drcrck.openlan.ui.common.macNote
import io.github.drcrck.openlan.ui.theme.Brand
import io.github.drcrck.openlan.ui.theme.Mono
import java.text.DateFormat
import java.util.Date

@Composable
fun DevicesScreen(
    state: DevicesUiState,
    onScan: () -> Unit,
    onOpenDevice: (Long) -> Unit,
    onOpenTraceroute: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenAbout: () -> Unit,
    onMessageShown: () -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it.resolve(context))
            onMessageShown()
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbar, Modifier.navigationBarsPadding()) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { if (!state.scanning) onScan() },
                modifier = Modifier.navigationBarsPadding(),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                icon = {
                    if (state.scanning) CircularProgressIndicator(
                        Modifier.size(20.dp), strokeWidth = 2.5.dp, color = MaterialTheme.colorScheme.onPrimary,
                    ) else Icon(Icons.Filled.Radar, contentDescription = null)
                },
                text = { Text(stringResource(if (state.scanning) R.string.scanning else R.string.action_scan)) },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 112.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "hero") {
                Hero(state, onOpenTraceroute, onOpenDiagnostics, onOpenAbout)
            }

            if (state.network != null) {
                item(key = "net") { NetworkDetails(state.network, state.apVendor, Modifier.padding(horizontal = 16.dp)) }
            }

            if (state.rangeLimited && !state.scanning) {
                item(key = "range") {
                    Text(
                        stringResource(R.string.range_limited),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                }
            }

            if (state.devices.isEmpty() && !state.scanning && state.network != null) {
                item(key = "empty") { EmptyState() }
            }

            // Due gruppi espliciti, ciascuno ordinato per IP
            val (onlineRows, offlineRows) = state.devices.partition { it.online }
            if (onlineRows.isNotEmpty()) item(key = "h-on") {
                GroupHeader(stringResource(R.string.group_online), onlineRows.size, Brand.Online)
            }
            items(onlineRows, key = { it.id }) { row ->
                DeviceCard(row, onClick = { onOpenDevice(row.id) }, Modifier.padding(horizontal = 16.dp))
            }
            if (offlineRows.isNotEmpty()) item(key = "h-off") {
                GroupHeader(stringResource(R.string.group_offline), offlineRows.size, Brand.Offline)
            }
            items(offlineRows, key = { it.id }) { row ->
                DeviceCard(row, onClick = { onOpenDevice(row.id) }, Modifier.padding(horizontal = 16.dp))
            }
        }
    }
}

/** Intestazione a tutta larghezza: nome rete, conteggi e radar. */
@Composable
private fun Hero(
    state: DevicesUiState,
    onOpenTraceroute: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    val net = state.network
    val onWhite = Color.White
    val dim = Color.White.copy(alpha = 0.72f)
    Box(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
            .background(Brand.heroGradient)
    ) {
        Column(Modifier.statusBarsPadding().padding(start = 20.dp, end = 8.dp, top = 4.dp, bottom = 22.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    buildBrand(),
                    style = MaterialTheme.typography.titleMedium,
                    color = onWhite,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onOpenTraceroute) {
                    Icon(Icons.Filled.Route, stringResource(R.string.traceroute), tint = onWhite)
                }
                IconButton(onClick = onOpenDiagnostics) {
                    Icon(Icons.Filled.BugReport, stringResource(R.string.diagnostics), tint = onWhite)
                }
                IconButton(onClick = onOpenAbout) {
                    Icon(Icons.Filled.Info, stringResource(R.string.about), tint = onWhite)
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(end = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (net == null) Icons.Filled.WifiOff else Icons.Filled.Wifi, null,
                            tint = Brand.TealBright, modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            stringResource(if (net?.isWifi == false) R.string.net_wired else R.string.net_wifi).uppercase(),
                            style = MaterialTheme.typography.labelMedium,
                            color = Brand.TealBright,
                        )
                    }
                    Text(
                        when {
                            net == null -> stringResource(R.string.no_network)
                            net.ssid != null -> net.ssid
                            net.isWifi -> stringResource(R.string.ssid_unknown)
                            else -> "Ethernet"
                        },
                        style = MaterialTheme.typography.headlineMedium,
                        color = onWhite,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (net != null) {
                        Text(
                            "${net.ipAddress}/${net.prefixLength}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = Mono,
                            color = dim,
                        )
                    } else {
                        Text(stringResource(R.string.no_network_hint), style = MaterialTheme.typography.bodyMedium, color = dim)
                    }
                    if (net != null && net.isWifi && net.ssid == null) {
                        Text(
                            stringResource(R.string.ssid_permission_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = Brand.Amber,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
                Radar(
                    points = state.devices.map { it.ip to it.online },
                    scanning = state.scanning,
                    modifier = Modifier.padding(end = 12.dp).size(108.dp),
                )
            }

            Spacer(Modifier.height(16.dp))
            if (state.scanning) {
                val p = state.progress
                Text(
                    if (p == null) stringResource(R.string.starting)
                    else stringResource(p.phase.labelRes) + " · ${p.done}/${p.total}",
                    style = MaterialTheme.typography.bodySmall,
                    color = dim,
                )
                Spacer(Modifier.height(6.dp))
                val mod = Modifier.fillMaxWidth().padding(end = 12.dp).height(6.dp).clip(RoundedCornerShape(50))
                if (p == null) LinearProgressIndicator(mod, color = Brand.TealBright, trackColor = Color.White.copy(alpha = 0.15f))
                else LinearProgressIndicator(
                    progress = { p.fraction }, modifier = mod,
                    color = Brand.TealBright, trackColor = Color.White.copy(alpha = 0.15f),
                    drawStopIndicator = {},
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Stat(state.devices.count { it.online }.toString(), stringResource(R.string.stat_online))
                    Stat(state.devices.size.toString(), stringResource(R.string.stat_known))
                    Stat(
                        state.lastScanAt?.let { DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(it)) } ?: "—",
                        stringResource(R.string.stat_last_scan),
                    )
                }
            }
        }
    }
}

@Composable
private fun buildBrand() = androidx.compose.ui.text.buildAnnotatedString {
    pushStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold))
    append("Open")
    pushStyle(androidx.compose.ui.text.SpanStyle(color = Brand.TealBright))
    append("LAN")
    pop()
    pop()
    append(" Scanner")
}

@Composable
private fun Stat(value: String, label: String) {
    Column {
        Text(value, style = MaterialTheme.typography.headlineSmall, color = Color.White)
        Text(label, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.7f))
    }
}

@Composable
private fun NetworkDetails(network: NetworkInfo, apVendor: String?, modifier: Modifier) {
    SectionCard(modifier) {
        network.gateway?.let { Tile("Gateway", it) }
        if (network.dnsServers.isNotEmpty()) Tile("DNS", network.dnsServers.joinToString(", "))
        network.dhcpServer?.let { Tile("DHCP", it) }
        network.bssid?.let { Tile(stringResource(R.string.access_point), it, apVendor) }
        val radio = listOfNotNull(
            network.rssi?.let { "$it dBm" },
            network.frequencyMhz?.let { if (it > 4900) "5/6 GHz" else "2.4 GHz" },
            network.linkSpeedMbps?.let { "$it Mbps" },
        )
        if (radio.isNotEmpty()) Tile(stringResource(R.string.signal), radio.joinToString(" · "))
    }
}

@Composable
private fun Tile(label: String, value: String, extra: String? = null) {
    Row(verticalAlignment = Alignment.Top) {
        Text(
            label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(96.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(value, style = MaterialTheme.typography.bodySmall, fontFamily = Mono)
            extra?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Filled.Radar, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.empty_title), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
private fun GroupHeader(text: String, count: Int, dot: Color) {
    Row(
        Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(RoundedCornerShape(50)).background(dot))
        Spacer(Modifier.width(8.dp))
        Text(text.uppercase(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(6.dp))
        Text(count.toString(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun DeviceCard(row: DeviceRow, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth().clip(CardShape).clickable(onClick = onClick),
        shape = CardShape,
        colors = CardDefaults.cardColors(
            containerColor = if (row.online) MaterialTheme.colorScheme.surfaceContainerHigh
            else MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            TypeAvatar(row.type, row.online)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    row.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (row.online) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(row.ip, style = MaterialTheme.typography.bodySmall, fontFamily = Mono, color = MaterialTheme.colorScheme.primary)
                    row.subtitle?.let {
                        Text(
                            "  ·  $it", style = MaterialTheme.typography.bodySmall, maxLines = 1,
                            overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                val mac = row.device.mac
                if (mac != null) {
                    Text(
                        mac + (macNote(row.device)?.let { "  · $it" } ?: ""),
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = Mono,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                RoleTags(row.tags, Modifier.padding(top = 2.dp))
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight, null,
                tint = MaterialTheme.colorScheme.outline,
            )
        }
    }
}
