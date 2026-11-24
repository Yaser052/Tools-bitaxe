package com.example.domain.engine

sealed class IpValidationResult {
    data class Valid(val normalizedHost: String, val baseUrl: String) : IpValidationResult()
    data class Invalid(val reasonKey: IpInvalidReason) : IpValidationResult()
}

enum class IpInvalidReason {
    EMPTY,
    MALFORMED,
    PUBLIC_INTERNET_BLOCKED
}

object NetworkValidator {

    private val ipv4Regex = Regex("""^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$""")
    private val mdnsRegex = Regex("""^[a-zA-Z0-9]([a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?\.local$""")

    /**
     * Validates that the entered host is a valid local/private LAN IPv4 address
     * or .local mDNS hostname. Public internet hosts are blocked to ensure the
     * miner's API is never exposed to or queried across the public internet.
     */
    fun validateLocalMinerAddress(rawInput: String): IpValidationResult {
        val cleaned = rawInput
            .trim()
            .removePrefix("http://")
            .removePrefix("https://")
            .substringBefore("/")
            .substringBefore(":")
            .trim()

        if (cleaned.isEmpty()) {
            return IpValidationResult.Invalid(IpInvalidReason.EMPTY)
        }

        if (cleaned.equals("localhost", ignoreCase = true)) {
            return IpValidationResult.Valid(cleaned, "http://$cleaned/")
        }

        if (mdnsRegex.matches(cleaned)) {
            return IpValidationResult.Valid(cleaned, "http://$cleaned/")
        }

        val match = ipv4Regex.matchEntire(cleaned)
            ?: return IpValidationResult.Invalid(IpInvalidReason.MALFORMED)

        val octets = match.groupValues.drop(1).mapNotNull { it.toIntOrNull() }
        if (octets.size != 4 || octets.any { it !in 0..255 }) {
            return IpValidationResult.Invalid(IpInvalidReason.MALFORMED)
        }

        if (!isPrivateLanIpv4(octets[0], octets[1])) {
            return IpValidationResult.Invalid(IpInvalidReason.PUBLIC_INTERNET_BLOCKED)
        }

        val normalized = octets.joinToString(".")
        return IpValidationResult.Valid(normalized, "http://$normalized/")
    }

    fun isPrivateLanIpv4(a: Int, b: Int): Boolean {
        return when {
            a == 10 -> true // 10.0.0.0/8
            a == 172 && b in 16..31 -> true // 172.16.0.0/12
            a == 192 && b == 168 -> true // 192.168.0.0/16
            a == 169 && b == 254 -> true // Link-local 169.254.0.0/16
            a == 127 -> true // Loopback for local unit/integration testing
            else -> false
        }
    }
}
