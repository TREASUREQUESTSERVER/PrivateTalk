package com.privatetalk.app.backend

import java.util.Locale

data class LocalSession(
    val userId: String,
    val displayName: String,
    val inviteId: String,
    val identityKeyPreview: String
)

object LocalPrivateTalkBackend {
    private val validInviteCodes = mapOf(
        "DEMO2026" to "invite-demo-2026",
        "PRIVATE2026" to "invite-private-2026",
        "FOUNDERS" to "invite-founders"
    )

    fun unlockWithInvite(code: String, displayName: String): Result<LocalSession> {
        val normalizedCode = code.trim().uppercase(Locale.US)
        val cleanName = displayName.trim()

        if (cleanName.length < 2) {
            return Result.failure(IllegalArgumentException("Enter your name first."))
        }

        val inviteId = validInviteCodes[normalizedCode]
            ?: return Result.failure(IllegalArgumentException("Invite code is not valid."))

        val userSlug = cleanName.lowercase(Locale.US)
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .ifBlank { "member" }

        return Result.success(
            LocalSession(
                userId = "local-$userSlug",
                displayName = cleanName,
                inviteId = inviteId,
                identityKeyPreview = "PT-${normalizedCode.take(4)}-${cleanName.hashCode().toUInt().toString(16).take(6).uppercase(Locale.US)}"
            )
        )
    }

    fun demoInviteCode(): String = "DEMO2026"
}
