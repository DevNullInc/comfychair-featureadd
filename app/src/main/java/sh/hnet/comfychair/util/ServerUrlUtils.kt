package sh.hnet.comfychair.util

/**
 * Utility for building server URLs consistently across the app.
 */
object ServerUrlUtils {

    /**
     * Build a full server URL from its components.
     * @param protocol Either "http" or "https"
     * @param hostname Server hostname or IP address
     * @param port Server port number
     * @return Full URL string, e.g. "https://192.168.1.100:8188"
     */
    fun buildServerUrl(protocol: String, hostname: String, port: Int): String {
        val cleanHost = hostname.trim()
        val hasProtocol = cleanHost.startsWith("http://", ignoreCase = true) ||
                cleanHost.startsWith("https://", ignoreCase = true)
        val base = if (hasProtocol) cleanHost else "$protocol://$cleanHost"
        return if (port > 0) "$base:$port" else base
    }
}
