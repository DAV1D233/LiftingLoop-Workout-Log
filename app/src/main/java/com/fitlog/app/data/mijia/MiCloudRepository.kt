package com.fitlog.app.data.mijia

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import com.fitlog.app.data.SettingsRepository

data class MiSyncState(val connected: Boolean = false, val profiles: List<MiProfile> = emptyList(),
    val selected: String? = null, val auto: Boolean = true, val busy: Boolean = false,
    val status: String = "连接米家，同步 S200 体重", val lastCheck: Long = 0, val measuredAt: Long = 0,
    val needsLogin: Boolean = false)

class MiCloudRepository(context: Context, private val settings: SettingsRepository) {
    private val file = File(context.noBackupFilesDir, "mijia.enc")
    private val client = MiCloudClient()
    private val lock = Mutex()
    private var auth: MiAuth? = null
    private val state = MutableStateFlow(MiSyncState())
    val values = state.asStateFlow()

    init {
        if (file.exists()) runCatching {
            val saved = file.readBytes()
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, saved.copyOfRange(0, 12)))
            val json = JSONObject(cipher.doFinal(saved.copyOfRange(12, saved.size)).toString(Charsets.UTF_8))
            val a = json.getJSONObject("auth")
            auth = MiAuth(a.getString("uid"), a.getString("token"), a.getString("security"), a.getString("region"), a.optString("cUserId"))
            val members = json.getJSONArray("profiles")
            state.value = MiSyncState(true, (0 until members.length()).map { MiProfile.from(members.getJSONObject(it)) },
                json.optString("selected").takeIf { it.isNotBlank() }, json.optBoolean("auto", true),
                status = "已连接米家", measuredAt = json.optLong("measuredAt"))
        }.onFailure {
            auth = null
            state.value = MiSyncState(status = "连接信息无法恢复，请重新连接米家", needsLogin = true)
        }
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("fitlog_mijia", null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("fitlog_mijia", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    private fun persist() {
        val a = auth ?: return
        val s = state.value
        val json = JSONObject().put("auth", a.json()).put("profiles", JSONArray().apply { s.profiles.forEach { put(it.json()) } })
            .put("selected", s.selected.orEmpty()).put("auto", s.auto).put("measuredAt", s.measuredAt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal(json.toString().toByteArray(Charsets.UTF_8))
        val atomic = android.util.AtomicFile(file)
        val output = atomic.startWrite()
        try { output.write(cipher.iv + encrypted); atomic.finishWrite(output) }
        catch (e: Exception) { atomic.failWrite(output); throw e }
    }

    private suspend fun work(block: () -> Unit) = lock.withLock {
        state.value = state.value.copy(busy = true)
        try { withContext(Dispatchers.IO) { block() } }
        catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) {
            val detail = when (e) {
                is MiLoginExpired -> "小米登录已过期，请重新连接"
                is java.io.IOException -> "无法连接米家，请检查网络后重试"
                is org.json.JSONException, is IllegalArgumentException -> "米家返回格式不受支持，尚未更新体重"
                else -> e.message?.takeIf { it.startsWith("米家") || it.startsWith("小米") } ?: "同步未完成，请重试"
            }
            state.value = state.value.copy(status = detail, needsLogin = e is MiLoginExpired)
        } finally { state.value = state.value.copy(busy = false) }
    }

    suspend fun connect(uid: String, pass: String, region: String) = work {
        val next = client.login(uid, pass, region)
        val profiles = client.profiles(next)
        val previous = state.value
        val sameAccount = auth?.uid == next.uid && auth?.region == next.region
        val selected = if (sameAccount) previous.selected?.takeIf { key -> profiles.any { it.key == key } } else null
        auth = next
        state.value = MiSyncState(true, profiles, selected, previous.auto, busy = true,
            status = if (profiles.isEmpty()) "未找到 S200 成员，请检查米家地区、设备绑定和成员设置" else "请选择要同步的成员",
            measuredAt = if (selected == previous.selected && sameAccount) previous.measuredAt else 0)
        persist()
    }
    suspend fun refreshProfiles() = work {
        val profiles = client.profiles(auth ?: throw MiLoginExpired())
        val selected = state.value.selected?.takeIf { key -> profiles.any { it.key == key } }
        state.value = state.value.copy(profiles = profiles, selected = selected,
            measuredAt = if (selected == null) 0 else state.value.measuredAt,
            status = if (profiles.isEmpty()) "未找到 S200 成员，请检查米家地区和成员设置" else "成员已刷新，请选择你的成员", needsLogin = false)
        persist()
    }
    suspend fun select(key: String) = work {
        require(state.value.profiles.any { it.key == key })
        state.value = state.value.copy(selected = key, measuredAt = 0, lastCheck = 0, status = "已选择成员，等待同步")
        persist()
    }
    suspend fun setAuto(enabled: Boolean) = work { state.value = state.value.copy(auto = enabled); persist() }
    suspend fun disconnect() = work {
        android.util.AtomicFile(file).delete()
        check(!file.exists()) { "无法移除连接信息，请重试" }
        auth = null
        state.value = MiSyncState(status = "已断开米家，保留当前体重", busy = true)
    }
    suspend fun sync(automatic: Boolean = false) {
        if (state.value.busy) return
        work {
            val current = state.value
            if (automatic && (!current.auto || current.needsLogin || System.currentTimeMillis() - current.lastCheck < 300000)) return@work
            val a = auth
            if (a == null || current.needsLogin) {
                if (!automatic) state.value = state.value.copy(status = "请先在体重秤详细设置中连接小米账号", needsLogin = true)
                return@work
            }
            val profile = current.profiles.firstOrNull { it.key == current.selected }
            if (profile == null) {
                if (!automatic) state.value = state.value.copy(status = "请先在体重秤详细设置中选择称重成员")
                return@work
            }
            state.value = current.copy(busy = true, lastCheck = System.currentTimeMillis(), status = "正在检查米家体重…")
            val latest = client.latest(a, profile)
            val updated = latest != null && latest.timeMs > current.measuredAt && settings.importBodyWeight(latest.kg, latest.timeMs)
            state.value = state.value.copy(status = when {
                latest == null -> "米家暂无该成员的称重记录，请先在米家确认数据已上传"
                updated -> "已同步 ${com.fitlog.app.domain.number(latest.kg)} kg"
                else -> "已检查，没有比当前体重更新的记录"
            }, measuredAt = maxOf(current.measuredAt, latest?.timeMs ?: 0), needsLogin = false)
            persist()
        }
    }
}
