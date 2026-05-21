package com.privatetalk.app.backend

data class InviteResult(
    val allowed: Boolean,
    val inviteId: String? = null,
    val reason: String? = null
)

data class PrivateUser(
    val id: String,
    val displayName: String,
    val photoUrl: String?,
    val publicIdentityKey: String,
    val lastSeenAtMillis: Long?
)

data class EncryptedMessagePayload(
    val chatId: String,
    val senderId: String,
    val cipherText: String,
    val nonce: String,
    val sentAtMillis: Long,
    val mediaUrl: String? = null,
    val mediaKind: MediaKind? = null
)

enum class MediaKind {
    Image,
    VoiceNote
}

data class CallSignal(
    val callId: String,
    val fromUserId: String,
    val toUserId: String,
    val type: CallType,
    val sdpOrCandidateJson: String,
    val createdAtMillis: Long
)

enum class CallType {
    AudioOffer,
    VideoOffer,
    Answer,
    IceCandidate,
    Ended
}

interface PrivateTalkBackend {
    suspend fun validateInvite(code: String): InviteResult
    suspend fun createUser(displayName: String, inviteId: String, publicIdentityKey: String): PrivateUser
    suspend fun sendEncryptedMessage(payload: EncryptedMessagePayload)
    suspend fun observeEncryptedMessages(chatId: String, onMessage: (EncryptedMessagePayload) -> Unit)
    suspend fun publishCallSignal(signal: CallSignal)
}
