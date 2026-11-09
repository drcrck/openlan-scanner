package io.github.drcrck.openlan.ui.traceroute

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.drcrck.openlan.R
import io.github.drcrck.openlan.scanner.Hop
import io.github.drcrck.openlan.scanner.HopZone
import io.github.drcrck.openlan.ui.common.SectionCard
import io.github.drcrck.openlan.ui.common.labelRes
import io.github.drcrck.openlan.ui.theme.Mono

private fun zoneColor(z: HopZone?) = when (z) {
    HopZone.LAN -> Color(0xFF22C55E)
    HopZone.CGNAT -> Color(0xFFF59E0B)
    HopZone.INTERNET -> Color(0xFF3B82F6)
    null -> Color(0xFF94A3B8)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TracerouteScreen(
    state: TracerouteState,
    onBack: () -> Unit,
    onTargetChange: (String) -> Unit,
    onStart: () -> Unit,
) {
    val context = LocalContext.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.traceroute)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = state.target,
                        onValueChange = onTargetChange,
                        label = { Text(stringResource(R.string.trace_target)) },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { onStart() }),
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = onStart, enabled = !state.running, modifier = Modifier.height(56.dp)) {
                        Text(stringResource(R.string.action_start))
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
            if (state.running) item {
                LinearProgressIndicator(Modifier.fillMaxWidth().padding(bottom = 12.dp).clip(RoundedCornerShape(50)))
            }
            state.error?.let { item { Text(it.resolve(context), color = MaterialTheme.colorScheme.error) } }

            if (state.finished && state.allSilent) item {
                // Onestà: lo strumento qui non funziona, e lo diciamo invece di stampare 30 righe vuote
                SectionCard(title = stringResource(R.string.trace_silent_title), icon = Icons.Filled.Public) {
                    Text(stringResource(R.string.trace_silent_body), style = MaterialTheme.typography.bodyMedium)
                }
            } else if (state.finished) item { Summary(state.hops); Spacer(Modifier.height(16.dp)) }

            itemsIndexed(state.hops, key = { _, h -> h.ttl }) { i, h ->
                HopRow(h, first = i == 0, last = i == state.hops.lastIndex)
            }

            if (state.finished && !state.allSilent && state.hiddenSilent > 0) item {
                Text(
                    stringResource(R.string.trace_hidden_silent, state.hiddenSilent),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 52.dp, top = 4.dp),
                )
            }

            if (!state.running && !state.finished && state.hops.isEmpty() && state.error == null) {
                item {
                    Text(
                        stringResource(R.string.trace_intro),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Riassunto: dove finisce la rete di casa e qual è il primo nodo pubblico (uscita verso Internet). */
@Composable
private fun Summary(hops: List<Hop>) {
    val reached = hops.lastOrNull()?.reached == true
    val lanHops = hops.count { it.zone == HopZone.LAN }
    val cgnat = hops.any { it.zone == HopZone.CGNAT }
    val exit = hops.firstOrNull { it.zone == HopZone.INTERNET && !it.reached } ?: hops.firstOrNull { it.zone == HopZone.INTERNET }
    SectionCard(
        title = if (reached) stringResource(R.string.trace_reached, hops.size)
        else stringResource(R.string.trace_not_reached, hops.size),
        icon = Icons.Filled.Public,
    ) {
        Text(stringResource(R.string.trace_lan_hops, lanHops), style = MaterialTheme.typography.bodyMedium)
        if (cgnat) {
            Text(
                stringResource(R.string.trace_cgnat),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
        exit?.let {
            Text(
                stringResource(R.string.trace_exit, it.ip + (it.hostname?.let { h -> " ($h)" } ?: "")),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/** Un salto come nodo di una linea temporale verticale, colorato per zona. */
@Composable
private fun HopRow(h: Hop, first: Boolean, last: Boolean) {
    val color = zoneColor(h.zone)
    val line = MaterialTheme.colorScheme.outlineVariant
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Text(
            h.ttl.toString(),
            fontFamily = Mono,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(28.dp).padding(top = 12.dp),
        )
        Canvas(Modifier.width(24.dp).fillMaxHeight()) {
            val x = size.width / 2
            val y = 22.dp.toPx()
            if (!first) drawLine(line, Offset(x, 0f), Offset(x, y), 4f)
            if (!last) drawLine(line, Offset(x, y), Offset(x, size.height), 4f)
            if (h.ip == null) drawCircle(color, 6.dp.toPx(), Offset(x, y), style = Stroke(2.dp.toPx()))
            else {
                drawCircle(color.copy(alpha = 0.25f), 9.dp.toPx(), Offset(x, y))
                drawCircle(color, 5.dp.toPx(), Offset(x, y))
            }
        }
        Column(Modifier.weight(1f).padding(start = 10.dp, top = 10.dp, bottom = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    h.ip ?: stringResource(R.string.trace_no_reply),
                    fontFamily = Mono,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                    color = if (h.ip == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                )
                h.rttMs?.let {
                    Text("~$it ms", style = MaterialTheme.typography.bodySmall, fontFamily = Mono, color = MaterialTheme.colorScheme.primary)
                }
            }
            h.hostname?.let {
                Text(
                    it, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            h.zone?.let { Text(stringResource(it.labelRes), style = MaterialTheme.typography.labelSmall, color = color) }
        }
    }
}
