package com.hermex.android.core.network

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

class PersistentCookieJar(private val onChange: (String) -> Unit) : CookieJar {

    private val cookies = mutableListOf<Pair<HttpUrl, Cookie>>()

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (cookies.isEmpty()) return
        for (cookie in cookies) {
            this.cookies.removeAll { (_, existing) ->
                existing.name == cookie.name && existing.domain == cookie.domain && existing.path == cookie.path
            }
            if (cookie.expiresAt > System.currentTimeMillis()) this.cookies += url to cookie
        }
        onChange(serialize())
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        cookies.removeAll { it.second.expiresAt <= now }
        return cookies.map { it.second }.filter { it.matches(url) }
    }

    @Synchronized
    fun hasCookies(): Boolean = cookies.isNotEmpty()

    @Synchronized
    fun clear() {
        cookies.clear()
        onChange("")
    }

    @Synchronized
    fun restore(serialized: String?) {
        cookies.clear()
        serialized?.lineSequence()?.forEach { line ->
            val tab = line.indexOf('\t')
            if (tab <= 0) return@forEach
            val url = line.substring(0, tab).toHttpUrlOrNull() ?: return@forEach
            val cookie = Cookie.parse(url, line.substring(tab + 1)) ?: return@forEach
            if (cookie.expiresAt > System.currentTimeMillis()) cookies += url to cookie
        }
    }

    private fun serialize(): String = cookies.joinToString("\n") { (url, cookie) -> "$url\t$cookie" }
}
