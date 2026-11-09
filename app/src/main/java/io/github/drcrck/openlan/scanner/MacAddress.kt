package io.github.drcrck.openlan.scanner

/**
 * Utilità per gli indirizzi MAC. Formato canonico: "AA:BB:CC:DD:EE:FF".
 */
object MacAddress {

    private const val HEX = "0123456789ABCDEF"

    /** Normalizza un MAC in qualunque formato comune; null se non valido o non utilizzabile. */
    fun normalize(raw: String?): String? {
        if (raw == null) return null
        val hex = raw.filter { it.isLetterOrDigit() }.uppercase()
        if (hex.length != 12 || hex.any { it !in HEX }) return null
        val mac = hex.chunked(2).joinToString(":")
        return if (isUsable(mac)) mac else null
    }

    fun fromBytes(bytes: ByteArray, offset: Int = 0): String? {
        if (offset < 0 || offset + 6 > bytes.size) return null
        val hex = (offset until offset + 6).joinToString("") { "%02X".format(bytes[it].toInt() and 0xFF) }
        return normalize(hex)
    }

    /** Scarta MAC nulli, broadcast e multicast. */
    fun isUsable(mac: String): Boolean {
        val hex = hex(mac)
        if (hex == "000000000000" || hex == "FFFFFFFFFFFF") return false
        return firstByte(mac) and 0x01 == 0
    }

    /** MAC "locally administered": tipicamente randomizzato, non adatto al lookup del produttore. */
    fun isLocallyAdministered(mac: String): Boolean = firstByte(mac) and 0x02 != 0

    fun hex(mac: String): String = mac.replace(":", "")

    private fun firstByte(mac: String): Int = mac.substring(0, 2).toInt(16)
}
