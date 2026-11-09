package io.github.drcrck.openlan.ui.common

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.DeviceUnknown
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tablet
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.drcrck.openlan.R
import io.github.drcrck.openlan.data.db.DeviceEntity
import io.github.drcrck.openlan.data.network.NetworkInfo
import io.github.drcrck.openlan.scanner.DeviceType
import io.github.drcrck.openlan.scanner.HopZone
import io.github.drcrck.openlan.scanner.MacAddress
import io.github.drcrck.openlan.scanner.MacSource
import io.github.drcrck.openlan.scanner.ScanPhase
import io.github.drcrck.openlan.ui.theme.Brand

/** Messaggio da tradurre al momento della visualizzazione (i ViewModel non conoscono la lingua). */
data class UiMessage(@param:StringRes val id: Int, val args: List<Any> = emptyList()) {
    fun resolve(ctx: Context): String = ctx.getString(id, *args.toTypedArray())
}

fun DeviceType.icon(): ImageVector = when (this) {
    DeviceType.ROUTER -> Icons.Filled.Router
    DeviceType.COMPUTER -> Icons.Filled.Computer
    DeviceType.SERVER -> Icons.Filled.Dns
    DeviceType.PHONE -> Icons.Filled.Smartphone
    DeviceType.TABLET -> Icons.Filled.Tablet
    DeviceType.PRINTER -> Icons.Filled.Print
    DeviceType.TV -> Icons.Filled.Tv
    DeviceType.SPEAKER -> Icons.Filled.Speaker
    DeviceType.NAS -> Icons.Filled.Storage
    DeviceType.CAMERA -> Icons.Filled.Videocam
    DeviceType.CONSOLE -> Icons.Filled.SportsEsports
    DeviceType.IOT -> Icons.Filled.Lightbulb
    DeviceType.UNKNOWN -> Icons.Filled.DeviceUnknown
}

/** Colore per categoria: aiuta a riconoscere i dispositivi a colpo d'occhio. */
fun DeviceType.color(): Color = when (this) {
    DeviceType.ROUTER -> Color(0xFF14B8A6)
    DeviceType.COMPUTER -> Color(0xFF3B82F6)
    DeviceType.SERVER -> Color(0xFF6366F1)
    DeviceType.PHONE, DeviceType.TABLET -> Color(0xFF8B5CF6)
    DeviceType.PRINTER -> Color(0xFFF97316)
    DeviceType.TV, DeviceType.SPEAKER -> Color(0xFFEC4899)
    DeviceType.NAS -> Color(0xFFF59E0B)
    DeviceType.CAMERA -> Color(0xFFEF4444)
    DeviceType.CONSOLE -> Color(0xFF22C55E)
    DeviceType.IOT -> Color(0xFFEAB308)
    DeviceType.UNKNOWN -> Color(0xFF94A3B8)
}

@get:StringRes
val DeviceType.labelRes: Int
    get() = when (this) {
        DeviceType.ROUTER -> R.string.type_router
        DeviceType.COMPUTER -> R.string.type_computer
        DeviceType.SERVER -> R.string.type_server
        DeviceType.PHONE -> R.string.type_phone
        DeviceType.TABLET -> R.string.type_tablet
        DeviceType.PRINTER -> R.string.type_printer
        DeviceType.TV -> R.string.type_tv
        DeviceType.SPEAKER -> R.string.type_speaker
        DeviceType.NAS -> R.string.type_nas
        DeviceType.CAMERA -> R.string.type_camera
        DeviceType.CONSOLE -> R.string.type_console
        DeviceType.IOT -> R.string.type_iot
        DeviceType.UNKNOWN -> R.string.type_unknown
    }

@get:StringRes
val HopZone.labelRes: Int
    get() = when (this) {
        HopZone.LAN -> R.string.zone_lan
        HopZone.CGNAT -> R.string.zone_cgnat
        HopZone.INTERNET -> R.string.zone_internet
    }

@get:StringRes
val ScanPhase.labelRes: Int
    get() = when (this) {
        ScanPhase.DISCOVERY -> R.string.phase_discovery
        ScanPhase.SERVICES -> R.string.phase_services
        ScanPhase.NAMES -> R.string.phase_names
    }

val DeviceEntity.type: DeviceType
    get() = when {
        isGateway -> DeviceType.ROUTER
        isSelf -> DeviceType.PHONE
        else -> deviceType?.let { runCatching { DeviceType.valueOf(it) }.getOrNull() } ?: DeviceType.UNKNOWN
    }

/** Nome da mostrare: personalizzato → pubblicato dal dispositivo → nomi di rete → modello/produttore → IP. */
fun DeviceEntity.displayName(thisPhone: String): String =
    customName
        ?: (if (isSelf) thisPhone else null)
        ?: friendlyName
        ?: netbiosName
        ?: mdnsName
        ?: hostname?.substringBefore('.')
        ?: model
        ?: vendor
        ?: (if (isGateway) "Router" else null)
        ?: lastIp

@Composable
fun DeviceEntity.displayName(): String = displayName(stringResource(R.string.this_phone))

/** Ruoli del dispositivo nella rete. */
enum class Role { SELF, GATEWAY, DNS, DHCP }

/**
 * Ruoli legati all'IP (gateway/DNS/DHCP): valgono solo se il dispositivo è online ora,
 * perché un IP vecchio potrebbe appartenere a qualcun altro.
 */
fun roleTags(d: DeviceEntity, online: Boolean, network: NetworkInfo?): List<Role> = buildList {
    if (d.isSelf) add(Role.SELF)
    if (network != null && (online || d.isGateway)) {
        if (d.lastIp == network.gateway || d.isGateway) add(Role.GATEWAY)
        if (d.lastIp in network.dnsServers) add(Role.DNS)
        if (d.lastIp == network.dhcpServer) add(Role.DHCP)
    } else if (d.isGateway) add(Role.GATEWAY)
}

/** Etichette colorate per i ruoli; "Questo dispositivo" in evidenza. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RoleTags(tags: List<Role>, modifier: Modifier = Modifier) {
    if (tags.isEmpty()) return
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        tags.distinct().forEach { t ->
            val (bg, fg) = when (t) {
                Role.SELF -> MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.onPrimary
                Role.GATEWAY -> Brand.Teal.copy(alpha = 0.18f) to MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
            }
            Surface(shape = RoundedCornerShape(50), color = bg, contentColor = fg) {
                Text(
                    when (t) {
                        Role.SELF -> stringResource(R.string.role_self)
                        Role.GATEWAY -> "Gateway"
                        Role.DNS -> "DNS"
                        Role.DHCP -> "DHCP"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        }
    }
}

/** Nota sull'origine del MAC, es. "da NetBIOS" o "MAC casuale · da mDNS". */
@Composable
fun macNote(d: DeviceEntity): String? {
    val m = d.mac ?: return null
    val source = d.macSource?.let { s -> runCatching { MacSource.valueOf(s).label }.getOrNull() } ?: "?"
    return if (MacAddress.isLocallyAdministered(m)) stringResource(R.string.mac_random_from, source)
    else stringResource(R.string.mac_from, source)
}

/** Icona del tipo su un tondo colorato, con pallino di stato opzionale. */
@Composable
fun TypeAvatar(type: DeviceType, online: Boolean?, size: Dp = 44.dp) {
    val c = type.color()
    Box(contentAlignment = Alignment.BottomEnd) {
        Box(
            Modifier.size(size).clip(RoundedCornerShape(size * 0.32f)).background(c.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(type.icon(), contentDescription = stringResource(type.labelRes), tint = c, modifier = Modifier.size(size * 0.55f))
        }
        if (online != null) {
            Box(
                Modifier.size(size * 0.3f).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(size * 0.05f).clip(CircleShape)
                    .background(if (online) Brand.Online else Brand.Offline)
            )
        }
    }
}

/** Pillola "Online" / "Offline". */
@Composable
fun StatusPill(online: Boolean) {
    val c = if (online) Brand.Online else Brand.Offline
    Surface(shape = RoundedCornerShape(50), color = c.copy(alpha = 0.16f)) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(c))
            Spacer(Modifier.width(6.dp))
            Text(
                stringResource(if (online) R.string.status_online else R.string.status_offline),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (online) Color(0xFF15803D).takeUnless { MaterialTheme.colorScheme.isDark() } ?: Brand.Online
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun androidx.compose.material3.ColorScheme.isDark(): Boolean = background.luminance() < 0.5f

private fun Color.luminance(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue
