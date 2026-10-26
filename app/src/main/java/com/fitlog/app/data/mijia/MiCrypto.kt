package com.fitlog.app.data.mijia

import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/** Xiaomi Home's wire protocol; never use RC4 for local credential storage. */
object MiCrypto {
    fun encode(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)
    fun decode(text: String): ByteArray = Base64.getDecoder().decode(text.trim().trim('"', '\''))
    fun nonce(): ByteArray = ByteBuffer.allocate(12).putLong(SecureRandom().nextLong())
        .putInt((System.currentTimeMillis() / 60000).toInt()).array()
    fun signedNonce(security: String, nonce: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(decode(security) + nonce)
    fun signature(path: String, key: ByteArray, params: Map<String, String>): String {
        val value = (listOf("POST", path) + params.map { "${it.key}=${it.value}" } + encode(key)).joinToString("&")
        return encode(MessageDigest.getInstance("SHA-1").digest(value.toByteArray(Charsets.UTF_8)))
    }
    fun crypt(key: ByteArray, input: ByteArray): ByteArray {
        require(key.isNotEmpty())
        val state = IntArray(256) { it }
        var j = 0
        for (i in 0..255) {
            j = (j + state[i] + (key[i % key.size].toInt() and 255)) and 255
            val old = state[i]; state[i] = state[j]; state[j] = old
        }
        var i = 0; j = 0
        fun next(): Int {
            i = (i + 1) and 255; j = (j + state[i]) and 255
            val old = state[i]; state[i] = state[j]; state[j] = old
            return state[(state[i] + state[j]) and 255]
        }
        repeat(1024) { next() }
        return ByteArray(input.size) { (input[it].toInt() xor next()).toByte() }
    }
    fun params(path: String, security: String, json: String, nonce: ByteArray): Map<String, String> {
        val key = signedNonce(security, nonce)
        val plain = linkedMapOf("data" to json)
        plain["rc4_hash__"] = signature(path, key, plain)
        val encrypted = plain.mapValuesTo(linkedMapOf()) { encode(crypt(key, it.value.toByteArray(Charsets.UTF_8))) }
        encrypted["signature"] = signature(path, key, encrypted)
        encrypted["ssecurity"] = security
        encrypted["_nonce"] = encode(nonce)
        return encrypted
    }
}
