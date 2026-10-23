package com.ai.assistance.operit.sync

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-256-GCM + PBKDF2 客户端侧加密工具。
 * 与服务端约定：客户端加密后上传密文 blob + nonce（base64编码），服务端不解密只存储。
 * 密钥派生：PBKDF2WithHmacSHA256，256bit key，16字节盐，10000次迭代。
 */
object SyncCrypto {

    private const val ALGO = "AES"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val KEY_SIZE_BITS = 256
    private const val GCM_IV_LEN = 12 // GCM 推荐 12 字节 nonce
    private const val GCM_TAG_LEN_BITS = 128
    private const val PBKDF2_ITERATIONS = 10_000
    private const val PBKDF2_SALT_LEN = 16
    private const val PBKDF2_ALGO = "PBKDF2WithHmacSHA256"

    private val secureRandom = SecureRandom()

    /** 生成随机 hex salt（用于 PBKDF2 密钥派生） */
    fun generateSaltHex(): String {
        val bytes = ByteArray(PBKDF2_SALT_LEN)
        secureRandom.nextBytes(bytes)
        return bytes.toHex()
    }

    /** 生成随机 12 字节 nonce（用于 AES-GCM IV） */
    fun generateNonce(): ByteArray {
        val bytes = ByteArray(GCM_IV_LEN)
        secureRandom.nextBytes(bytes)
        return bytes
    }

    /** PBKDF2 派生 AES-256 key */
    fun deriveKey(password: String, saltHex: String): ByteArray {
        val salt = saltHex.hexToBytes()
        val factory = SecretKeyFactory.getInstance(PBKDF2_ALGO)
        val spec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_SIZE_BITS)
        return factory.generateSecret(spec).encoded
    }

    /** AES-256-GCM 加密，返回密文+IV 拼接 */
    fun encrypt(data: ByteArray, key: ByteArray, nonce: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val keySpec = SecretKeySpec(key, ALGO)
        val gcmSpec = GCMParameterSpec(GCM_TAG_LEN_BITS, nonce)
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec)
        return cipher.doFinal(data)
    }

    /** AES-256-GCM 解密 */
    fun decrypt(ciphertext: ByteArray, key: ByteArray, nonce: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val keySpec = SecretKeySpec(key, ALGO)
        val gcmSpec = GCMParameterSpec(GCM_TAG_LEN_BITS, nonce)
        cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec)
        return cipher.doFinal(ciphertext)
    }

    // ============ 便捷封装：String 明文 → base64 密文 + hex nonce ============

    fun encryptString(plain: String, key: ByteArray): Pair<String, String> {
        val nonce = generateNonce()
        val ciphertext = encrypt(plain.toByteArray(Charsets.UTF_8), key, nonce)
        return ciphertext.toBase64() to nonce.toHex()
    }

    fun decryptString(ciphertextBase64: String, nonceHex: String, key: ByteArray): String {
        val ciphertext = ciphertextBase64.base64ToBytes()
        val nonce = nonceHex.hexToBytes()
        val plain = decrypt(ciphertext, key, nonce)
        return plain.toString(Charsets.UTF_8)
    }
}

// ============ 编码扩展 ============

private fun ByteArray.toHex(): String {
    val sb = StringBuilder(size * 2)
    forEach { sb.append(String.format("%02x", it)) }
    return sb.toString()
}

private fun String.hexToBytes(): ByteArray {
    require(length % 2 == 0) { "hex string must have even length" }
    return ByteArray(length / 2) { i ->
        ((this[i * 2].digitToInt(16) shl 4) or this[i * 2 + 1].digitToInt(16)).toByte()
    }
}

private fun ByteArray.toBase64(): String =
    android.util.Base64.encodeToString(this, android.util.Base64.NO_WRAP)

private fun String.base64ToBytes(): ByteArray =
    android.util.Base64.decode(this, android.util.Base64.NO_WRAP)
