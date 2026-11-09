package io.github.drcrck.openlan.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.drcrck.openlan.R
import io.github.drcrck.openlan.ads.Ads
import io.github.drcrck.openlan.data.repository.MatchKind
import io.github.drcrck.openlan.scanner.KnownPorts
import io.github.drcrck.openlan.scanner.OpenPort
import io.github.drcrck.openlan.scanner.PingStats
import io.github.drcrck.openlan.scanner.PortKind
import io.github.drcrck.openlan.ui.common.InfoRow
import io.github.drcrck.openlan.ui.common.RoleTags
import io.github.drcrck.openlan.ui.common.SectionCard
import io.github.drcrck.openlan.ui.common.StatusPill
import io.github.drcrck.openlan.ui.common.TypeAvatar
import io.github.drcrck.openlan.ui.common.displayName
import io.github.drcrck.openlan.ui.common.labelRes
import io.github.drcrck.openlan.ui.common.macNote
import io.github.drcrck.openlan.ui.common.type
import io.github.drcrck.openlan.ui.theme.Mono
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/**
 * Scheda dispositivo, nell'ordine in cui lavora un tecnico: chi è → azioni rapide → porte → ping → dettagli.
 * [asPane] = mostrata nel pannello destro su schermo largo (icona di chiusura invece della freccia).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceDetailScreen(
    detail: DeviceDetail?,
    ping: PingStats,
    ports: PortScanState,
    onBack: () -> Unit,
    onScanPorts: (extended: Boolean) -> Unit,
    onStopPorts: () -> Unit,
    onRename: (String) -> Unit,
    asPane: Boolean = false,
) {
    val device = detail?.device
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var renaming by remember { mutableStateOf(false) }
    fun toast(msg: String) = scope.launch { snackbar.showSnackbar(msg) }
    fun copy(text: String) {
        PortActions.copy(context, text)
        toast(context.getString(R.string.msg_copied, text))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        if (asPane) Icon(Icons.Filled.Close, stringResource(R.string.action_close))
                        else Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (device != null) {
                        IconButton(onClick = { renaming = true }) { Icon(Icons.Filled.Edit, stringResource(R.string.action_rename)) }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        // Banner pubblicitario solo qui, in fondo alla scheda (nella versione "foss" non c'è)
        bottomBar = { Ads.Banner(Modifier.fillMaxWidth().navigationBarsPadding()) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (detail == null || device == null) return@Scaffold
        LazyColumn(
            // Su schermi larghi le schede non si stirano oltre una larghezza leggibile
            modifier = Modifier.fillMaxSize().padding(padding).wrapContentWidth(Alignment.Start).widthIn(max = 720.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Header(detail) }

            item {
                QuickActions(
                    ip = device.lastIp,
                    ports = ports,
                    onOpenWeb = { url -> PortActions.open(context, PortAction(R.string.port_open_browser, url))?.let { toast(it) } },
                    onCopyIp = { copy(device.lastIp) },
                )
            }

            item {
                PortsCard(device.lastIp, ports, onScanPorts, onStopPorts) { action ->
                    PortActions.open(context, action)?.let { toast(it) }
                }
            }

            if (!device.isSelf) item { PingChart(ping) }

            item { InfoCard(detail, onCopy = ::copy) }
        }
    }

    if (renaming && device != null) {
        RenameDialog(
            current = device.customName ?: "",
            placeholder = device.displayName(),
            onDismiss = { renaming = false },
            onConfirm = { onRename(it); renaming = false },
        )
    }
}

/** Intestazione compatta: icona accanto al nome, stato e ruoli sotto. */
@Composable
private fun Header(detail: DeviceDetail) {
    val d = detail.device
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        TypeAvatar(d.type, online = null, size = 56.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(d.displayName(), style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                stringResource(d.type.labelRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusPill(detail.online)
                RoleTags(detail.tags)
            }
        }
    }
}

/**
 * Le azioni che un tecnico fa per prime: aprire l'interfaccia web e copiare l'IP (le porte sono subito sotto).
 * "Apri web" usa HTTPS se l'ultima scansione porte l'ha trovata, altrimenti prova HTTP.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuickActions(
    ip: String,
    ports: PortScanState,
    onOpenWeb: (String) -> Unit,
    onCopyIp: () -> Unit,
) {
    val web = ports.open.firstOrNull { it.isTls || KnownPorts.byPort[it.port]?.kind == PortKind.HTTPS }
        ?.let { "https://$ip" + if (it.port == 443) "/" else ":${it.port}/" }
        ?: ports.open.firstOrNull { it.isHttp || KnownPorts.byPort[it.port]?.kind == PortKind.HTTP }
            ?.let { "http://$ip" + if (it.port == 80) "/" else ":${it.port}/" }
        ?: "http://$ip/"
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FilledTonalButton(onClick = { onOpenWeb(web) }) {
            Icon(Icons.Filled.Language, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.action_open_web))
        }
        OutlinedButton(onClick = onCopyIp) {
            Icon(Icons.Filled.ContentCopy, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.action_copy_ip))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InfoCard(detail: DeviceDetail, onCopy: (String) -> Unit) {
    val d = detail.device
    SectionCard(title = stringResource(R.string.section_details), icon = Icons.Filled.Info) {
        InfoRow("IP", d.lastIp, mono = true, onCopy = onCopy)
        if (d.mac != null) InfoRow("MAC", d.mac, mono = true, note = macNote(d), onCopy = onCopy)
        else if (!d.isSelf) InfoRow("MAC", stringResource(R.string.mac_unavailable), note = stringResource(R.string.mac_unavailable_hint))
        d.vendor?.let { InfoRow(stringResource(R.string.field_vendor), it) }
        d.manufacturer?.takeIf { it != d.vendor }?.let { InfoRow(stringResource(R.string.field_brand), it) }
        d.model?.let { InfoRow(stringResource(R.string.field_model), it) }
        d.friendlyName?.let { InfoRow(stringResource(R.string.field_friendly), it) }
        d.netbiosName?.let { InfoRow("NetBIOS", it, mono = true) }
        d.mdnsName?.let { InfoRow("mDNS", "$it.local", mono = true) }
        d.hostname?.let { InfoRow("DNS", it, mono = true) }
        val df = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
        val first = df.format(Date(d.firstSeen))
        val last = df.format(Date(d.lastSeen))
        // Stessa data due volte non aggiunge nulla: una riga sola
        if (first == last) InfoRow(stringResource(R.string.field_seen), first)
        else {
            InfoRow(stringResource(R.string.field_first_seen), first)
            InfoRow(stringResource(R.string.field_last_seen), last)
        }
        detail.matchedBy?.let { InfoRow(stringResource(R.string.field_matched_by), stringResource(matchLabel(it))) }

        val services = d.services?.split(",")?.filter { it.isNotBlank() }.orEmpty()
        if (services.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.field_services),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                services.forEach { s ->
                    Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.secondaryContainer) {
                        Text(
                            s.removeSuffix("._tcp").removeSuffix("._udp").removePrefix("_"),
                            style = MaterialTheme.typography.labelMedium,
                            fontFamily = Mono,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

private fun matchLabel(k: MatchKind): Int = when (k) {
    MatchKind.SELF -> R.string.match_self
    MatchKind.MAC -> R.string.match_mac
    MatchKind.UDN -> R.string.match_udn
    MatchKind.NETBIOS -> R.string.match_netbios
    MatchKind.MDNS -> R.string.match_mdns
    MatchKind.HOSTNAME -> R.string.match_hostname
    MatchKind.IP -> R.string.match_ip
    MatchKind.NEW -> R.string.match_new
}

@Composable
private fun PortsCard(
    ip: String,
    ports: PortScanState,
    onScan: (Boolean) -> Unit,
    onStop: () -> Unit,
    onAction: (PortAction) -> Unit,
) {
    SectionCard(title = stringResource(R.string.section_ports), icon = Icons.Filled.Lan) {
        if (ports.running) {
            Text(
                stringResource(R.string.ports_progress, ports.done, ports.total, ports.open.size),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LinearProgressIndicator(
                progress = { if (ports.total == 0) 0f else ports.done.toFloat() / ports.total },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)),
                drawStopIndicator = {},
            )
            OutlinedButton(onClick = onStop) { Text(stringResource(R.string.action_stop)) }
        } else {
            when {
                ports.remembered -> {
                    val at = ports.scannedAt?.let {
                        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it))
                    } ?: "?"
                    Text(
                        stringResource(R.string.ports_remembered, at),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                ports.finished -> Text(
                    stringResource(
                        R.string.ports_result, ports.open.size, ports.total,
                        String.format(java.util.Locale.getDefault(), "%.1f", (ports.durationMs ?: 0) / 1000.0),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> Text(
                    stringResource(R.string.ports_intro),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onScan(false) }) {
                    Text(stringResource(if (ports.remembered || ports.finished) R.string.ports_rescan else R.string.ports_common))
                }
                FilledTonalButton(onClick = { onScan(true) }) { Text(stringResource(R.string.ports_extended)) }
            }
            if (ports.finished && ports.open.isEmpty()) {
                Text(stringResource(R.string.ports_none), style = MaterialTheme.typography.bodyMedium)
            }
        }

        ports.open.forEachIndexed { i, p ->
            if (i == 0) Spacer(Modifier.height(4.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            PortRow(ip, p, onAction)
        }
    }
}

private fun portIcon(p: OpenPort): ImageVector = when (KnownPorts.byPort[p.port]?.kind) {
    PortKind.HTTPS -> Icons.Filled.Lock
    PortKind.HTTP -> Icons.Filled.Language
    PortKind.SSH, PortKind.TELNET -> Icons.Filled.Terminal
    PortKind.SMB, PortKind.FTP -> Icons.Filled.Folder
    PortKind.RDP, PortKind.VNC -> Icons.Filled.DesktopWindows
    PortKind.RTSP -> Icons.Filled.Videocam
    else -> if (p.isTls) Icons.Filled.Lock else if (p.isHttp) Icons.Filled.Language else Icons.Filled.Lan
}

@Composable
private fun PortRow(ip: String, p: OpenPort, onAction: (PortAction) -> Unit) {
    val actions = PortActions.forPort(ip, p)
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(portIcon(p), null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(p.port.toString(), fontFamily = Mono, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
            Text(
                p.service ?: stringResource(R.string.port_unknown),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        actions.forEach { a ->
            TextButton(onClick = { onAction(a) }) {
                Text(stringResource(a.label))
                Spacer(Modifier.width(4.dp))
                Icon(Icons.AutoMirrored.Filled.OpenInNew, null, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun RenameDialog(current: String, placeholder: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rename_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                placeholder = { Text(placeholder) },
                supportingText = { Text(stringResource(R.string.rename_hint)) },
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(text) }) { Text(stringResource(R.string.action_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
