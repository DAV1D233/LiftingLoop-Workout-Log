package com.fitlog.app.data.mijia

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.ZoneId
import java.util.zip.GZIPInputStream

class MiLoginExpired : Exception("小米登录已过期，请重新连接")
data class MiAuth(val uid: String, val token: String, val security: String, val region: String, val cUserId: String = "") {
    fun json() = JSONObject().put("uid", uid).put("token", token).put("security", security).put("region", region).put("cUserId", cUserId)
}
data class MiProfile(val did: String, val model: String, val scale: String, val uid: String,
    val accountId: String, val name: String) {
    val key get() = "$did:$uid:$accountId"
    fun json() = JSONObject().put("did", did).put("model", model).put("scale", scale).put("uid", uid)
        .put("accountId", accountId).put("name", name)
    companion object {
        fun from(j: JSONObject) = MiProfile(j.getString("did"), j.getString("model"), j.getString("scale"),
            j.getString("uid"), j.getString("accountId"), j.getString("name"))
    }
}
data class MiMeasurement(val kg: Double, val timeMs: Long)

/** Read-only calls, based on dolczykk/xiaomi-scale-mcp (MIT; see THIRD-PARTY-NOTICES). */
class MiCloudClient {
    companion object {
        val regions = linkedMapOf("cn" to "中国大陆", "de" to "欧洲", "us" to "美国", "sg" to "新加坡", "tw" to "台湾", "ru" to "俄罗斯", "in" to "印度", "i2" to "印度（i2）")
        const val LOGIN_URL = "https://account.xiaomi.com/pass/serviceLogin?sid=xiaomiio&_locale=zh_CN"
        private const val AGENT = "Android-7.1.1-1.0.0-ONEPLUS A3010-136-46C3D4313D5679D061F49997E21F5199 APP/xiaomi.smarthome APPV/11.6.625"
        fun trustedLoginUrl(value: String): Boolean = runCatching {
            val url = URL(value)
            url.protocol == "https" && (url.host == "xiaomi.com" || url.host.endsWith(".xiaomi.com") ||
                url.host == "mi.com" || url.host.endsWith(".mi.com"))
        }.getOrDefault(false)
        fun cookies(header: String) = header.split(';').mapNotNull {
            val pair = it.trim().split('=', limit = 2)
            if (pair.size == 2) pair[0] to pair[1] else null
        }.toMap()
    }

    private data class Reply(val body: String, val cookies: Map<String, String>, val location: String?, val security: String?)
    private fun request(url: String, cookie: String, form: Map<String, String>? = null,
        headers: Map<String, String> = emptyMap()): Reply {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 20000; connection.readTimeout = 20000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("User-Agent", AGENT)
            connection.setRequestProperty("Cookie", cookie)
            connection.setRequestProperty("Accept-Encoding", "identity")
            headers.forEach { (key, value) -> connection.setRequestProperty(key, value) }
            if (form != null) {
                connection.requestMethod = "POST"; connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                val body = form.entries.joinToString("&") { "${URLEncoder.encode(it.key, "UTF-8")}=${URLEncoder.encode(it.value, "UTF-8")}" }
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = connection.responseCode
            if (code == 401 || code == 403) throw MiLoginExpired()
            check(code in 200..399) { "米家服务暂时不可用（HTTP $code）" }
            val saved = connection.headerFields.entries.filter { it.key.equals("Set-Cookie", true) }
                .flatMap { it.value }.mapNotNull { line -> cookies(line.substringBefore(';')).entries.firstOrNull()?.toPair() }.toMap()
            val body = connection.inputStream.use { stream ->
                val bytes = stream.readBytes()
                check(bytes.size <= 4 * 1024 * 1024) { "米家返回数据过大" }
                if (connection.contentEncoding.equals("gzip", true)) GZIPInputStream(bytes.inputStream()).bufferedReader().use { it.readText() }
                else bytes.toString(Charsets.UTF_8)
            }
            val security = connection.getHeaderField("Extension-Pragma")?.let { JSONObject(it).optString("ssecurity").takeIf(String::isNotBlank) }
            return Reply(body, saved, connection.getHeaderField("Location"), security)
        } finally { connection.disconnect() }
    }

    fun login(uid: String, passToken: String, region: String): MiAuth {
        require(region in regions)
        require(uid.matches(Regex("[0-9]+")) && passToken.isNotBlank() && !passToken.contains(Regex("[;\\r\\n]")))
        val jar = mutableMapOf("userId" to uid, "passToken" to passToken)
        val first = request("https://account.xiaomi.com/pass/serviceLogin?sid=xiaomiio&_json=true&_locale=zh_CN&appName=com.xiaomi.smarthome",
            jar.entries.joinToString("; ") { "${it.key}=${it.value}" })
        val json = JSONObject(first.body.removePrefix("&&&START&&&"))
        jar.putAll(first.cookies)
        if (json.optInt("code", -1) != 0 || !json.has("ssecurity")) throw MiLoginExpired()
        var security = json.getString("ssecurity")
        val user = json.optString("userId", uid)
        var location = json.getString("location")
        for (step in 0 until 10) {
            check(trustedLoginUrl(location)) { "小米登录跳转地址异常" }
            val reply = request(location, jar.entries.joinToString("; ") { "${it.key}=${it.value}" })
            jar.putAll(reply.cookies)
            reply.security?.let { security = it }
            if (!jar["serviceToken"].isNullOrBlank()) return MiAuth(user, jar.getValue("serviceToken"), security, region, jar["cUserId"].orEmpty())
            location = reply.location?.let { URL(URL(location), it).toString() } ?: break
        }
        throw MiLoginExpired()
    }

    private fun api(auth: MiAuth, path: String, data: JSONObject, model: String? = null, core: Boolean = false): Any {
        val prefix = if (auth.region == "cn") "" else "${auth.region}."
        val base = if (core) "https://${prefix}core.api.io.mi.com/app/v2" else "https://${prefix}api.io.mi.com/app"
        val signaturePath = if (core) "/v2$path" else path
        val nonce = MiCrypto.nonce()
        val headers = mutableMapOf("miot-encrypt-algorithm" to "ENCRYPT-RC4", "miot-accept-encoding" to "GZIP",
            "origin-from" to "MiHome", "origin-model" to "mphone.phone.online",
            "miot-origin-request-version" to "android;phone;11.6.625.4316", "miot-request-page" to "none")
        if (model != null) headers["miot-request-model"] = model
        val cookie = "userId=${auth.uid};cUserId=${auth.cUserId};serviceToken=${auth.token};yetAnotherServiceToken=${auth.token};locale=zh_CN;countryCode=CN;timezone_id=Asia/Shanghai;timezone=GMT+08:00;channel=MI_APP_STORE"
        val reply = request(base + path, cookie, MiCrypto.params(signaturePath, auth.security, data.toString(), nonce), headers)
        val raw = reply.body.trim()
        val text = if (raw.startsWith("{")) raw else {
            val bytes = MiCrypto.crypt(MiCrypto.signedNonce(auth.security, nonce), MiCrypto.decode(raw))
            if (bytes.size > 1 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte())
                GZIPInputStream(bytes.inputStream()).bufferedReader().use { it.readText() } else bytes.toString(Charsets.UTF_8)
        }
        val json = JSONObject(text)
        val code = json.optInt("code", -1)
        if (code in listOf(-3, -6, 401, 403, -10001, -10002)) throw MiLoginExpired()
        check(code == 0) { "米家未能返回数据（代码 $code），请检查地区或重新连接" }
        return json.get("result")
    }

    fun profiles(auth: MiAuth): List<MiProfile> {
        val request = JSONObject().put("ssid", "<unknown ssid>").put("bssid", "02:00:00:00:00:00")
            .put("getVirtualModel", true).put("getHuamiDevices", 1)
        listOf("get_split_device", "support_smart_home", "get_cariot_device", "get_third_device", "get_phone_device", "get_miwear_device").forEach { request.put(it, true) }
        val result = api(auth, "/home/device_list_page", request, core = true) as JSONObject
        val devices = result.getJSONArray("list")
        val profiles = mutableListOf<MiProfile>()
        for (i in 0 until devices.length()) {
            val device = devices.getJSONObject(i)
            val model = device.optString("model")
            if (model != "yunmai.scales.ms106") continue
            val did = device.getString("did")
            val uid = device.optString("uid", auth.uid)
            val members = api(auth, "/eco/scale/account/list", JSONObject().put("uid", uid).put("deviceId", did), model) as JSONArray
            for (m in 0 until members.length()) {
                val member = members.getJSONObject(m)
                profiles += MiProfile(did, model, device.optString("name", "米家 S200"), uid,
                    member.getString("accountId"), member.optString("name", "成员 ${m + 1}"))
            }
        }
        return profiles.distinctBy { it.key }
    }

    fun latest(auth: MiAuth, profile: MiProfile): MiMeasurement? {
        val offset = ZoneId.systemDefault().rules.getOffset(java.time.Instant.now()).totalSeconds
        val data = JSONObject().put("model", profile.model).put("uid", profile.uid).put("did", profile.did)
            .put("accountId", profile.accountId).put("beginTime", System.currentTimeMillis())
            .put("endTime", -offset).put("pageSize", 20)
        val records = api(auth, "/eco/common/scale/getUserDataByPage", data, profile.model) as JSONArray
        return parseMeasurements(records, profile).maxByOrNull { it.timeMs }
    }

    fun parseMeasurements(records: JSONArray, profile: MiProfile): List<MiMeasurement> =
        (0 until records.length()).mapNotNull { i ->
            val record = records.getJSONObject(i)
            // Never use another member's record even if the service ignores its filter.
            if (record.optString("accountId") != profile.accountId || record.optString("uid") != profile.uid ||
                record.optString("did") != profile.did || record.optString("model") != profile.model) return@mapNotNull null
            val measurement = when (val data = record.get("data")) {
                is JSONObject -> data
                is String -> JSONObject(data)
                else -> error("米家称重数据格式不受支持")
            }
            val kg = measurement.getDouble("weight")
            val seconds = measurement.getString("time").toLong()
            if (seconds !in 1..(System.currentTimeMillis() / 1000 + 300) ||
                !com.fitlog.app.domain.BodyWeight.valid(kg)) return@mapNotNull null
            MiMeasurement(kg, seconds * 1000)
        }
}
