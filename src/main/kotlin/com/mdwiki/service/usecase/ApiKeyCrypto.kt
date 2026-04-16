package com.mdwiki.service.usecase

import java.security.MessageDigest
import java.security.SecureRandom

object ApiKeyCrypto {
    fun hashKey(rawKey: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(rawKey.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    fun generateRawKey(secureRandom: SecureRandom): String {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        return "mdw_${bytes.joinToString("") { "%02x".format(it) }}"
    }
}
