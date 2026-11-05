package com.zl.qiewash.data

import com.zl.qiewash.BuildConfig

import android.content.Context
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Qiekj API transport: form POST + the two SHA-256 header signatures.
 * Signing rules were verified byte-for-byte against the official app (1.96.1).
 */
object Api {
    private const val BASE = "https://userapi.qiekj.com/"
    // Signing secret comes from local.properties at build time (never committed).
    private val SECRET: String get() = BuildConfig.APP_SECRET
    const val VERSION = "1.96.1"

    private val form = "application/x-www-form-urlencoded;charset=UTF-8".toMediaType()
    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    var token: String = ""
    var phoneBrand: String = "Xiaomi"

    private fun sha256(s: String): String =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray())
            .joinToString("") { "%02x".format(it) }

    fun sign(path: String, ts: String, token: String): String =
        sha256("appSecret=$SECRET&channel=android_app&timestamp=$ts&token=$token&version=$VERSION&$path")

    fun riskSign(path: String, ts: String, token: String, imei: String, cc: String, lat: String, lng: String): String =
        sha256(
            "appSecret=$SECRET&categoryCode=$cc&channel=android_app&imei=$imei&lat=$lat&lng=$lng" +
                "&timestamp=$ts&token=$token&version=$VERSION&$path"
        )

    /** Trade create needs the extra geo/device headers plus the risk signature. */
    fun riskHeaders(path: String, imei: String, cc: String, lat: String, lng: String): Map<String, String> {
        val ts = System.currentTimeMillis().toString()
        return mapOf(
            "imei" to imei,
            "categoryCode" to cc,
            "lat" to lat,
            "lng" to lng,
            "orderRiskTimestamp" to ts,
            "orderRiskSign" to riskSign(path, ts, token, imei, cc, lat, lng)
        )
    }

    fun post(path: String, params: List<Pair<String, String>>, extra: Map<String, String> = emptyMap()): JSONObject {
        val p = "/" + path.trimStart('/')
        val ts = System.currentTimeMillis().toString()
        val encoded = params.filter { it.second.isNotEmpty() }
            .joinToString("&") { "${URLEncoder.encode(it.first, "UTF-8")}=${URLEncoder.encode(it.second, "UTF-8")}" }
        val body = when {
            token.isEmpty() -> encoded
            encoded.isEmpty() -> "token=" + URLEncoder.encode(token, "UTF-8")
            else -> "$encoded&token=" + URLEncoder.encode(token, "UTF-8")
        }

        val builder = Request.Builder()
            .url(BASE + path.trimStart('/'))
            .header("Authorization", token)
            .header("version", VERSION)
            .header("channel", "android_app")
            .header("phoneBrand", phoneBrand)
            .header("Timestamp", ts)
            .header("Sign", sign(p, ts, token))
            .header("User-Agent", "QEUser/$VERSION (com.qiekj.user; build:1440; Android 13; userChannel:android_app; version:$VERSION) OkHttp/4.9.3")
            .post(body.toRequestBody(form))
        extra.forEach { (k, v) -> if (v.isNotEmpty()) builder.header(k, v) }

        client.newCall(builder.build()).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (text.isBlank()) return JSONObject()
            val json = runCatching { JSONObject(text) }.getOrElse {
                JSONObject().put("code", -1).put("msg", "服务异常 (${resp.code})")
            }
            if (json.optString("code") == "2") throw SessionExpired(path)
            return json
        }
    }
}

class SessionExpired(val path: String) : Exception("登录已过期")

/** Token + phone persisted in SharedPreferences; no refresh endpoint exists. */
class Session(ctx: Context) {
    private val sp = ctx.getSharedPreferences("qiewash", Context.MODE_PRIVATE)

    var token: String
        get() = sp.getString("token", "").orEmpty()
        set(v) { sp.edit().putString("token", v).apply(); Api.token = v }

    var phone: String
        get() = sp.getString("phone", "").orEmpty()
        set(v) = sp.edit().putString("phone", v).apply()

    var brand: String
        get() = sp.getString("brand", "Xiaomi").orEmpty()
        set(v) = sp.edit().putString("brand", v).apply()

    /** Coordinates only feed the order risk signature; chosen during onboarding. */
    var lat: String
        get() = sp.getString("lat", "").orEmpty()
        set(v) = sp.edit().putString("lat", v).apply()

    var lng: String
        get() = sp.getString("lng", "").orEmpty()
        set(v) = sp.edit().putString("lng", v).apply()

    /** The run currently believed to be in flight; survives restarts so the banner/notification resume. */
    var runOrderNo: String
        get() = sp.getString("runOrderNo", "").orEmpty()
        set(v) = sp.edit().putString("runOrderNo", v).apply()

    var runTitle: String
        get() = sp.getString("runTitle", "").orEmpty()
        set(v) = sp.edit().putString("runTitle", v).apply()

    var runGoodsId: String
        get() = sp.getString("runGoodsId", "").orEmpty()
        set(v) = sp.edit().putString("runGoodsId", v).apply()

    var runEndAt: Long
        get() = sp.getLong("runEndAt", 0L)
        set(v) = sp.edit().putLong("runEndAt", v).apply()

    /** How far the nearby scan looks around each known machine id. */
    var spread: Int
        get() = sp.getInt("spread", 20)
        set(v) = sp.edit().putInt("spread", v).apply()

    init {
        Api.token = token
        Api.phoneBrand = brand
    }

    fun save(t: String, p: String) {
        token = t
        phone = p
        brand = android.os.Build.BRAND?.replaceFirstChar { it.uppercase() } ?: "Xiaomi"
        Api.phoneBrand = brand
    }

    fun clear() {
        sp.edit().remove("token").apply()
        Api.token = ""
    }
}
