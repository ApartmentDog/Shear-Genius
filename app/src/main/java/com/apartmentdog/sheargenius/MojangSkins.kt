package com.apartmentdog.sheargenius

import android.util.Base64
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Looks up a Java Edition player's current skin. Blocking: call off the main thread. */
object MojangSkins {
    class Skin(val name: String, val bytes: ByteArray, val slim: Boolean)

    class LookupException(message: String) : Exception(message)

    private val validName = Regex("^[A-Za-z0-9_]{1,16}$")

    fun fetch(username: String): Skin {
        val clean = username.trim()
        if (!validName.matches(clean)) throw LookupException("That isn't a valid Minecraft username.")

        val profile = lookupProfile(clean) ?: throw LookupException("No Java Edition player is named $clean.")
        val id = profile.getString("id")
        val name = profile.optString("name", clean)

        val session = get("https://sessionserver.mojang.com/session/minecraft/profile/$id")
        if (session.first != 200 || session.second == null) throw LookupException("Couldn't load $name's profile. Try again in a minute.")
        val props = JSONObject(String(session.second!!)).optJSONArray("properties")
            ?: throw LookupException("$name's profile has no skin data.")
        var texturesB64: String? = null
        for (i in 0 until props.length()) {
            val p = props.getJSONObject(i)
            if (p.optString("name") == "textures") texturesB64 = p.optString("value")
        }
        if (texturesB64 == null) throw LookupException("$name's profile has no skin data.")
        val textures = JSONObject(String(Base64.decode(texturesB64, Base64.DEFAULT))).optJSONObject("textures")
        val skin = textures?.optJSONObject("SKIN")
            ?: throw LookupException("$name uses a default skin, so there's no custom skin to download.")
        val url = skin.getString("url").replaceFirst("http://", "https://")
        val slim = skin.optJSONObject("metadata")?.optString("model") == "slim"

        val png = get(url)
        if (png.first != 200 || png.second == null) throw LookupException("Couldn't download $name's skin file.")
        return Skin(name, png.second!!, slim)
    }

    private fun lookupProfile(name: String): JSONObject? {
        val urls = listOf(
            "https://api.mojang.com/users/profiles/minecraft/$name",
            "https://api.minecraftservices.com/minecraft/profile/lookup/name/$name"
        )
        var reached = false
        for (u in urls) {
            val r = try {
                get(u)
            } catch (e: IOException) {
                continue
            }
            reached = true
            if (r.first == 200 && r.second != null) {
                val json = JSONObject(String(r.second!!))
                if (json.has("id")) return json
            }
        }
        if (!reached) throw LookupException("Couldn't reach Minecraft's servers. Check your connection.")
        return null
    }

    private fun get(url: String): Pair<Int, ByteArray?> {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.setRequestProperty("User-Agent", "ShearGenius/1.0")
            val code = conn.responseCode
            val body = if (code == 200) conn.inputStream.use { it.readBytes() } else null
            return code to body
        } finally {
            conn.disconnect()
        }
    }
}
