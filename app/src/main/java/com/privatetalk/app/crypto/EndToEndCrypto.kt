package com.privatetalk.app.crypto

import android.content.Context
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.security.spec.ECGenParameterSpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

data class EncryptedText(
    val cipherText: String,
    val nonce: String
)

data class EncryptedBytes(
    val bytes: ByteArray,
    val nonce: String
)

class EndToEndCrypto(context: Context) {
    private val prefs = context.getSharedPreferences("privatetalk_e2e_identity", Context.MODE_PRIVATE)
    private val keyFactory = KeyFactory.getInstance("EC")
    private val random = SecureRandom()

    private val identityKeyPair: KeyPair by lazy {
        val storedPrivate = prefs.getString("private_key", null)
        val storedPublic = prefs.getString("public_key", null)
        if (storedPrivate != null && storedPublic != null) {
            KeyPair(
                keyFactory.generatePublic(X509EncodedKeySpec(storedPublic.base64Decode())),
                keyFactory.generatePrivate(PKCS8EncodedKeySpec(storedPrivate.base64Decode()))
            )
        } else {
            KeyPairGenerator.getInstance("EC").apply {
                initialize(ECGenParameterSpec("secp256r1"), random)
            }.generateKeyPair().also { pair ->
                prefs.edit()
                    .putString("private_key", pair.private.encoded.base64Encode())
                    .putString("public_key", pair.public.encoded.base64Encode())
                    .apply()
            }
        }
    }

    fun publicKeyBase64(): String = identityKeyPair.public.encoded.base64Encode()

    fun encrypt(chatId: String, peerPublicKeyBase64: String, plainText: String): EncryptedText {
        val nonce = ByteArray(12).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, sharedSecret(chatId, peerPublicKeyBase64), GCMParameterSpec(128, nonce))
        return EncryptedText(
            cipherText = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8)).base64Encode(),
            nonce = nonce.base64Encode()
        )
    }

    fun decrypt(chatId: String, peerPublicKeyBase64: String, cipherText: String, nonce: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            sharedSecret(chatId, peerPublicKeyBase64),
            GCMParameterSpec(128, nonce.base64Decode())
        )
        return cipher.doFinal(cipherText.base64Decode()).toString(Charsets.UTF_8)
    }

    fun encryptBytes(chatId: String, peerPublicKeyBase64: String, plainBytes: ByteArray): EncryptedBytes {
        val nonce = ByteArray(12).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, sharedSecret(chatId, peerPublicKeyBase64), GCMParameterSpec(128, nonce))
        return EncryptedBytes(
            bytes = cipher.doFinal(plainBytes),
            nonce = nonce.base64Encode()
        )
    }

    fun decryptBytes(chatId: String, peerPublicKeyBase64: String, encryptedBytes: ByteArray, nonce: String): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            sharedSecret(chatId, peerPublicKeyBase64),
            GCMParameterSpec(128, nonce.base64Decode())
        )
        return cipher.doFinal(encryptedBytes)
    }

    private fun sharedSecret(chatId: String, peerPublicKeyBase64: String): SecretKeySpec {
        val agreement = KeyAgreement.getInstance("ECDH")
        agreement.init(identityKeyPair.private)
        agreement.doPhase(peerPublicKeyBase64.toPublicKey(), true)
        val rawSecret = agreement.generateSecret()
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(rawSecret + chatId.toByteArray(Charsets.UTF_8))
        return SecretKeySpec(digest, "AES")
    }

    private fun String.toPublicKey(): PublicKey {
        return keyFactory.generatePublic(X509EncodedKeySpec(base64Decode()))
    }
}

private fun ByteArray.base64Encode(): String = Base64.getEncoder().encodeToString(this)

private fun String.base64Decode(): ByteArray = Base64.getDecoder().decode(this)
