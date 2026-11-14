package com.freetia.tv.data.backup

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** JSON form of [BackupData]. [parse] treats the text as untrusted: bounded, schema-checked and field-validated. */
object BackupCodec {
    private val pinKeys = setOf(
        "safety_pin_hash", "safety_recovery_code_hash", "safety_recovery_question", "safety_recovery_answer_hash",
    )
    private val hashPattern = Regex("^[A-Za-z0-9+/=]{1,200}:[A-Za-z0-9+/=]{1,200}$")

    fun encode(data: BackupData, createdAt: Long = System.currentTimeMillis()): String {
        val root = JSONObject()
        root.put("format", BACKUP_FORMAT)
        root.put("schema", BACKUP_SCHEMA_VERSION)
        root.put("created", createdAt)
        root.put("settings", JSONObject().also { o -> data.settings.toSortedMap().forEach { (k, v) -> o.put(k, v) } })
        root.put("playlists", JSONArray().also { a ->
            data.playlists.forEach { p ->
                a.put(JSONObject().apply {
                    put("name", p.name)
                    p.url?.let { put("url", it) }
                    p.userEpgUrl?.let { put("epgUrl", it) }
                    put("region", p.region)
                    put("default", p.isDefault)
                    put("updateOnStartup", p.updateOnStartup)
                    put("refreshMinutes", p.refreshMinutes)
                    put("backgroundPlay", p.allowBackgroundPlay)
                    put("resume", p.resumeLastChannel)
                })
            }
        })
        data.favourites?.let { root.put("favourites", refs(it)) }
        data.groups?.let { groups ->
            root.put("groups", JSONArray().also { a ->
                groups.forEach { g -> a.put(JSONObject().put("name", g.name).put("channels", refs(g.channels))) }
            })
        }
        if (data.lockedChannels != null || data.categoryLocks != null) {
            root.put("locks", JSONObject().apply {
                data.lockedChannels?.let { put("channels", refs(it)) }
                data.categoryLocks?.let { list ->
                    put("categories", JSONArray().also { a ->
                        list.forEach { a.put(JSONObject().put("scope", it.scope).put("value", it.value)) }
                    })
                }
            })
        }
        data.epgSourceUrl?.let { root.put("epgSourceUrl", it) }
        data.pinSecrets?.let { pin -> root.put("pin", JSONObject().also { o -> pin.forEach { (k, v) -> o.put(k, v) } }) }
        return root.toString()
    }

    fun parse(text: String): BackupOpen {
        if (text.length > MAX_IMPORT_CHARS) return invalid("This file is too large to be a Freetia backup.")
        val root = try {
            JSONObject(text)
        } catch (e: JSONException) {
            return invalid("This is not a Freetia backup file.")
        }
        if (root.optString("format") != BACKUP_FORMAT) return invalid("This is not a Freetia backup file.")
        val schema = root.optInt("schema", -1)
        if (schema > BACKUP_SCHEMA_VERSION) return invalid("This backup was made by a newer version of Freetia TV.")
        if (schema != BACKUP_SCHEMA_VERSION) return invalid("This backup uses a format Freetia TV cannot read.")
        return try {
            BackupOpen.Ok(read(root))
        } catch (e: BadBackup) {
            invalid(e.message ?: "This backup could not be read.")
        } catch (e: JSONException) {
            invalid("This backup is damaged.")
        }
    }

    private fun read(root: JSONObject): BackupData {
        val settings = LinkedHashMap<String, Any>()
        root.optJSONObject("settings")?.let { o ->
            o.keys().forEach { key ->
                if (key !in SettingRules.rules) return@forEach
                settings[key] = SettingRules.normalize(key, o.opt(key)) ?: bad("The setting \"$key\" has a value that is not allowed.")
            }
        }

        val playlistsJson = root.optJSONArray("playlists") ?: JSONArray()
        if (playlistsJson.length() > MAX_BACKUP_PLAYLISTS) bad("This backup has too many playlists.")
        val playlists = (0 until playlistsJson.length()).map { i ->
            val o = playlistsJson.optJSONObject(i) ?: bad("A playlist entry is damaged.")
            val name = o.optString("name").trim()
            if (name.isEmpty() || name.length > 80) bad("A playlist has no name or a name that is too long.")
            BackupPlaylist(
                name = name,
                url = optUrl(o, "url", "playlist link"),
                userEpgUrl = optUrl(o, "epgUrl", "guide link"),
                region = o.optString("region").take(64),
                isDefault = o.optBoolean("default", false),
                updateOnStartup = o.optBoolean("updateOnStartup", false),
                refreshMinutes = o.optInt("refreshMinutes", 24 * 60).coerceIn(0, 7 * 24 * 60),
                allowBackgroundPlay = o.optBoolean("backgroundPlay", false),
                resumeLastChannel = o.optBoolean("resume", true),
            )
        }

        val favourites = root.optJSONArray("favourites")?.let { readRefs(it, playlists.size) }
        val groups = root.optJSONArray("groups")?.let { a ->
            if (a.length() > MAX_BACKUP_GROUPS) bad("This backup has too many groups.")
            (0 until a.length()).map { i ->
                val g = a.optJSONObject(i) ?: bad("A group entry is damaged.")
                val name = g.optString("name").trim()
                if (name.isEmpty() || name.length > 80) bad("A group has no name or a name that is too long.")
                BackupGroup(name, g.optJSONArray("channels")?.let { readRefs(it, playlists.size) }.orEmpty())
            }
        }
        val locks = root.optJSONObject("locks")
        val lockedChannels = locks?.optJSONArray("channels")?.let { readRefs(it, playlists.size) }
        val categoryLocks = locks?.optJSONArray("categories")?.let { a ->
            if (a.length() > 1_000) bad("This backup has too many locks.")
            (0 until a.length()).map { i ->
                val o = a.optJSONObject(i) ?: bad("A lock entry is damaged.")
                val scope = o.optString("scope")
                val value = o.optString("value").trim()
                if (scope != "group" && scope != "region") bad("A lock has an unknown type.")
                if (value.isEmpty() || value.length > 120) bad("A lock has an empty or too long name.")
                CategoryLockRef(scope, value)
            }
        }

        val epg = if (root.has("epgSourceUrl")) {
            root.optString("epgSourceUrl").takeIf { it.isNotEmpty() }?.also {
                if (!UrlRules.isHttpUrl(it)) bad("The guide link must start with http or https.")
            }
        } else null

        val pin = root.optJSONObject("pin")?.let { o ->
            val map = LinkedHashMap<String, String>()
            o.keys().forEach { key ->
                if (key !in pinKeys) return@forEach
                val v = o.optString(key)
                val valid = if (key.endsWith("_question")) v.length in 1..200 else hashPattern.matches(v)
                if (!valid) bad("The PIN data in this backup is damaged.")
                map[key] = v
            }
            map.takeIf { it.isNotEmpty() }
        }

        return BackupData(settings, playlists, favourites, groups, lockedChannels, categoryLocks, epg, pin)
    }

    private fun optUrl(o: JSONObject, key: String, what: String): String? {
        if (!o.has(key)) return null
        val v = o.optString(key)
        if (v.isEmpty()) return null
        if (!UrlRules.isHttpUrl(v)) bad("A $what must start with http or https.")
        return v
    }

    private fun refs(list: List<ChannelRef>) = JSONArray().also { a ->
        list.forEach { a.put(JSONObject().put("p", it.playlist).put("id", it.tvgId).put("n", it.name)) }
    }

    private fun readRefs(a: JSONArray, playlistCount: Int): List<ChannelRef> {
        if (a.length() > MAX_BACKUP_REFS) bad("This backup lists too many channels.")
        return (0 until a.length()).map { i ->
            val o = a.optJSONObject(i) ?: bad("A channel entry is damaged.")
            val p = o.optInt("p", -1)
            if (p !in 0 until playlistCount) bad("A channel points at a playlist that is not in the backup.")
            val id = o.optString("id")
            val name = o.optString("n")
            if (id.length > 200 || name.length > 200 || (id.isEmpty() && name.isEmpty())) bad("A channel entry is damaged.")
            ChannelRef(p, id, name)
        }
    }

    private class BadBackup(message: String) : Exception(message)

    private fun bad(message: String): Nothing = throw BadBackup(message)

    private fun invalid(reason: String) = BackupOpen.Invalid(reason)
}
