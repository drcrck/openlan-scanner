package io.github.drcrck.openlan.ui.devices

import android.text.format.DateUtils
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.drcrck.openlan.R
import io.github.drcrck.openlan.data.network.NetworkInfo
import io.github.drcrck.openlan.ui.common.CardShape
import io.github.drcrck.openlan.ui.common.RoleTags
import io.github.drcrck.openlan.ui.common.TypeAvatar
import io.github.drcrck.openlan.ui.common.labelRes
import io.github.drcrck.openlan.ui.common.macNote
import io.github.drcrck.openlan.ui.theme.Brand
import io.github.drcrck.openlan.ui.theme.Mono
import java.text.DateFormat
import java.util.Date

/** Oltre questa età l'ultima scansione viene segnalata come vecchia. */
private const val STALE_MS = 15 * 60_000L

@Composable
fun DevicesScreen(
    state: DevicesUiState,
    onScan: () -> Unit,
    onStop: () -> Unit,
    onOpenDevice: (Long) -> Unit,
    onOpenTraceroute: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenAbout: () -> Unit,
    onMessageShown: () -> Unit,
    onQueryChange: (String) -> Unit,
    onToggleFilter: (ListFilter) -> Unit,
    onClearFilters: () -> Unit,
    /** Dispositivo aperto nel pannello di destra (layout a due colonne). */
    selectedId: Long? = null,
) {
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it.resolve(context))
            onMessageShown()
        }
    }

    // Gruppi meno utili chiusi di default: si aprono con un tocco
    var showUnidentified by rememberSaveable { mutableStateOf(false) }
    var showOffline by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbar, Modifier.navigationBarsPadding()) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { if (state.scanning) onStop() else onScan() },
                modifier = Modifier.navigationBarsPadding(),
                containerColor = if (state.scanning) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primary,
                contentColor = if (state.scanning) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onPrimary,
                icon = { Icon(if (state.scanning) Icons.Filled.Stop else Icons.Filled.Radar, contentDescription = null) },
                text = { Text(stringResource(if (state.scanning) R.string.action_stop_scan else R.string.action_scan)) },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 112.dp),
        ) {
            item(key = "hero") {
                Hero(state, onOpenTraceroute, onOpenDiagnostics, onOpenAbout, onQueryChange, onToggleFilter)
            }

            if (state.network != null && state.results == null) {
                item(key = "net") {
                    NetworkPanel(
                        state.network, state.apVendor,
                        roleDevice = { ip -> state.devices.firstOrNull { it.online && it.ip == ip }?.id },
                        onOpenDevice = onOpenDevice,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
                    )
                }
            }

            if (state.rangeLimited && !state.scanning) {
                item(key = "range") {
                    Text(
                        stringResource(R.string.range_limited),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    )
                }
            }

            if (state.devices.isEmpty() && !state.scanning && state.network != null) {
                item(key = "empty") { EmptyState() }
            }

            val results = state.results
            if (results != null) {
                item(key = "res-h") {
                    ResultsHeader(results.size, onClearFilters)
                }
                if (results.isEmpty()) item(key = "res-empty") {
                    Text(
                        stringResource(R.string.filter_no_results),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                    )
                }
                deviceItems("res", results, selectedId, onOpenDevice)
            } else {
                val s = state.sections
                if (s.infra.isNotEmpty()) {
                    item(key = "h-infra") { GroupHeader(stringResource(R.string.group_infra), s.infra.size, Brand.Teal) }
                    deviceItems("infra", s.infra, selectedId, onOpenDevice)
                }
                if (s.identified.isNotEmpty()) {
                    item(key = "h-id") { GroupHeader(stringResource(R.string.group_identified), s.identified.size, Brand.Online) }
                    deviceItems("id", s.identified, selectedId, onOpenDevice)
                }
                if (s.unidentified.isNotEmpty()) {
                    item(key = "h-unid") {
                        CollapsibleHeader(
                            stringResource(R.string.group_unidentified), s.unidentified.size, Brand.Offline,
                            expanded = showUnidentified, onToggle = { showUnidentified = !showUnidentified },
                            hint = stringResource(R.string.group_unidentified_hint),
                        )
                    }
                    if (showUnidentified) compactItems("unid", s.unidentified, selectedId, onOpenDevice)
                }
                if (s.offline.isNotEmpty()) {
                    item(key = "h-off") {
                        CollapsibleHeader(
                            stringResource(R.string.group_offline), s.offline.size, Brand.Offline,
                            expanded = showOffline, onToggle = { showOffline = !showOffline },
                        )
                    }
                    if (showOffline) deviceItems("off", s.offline, selectedId, onOpenDevice)
                }
            }
        }
    }
}

/** Righe complete per i dispositivi identificati; righe compatte per quelli noti solo per IP. */
private fun LazyListScope.deviceItems(prefix: String, rows: List<DeviceRow>, selectedId: Long?, onOpen: (Long) -> Unit) {
    items(rows, key = { "$prefix-${it.id}" }) { row ->
        if (row.identified) DeviceCard(row, selected = row.id == selectedId, onClick = { onOpen(row.id) })
        else CompactRow(row, selected = row.id == selectedId, onClick = { onOpen(row.id) })
    }
}

private fun LazyListScope.compactItems(prefix: String, rows: List<DeviceRow>, selectedId: Long?, onOpen: (Long) -> Unit) {
    items(rows, key = { "$prefix-${it.id}" }) { row ->
        CompactRow(row, selected = row.id == selectedId, onClick = { onOpen(row.id) })
    }
}

/** Intestazione: rete, età della scansione, ricerca e riepilogo per tipo (ogni voce filtra la lista). */
@Composable
private fun Hero(
    state: DevicesUiState,
    onOpenTraceroute: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenAbout: () -> Unit,
    onQueryChange: (String) -> Unit,
    onToggleFilter: (ListFilter) -> Unit,
) {
    val net = state.network
    val dim = Brand.HeroText
    var menu by remember { mutableStateOf(false) }
    Box(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
            .background(Brand.heroGradient)
    ) {
        Column(Modifier.statusBarsPadding().padding(top = 4.dp, bottom = 16.dp)) {
            // Barra: marchio + traceroute (con etichetta) + menu
            Row(Modifier.padding(start = 20.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    buildAnnotatedString {
                        pushStyle(SpanStyle(fontWeight = FontWeight.Bold)); append("Open")
                        pushStyle(SpanStyle(color = Brand.TealBright)); append("LAN"); pop(); pop()
                        append(" Scanner")
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onOpenTraceroute) {
                    Icon(Icons.Filled.Route, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.traceroute), color = Color.White)
                }
                Box {
                    IconButton(onClick = { menu = true }) {
                        Icon(Icons.Filled.MoreVert, stringResource(R.string.more_options), tint = Color.White)
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.diagnostics)) },
                            leadingIcon = { Icon(Icons.Filled.BugReport, null) },
                            onClick = { menu = false; onOpenDiagnostics() },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.about)) },
                            leadingIcon = { Icon(Icons.Filled.Info, null) },
                            onClick = { menu = false; onOpenAbout() },
                        )
                    }
                }
            }

            Column(Modifier.padding(horizontal = 20.dp)) {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (net == null) Icons.Filled.WifiOff else Icons.Filled.Wifi, null,
                        tint = Brand.TealBright, modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        when {
                            net == null -> stringResource(R.string.no_network)
                            net.ssid != null -> net.ssid
                            net.isWifi -> stringResource(R.string.ssid_unknown)
                            else -> "Ethernet"
                        },
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (net != null) {
                        Spacer(Modifier.width(10.dp))
                        Text("${net.ipAddress}/${net.prefixLength}", style = MaterialTheme.typography.bodyMedium, fontFamily = Mono, color = dim)
                    }
                }
                when {
                    net == null -> Text(stringResource(R.string.no_network_hint), style = MaterialTheme.typography.bodyMedium, color = dim)
                    state.scanning -> {
                        val p = state.progress
                        Spacer(Modifier.height(10.dp))
                        Text(
                            if (p == null) stringResource(R.string.starting)
                            else stringResource(p.phase.labelRes) + " · ${p.done}/${p.total}",
                            style = MaterialTheme.typography.bodySmall,
                            color = dim,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        )
                        Spacer(Modifier.height(6.dp))
                        val mod = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50))
                        if (p == null) LinearProgressIndicator(mod, color = Brand.TealBright, trackColor = Color.White.copy(alpha = 0.15f))
                        else LinearProgressIndicator(
                            progress = { p.fraction }, modifier = mod,
                            color = Brand.TealBright, trackColor = Color.White.copy(alpha = 0.15f), drawStopIndicator = {},
                        )
                    }
                    state.lastScanAt != null -> {
                        val age = System.currentTimeMillis() - state.lastScanAt
                        val time = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(state.lastScanAt))
                        val rel = if (age < 60_000) stringResource(R.string.just_now)
                        else DateUtils.getRelativeTimeSpanString(
                            state.lastScanAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS,
                        ).toString()
                        Text(
                            stringResource(R.string.scanned_at, time, rel),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (age > STALE_MS) Brand.Amber else dim,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    else -> Text(stringResource(R.string.never_scanned), style = MaterialTheme.typography.bodySmall, color = dim)
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

            if (state.devices.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                SearchField(state.query, onQueryChange, Modifier.padding(horizontal = 16.dp))
                Spacer(Modifier.height(10.dp))
                SummaryChips(state, onToggleFilter)
            }
        }
    }
}

@Composable
private fun SearchField(query: String, onChange: (String) -> Unit, modifier: Modifier) {
    TextField(
        value = query,
        onValueChange = onChange,
        modifier = modifier.fillMaxWidth().heightIn(min = 52.dp),
        singleLine = true,
        placeholder = { Text(stringResource(R.string.search_hint), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = { Icon(Icons.Filled.Search, null) },
        trailingIcon = {
            if (query.isNotEmpty()) IconButton(onClick = { onChange("") }) {
                Icon(Icons.Filled.Close, stringResource(R.string.search_clear))
            }
        },
        shape = RoundedCornerShape(50),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.White.copy(alpha = 0.14f),
            unfocusedContainerColor = Color.White.copy(alpha = 0.10f),
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            focusedPlaceholderColor = Brand.HeroText,
            unfocusedPlaceholderColor = Brand.HeroText,
            focusedLeadingIconColor = Brand.TealBright,
            unfocusedLeadingIconColor = Brand.HeroText,
            focusedTrailingIconColor = Color.White,
            unfocusedTrailingIconColor = Color.White,
            cursorColor = Brand.TealBright,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
    )
}

/** Riepilogo per tipo: "Infrastruttura 3 · Server 4 · NAS 3 · …". Ogni chip filtra la lista. */
@Composable
private fun SummaryChips(state: DevicesUiState, onToggle: (ListFilter) -> Unit) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        state.counts.forEach { (f, n) ->
            val selected = state.filter == f
            val label = stringResource(f.labelRes())
            Surface(
                onClick = { onToggle(f) },
                shape = RoundedCornerShape(50),
                color = if (selected) Brand.TealBright else Color.White.copy(alpha = 0.10f),
                contentColor = if (selected) Brand.Night else Color.White,
                modifier = Modifier.heightIn(min = 40.dp).semantics {
                    this.selected = selected
                },
            ) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(label, style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        n.toString(),
                        style = MaterialTheme.typography.labelLarge,
                        fontFamily = Mono,
                        fontWeight = FontWeight.Bold,
                        color = if (selected) Brand.Night else if (f == ListFilter.NEW) Brand.Amber else Brand.TealBright,
                    )
                }
            }
        }
    }
}

private fun ListFilter.labelRes(): Int = when (this) {
    ListFilter.INFRA -> R.string.filter_infra
    ListFilter.NEW -> R.string.filter_new
    ListFilter.SERVER -> R.string.filter_server
    ListFilter.NAS -> R.string.filter_nas
    ListFilter.COMPUTER -> R.string.filter_computer
    ListFilter.PRINTER -> R.string.filter_printer
    ListFilter.MEDIA -> R.string.filter_media
    ListFilter.MOBILE -> R.string.filter_mobile
    ListFilter.CAMERA -> R.string.filter_camera
    ListFilter.IOT -> R.string.filter_iot
    ListFilter.UNIDENTIFIED -> R.string.filter_unidentified
    ListFilter.OFFLINE -> R.string.filter_offline
}

/**
 * Dati di rete compressi in una riga ("Gateway 192.168.0.254 · DNS 192.168.0.2"): i dispositivi
 * con questi ruoli sono già in cima alla lista. Espandendo: ruoli toccabili, access point e segnale.
 */
@Composable
private fun NetworkPanel(
    network: NetworkInfo,
    apVendor: String?,
    roleDevice: (String) -> Long?,
    onOpenDevice: (Long) -> Unit,
    modifier: Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Surface(modifier.fillMaxWidth(), shape = CardShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable { expanded = !expanded }.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                    Text(stringResource(R.string.network_details), style = MaterialTheme.typography.labelLarge)
                    if (!expanded) {
                        Text(
                            listOfNotNull(
                                network.gateway?.let { "Gateway $it" },
                                network.dnsServers.firstOrNull()?.let { "DNS $it" },
                            ).joinToString("  ·  "),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = Mono,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Icon(
                    if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    stringResource(if (expanded) R.string.action_collapse else R.string.action_expand),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AnimatedVisibility(expanded) {
                Column(Modifier.padding(bottom = 10.dp)) {
                    network.gateway?.let { RoleLine("Gateway", it, roleDevice(it), onOpenDevice) }
                    network.dnsServers.forEach { RoleLine("DNS", it, roleDevice(it), onOpenDevice) }
                    network.dhcpServer?.let { RoleLine("DHCP", it, roleDevice(it), onOpenDevice) }
                    if (network.isWifi) {
                        HorizontalDivider(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            network.bssid?.let { Detail(stringResource(R.string.access_point), it, apVendor) }
                            val radio = listOfNotNull(
                                network.rssi?.let { "$it dBm" },
                                network.frequencyMhz?.let { if (it > 4900) "5/6 GHz" else "2.4 GHz" },
                                network.linkSpeedMbps?.let { "$it Mbps" },
                            )
                            if (radio.isNotEmpty()) Detail(stringResource(R.string.signal), radio.joinToString(" · "))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RoleLine(role: String, ip: String, deviceId: Long?, onOpen: (Long) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .then(if (deviceId != null) Modifier.clickable { onOpen(deviceId) } else Modifier)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(role, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(76.dp))
        Text(ip, style = MaterialTheme.typography.bodyMedium, fontFamily = Mono, modifier = Modifier.weight(1f))
        if (deviceId != null) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.outline)
    }
}

@Composable
private fun Detail(label: String, value: String, extra: String? = null) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, fontFamily = Mono)
        extra?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
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
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ResultsHeader(count: Int, onClear: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.filter_results, count),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        TextButton(onClick = onClear) { Text(stringResource(R.string.filter_clear)) }
    }
}

@Composable
private fun GroupHeader(text: String, count: Int, dot: Color) {
    Row(
        Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp).semantics(mergeDescendants = true) { heading() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(RoundedCornerShape(50)).background(dot))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.width(8.dp))
        Text(count.toString(), style = MaterialTheme.typography.titleSmall, fontFamily = Mono, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun CollapsibleHeader(text: String, count: Int, dot: Color, expanded: Boolean, onToggle: () -> Unit, hint: String? = null) {
    Row(
        Modifier.fillMaxWidth().padding(top = 12.dp).heightIn(min = 48.dp).clickable(onClick = onToggle)
            .padding(start = 20.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)
            .semantics(mergeDescendants = true) { heading() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(RoundedCornerShape(50)).background(dot))
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.width(8.dp))
                Text(count.toString(), style = MaterialTheme.typography.titleSmall, fontFamily = Mono, color = MaterialTheme.colorScheme.primary)
            }
            if (hint != null && !expanded) {
                Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Icon(
            if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
            stringResource(if (expanded) R.string.action_collapse else R.string.action_expand),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun rowColors(selected: Boolean, online: Boolean) = when {
    selected -> MaterialTheme.colorScheme.primaryContainer
    online -> MaterialTheme.colorScheme.surfaceContainerHigh
    else -> MaterialTheme.colorScheme.surfaceContainerLow
}

/** Scheda di un dispositivo identificato. TalkBack la legge come un'unica voce con lo stato. */
@Composable
private fun DeviceCard(row: DeviceRow, selected: Boolean, onClick: () -> Unit) {
    val status = stringResource(if (row.online) R.string.status_online else R.string.status_offline)
    Surface(
        onClick = onClick,
        shape = CardShape,
        color = rowColors(selected, row.online),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
            .semantics(mergeDescendants = true) { stateDescription = status },
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            TypeAvatar(row.type, row.online)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        row.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                        color = if (row.online) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (row.isNew) {
                        Spacer(Modifier.width(8.dp))
                        NewBadge()
                    }
                }
                Text(
                    buildAnnotatedString {
                        pushStyle(SpanStyle(fontFamily = Mono, color = MaterialTheme.colorScheme.primary)); append(row.ip); pop()
                        row.subtitle?.let { append("  ·  $it") }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val mac = row.device.mac
                if (mac != null) {
                    Text(
                        buildAnnotatedString {
                            pushStyle(SpanStyle(fontFamily = Mono)); append(mac); pop()
                            macNote(row.device)?.let { append("  ·  $it") }
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                RoleTags(row.tags, Modifier.padding(top = 2.dp))
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.outline)
        }
    }
}

/** Riga compatta per gli host noti solo per IP: niente nome da ripetere, metà dell'altezza. */
@Composable
private fun CompactRow(row: DeviceRow, selected: Boolean, onClick: () -> Unit) {
    val status = stringResource(if (row.online) R.string.status_online else R.string.status_offline)
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = rowColors(selected, row.online),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp)
            .semantics(mergeDescendants = true) { stateDescription = status },
    ) {
        Row(Modifier.heightIn(min = 52.dp).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(8.dp).clip(RoundedCornerShape(50))
                    .background(if (row.online) Brand.Online else Brand.Offline)
            )
            Spacer(Modifier.width(14.dp))
            Text(row.ip, style = MaterialTheme.typography.bodyMedium, fontFamily = Mono, modifier = Modifier.width(132.dp))
            Text(
                row.device.vendor ?: row.device.mac ?: stringResource(R.string.no_identity),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (row.isNew) { NewBadge(); Spacer(Modifier.width(6.dp)) }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
private fun NewBadge() {
    Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.tertiaryContainer) {
        Text(
            stringResource(R.string.badge_new),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 1.dp),
        )
    }
}
