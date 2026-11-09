package io.github.drcrck.openlan.scanner

enum class DeviceType(val label: String) {
    ROUTER("Router / access point"),
    COMPUTER("Computer"),
    SERVER("Server / gestione remota"),
    PHONE("Telefono"),
    TABLET("Tablet"),
    PRINTER("Stampante"),
    TV("TV / media player"),
    SPEAKER("Speaker / audio"),
    NAS("NAS / archiviazione"),
    CAMERA("Videocamera"),
    CONSOLE("Console di gioco"),
    IOT("Smart home / IoT"),
    UNKNOWN("Sconosciuto"),
}

/** Tutto ciò che sappiamo di un host, per dedurne il tipo. */
data class ClassifierInput(
    val isGateway: Boolean = false,
    val isSelf: Boolean = false,
    val vendor: String? = null,
    val services: Set<String> = emptySet(),
    val model: String? = null,
    val manufacturer: String? = null,
    val upnpDeviceType: String? = null,
    /** Intestazione SERVER della risposta SSDP (es. "Synology/DSM/..."). */
    val upnpServer: String? = null,
    val netbiosName: String? = null,
    val names: List<String> = emptyList(),
)

/** Euristiche per il tipo di dispositivo: regole in ordine, vince la prima che corrisponde. */
object DeviceClassifier {

    fun classify(i: ClassifierInput): DeviceType {
        if (i.isGateway) return DeviceType.ROUTER
        if (i.isSelf) return DeviceType.PHONE

        val s = i.services
        fun has(vararg prefixes: String) = prefixes.any { p -> s.any { it.startsWith(p) } }
        val vendor = i.vendor.orEmpty().lowercase()
        val model = i.model.orEmpty().lowercase()
        val maker = i.manufacturer.orEmpty().lowercase()
        val upnp = i.upnpDeviceType.orEmpty().lowercase()
        val server = i.upnpServer.orEmpty().lowercase()
        val names = i.names.joinToString(" ").lowercase()
        val nameList = i.names.map { it.lowercase() }
        fun v(vararg words: String) = words.any { vendor.contains(it) || maker.contains(it) || server.contains(it) }
        fun n(vararg words: String) = words.any { names.contains(it) || model.contains(it) }

        return when {
            upnp.contains("internetgatewaydevice") || upnp.contains("wfadevice") -> DeviceType.ROUTER
            has("_ipp.", "_ipps.", "_printer.", "_pdl-datastream.", "_uscan.", "_scanner.") ||
                upnp.contains("printer") -> DeviceType.PRINTER
            has("_rtsp.") || v("hikvision", "dahua", "reolink", "axis comm", "ezviz", "foscam") -> DeviceType.CAMERA
            // NAS: Synology si annuncia via mDNS come "Xserve"; QNAP con i servizi _qdiscover/_qmobile; ICP Electronics = QNAP
            v("synology", "qnap", "icp electronics", "western digital", "asustor", "buffalo", "terramaster") ||
                has("_qdiscover.", "_qmobile.", "_qboxcluster.") ||
                n("xserve", "diskstation", "rackstation", "-nas", "nas-") -> DeviceType.NAS
            // Server e schede di gestione remota (Dell iDRAC, HP iLO, IPMI), hypervisor
            n("idrac", "ilo-", "ipmi", "esxi", "proxmox") || nameList.any { it == "pve" || it.startsWith("srv") } ||
                v("super micro") -> DeviceType.SERVER
            v("nintendo", "sony interactive") || n("xbox", "playstation", "ps4", "ps5") -> DeviceType.CONSOLE

            // Apple: il modello pubblicato via mDNS (es. "iPhone15,2", "MacBookPro18,1", "AppleTV11,1")
            n("iphone") -> DeviceType.PHONE
            n("ipad") -> DeviceType.TABLET
            n("appletv") -> DeviceType.TV
            n("macbook", "imac", "macmini", "macpro", "mac14", "mac15", "mac16") -> DeviceType.COMPUTER
            n("audioaccessory", "homepod") -> DeviceType.SPEAKER

            v("sonos") || has("_sonos.") -> DeviceType.SPEAKER
            has("_googlecast.") && n("speaker", "nest mini", "home mini", "nest audio", "google home", "audio") -> DeviceType.SPEAKER
            has("_googlecast.", "_androidtvremote2.", "_amzn-wplay.") -> DeviceType.TV
            upnp.contains("mediarenderer") &&
                (v("samsung", "lg ", "lg electronics", "sony", "philips", "tcl", "hisense", "panasonic", "sharp") || n("tv")) -> DeviceType.TV
            v("amazon") && n("echo") -> DeviceType.SPEAKER
            has("_raop.") && !has("_airplay.") -> DeviceType.SPEAKER
            has("_airplay.") -> DeviceType.TV

            has("_hap.", "_homekit.", "_hue.", "_esphomelib.", "_shelly.", "_matter.", "_miio.") ||
                v("espressif", "tuya", "shelly", "itead", "signify", "philips lighting", "lumi united", "sonoff", "meross", "nanoleaf") ->
                DeviceType.IOT

            n("android", "galaxy", "pixel", "redmi", "oneplus", "huawei-", "xiaomi") -> DeviceType.PHONE
            has("_apple-mobdev2.") -> DeviceType.PHONE
            i.netbiosName != null -> DeviceType.COMPUTER
            has("_workstation.", "_ssh.", "_sftp-ssh.", "_rfb.", "_smb.", "_afpovertcp.", "_companion-link.") ->
                DeviceType.COMPUTER
            n("desktop-", "laptop-", "-pc", "macbook") -> DeviceType.COMPUTER
            else -> DeviceType.UNKNOWN
        }
    }
}
