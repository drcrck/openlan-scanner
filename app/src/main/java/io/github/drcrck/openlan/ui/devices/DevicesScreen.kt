package io.github.drcrck.openlan.ui.devices

import android.text.format.DateUtils
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.ViewList
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
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
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
import io.github.drcrck.openlan.scanner.DeviceType
import io.github.drcrck.openlan.ui.common.CardShape
import io.github.drcrck.openlan.ui.common.Role
import io.github.drcrck.openlan.ui.common.TypeAvatar
import io.github.drcrck.openlan.ui.common.color
import io.github.drcrck.openlan.ui.common.labelRes
import io.github.drcrck.openlan.ui.detail.PortActions
import io.github.drcrck.openlan.ui.theme.Brand
import io.github.drcrck.openlan.ui.theme.Mono
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import androidx.compose.ui.semantics.Role as SemRole

/** Oltre questa età l'ultima scansione viene segnalata come vecchia. */
private const val STALE_MS = 15 * 60_000L

@OptIn(ExperimentalFoundationApi::class)
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
    onModeChange: (ViewMode) -> Unit,
    /** Dispositivo aperto nel pannello di destra (layout a due colonne). */
    selectedId: Long? = null,
) {
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it.resolve(context))
            onMessageShown()
        }
    }

    // Tocco lungo su una riga: copia l'IP senza aprire la scheda
    val onCopyIp: (String) -> Unit = { ip ->
        PortActions.copy(context, ip)
        scope.launch { snackbar.showSnackbar(context.getString(R.string.msg_copied, ip)) }
    }

    // Stato aperto/chiuso dei gruppi. Di default chiusi solo "non identificati" e "offline".
    var toggled by rememberSaveable { mutableStateOf("") }
    fun isOpen(g: DeviceGroup) = (g.kind.name in toggled.split(',')) == g.collapsedByDefault
    fun toggle(g: DeviceGroup) {
        val set = toggled.split(',').filter { it.isNotEmpty() }.toMutableSet()
        if (!set.add(g.kind.name)) set.remove(g.kind.name)
        toggled = set.joinToString(",")
    }

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
        Column(Modifier.fillMaxSize().padding(padding)) {
            // Fascia fissa sotto la barra di stato: il contenuto che scorre non ci passa mai sotto
            Box(Modifier.fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars).background(Brand.Night))
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 120.dp),
            ) {
                item(key = "hero") { Hero(state, onOpenTraceroute, onOpenDiagnostics, onOpenAbout) }

                // Ricerca, scelta della vista e filtri restano agganciati in alto mentre si scorre
                if (state.devices.isNotEmpty()) {
                    stickyHeader(key = "controls") { Controls(state, onQueryChange, onToggleFilter, onModeChange) }
                    // Riepilogo per tipo: tutti i chip visibili su più righe (niente scorrimento laterale)
                    item(key = "chips") { SummaryChips(state, onToggleFilter) }
                }

                if (state.network != null && !state.filtering) {
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

                val flat = state.flat
                if (flat != null) {
                    item(key = "flat-h") { FlatHeader(state, flat.size, onClearFilters) }
                    if (flat.isEmpty() && state.filtering) item(key = "flat-empty") {
                        Text(
                            stringResource(R.string.filter_no_results),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                        )
                    }
                    rows("flat", flat, selectedId, onOpenDevice, onCopyIp)
                } else {
                    state.groups.forEach { g ->
                        val open = isOpen(g)
                        item(key = "h-${g.kind}") { GroupHeader(g, expanded = open, onToggle = { toggle(g) }) }
                        if (open) rows(g.kind.name, g.rows, selectedId, onOpenDevice, onCopyIp)
                    }
                }
            }
        }
    }
}

private fun LazyListScope.rows(
    prefix: String,
    rows: List<DeviceRow>,
    selectedId: Long?,
    onOpen: (Long) -> Unit,
    onCopyIp: (String) -> Unit,
) {
    items(rows, key = { "$prefix-${it.id}" }) { row ->
        DeviceRowItem(row, selected = row.id == selectedId, onClick = { onOpen(row.id) }, onLongClick = { onCopyIp(row.ip) })
    }
}

/** Intestazione: marchio, rete, età della scansione o avanzamento. */
@Composable
private fun Hero(
    state: DevicesUiState,
    onOpenTraceroute: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    val net = state.network
    val dim = Brand.HeroText
    var menu by remember { mutableStateOf(false) }
    // Senza dispositivi non c'è la barra dei filtri sotto: l'intestazione chiude con gli angoli arrotondati
    val radius = if (state.devices.isEmpty()) 28.dp else 0.dp
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = radius, bottomEnd = radius))
            .background(Brand.heroVertical)
            .padding(top = 4.dp, bottom = if (state.devices.isEmpty()) 20.dp else 6.dp)
    ) {
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
                    Spacer(Modifier.height(4.dp))
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
    }
}

/** Barra agganciata: ricerca + scelta della vista [Gruppi | IP], poi il riepilogo per tipo (ogni chip filtra). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Controls(
    state: DevicesUiState,
    onQueryChange: (String) -> Unit,
    onToggleFilter: (ListFilter) -> Unit,
    onModeChange: (ViewMode) -> Unit,
) {
    Column(
        Modifier.fillMaxWidth()
            .background(Brand.Deep)
            .padding(top = 6.dp, bottom = 10.dp)
    ) {
        SearchField(state.query, onQueryChange, Modifier.fillMaxWidth().padding(horizontal = 16.dp))
        Spacer(Modifier.height(10.dp))
        // Scelta della vista, sempre a portata di mano anche dopo aver scrollato
        Row(verticalAlignment = Alignment.CenterVertically) {
            SingleChoiceSegmentedButtonRow(Modifier.padding(start = 16.dp, end = 8.dp)) {
                val colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = Brand.TealBright,
                    activeContentColor = Brand.Night,
                    activeBorderColor = Brand.TealBright,
                    inactiveContainerColor = Color.Transparent,
                    inactiveContentColor = Color.White,
                    inactiveBorderColor = Color.White.copy(alpha = 0.35f),
                )
                ViewMode.entries.forEachIndexed { i, m ->
                    SegmentedButton(
                        selected = state.mode == m,
                        onClick = { onModeChange(m) },
                        shape = SegmentedButtonDefaults.itemShape(i, ViewMode.entries.size),
                        colors = colors,
                        icon = {
                            Icon(
                                if (m == ViewMode.GROUPS) Icons.Filled.ViewAgenda else Icons.AutoMirrored.Filled.ViewList,
                                null, Modifier.size(18.dp),
                            )
                        },
                        label = {
                            Text(stringResource(if (m == ViewMode.GROUPS) R.string.view_groups else R.string.view_ip), maxLines = 1)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchField(query: String, onChange: (String) -> Unit, modifier: Modifier) {
    TextField(
        value = query,
        onValueChange = onChange,
        modifier = modifier.heightIn(min = 52.dp),
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

/** Riepilogo per tipo: "Infrastruttura 3 · Server 4 · NAS 3 · …", va a capo. Ogni chip filtra la lista. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SummaryChips(state: DevicesUiState, onToggle: (ListFilter) -> Unit) {
    FlowRow(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
            .background(Brand.Deep)
            .padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        state.counts.forEach { (f, n) ->
            val selected = state.filter == f
            Surface(
                onClick = { onToggle(f) },
                shape = RoundedCornerShape(50),
                color = if (selected) Brand.TealBright else Color.White.copy(alpha = 0.10f),
                contentColor = if (selected) Brand.Night else Color.White,
                // Visivamente 36dp; Surface cliccabile garantisce comunque 48dp di area di tocco
                modifier = Modifier.heightIn(min = 36.dp).semantics { this.selected = selected },
            ) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(f.labelRes()), style = MaterialTheme.typography.labelLarge)
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
    ListFilter.OTHER -> R.string.filter_other
    ListFilter.UNIDENTIFIED -> R.string.filter_unidentified
    ListFilter.OFFLINE -> R.string.filter_offline
}

/** Colore del pallino del gruppo: lo stesso delle icone di quel tipo. */
private fun ListFilter.groupColor(): Color = when (this) {
    ListFilter.INFRA -> Brand.Teal
    ListFilter.SERVER -> DeviceType.SERVER.color()
    ListFilter.NAS -> DeviceType.NAS.color()
    ListFilter.COMPUTER -> DeviceType.COMPUTER.color()
    ListFilter.PRINTER -> DeviceType.PRINTER.color()
    ListFilter.MEDIA -> DeviceType.TV.color()
    ListFilter.MOBILE -> DeviceType.PHONE.color()
    ListFilter.CAMERA -> DeviceType.CAMERA.color()
    ListFilter.IOT -> DeviceType.IOT.color()
    ListFilter.NEW -> Brand.Amber
    ListFilter.OTHER, ListFilter.UNIDENTIFIED, ListFilter.OFFLINE -> Brand.Offline
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
    val stateText = stringResource(if (expanded) R.string.state_expanded else R.string.state_collapsed)
    Surface(modifier.fillMaxWidth(), shape = CardShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 52.dp)
                    .clickable(role = SemRole.Button) { expanded = !expanded }
                    .semantics { stateDescription = stateText }
                    .padding(horizontal = 16.dp),
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
                        )
                    }
                }
                Icon(
                    if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null,
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
            .then(if (deviceId != null) Modifier.clickable(role = SemRole.Button) { onOpen(deviceId) } else Modifier)
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

/** Intestazione della lista piatta: conteggio, "Azzera" se c'è un filtro, e cosa vuol dire "Nuovi". */
@Composable
private fun FlatHeader(state: DevicesUiState, count: Int, onClear: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 48.dp)) {
            val results = pluralStringResource(R.plurals.filter_results, count, count)
            Text(
                when {
                    state.filter != null -> stringResource(state.filter.labelRes()) + " · " + results
                    state.filtering -> results
                    else -> pluralStringResource(R.plurals.devices_by_ip, count, count)
                },
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            if (state.filtering) TextButton(onClick = onClear) { Text(stringResource(R.string.filter_clear)) }
        }
        if (state.filter == ListFilter.NEW) {
            Text(
                stringResource(R.string.filter_new_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun GroupHeader(g: DeviceGroup, expanded: Boolean, onToggle: () -> Unit) {
    val stateText = stringResource(if (expanded) R.string.state_expanded else R.string.state_collapsed)
    Row(
        Modifier.fillMaxWidth().padding(top = 10.dp).heightIn(min = 48.dp)
            .clickable(role = SemRole.Button, onClick = onToggle)
            .semantics(mergeDescendants = true) { heading(); stateDescription = stateText }
            .padding(start = 20.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(RoundedCornerShape(50)).background(g.kind.groupColor()))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(g.kind.labelRes()), style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.width(8.dp))
                Text(g.rows.size.toString(), style = MaterialTheme.typography.titleSmall, fontFamily = Mono, color = MaterialTheme.colorScheme.primary)
            }
            if (g.kind == ListFilter.UNIDENTIFIED && !expanded) {
                Text(
                    stringResource(R.string.group_unidentified_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Icon(
            if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Una riga per dispositivo, due righe di testo: nome (+ ruoli) e "IP · produttore · MAC".
 * Gli host senza nome mostrano l'IP come titolo, senza ripeterlo. Tocco lungo = copia IP.
 * TalkBack legge una frase breve; copia IP e copia MAC sono azioni dedicate.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DeviceRowItem(row: DeviceRow, selected: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val status = stringResource(if (row.online) R.string.status_online else R.string.status_offline)
    val typeLabel = stringResource(row.type.labelRes)
    val roles = row.tags.joinToString(", ") { roleLabel(it, context) }
    val spoken = listOfNotNull(row.title, if (row.identified) row.ip else null, typeLabel, roles.ifEmpty { null }, status)
        .joinToString(", ")
    val copyLabel = stringResource(R.string.action_copy_ip)
    val copyMacLabel = stringResource(R.string.action_copy_mac)
    val bg = when {
        selected -> MaterialTheme.colorScheme.secondaryContainer
        row.online -> MaterialTheme.colorScheme.surfaceContainerHigh
        else -> MaterialTheme.colorScheme.surfaceContainerLow
    }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(18.dp)).background(bg)
            .combinedClickable(
                onClick = onClick,
                onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onLongClick() },
                onLongClickLabel = copyLabel,
            )
            .clearAndSetSemantics {
                contentDescription = spoken
                role = SemRole.Button
                this.selected = selected
                customActions = listOfNotNull(
                    CustomAccessibilityAction(copyLabel) { onLongClick(); true },
                    row.device.mac?.let { mac ->
                        CustomAccessibilityAction(copyMacLabel) { PortActions.copy(context, mac); true }
                    },
                )
            }
            .heightIn(min = 64.dp)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TypeAvatar(row.type, row.online, size = 40.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    row.title,
                    style = if (row.identified) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
                    fontFamily = if (row.identified) null else Mono,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                    color = if (row.online) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                row.tags.forEach { MiniTag(it) }
                if (row.isNew) MiniBadge(stringResource(R.string.badge_new))
            }
            val noIdentity = stringResource(R.string.no_identity)
            val ipColor = MaterialTheme.colorScheme.primary
            val second = buildAnnotatedString {
                val rest = listOfNotNull(row.subtitle ?: row.device.vendor, row.device.mac)
                if (row.identified) {
                    pushStyle(SpanStyle(fontFamily = Mono, color = ipColor)); append(row.ip); pop()
                    if (rest.isNotEmpty()) append("  ·  ")
                }
                if (rest.isEmpty() && !row.identified) append(noIdentity)
                rest.forEachIndexed { i, part ->
                    if (i > 0) append("  ·  ")
                    if (part == row.device.mac) { pushStyle(SpanStyle(fontFamily = Mono)); append(part); pop() } else append(part)
                }
            }
            Text(
                second,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.outline)
    }
}

private fun roleLabel(r: Role, ctx: android.content.Context): String = when (r) {
    Role.SELF -> ctx.getString(R.string.role_self)
    Role.GATEWAY -> "Gateway"
    Role.DNS -> "DNS"
    Role.DHCP -> "DHCP"
}

/** Etichetta di ruolo compatta, sulla stessa riga del nome. */
@Composable
private fun MiniTag(r: Role) {
    val context = LocalContext.current
    val (bg, fg) = when (r) {
        Role.SELF -> MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.onPrimary
        Role.GATEWAY -> Brand.Teal.copy(alpha = 0.18f) to MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
    }
    Surface(shape = RoundedCornerShape(50), color = bg, contentColor = fg) {
        Text(
            if (r == Role.SELF) stringResource(R.string.role_self_short) else roleLabel(r, context),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 1.dp),
        )
    }
}

@Composable
private fun MiniBadge(text: String) {
    Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.tertiaryContainer) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 1.dp),
        )
    }
}
