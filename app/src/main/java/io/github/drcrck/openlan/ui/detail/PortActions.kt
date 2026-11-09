package io.github.drcrck.openlan.ui.detail

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.annotation.StringRes
import io.github.drcrck.openlan.R
import io.github.drcrck.openlan.scanner.KnownPorts
import io.github.drcrck.openlan.scanner.OpenPort
import io.github.drcrck.openlan.scanner.PortKind

/** Azione su una porta aperta: un URI da aprire con l'app che lo gestisce. */
data class PortAction(@param:StringRes val label: Int, val uri: String)

object PortActions {

    fun forPort(ip: String, p: OpenPort): List<PortAction> {
        val kind = KnownPorts.byPort[p.port]?.kind
        fun suffix(default: Int) = if (p.port == default) "" else ":${p.port}"
        return when {
            kind == PortKind.HTTPS || p.isTls -> listOf(PortAction(R.string.port_open_browser, "https://$ip${suffix(443)}/"))
            kind == PortKind.HTTP || p.isHttp -> listOf(PortAction(R.string.port_open_browser, "http://$ip${suffix(80)}/"))
            kind == PortKind.SSH -> listOf(PortAction(R.string.port_open_ssh, "ssh://$ip${suffix(22)}"))
            kind == PortKind.TELNET -> listOf(PortAction(R.string.port_open_telnet, "telnet://$ip${suffix(23)}"))
            kind == PortKind.FTP -> listOf(PortAction(R.string.port_open_ftp, "ftp://$ip${suffix(21)}/"))
            kind == PortKind.SMB && p.port == 445 -> listOf(PortAction(R.string.port_open_smb, "smb://$ip/"))
            kind == PortKind.RDP -> listOf(PortAction(R.string.port_open_rdp, "rdp://full%20address=s:$ip:${p.port}"))
            kind == PortKind.VNC -> listOf(PortAction(R.string.port_open_vnc, "vnc://$ip:${p.port}"))
            kind == PortKind.RTSP -> listOf(PortAction(R.string.port_open_rtsp, "rtsp://$ip:${p.port}/"))
            else -> emptyList()
        }
    }

    /**
     * Apre l'URI con l'app installata che lo gestisce. Se non ce n'è nessuna (es. nessun client SSH),
     * copia l'indirizzo negli appunti e restituisce un messaggio da mostrare.
     */
    fun open(context: Context, action: PortAction): String? = try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(action.uri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        null
    } catch (_: ActivityNotFoundException) {
        copy(context, action.uri)
        context.getString(R.string.msg_no_app_for, action.uri.substringBefore(':'))
    }

    /** Apre un link web (pagina del progetto, licenza, privacy). */
    fun openUrl(context: Context, url: String) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    fun copy(context: Context, text: String) {
        context.getSystemService(ClipboardManager::class.java)
            ?.setPrimaryClip(ClipData.newPlainText("OpenLAN", text))
    }
}
