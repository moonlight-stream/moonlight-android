package com.limelight.utils

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import com.limelight.LimeLog
import okhttp3.HttpUrl
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.SocketException
import java.util.Collections

object NetHelper {

    fun isActiveNetworkVpn(context: Context): Boolean {
        val connMgr = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val activeNetwork = connMgr.activeNetwork ?: return false
            val netCaps = connMgr.getNetworkCapabilities(activeNetwork) ?: return false
            netCaps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) ||
                    !netCaps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
        } else {
            @Suppress("DEPRECATION")
            connMgr.activeNetworkInfo?.type == ConnectivityManager.TYPE_VPN
        }
    }

    fun isActiveNetworkMobile(context: Context): Boolean {
        val connMgr = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val activeNetwork = connMgr.activeNetwork ?: return false
            val netCaps = connMgr.getNetworkCapabilities(activeNetwork) ?: return false
            netCaps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
        } else {
            @Suppress("DEPRECATION")
            val info = connMgr.activeNetworkInfo ?: return false
            @Suppress("DEPRECATION")
            info.type in intArrayOf(
                ConnectivityManager.TYPE_MOBILE,
                ConnectivityManager.TYPE_MOBILE_DUN,
                ConnectivityManager.TYPE_MOBILE_HIPRI,
                ConnectivityManager.TYPE_MOBILE_MMS,
                ConnectivityManager.TYPE_MOBILE_SUPL,
                ConnectivityManager.TYPE_WIMAX
            )
        }
    }

    fun isActiveNetworkWifi(context: Context): Boolean {
        val connMgr = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val activeNetwork = connMgr.activeNetwork ?: return false
            val netCaps = connMgr.getNetworkCapabilities(activeNetwork) ?: return false
            netCaps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        } else {
            @Suppress("DEPRECATION")
            connMgr.activeNetworkInfo?.type == ConnectivityManager.TYPE_WIFI
        }
    }

    fun isActiveNetworkEthernet(context: Context): Boolean {
        val connMgr = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val activeNetwork = connMgr.activeNetwork ?: return false
            val netCaps = connMgr.getNetworkCapabilities(activeNetwork) ?: return false
            netCaps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        } else {
            @Suppress("DEPRECATION")
            connMgr.activeNetworkInfo?.type == ConnectivityManager.TYPE_ETHERNET
        }
    }

    fun getDownstreamBandwidthKbps(context: Context): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val connMgr = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val activeNetwork = connMgr?.activeNetwork
            val netCaps = activeNetwork?.let { connMgr.getNetworkCapabilities(it) }
            if (netCaps != null) {
                return netCaps.linkDownstreamBandwidthKbps
            }
        }
        return -1
    }

    fun isLanAddress(addressStr: String?): Boolean {
        if (addressStr.isNullOrEmpty()) return false
        if (!isIpLiteral(addressStr)) return false
        val host = normalizeIpLiteral(addressStr) ?: return false

        // 地址分类必须是纯本地操作。域名由 OkHttp/native 在真正建连时按需解析，
        // 不能因为每 1.5 秒一次的候选筛选而额外触发 DNS 查询。
        return try {
            val addr = InetAddress.getByName(host)
            addr.isSiteLocalAddress || addr.isLoopbackAddress || isPrivateAddress(addr)
        } catch (_: Exception) {
            false
        }
    }

    fun isLocalNetworkInterfaceAvailable(): Boolean {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return false

            for (nif in Collections.list(interfaces)) {
                if (!nif.isUp || nif.isLoopback) continue

                val name = nif.name.lowercase()
                if (name.startsWith("rmnet") || name.startsWith("pdp") ||
                    name.startsWith("wwan") || name.startsWith("tun") ||
                    name.startsWith("ppp")
                ) continue

                for (addr in Collections.list(nif.inetAddresses)) {
                    if (addr.isLoopbackAddress) continue
                    if (addr.address.size == 4 && isPrivateAddress(addr)) {
                        return true
                    }
                }
            }
        } catch (e: SocketException) {
            LimeLog.warning("Error checking local network interfaces: ${e.message}")
        }
        return false
    }

    fun isPrivateAddress(addr: InetAddress): Boolean {
        val bytes = addr.address
        if (bytes.size == 4) {
            if (bytes[0] == 10.toByte()) return true
            if (bytes[0] == 172.toByte() && bytes[1] >= 16 && bytes[1] <= 31) return true
            if (bytes[0] == 192.toByte() && bytes[1] == 168.toByte()) return true
        }
        return false
    }

    @SuppressLint("DefaultLocale")
    fun calculateBandwidth(currentRxBytes: Long, previousRxBytes: Long, timeInterval: Long): String {
        if (timeInterval !in 1..5000) return "N/A"
        if (currentRxBytes < 0 || previousRxBytes < 0) return "N/A"

        val rxBytesDifference = currentRxBytes - previousRxBytes
        if (rxBytesDifference < 0) return "N/A"

        val rxBytesPerDifference = rxBytesDifference / 1024
        val speedKBps = rxBytesPerDifference / (timeInterval / 1000.0)

        // 单位之间使用 NBSP (\u00A0)，斜杠两侧使用 Word Joiner (\u2060, zero-width)
        // 防止性能覆盖层 TextView 在空格或斜杠处断行（UAX#14 中 / 属 Slash 类是断行机会）
        return if (speedKBps < 1024) {
            String.format("%.0f\u00A0K\u2060/\u2060s", speedKBps)
        } else {
            String.format("%.2f\u00A0M\u2060/\u2060s", speedKBps / 1024)
        }
    }

    /**
     * Returns true if [host] is an IP literal in a private range:
     *   IPv4: RFC1918 (10/8, 172.16/12, 192.168/16), loopback (127/8) or link-local (169.254/16).
     *   IPv6: loopback (::1), link-local (fe80::/10) or unique-local (fc00::/7).
     * Domain names and invalid input return false. Brackets around IPv6 are accepted.
     */
    fun isPrivateAddress(host: String?): Boolean {
        if (host == null || !isIpLiteral(host)) return false
        val h = normalizeIpLiteral(host) ?: return false
        val addr = try { InetAddress.getByName(h) } catch (_: Exception) { return false }
        return addr.isSiteLocalAddress || addr.isLoopbackAddress || addr.isLinkLocalAddress ||
                (addr is java.net.Inet6Address && isIpv6UniqueLocal(addr))
    }

    /** Convenience: parse [url] and apply [isPrivateAddress] to its host. */
    fun isPrivateNetworkUrl(url: String?): Boolean {
        val host = try { android.net.Uri.parse(url ?: return false).host } catch (_: Exception) { null }
        return isPrivateAddress(host)
    }

    fun isIpLiteral(h: String): Boolean {
        val host = normalizeIpLiteral(h) ?: return false
        val isBracketed = h.startsWith('[')
        if (isBracketed && !host.contains(':')) return false

        if (host.contains(':')) {
            // HttpUrl accepts percent-encoded host text, so validate the raw IPv6 form first.
            if (!host.all { it == ':' || it == '.' || it in '0'..'9' ||
                        it in 'a'..'f' || it in 'A'..'F' }) return false
            return try {
                HttpUrl.Builder().scheme("http").host(host).build().host.contains(':')
            } catch (_: IllegalArgumentException) {
                false
            }
        }
        val parts = host.split('.')
        return parts.size == 4 && parts.all { octet ->
            octet.isNotEmpty() && octet.all { it in '0'..'9' } &&
                    (octet.toIntOrNull() ?: -1) in 0..255
        }
    }

    private fun normalizeIpLiteral(host: String): String? {
        val startsWithBracket = host.startsWith('[')
        val endsWithBracket = host.endsWith(']')
        if (startsWithBracket != endsWithBracket) return null

        return if (startsWithBracket) {
            host.substring(1, host.length - 1).takeIf { it.isNotEmpty() }
        } else {
            host.takeIf { it.isNotEmpty() }
        }
    }

    /** IPv6 unique-local fc00::/7 → top byte is 0xFC or 0xFD. */
    private fun isIpv6UniqueLocal(addr: java.net.Inet6Address): Boolean {
        val b = addr.address[0].toInt() and 0xFF
        return b == 0xFC || b == 0xFD
    }
}
