package com.greenslocks.iptv

import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import java.security.MessageDigest
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

const val LSEP = "\u0002"
private const val KV = "\u0003"

data class Profile(val id: Int, val name: String)

/** AES-GCM with a key that never leaves the Android Keystore. */
object Crypto {
    private const val ALIAS = "iptv_profile_key"

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    fun encrypt(text: String): String {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key())
        return Base64.encodeToString(c.iv + c.doFinal(text.toByteArray()), Base64.NO_WRAP)
    }

    fun decrypt(s: String): String? = runCatching {
        val raw = Base64.decode(s, Base64.NO_WRAP)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, raw, 0, 12))
        String(c.doFinal(raw, 12, raw.size - 12))
    }.getOrNull()
}

/**
 * Settings and per-profile data. Profile 0 keeps the original key names so earlier installs
 * carry over; other profiles are prefixed. Playlist URLs (they contain the password) are
 * encrypted; the PIN is stored as a salted hash.
 */
class Store(private val prefs: SharedPreferences) {
    var active: Int
        get() = prefs.getInt("active", 0)
        set(v) { prefs.edit().putInt("active", v).apply() }

    private fun k(name: String) = if (active == 0) name else "p${active}_$name"

    // ---- profiles
    fun profiles(): List<Profile> {
        val l = prefs.getString("profiles", "").orEmpty().split(LSEP).filter { it.isNotEmpty() }.mapNotNull {
            val p = it.split(KV, limit = 2)
            p[0].toIntOrNull()?.let { id -> Profile(id, p.getOrElse(1) { "Profile $id" }) }
        }
        return l.ifEmpty { listOf(Profile(0, "Default")) }
    }

    fun saveProfiles(l: List<Profile>) {
        prefs.edit().putString("profiles", l.joinToString(LSEP) { "${it.id}$KV${it.name}" }).apply()
    }

    fun nextProfileId(): Int = (profiles().maxOfOrNull { it.id } ?: 0) + 1

    fun deleteProfileData(id: Int) {
        val prefix = "p${id}_"
        val e = prefs.edit()
        prefs.all.keys.filter { it.startsWith(prefix) }.forEach { e.remove(it) }
        e.apply()
    }

    // ---- active profile's playlist URL
    fun url(): String {
        prefs.getString(k("url_enc"), null)?.let { Crypto.decrypt(it) }?.let { return it }
        val plain = prefs.getString(k("url"), null) ?: return ""
        setUrl(plain) // migrate old plain-text value to encrypted storage
        return plain
    }

    fun setUrl(u: String) {
        val enc = runCatching { Crypto.encrypt(u) }.getOrNull()
        val e = prefs.edit()
        if (enc != null) { e.putString(k("url_enc"), enc); e.remove(k("url")) } else e.putString(k("url"), u)
        e.apply()
    }

    // ---- per-profile data
    fun list(name: String): List<String> =
        prefs.getString(k(name), "").orEmpty().split(LSEP).filter { it.isNotEmpty() }

    fun setList(name: String, l: List<String>) {
        prefs.edit().putString(k(name), l.joinToString(LSEP)).apply()
    }

    fun map(name: String): Map<String, String> = list(name).mapNotNull {
        val p = it.split(KV, limit = 2)
        if (p.size == 2) p[0] to p[1] else null
    }.toMap()

    fun setMap(name: String, m: Map<String, String>) = setList(name, m.map { "${it.key}$KV${it.value}" })

    fun long(name: String): Long = prefs.getLong(k(name), 0L)
    fun setLong(name: String, v: Long) { prefs.edit().putLong(k(name), v).apply() }
    fun remove(name: String) { prefs.edit().remove(k(name)).apply() }
    fun string(name: String): String? = prefs.getString(k(name), null)
    fun setString(name: String, v: String) { prefs.edit().putString(k(name), v).apply() }

    fun favorites(): List<String> {
        val l = list("fav2")
        if (l.isNotEmpty() || active != 0) return l
        return prefs.getStringSet("fav", null)?.toList() ?: emptyList() // first-version format
    }

    // ---- global settings
    fun flag(name: String, def: Boolean) = prefs.getBoolean(name, def)
    fun setFlag(name: String, v: Boolean) { prefs.edit().putBoolean(name, v).apply() }
    fun int(name: String, def: Int) = prefs.getInt(name, def)
    fun setInt(name: String, v: Int) { prefs.edit().putInt(name, v).apply() }

    // ---- backup / restore (favorites, hidden/pinned categories, names, layout and settings; never logins or PIN)
    private val backupLists = listOf("fav2", "hist", "hidCats", "hidEntries", "lockCats", "pinCats", "mylist")
    private val backupMaps = listOf("renames", "catOrder")
    private val backupInts = listOf("accent", "bg", "cards", "text", "anim", "start", "buffer", "resize", "subSize", "sortMode")
    private val backupFlags = listOf("sortAz", "showHidden", "lockAdult", "autoNext", "matchFps", "liveTs")

    fun exportBackup(): String {
        val root = JSONObject().put("app", "VanceTV").put("version", 1)
        val lists = JSONObject()
        backupLists.forEach { n -> lists.put(n, JSONArray(if (n == "fav2") favorites() else list(n))) }
        val maps = JSONObject()
        backupMaps.forEach { n -> maps.put(n, JSONObject(map(n))) }
        val ints = JSONObject()
        backupInts.forEach { n -> if (prefs.contains(n)) ints.put(n, prefs.getInt(n, 0)) }
        val flags = JSONObject()
        backupFlags.forEach { n -> if (prefs.contains(n)) flags.put(n, prefs.getBoolean(n, false)) }
        return root.put("lists", lists).put("maps", maps).put("ints", ints).put("flags", flags).toString(2)
    }

    /** Returns false if the text isn't a Vance TV backup. */
    fun importBackup(text: String): Boolean {
        val root = runCatching { JSONObject(text) }.getOrNull() ?: return false
        if (root.optString("app") != "VanceTV") return false
        root.optJSONObject("lists")?.let { o ->
            backupLists.forEach { n -> o.optJSONArray(n)?.let { a -> setList(n, List(a.length()) { a.getString(it) }) } }
        }
        root.optJSONObject("maps")?.let { o ->
            backupMaps.forEach { n ->
                o.optJSONObject(n)?.let { m -> setMap(n, m.keys().asSequence().associateWith { k -> m.getString(k) }) }
            }
        }
        root.optJSONObject("ints")?.let { o -> backupInts.forEach { n -> if (o.has(n)) setInt(n, o.getInt(n)) } }
        root.optJSONObject("flags")?.let { o -> backupFlags.forEach { n -> if (o.has(n)) setFlag(n, o.getBoolean(n)) } }
        return true
    }

    // ---- parental PIN
    fun hasPin() = prefs.contains("pin_hash")

    fun setPin(pin: String) {
        val salt = UUID.randomUUID().toString()
        prefs.edit().putString("pin_salt", salt).putString("pin_hash", hash(salt, pin)).apply()
    }

    fun checkPin(pin: String): Boolean {
        val salt = prefs.getString("pin_salt", null) ?: return false
        return prefs.getString("pin_hash", null) == hash(salt, pin)
    }

    fun clearPin() { prefs.edit().remove("pin_hash").remove("pin_salt").apply() }

    private fun hash(salt: String, pin: String) =
        MessageDigest.getInstance("SHA-256").digest((salt + pin).toByteArray())
            .joinToString("") { "%02x".format(it) }
}
