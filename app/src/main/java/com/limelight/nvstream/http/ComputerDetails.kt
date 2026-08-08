package com.limelight.nvstream.http

import android.content.Context

import com.limelight.utils.NetHelper

import java.net.Inet4Address
import java.net.InetAddress
import java.security.cert.X509Certificate

class ComputerDetails {
    enum class State {
        ONLINE, OFFLINE, UNKNOWN
    }

    class AddressTuple(var address: String, var port: Int) {
        init {
            require(port > 0) { "Invalid port" }

            // Only IPv6 literals may use brackets. Preserve other bracketed input for validation.
            if (address.startsWith("[") && address.endsWith("]")) {
                val unbracketedAddress = address.substring(1, address.length - 1)
                if (unbracketedAddress.contains(':')) {
                    address = unbracketedAddress
                }
            }
        }

        override fun hashCode(): Int {
            return address.hashCode() * 31 + port
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is AddressTuple) return false
            return port == other.port && address == other.address
        }

        override fun toString(): String {
            return if (address.contains(":")) "[$address]:$port" else "$address:$port"
        }
    }

    // Persistent attributes
    var uuid: String? = null
    var name: String? = null
    var localAddress: AddressTuple? = null
    var remoteAddress: AddressTuple? = null
    var manualAddress: AddressTuple? = null
    var ipv6Address: AddressTuple? = null
    var macAddress: String? = null
    var serverCert: X509Certificate? = null
    var ipv6Disabled = false

    // Transient attributes
    var state: State = State.UNKNOWN
    var activeAddress: AddressTuple? = null
    var availableAddresses: MutableList<AddressTuple> = ArrayList()
    var httpsPort = 0
    var externalPort = 0
    var pairState: PairingManager.PairState? = null
    var serverInfoTrustedByCert = false
    var runningGameId = 0
    var rawAppList: String? = null
    var nvidiaServer = false
    var useVdd = false
    var sunshineVersion: String? = null
    var supportsDesktopSpecialApp = false
    var vddCapabilityVersion: Int? = null

    constructor()

    constructor(details: ComputerDetails) : this() {
        update(details)
    }

    fun guessExternalPort(): Int {
        if (externalPort != 0) return externalPort
        if (remoteAddress != null) return (remoteAddress?.port ?: 0)
        if (activeAddress != null) return (activeAddress?.port ?: 0)
        if (ipv6Address != null) return (ipv6Address?.port ?: 0)
        if (localAddress != null) return (localAddress?.port ?: 0)
        return NvHTTP.DEFAULT_HTTP_PORT
    }

    fun update(details: ComputerDetails) {
        this.state = details.state
        this.name = details.name
        this.uuid = details.uuid

        if (details.activeAddress != null) {
            this.activeAddress = details.activeAddress
        }
        if (details.localAddress != null && details.localAddress?.address?.startsWith("127.") != true) {
            this.localAddress = details.localAddress
        }
        if (details.remoteAddress != null) {
            this.remoteAddress = details.remoteAddress
        } else if (this.remoteAddress != null && details.externalPort != 0) {
            this.remoteAddress?.port = details.externalPort
        }
        if (details.manualAddress != null) {
            this.manualAddress = details.manualAddress
        }
        if (details.ipv6Address != null && !this.ipv6Disabled) {
            this.ipv6Address = details.ipv6Address
        }
        if (details.macAddress != null && ZERO_MAC != details.macAddress) {
            this.macAddress = details.macAddress
        }
        if (details.serverCert != null) {
            this.serverCert = details.serverCert
        }

        this.externalPort = details.externalPort
        this.httpsPort = details.httpsPort
        this.pairState = details.pairState
        this.serverInfoTrustedByCert = details.serverInfoTrustedByCert
        this.runningGameId = details.runningGameId
        this.nvidiaServer = details.nvidiaServer
        this.useVdd = details.useVdd
        this.rawAppList = details.rawAppList
        if (details.sunshineVersion != null) {
            this.sunshineVersion = details.sunshineVersion
        }
        this.supportsDesktopSpecialApp = details.supportsDesktopSpecialApp
        this.vddCapabilityVersion = details.vddCapabilityVersion

        this.availableAddresses = ArrayList(details.availableAddresses)
    }

    fun addAvailableAddress(address: AddressTuple?) {
        if (address == null) return

        if (ipv6Disabled && isIpv6Address(address)) {
            return
        }

        if (!availableAddresses.contains(address)) {
            availableAddresses.add(address)
        }
    }

    fun hasMultipleAddresses(): Boolean {
        return availableAddresses.size > 1
    }

    fun getAddressTypeDescription(address: AddressTuple?): String {
        if (address == null) return ""

        if (address == localAddress) return "本地网络"
        if (address == remoteAddress) return "远程网络"
        if (address == manualAddress) return "手动配置"
        if (address == ipv6Address) return "IPv6网络"
        return "其他网络"
    }

    fun getLanIpv4Addresses(): List<AddressTuple> {
        val lanAddresses = ArrayList<AddressTuple>()
        for (address in availableAddresses) {
            if (isLanIpv4Address(address)) {
                lanAddresses.add(address)
            }
        }
        return lanAddresses
    }

    fun hasMultipleLanAddresses(): Boolean {
        return getLanIpv4Addresses().size > 1
    }

    fun selectBestAddress(): AddressTuple? {
        if (availableAddresses.isEmpty()) {
            return selectBestAddressFromFields()
        }

        val lanAddresses = getLanIpv4Addresses()
        if (lanAddresses.isNotEmpty()) {
            return selectFromLanAddresses(lanAddresses)
        }

        if (!ipv6Disabled && ipv6Address != null && availableAddresses.contains(ipv6Address)) {
            return ipv6Address
        }

        if (remoteAddress != null && availableAddresses.contains(remoteAddress)) {
            return remoteAddress
        }

        if (ipv6Disabled) {
            for (address in availableAddresses) {
                if (!isIpv6Address(address)) {
                    return address
                }
            }
        }

        return availableAddresses[0]
    }

    private fun selectBestAddressFromFields(): AddressTuple? {
        if (localAddress != null && isLanIpv4Address(localAddress)) {
            return localAddress
        }
        if (!ipv6Disabled && ipv6Address != null) {
            return ipv6Address
        }
        if (remoteAddress != null) {
            return remoteAddress
        }
        return localAddress
    }

    private fun selectFromLanAddresses(lanAddresses: List<AddressTuple>): AddressTuple {
        if (localAddress != null && lanAddresses.contains(localAddress)) {
            return localAddress!!
        }
        if (manualAddress != null && lanAddresses.contains(manualAddress)) {
            return manualAddress!!
        }
        return lanAddresses[0]
    }

    fun getPairName(context: Context): String {
        val prefs = context.getSharedPreferences("pair_name_map", Context.MODE_PRIVATE)
        return prefs.getString(uuid, "")!!
    }

    fun getSunshineVersionDisplay(): String {
        return if (!sunshineVersion.isNullOrEmpty()) sunshineVersion!! else "Unknown"
    }

    override fun toString(): String {
        val str = StringBuilder()
        str.append("Name: ").append(name).append("\n")
        str.append("State: ").append(state).append("\n")
        str.append("Active Address: ").append(activeAddress).append("\n")
        str.append("UUID: ").append(uuid).append("\n")
        str.append("Local Address: ").append(localAddress).append("\n")
        str.append("Remote Address: ").append(remoteAddress).append("\n")
        str.append("IPv6 Address: ").append(if (ipv6Disabled) "Disabled" else ipv6Address).append("\n")
        str.append("Manual Address: ").append(manualAddress).append("\n")
        str.append("MAC Address: ").append(macAddress).append("\n")
        str.append("Pair State: ").append(pairState).append("\n")
        str.append("Running Game ID: ").append(runningGameId).append("\n")
        str.append("HTTPS Port: ").append(httpsPort).append("\n")
        str.append("Sunshine Version: ").append(getSunshineVersionDisplay()).append("\n")
        str.append("Desktop Special App Support: ").append(supportsDesktopSpecialApp).append("\n")
        str.append("VDD Capability Version: ").append(vddCapabilityVersion).append("\n")
        return str.toString()
    }

    companion object {
        private const val ZERO_MAC = "00:00:00:00:00:00"

        fun isLanIpv4Address(address: AddressTuple?): Boolean {
            if (address?.address == null) return false
            if (!NetHelper.isIpLiteral(address.address) || address.address.contains(':')) return false

            return try {
                val inetAddress = InetAddress.getByName(address.address)
                if (inetAddress !is Inet4Address) return false
                NetHelper.isLanAddress(address.address)
            } catch (e: Exception) {
                false
            }
        }

        fun isIpv6Address(address: AddressTuple?): Boolean {
            return address?.address?.contains(":") == true
        }
    }
}
