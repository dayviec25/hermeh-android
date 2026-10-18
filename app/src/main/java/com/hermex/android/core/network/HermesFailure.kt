package com.hermex.android.core.network

import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

const val MINIMUM_HERMES_VERSION = "0.21.3"

sealed class HermesFailure(message: String) : Exception(message) {
    class Rejected(val code: Int, val host: String) : HermesFailure(rejectedMessage(code, host))
    class Outdated(val version: String) :
        HermesFailure("This Hermes host runs $version. Hermex needs Hermes $MINIMUM_HERMES_VERSION or later. Update Hermes on the host, then try again.")
    class NotDashboard(host: String) :
        HermesFailure("$host isn't a Hermes dashboard. Use the dashboard address, not the Hermes Web UI.")
    class Blocked : HermesFailure("Something in front of Hermes, such as Cloudflare Access, wants its own sign-in first. Add its service token under Advanced, or use an address that skips it, such as the dashboard's local network address.")
    class BrowserSignIn : HermesFailure("This Hermes host only offers sign-in with a browser, which Hermex doesn't support yet. To connect now, add a dashboard username and password on the host.")
    class Unsupported : HermesFailure("This Hermes connection does not support chat here.")
    class InvalidAddress : HermesFailure("Enter a Hermes HTTP or HTTPS address without credentials or query.")
    class UpgradeRefused(val status: Int) :
        HermesFailure("Hermes accepted the sign-in, but the live connection was refused. If a proxy or tunnel sits in front of Hermes, turn on WebSocket support and let the Sec-WebSocket-Protocol header through.")
    class Transport(host: String, cause: Throwable?) : HermesFailure(transportMessage(host, cause))
    class Rpc(val code: Int, message: String) : HermesFailure(message)
    class Refused(val code: Int, val detail: String) : HermesFailure(detail)
    class SignInExpired : HermesFailure("Your Hermes sign-in expired. Sign in again.")

    companion object {
        fun rejectedMessage(code: Int, host: String): String = when (code) {
            400 -> "Hermes doesn't accept $host as its address. On the host, set dashboard.public_url to this address, then restart the dashboard."
            401 -> "Hermes didn't accept the username or password."
            403 -> "Something in front of Hermes, such as Cloudflare Access, blocked the request."
            429 -> "Too many sign-in attempts. Wait a minute, then try again."
            in 502..504 -> "Your proxy answered, but Hermes didn't. Check that the dashboard is running on the host."
            in 520..530 -> "Cloudflare can't reach your tunnel. Check that cloudflared and the dashboard are running on the host."
            else -> "Hermes answered with HTTP $code."
        }

        fun transportMessage(host: String, cause: Throwable?): String = when (cause) {
            is UnknownHostException -> "Couldn't find $host. Check the address. For a Tailscale or VPN name, make sure this phone is connected to it."
            is ConnectException -> "$host refused the connection. Check the port and that the Hermes dashboard is running."
            is SocketTimeoutException -> "$host didn't answer. Check that this phone can reach it on this network, or use your tunnel address."
            is SSLException -> "Couldn't make a secure connection to $host. Check its certificate. A dashboard on your local network without HTTPS needs http://."
            else -> "Couldn't reach $host. Check the address and network."
        }

        fun from(error: Throwable, host: String): HermesFailure = when (error) {
            is HermesFailure -> error
            is IOException -> Transport(host, error)
            else -> Transport(host, error)
        }
    }
}

object HermesCompatibility {
    fun isSupported(version: String?): Boolean {
        val reported = release(version) ?: return true
        val minimum = release(MINIMUM_HERMES_VERSION) ?: return true
        for (i in 0 until 3) {
            if (reported[i] != minimum[i]) return reported[i] > minimum[i]
        }
        return true
    }

    fun release(version: String?): List<Int>? {
        if (version == null) return null
        val head = version.takeWhile { it.isDigit() || it == '.' }
        val numbers = head.split('.').mapNotNull { it.toIntOrNull() }
        return if (numbers.size >= 3) numbers.take(3) else null
    }
}
