package com.github.shadowsocks.database

import android.annotation.TargetApi
import android.net.Uri
import android.os.Parcelable
import android.util.Base64
import android.util.LongSparseArray
import androidx.core.net.toUri
import androidx.room.*
import com.github.shadowsocks.plugin.PluginConfiguration
import com.github.shadowsocks.plugin.PluginOptions
import com.github.shadowsocks.preference.DataStore
import com.github.shadowsocks.utils.Key
import com.github.shadowsocks.utils.parsePort
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import kotlinx.parcelize.Parcelize
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.io.Serializable
import java.net.URI
import java.net.URISyntaxException
import java.util.*

@Entity
@Parcelize
data class Profile(
    @PrimaryKey(autoGenerate = true)
    var id: Long = 0,

    // user configurable fields
    var name: String? = "",

    var host: String = "au1.ssocks.xyz",
    var remotePort: Int = 2087,
    var password: String = "sH5xVxdGMyWEsGQN",
    var method: String = "chacha20-ietf-poly1305",

    var route: String = "all",
    var remoteDns: String = "dns.google",
    var proxyApps: Boolean = false,
    var bypass: Boolean = false,
    var udpdns: Boolean = false,
    var ipv6: Boolean = false,
    @TargetApi(28)
    var metered: Boolean = false,
    var individual: String = "",
    var plugin: String? = null,
    var udpFallback: Long? = null,

    // managed fields
    var subscription: SubscriptionStatus = SubscriptionStatus.UserConfigured,
    var tx: Long = 0,
    var rx: Long = 0,
    var userOrder: Long = 0,

    @Ignore // not persisted in db, only used by direct boot
    var dirty: Boolean = false
) : Parcelable, Serializable {
    enum class SubscriptionStatus(val persistedValue: Int) {
        UserConfigured(0),
        Active(1),

        /**
         * This profile is no longer present in subscriptions.
         */
        Obsolete(2),
        ;

        companion object {
            @JvmStatic
            @TypeConverter
            fun of(value: Int) = values().single { it.persistedValue == value }

            @JvmStatic
            @TypeConverter
            fun toInt(status: SubscriptionStatus) = status.persistedValue
        }
    }


    @androidx.room.Dao
    interface Dao {
        @Query("SELECT * FROM `Profile` WHERE `id` = :id")
        operator fun get(id: Long): Profile?

        @Query("SELECT * FROM `Profile` WHERE `Subscription` != 2 ORDER BY `userOrder`")
        fun listActive(): List<Profile>

        @Query("SELECT * FROM `Profile`")
        fun listAll(): List<Profile>

        @Query("SELECT MAX(`userOrder`) + 1 FROM `Profile`")
        fun nextOrder(): Long?

        @Query("SELECT 1 FROM `Profile` LIMIT 1")
        fun isNotEmpty(): Boolean

        @Insert
        fun create(value: Profile): Long

        @Update
        fun update(value: Profile): Int

        @Query("DELETE FROM `Profile` WHERE `id` = :id")
        fun delete(id: Long): Int

        @Query("DELETE FROM `Profile`")
        fun deleteAll(): Int
    }

    val formattedAddress
        get() = (if (host.contains(":")) "[%s]:%d" else "%s:%d").format(
            host,
            remotePort
        )
    val formattedName get() = if (name.isNullOrEmpty()) formattedAddress else name!!


    fun toUri(): Uri {
        val auth = Base64.encodeToString(
            "$method:$password".toByteArray(),
            Base64.NO_PADDING or Base64.NO_WRAP or Base64.URL_SAFE
        )
        val wrappedHost = if (host.contains(':')) "[$host]" else host
        val builder = Uri.Builder()
            .scheme("ss")
            .encodedAuthority("$auth@$wrappedHost:$remotePort")
        val configuration = PluginConfiguration(plugin ?: "")
        if (configuration.selected.isNotEmpty()) {
            builder.appendQueryParameter(Key.plugin, configuration.getOptions().toString(false))
        }
        if (!name.isNullOrEmpty()) builder.fragment(name)
        return builder.build()
    }

    override fun toString() = toUri().toString()

    fun toJson(profiles: LongSparseArray<Profile>? = null): JSONObject = JSONObject().apply {
        put("server", host)
        put("server_port", remotePort)
        put("password", password)
        put("method", method)
        if (profiles == null) return@apply
        PluginConfiguration(plugin ?: "").getOptions().also {
            if (it.id.isNotEmpty()) {
                put("plugin", it.id)
                put("plugin_opts", it.toString())
            }
        }
        put("remarks", name)
        put("route", route)
        put("remote_dns", remoteDns)
        put("ipv6", ipv6)
        put("metered", metered)
        put("proxy_apps", JSONObject().apply {
            put("enabled", proxyApps)
            if (proxyApps) {
                put("bypass", bypass)
                // android_ prefix is used because package names are Android specific
                put("android_list", JSONArray(individual.split("\n")))
            }
        })
        put("udpdns", udpdns)
        val fallback = profiles.get(udpFallback ?: return@apply)
        if (fallback != null && fallback.plugin.isNullOrEmpty()) fallback.toJson()
            .also { put("udp_fallback", it) }
    }

    fun serialize() {
        DataStore.editingId = id
        DataStore.privateStore.putString(Key.name, name)
        DataStore.privateStore.putString(Key.host, host)
        DataStore.privateStore.putString(Key.remotePort, remotePort.toString())
        DataStore.privateStore.putString(Key.password, password)
        DataStore.privateStore.putString(Key.route, route)
        DataStore.privateStore.putString(Key.remoteDns, remoteDns)
        DataStore.privateStore.putString(Key.method, method)
        DataStore.proxyApps = proxyApps
        DataStore.bypass = bypass
        DataStore.privateStore.putBoolean(Key.udpdns, udpdns)
        DataStore.privateStore.putBoolean(Key.ipv6, ipv6)
        DataStore.privateStore.putBoolean(Key.metered, metered)
        DataStore.individual = individual
        DataStore.plugin = plugin ?: ""
        DataStore.udpFallback = udpFallback
        DataStore.privateStore.remove(Key.dirty)
    }

    fun deserialize() {
        check(id == 0L || DataStore.editingId == id)
        DataStore.editingId = null
        // It's assumed that default values are never used, so 0/false/null is always used even if that isn't the case
        name = DataStore.privateStore.getString(Key.name) ?: ""
        // It's safe to trim the hostname, as we expect no leading or trailing whitespaces here
        host = (DataStore.privateStore.getString(Key.host) ?: "").trim()
        remotePort = parsePort(DataStore.privateStore.getString(Key.remotePort), 8388, 1)
        password = DataStore.privateStore.getString(Key.password) ?: ""
        method = DataStore.privateStore.getString(Key.method) ?: ""
        route = DataStore.privateStore.getString(Key.route) ?: ""
        remoteDns = DataStore.privateStore.getString(Key.remoteDns) ?: ""
        proxyApps = DataStore.proxyApps
        bypass = DataStore.bypass
        udpdns = DataStore.privateStore.getBoolean(Key.udpdns, false)
        ipv6 = DataStore.privateStore.getBoolean(Key.ipv6, false)
        metered = DataStore.privateStore.getBoolean(Key.metered, false)
        individual = DataStore.individual
        plugin = DataStore.plugin
        udpFallback = DataStore.udpFallback
    }
}
