package com.limelight.utils.easytier

internal data class EasyTierConfigUiState(
        val networkName: String = "",
        val networkSecret: String = "",
        val ipv4: String = "",
        val listeners: String = "",
        val peers: String = "",
        val useSmoltcp: Boolean = false,
        val latencyFirst: Boolean = false,
        val disableP2p: Boolean = false,
        val privateMode: Boolean = false,
        val disableIpv6: Boolean = false,
        val enableKcpProxy: Boolean = false,
        val disableKcpInput: Boolean = false,
        val enableQuicProxy: Boolean = false,
        val disableQuicInput: Boolean = false,
        val proxyForwardBySystem: Boolean = false,
        val disableEncryption: Boolean = false,
        val disableUdpHolePunching: Boolean = false,
        val disableSymHolePunching: Boolean = false
)

internal object EasyTierTomlCodec {
    private const val DEFAULT_IPV4 = "10.0.0.1"

    fun parseConfig(toml: String): EasyTierConfigUiState {
        val ipv4Full = extractValue(toml, "ipv4", "")
        val ipv4 = if (ipv4Full.contains("/")) {
            ipv4Full.split("/")[0]
        } else {
            ipv4Full
        }
        val isIpv6Enabled = extractValue(toml, "enable_ipv6", "true").toBoolean()
        val isEncryptionEnabled = extractValue(toml, "enable_encryption", "true").toBoolean()

        return EasyTierConfigUiState(
                networkName = extractValue(toml, "network_name", ""),
                networkSecret = extractValue(toml, "network_secret", ""),
                ipv4 = ipv4,
                listeners = extractListAsString(toml, "listeners"),
                peers = extractListAsString(toml, "uri"),
                useSmoltcp = extractValue(toml, "use_smoltcp", "false").toBoolean(),
                latencyFirst = extractValue(toml, "latency_first", "false").toBoolean(),
                disableP2p = extractValue(toml, "disable_p2p", "false").toBoolean(),
                privateMode = extractValue(toml, "private_mode", "false").toBoolean(),
                disableIpv6 = !isIpv6Enabled,
                enableKcpProxy = extractValue(toml, "enable_kcp_proxy", "false").toBoolean(),
                disableKcpInput = extractValue(toml, "disable_kcp_input", "false").toBoolean(),
                enableQuicProxy = extractValue(toml, "enable_quic_proxy", "false").toBoolean(),
                disableQuicInput = extractValue(toml, "disable_quic_input", "false").toBoolean(),
                proxyForwardBySystem = extractValue(toml, "proxy_forward_by_system", "false").toBoolean(),
                disableEncryption = !isEncryptionEnabled,
                disableUdpHolePunching = extractValue(toml, "disable_udp_hole_punching", "false").toBoolean(),
                disableSymHolePunching = extractValue(toml, "disable_sym_hole_punching", "false").toBoolean()
        )
    }

    fun build(config: EasyTierConfigUiState): String {
        val sb = StringBuilder()
        appendTomlString(sb, "hostname", "moonlight-V+")
        appendTomlString(sb, "instance_name", "Default")
        sb.append("dhcp = false\n")
        appendTomlString(sb, "ipv4", "${config.ipv4.ifBlank { DEFAULT_IPV4 }}/24", writeEmpty = true)

        appendTomlStringArray(sb, "listeners", nonBlankLines(config.listeners))

        appendTomlString(sb, "rpc_portal", "0.0.0.0:0")
        sb.append("\n[network_identity]\n")

        appendTomlString(sb, "network_name", config.networkName)
        appendTomlString(sb, "network_secret", config.networkSecret, writeEmpty = true)

        for (peer in nonBlankLines(config.peers)) {
            appendTomlPeer(sb, peer)
        }

        sb.append("\n[flags]\n")
        appendFlagIfNotDefault(sb, "use_smoltcp", config.useSmoltcp, false)
        appendFlagIfNotDefault(sb, "latency_first", config.latencyFirst, false)
        appendFlagIfNotDefault(sb, "disable_p2p", config.disableP2p, false)
        appendFlagIfNotDefault(sb, "private_mode", config.privateMode, false)
        appendFlagIfNotDefault(sb, "enable_ipv6", !config.disableIpv6, true)
        appendFlagIfNotDefault(sb, "enable_kcp_proxy", config.enableKcpProxy, false)
        appendFlagIfNotDefault(sb, "disable_kcp_input", config.disableKcpInput, false)
        appendFlagIfNotDefault(sb, "enable_quic_proxy", config.enableQuicProxy, false)
        appendFlagIfNotDefault(sb, "disable_quic_input", config.disableQuicInput, false)
        appendFlagIfNotDefault(sb, "proxy_forward_by_system", config.proxyForwardBySystem, false)
        appendFlagIfNotDefault(sb, "enable_encryption", !config.disableEncryption, true)
        appendFlagIfNotDefault(sb, "disable_udp_hole_punching", config.disableUdpHolePunching, false)
        appendFlagIfNotDefault(sb, "disable_sym_hole_punching", config.disableSymHolePunching, false)

        return sb.toString()
    }

    private fun extractValue(toml: String, key: String, defaultValue: String): String {
        for (rawLine in toml.split("\n")) {
            val line = rawLine.trim()
            if (line.startsWith("$key =")) {
                try {
                    return tomlUnquoted(line.split("=", limit = 2)[1].trim())
                } catch (e: Exception) {
                    // Fall through to the default.
                }
            }
        }
        return defaultValue
    }

    private fun extractListAsString(toml: String, key: String): String {
        if ("uri" == key) {
            val peers = StringBuilder()
            for (rawLine in toml.split("\n")) {
                val line = rawLine.trim()
                if (line.startsWith("uri =")) {
                    if (peers.isNotEmpty()) peers.append("\n")
                    peers.append(tomlUnquoted(line.split("=", limit = 2)[1].trim()))
                }
            }
            return peers.toString()
        }
        for (rawLine in toml.split("\n")) {
            val line = rawLine.trim()
            if (line.startsWith("$key =")) {
                try {
                    val list = line.substring(line.indexOf('[') + 1, line.lastIndexOf(']'))
                    return splitTomlStringArray(list).joinToString("\n")
                } catch (e: Exception) {
                    // Fall through to an empty list.
                }
            }
        }
        return ""
    }

    private fun appendFlagIfNotDefault(sb: StringBuilder, key: String, value: Boolean, defaultValue: Boolean) {
        if (value != defaultValue) {
            sb.append(key).append(" = ").append(value).append("\n")
        }
    }

    private fun appendTomlString(
            sb: StringBuilder,
            key: String,
            value: String,
            writeEmpty: Boolean = false
    ) {
        if (writeEmpty || value.isNotEmpty()) {
            sb.append(key).append(" = ").append(tomlQuoted(value)).append("\n")
        }
    }

    private fun appendTomlStringArray(sb: StringBuilder, key: String, values: List<String>) {
        if (values.isNotEmpty()) {
            sb.append(key)
                    .append(" = [")
                    .append(values.joinToString(", ") { tomlQuoted(it) })
                    .append("]\n")
        }
    }

    private fun appendTomlPeer(sb: StringBuilder, uri: String) {
        sb.append("\n[[peer]]\n")
        appendTomlString(sb, "uri", uri)
    }

    private fun nonBlankLines(value: String): List<String> {
        return value.split("\n")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
    }

    private fun tomlQuoted(value: String): String {
        val escaped = StringBuilder()
        for (char in value) {
            when (char) {
                '\\' -> escaped.append("\\\\")
                '"' -> escaped.append("\\\"")
                '\b' -> escaped.append("\\b")
                '\t' -> escaped.append("\\t")
                '\n' -> escaped.append("\\n")
                '\u000C' -> escaped.append("\\f")
                '\r' -> escaped.append("\\r")
                else -> {
                    if (char.code < 0x20) {
                        escaped.append("\\u").append(char.code.toString(16).padStart(4, '0'))
                    } else {
                        escaped.append(char)
                    }
                }
            }
        }
        return "\"$escaped\""
    }

    private fun tomlUnquoted(value: String): String {
        val trimmed = value.trim()
        if (trimmed.length < 2 || trimmed.first() != '"' || trimmed.last() != '"') {
            return trimmed
        }
        return unescapeTomlString(trimmed.substring(1, trimmed.length - 1))
    }

    private fun splitTomlStringArray(value: String): List<String> {
        val items = ArrayList<String>()
        val current = StringBuilder()
        var inString = false
        var escaping = false

        for (char in value) {
            when {
                !inString && char == '"' -> {
                    current.setLength(0)
                    inString = true
                }
                inString && escaping -> {
                    current.append('\\').append(char)
                    escaping = false
                }
                inString && char == '\\' -> escaping = true
                inString && char == '"' -> {
                    items.add(unescapeTomlString(current.toString()))
                    inString = false
                }
                inString -> current.append(char)
            }
        }

        return items
    }

    private fun unescapeTomlString(value: String): String {
        val unescaped = StringBuilder()
        var index = 0
        while (index < value.length) {
            val char = value[index]
            if (char != '\\' || index == value.lastIndex) {
                unescaped.append(char)
                index++
                continue
            }

            val escaped = value[index + 1]
            when (escaped) {
                'b' -> unescaped.append('\b')
                't' -> unescaped.append('\t')
                'n' -> unescaped.append('\n')
                'f' -> unescaped.append('\u000C')
                'r' -> unescaped.append('\r')
                '"' -> unescaped.append('"')
                '\\' -> unescaped.append('\\')
                'u' -> {
                    if (index + 5 < value.length) {
                        val code = value.substring(index + 2, index + 6).toIntOrNull(16)
                        if (code != null) {
                            unescaped.append(code.toChar())
                            index += 6
                            continue
                        }
                    }
                    unescaped.append(escaped)
                }
                else -> unescaped.append(escaped)
            }
            index += 2
        }
        return unescaped.toString()
    }
}
