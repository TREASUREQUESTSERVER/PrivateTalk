package com.privatetalk.app.backend

import android.net.Uri
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.auth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore
import com.google.firebase.messaging.messaging
import com.google.firebase.storage.storage
import kotlinx.coroutines.tasks.await
import java.security.MessageDigest
import java.util.Locale

data class CloudChatMessage(
    val id: String,
    val chatId: String,
    val senderId: String,
    val senderName: String,
    val text: String,
    val kind: String,
    val mediaUrl: String?,
    val mediaInlineBase64: String?,
    val sentAtMillis: Long,
    val encrypted: Boolean,
    val cipherText: String?,
    val nonce: String?,
    val senderPublicKey: String?,
    val recipientPublicKey: String?
)

data class CloudUser(
    val userId: String,
    val displayName: String,
    val email: String,
    val username: String?
)

data class CloudChatPreview(
    val id: String,
    val title: String,
    val lastMessage: String,
    val updatedAtMillis: Long
)

data class AndroidUpdateInfo(
    val versionCode: Long,
    val versionName: String,
    val apkUrl: String,
    val notes: String
)

data class AppAnnouncement(
    val title: String,
    val body: String,
    val active: Boolean
)

class FirebasePrivateTalkBackend : PrivateTalkBackend {
    private val auth = Firebase.auth
    private val firestore = Firebase.firestore
    private val storage = Firebase.storage

    suspend fun restoreCurrentSession(): LocalSession? {
        val firebaseUser = auth.currentUser ?: return null
        val userDoc = firestore.collection("users").document(firebaseUser.uid).get().await()
        val email = userDoc.getString("email") ?: firebaseUser.email.orEmpty()
        val displayName = userDoc.getString("displayName")
            ?: firebaseUser.displayName
            ?: email.substringBefore("@").ifBlank { "Member" }
        val identityKey = userDoc.getString("publicIdentityKey")
            ?: "GO-${email.take(4).uppercase(Locale.US)}-${firebaseUser.uid.take(6).uppercase(Locale.US)}"
        val provider = userDoc.getString("authProvider")
            ?: firebaseUser.providerData.firstOrNull { it.providerId != "firebase" }?.providerId
            ?: "email"
        val invitePrefix = if (provider == "google.com") "google" else "email"
        return LocalSession(
            userId = firebaseUser.uid,
            displayName = displayName,
            inviteId = "$invitePrefix:$email",
            identityKeyPreview = identityKey
        )
    }

    suspend fun getCurrentUsername(userId: String): String {
        return firestore.collection("users").document(userId).get().await().getString("username").orEmpty()
    }

    suspend fun getCurrentProfilePhoto(userId: String): String {
        val doc = firestore.collection("users").document(userId).get().await()
        val inline = doc.getString("photoInlineBase64").orEmpty()
        return doc.getString("photoUrl").orEmpty()
            .ifBlank {
                if (inline.isNotBlank()) "data:image/jpeg;base64,$inline" else ""
            }
    }

    suspend fun getUserDisplayName(userId: String): String {
        return firestore.collection("users")
            .document(userId)
            .get()
            .await()
            .getString("displayName")
            .orEmpty()
    }

    suspend fun updateDisplayName(userId: String, displayName: String) {
        firestore.collection("users").document(userId).set(
            mapOf(
                "displayName" to displayName,
                "updatedAt" to FieldValue.serverTimestamp()
            ),
            SetOptions.merge()
        ).await()
    }

    suspend fun uploadProfilePhoto(userId: String, localUri: Uri): String {
        val ref = storage.reference.child("profile-photos/$userId/${System.currentTimeMillis()}.jpg")
        ref.putFile(localUri).await()
        val photoUrl = ref.downloadUrl.await().toString()
        firestore.collection("users").document(userId).set(
            mapOf(
                "photoUrl" to photoUrl,
                "updatedAt" to FieldValue.serverTimestamp()
            ),
            SetOptions.merge()
        ).await()
        return photoUrl
    }

    suspend fun updateProfilePhotoInline(userId: String, photoInlineBase64: String) {
        firestore.collection("users").document(userId).set(
            mapOf(
                "photoInlineBase64" to photoInlineBase64,
                "photoUrl" to null,
                "updatedAt" to FieldValue.serverTimestamp()
            ),
            SetOptions.merge()
        ).await()
    }

    suspend fun publishE2EPublicKey(userId: String, publicKey: String) {
        firestore.collection("users").document(userId).set(
            mapOf(
                "publicE2EKey" to publicKey,
                "publicE2EKeyUpdatedAt" to FieldValue.serverTimestamp()
            ),
            SetOptions.merge()
        ).await()
    }

    suspend fun publishMessagingToken(userId: String) {
        val token = Firebase.messaging.token.await()
        firestore.collection("users").document(userId).set(
            mapOf("fcmToken" to token),
            SetOptions.merge()
        ).await()
    }

    suspend fun getAndroidUpdateInfo(): AndroidUpdateInfo? {
        val doc = firestore.collection("adminConfig").document("androidUpdate").get().await()
        if (!doc.exists()) return null
        return AndroidUpdateInfo(
            versionCode = doc.getLong("versionCode") ?: 0L,
            versionName = doc.getString("versionName").orEmpty(),
            apkUrl = doc.getString("apkUrl").orEmpty(),
            notes = doc.getString("notes").orEmpty()
        )
    }

    suspend fun getAnnouncement(): AppAnnouncement? {
        val doc = firestore.collection("adminConfig").document("announcement").get().await()
        if (!doc.exists()) return null
        return AppAnnouncement(
            title = doc.getString("title").orEmpty(),
            body = doc.getString("body").orEmpty(),
            active = doc.getBoolean("active") == true
        )
    }

    suspend fun unlockWithGoogleIdToken(idToken: String): LocalSession {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        val authResult = auth.signInWithCredential(credential).await()
        val firebaseUser = requireNotNull(authResult.user) { "Google sign-in failed." }
        val email = firebaseUser.email?.trim()?.lowercase(Locale.US)
            ?: run {
                auth.signOut()
                throw IllegalArgumentException("Google account email is missing.")
            }

        val inviteRef = firestore.collection("inviteEmails").document(email)
        val inviteDoc = inviteRef.get().await()
        if (!inviteDoc.exists() || inviteDoc.getBoolean("active") == false) {
            auth.signOut()
            throw IllegalArgumentException("This Google account is not invited.")
        }

        val displayName = firebaseUser.displayName?.trim()?.ifBlank { null }
            ?: email.substringBefore("@")
        val identityPreview = "GO-${email.take(4).uppercase(Locale.US)}-${firebaseUser.uid.take(6).uppercase(Locale.US)}"

        val userData = mapOf(
            "displayName" to displayName,
            "email" to email,
            "photoUrl" to firebaseUser.photoUrl?.toString(),
            "publicIdentityKey" to identityPreview,
            "inviteEmail" to email,
            "authProvider" to "google.com",
            "lastSeenAt" to FieldValue.serverTimestamp()
        )

        firestore.runBatch { batch ->
            batch.set(firestore.collection("users").document(firebaseUser.uid), userData, SetOptions.merge())
            batch.set(
                inviteRef,
                mapOf(
                    "usedBy" to firebaseUser.uid,
                    "usedAt" to FieldValue.serverTimestamp(),
                    "lastLoginAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            )
        }.await()

        return LocalSession(
            userId = firebaseUser.uid,
            displayName = displayName,
            inviteId = "google:$email",
            identityKeyPreview = identityPreview
        )
    }

    suspend fun claimUsername(userId: String, displayName: String, email: String?, username: String): String {
        val normalized = username.normalizeUsername()
        require(normalized.length >= 3) { "Username must be at least 3 characters." }

        val usernameRef = firestore.collection("usernames").document(normalized)
        val userRef = firestore.collection("users").document(userId)

        firestore.runTransaction { tx ->
            val existing = tx.get(usernameRef)
            val existingUid = existing.getString("userId")
            if (existing.exists() && existingUid != userId) {
                throw IllegalArgumentException("Username is already taken.")
            }
            tx.set(
                usernameRef,
                mapOf(
                    "userId" to userId,
                    "displayName" to displayName,
                    "email" to email,
                    "username" to normalized,
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            )
            tx.set(
                userRef,
                mapOf(
                    "username" to normalized,
                    "displayName" to displayName,
                    "email" to email,
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            )
        }.await()

        return normalized
    }

    suspend fun findUserByUsername(username: String): CloudUser? {
        val normalized = username.normalizeUsername()
        val usernameDoc = firestore.collection("usernames").document(normalized).get().await()
        val userId = usernameDoc.getString("userId") ?: return null
        return CloudUser(
            userId = userId,
            displayName = usernameDoc.getString("displayName").orEmpty().ifBlank { normalized },
            email = usernameDoc.getString("email").orEmpty(),
            username = normalized
        )
    }

    suspend fun createOrOpenDirectChat(currentUser: CloudUser, otherUser: CloudUser): String {
        val chatId = directChatId(currentUser.userId, otherUser.userId)
        firestore.collection("chats").document(chatId).set(
            mapOf(
                "type" to "direct",
                "memberIds" to listOf(currentUser.userId, otherUser.userId).sorted(),
                "memberNames" to mapOf(
                    currentUser.userId to currentUser.displayName,
                    otherUser.userId to otherUser.displayName
                ),
                "updatedAtMillis" to System.currentTimeMillis(),
                "lastMessageAt" to FieldValue.serverTimestamp()
            ),
            SetOptions.merge()
        ).await()
        return chatId
    }

    suspend fun getDirectChatPeerPublicKey(chatId: String, currentUserId: String): String? {
        val peerUserId = getDirectChatPeerUserId(chatId, currentUserId) ?: return null
        return firestore.collection("users").document(peerUserId).get().await().getString("publicE2EKey")
    }

    suspend fun getDirectChatPeerUserId(chatId: String, currentUserId: String): String? {
        val chatDoc = firestore.collection("chats").document(chatId).get().await()
        val memberIds = chatDoc.get("memberIds") as? List<*> ?: return null
        return memberIds.firstOrNull { it is String && it != currentUserId } as? String
    }

    suspend fun unlockWithInvitedEmail(email: String, password: String, displayName: String): LocalSession {
        val normalizedEmail = email.trim().lowercase(Locale.US)
        val cleanName = displayName.trim()
        require(normalizedEmail.contains("@")) { "Enter a valid email." }
        require(password.length >= 6) { "Password must be at least 6 characters." }
        require(cleanName.length >= 2) { "Enter your name first." }

        val authResult = runCatching {
            auth.signInWithEmailAndPassword(normalizedEmail, password).await()
        }.recoverCatching { signInError ->
            val message = signInError.message.orEmpty()
            if (
                signInError is FirebaseAuthInvalidUserException ||
                message.contains("no user record", ignoreCase = true) ||
                message.contains("user-not-found", ignoreCase = true)
            ) {
                auth.createUserWithEmailAndPassword(normalizedEmail, password).await()
            } else {
                throw signInError
            }
        }.getOrThrow()

        val uid = requireNotNull(authResult.user?.uid) { "Firebase user id missing." }
        val inviteRef = firestore.collection("inviteEmails").document(normalizedEmail)
        val inviteDoc = inviteRef.get().await()
        if (!inviteDoc.exists()) {
            auth.signOut()
            throw IllegalArgumentException("This email is not invited.")
        }
        val active = inviteDoc.getBoolean("active") ?: true
        if (!active) {
            auth.signOut()
            throw IllegalArgumentException("This invite email is disabled.")
        }

        val identityPreview = "EM-${normalizedEmail.take(4).uppercase(Locale.US)}-${uid.take(6).uppercase(Locale.US)}"

        val userData = mapOf(
            "displayName" to cleanName,
            "email" to normalizedEmail,
            "photoUrl" to null,
            "publicIdentityKey" to identityPreview,
            "inviteEmail" to normalizedEmail,
            "authProvider" to "password",
            "createdAt" to FieldValue.serverTimestamp(),
            "lastSeenAt" to FieldValue.serverTimestamp()
        )

        firestore.runBatch { batch ->
            batch.set(firestore.collection("users").document(uid), userData, SetOptions.merge())
            batch.set(
                inviteRef,
                mapOf(
                    "usedBy" to uid,
                    "usedAt" to FieldValue.serverTimestamp(),
                    "lastLoginAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            )
        }.await()

        return LocalSession(
            userId = uid,
            displayName = cleanName,
            inviteId = "email:$normalizedEmail",
            identityKeyPreview = identityPreview
        )
    }

    suspend fun unlockWithInvite(code: String, displayName: String): LocalSession {
        val cleanName = displayName.trim()
        require(cleanName.length >= 2) { "Enter your name first." }

        if (auth.currentUser == null) {
            auth.signInAnonymously().await()
        }

        val invite = validateInvite(code)
        if (!invite.allowed || invite.inviteId == null) {
            throw IllegalArgumentException(invite.reason ?: "Invite code is invalid.")
        }

        val normalizedCode = code.trim().uppercase(Locale.US)
        val identityPreview = "FB-${normalizedCode.take(4)}-${cleanName.hashCode().toUInt().toString(16).take(6).uppercase(Locale.US)}"
        val user = createUser(cleanName, invite.inviteId, identityPreview)

        return LocalSession(
            userId = user.id,
            displayName = user.displayName,
            inviteId = invite.inviteId,
            identityKeyPreview = identityPreview
        )
    }

    override suspend fun validateInvite(code: String): InviteResult {
        val codeHash = code.sha256()
        val invite = firestore.collection("invites")
            .whereEqualTo("codeHash", codeHash)
            .whereEqualTo("usedBy", null)
            .limit(1)
            .get()
            .await()
            .documents
            .firstOrNull()
            ?: return InviteResult(allowed = false, reason = "Invite code is invalid or already used.")

        return InviteResult(allowed = true, inviteId = invite.id)
    }

    override suspend fun createUser(displayName: String, inviteId: String, publicIdentityKey: String): PrivateUser {
        val firebaseUser = auth.currentUser
            ?: throw IllegalStateException("Firebase user must be signed in before profile creation.")

        val user = PrivateUser(
            id = firebaseUser.uid,
            displayName = displayName.trim(),
            photoUrl = null,
            publicIdentityKey = publicIdentityKey,
            lastSeenAtMillis = System.currentTimeMillis()
        )

        val userData = mapOf(
            "displayName" to user.displayName,
            "photoUrl" to user.photoUrl,
            "publicIdentityKey" to user.publicIdentityKey,
            "inviteId" to inviteId,
            "createdAt" to FieldValue.serverTimestamp(),
            "lastSeenAt" to FieldValue.serverTimestamp()
        )

        firestore.runBatch { batch ->
            batch.set(firestore.collection("users").document(firebaseUser.uid), userData)
            batch.update(
                firestore.collection("invites").document(inviteId),
                mapOf(
                    "usedBy" to firebaseUser.uid,
                    "usedAt" to FieldValue.serverTimestamp()
                )
            )
        }.await()

        return user
    }

    override suspend fun sendEncryptedMessage(payload: EncryptedMessagePayload) {
        val message = mapOf(
            "senderId" to payload.senderId,
            "cipherText" to payload.cipherText,
            "nonce" to payload.nonce,
            "mediaUrl" to payload.mediaUrl,
            "mediaKind" to payload.mediaKind?.name?.lowercase(Locale.US),
            "sentAtMillis" to payload.sentAtMillis,
            "sentAt" to FieldValue.serverTimestamp()
        )

        val chatRef = firestore.collection("chats").document(payload.chatId)
        chatRef.collection("messages").add(message).await()
        chatRef.update(
            mapOf(
                "lastMessageAt" to FieldValue.serverTimestamp(),
                "lastMessagePreview" to genericPreview(payload)
            )
        ).await()
    }

    override suspend fun observeEncryptedMessages(
        chatId: String,
        onMessage: (EncryptedMessagePayload) -> Unit
    ) {
        firestore.collection("chats")
            .document(chatId)
            .collection("messages")
            .orderBy("sentAtMillis", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, _ ->
                snapshot?.documentChanges?.forEach { change ->
                    val data = change.document.data
                    onMessage(
                        EncryptedMessagePayload(
                            chatId = chatId,
                            senderId = data["senderId"] as? String ?: "",
                            cipherText = data["cipherText"] as? String ?: "",
                            nonce = data["nonce"] as? String ?: "",
                            sentAtMillis = data["sentAtMillis"] as? Long ?: 0L,
                            mediaUrl = data["mediaUrl"] as? String,
                            mediaKind = (data["mediaKind"] as? String)?.toMediaKind()
                        )
                    )
                }
            }
    }

    override suspend fun publishCallSignal(signal: CallSignal) {
        firestore.collection("calls")
            .document(signal.callId)
            .collection("signals")
            .add(
                mapOf(
                    "callId" to signal.callId,
                    "fromUserId" to signal.fromUserId,
                    "toUserId" to signal.toUserId,
                    "type" to signal.type.name,
                    "sdpOrCandidateJson" to signal.sdpOrCandidateJson,
                    "createdAtMillis" to signal.createdAtMillis,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )
            .await()
    }

    suspend fun publishIncomingCallRing(signal: CallSignal) {
        firestore.collection("callInbox")
            .document(signal.toUserId)
            .collection("rings")
            .document(signal.callId)
            .set(
                mapOf(
                    "callId" to signal.callId,
                    "fromUserId" to signal.fromUserId,
                    "toUserId" to signal.toUserId,
                    "type" to signal.type.name,
                    "createdAtMillis" to signal.createdAtMillis,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "status" to "ringing"
                ),
                SetOptions.merge()
            )
            .await()
    }

    fun listenToIncomingCallSignals(
        userId: String,
        onSignal: (CallSignal) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration {
        return firestore.collection("callInbox")
            .document(userId)
            .collection("rings")
            .whereEqualTo("status", "ringing")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onError(error)
                    return@addSnapshotListener
                }
                snapshot?.documentChanges
                    ?.filter { it.type == com.google.firebase.firestore.DocumentChange.Type.ADDED }
                    ?.forEach { change ->
                        val doc = change.document
                        val type = runCatching { CallType.valueOf(doc.getString("type").orEmpty()) }
                            .getOrDefault(CallType.AudioOffer)
                        if (type != CallType.AudioOffer && type != CallType.VideoOffer) {
                            return@forEach
                        }
                        onSignal(
                            CallSignal(
                                callId = doc.getString("callId")
                                    ?: doc.reference.parent.parent?.id.orEmpty(),
                                fromUserId = doc.getString("fromUserId").orEmpty(),
                                toUserId = doc.getString("toUserId").orEmpty(),
                                type = type,
                                sdpOrCandidateJson = doc.getString("sdpOrCandidateJson").orEmpty(),
                                createdAtMillis = doc.getLong("createdAtMillis") ?: 0L
                            )
                        )
                    }
            }
    }

    suspend fun markIncomingCallRingHandled(userId: String, callId: String, status: String) {
        firestore.collection("callInbox")
            .document(userId)
            .collection("rings")
            .document(callId)
            .set(
                mapOf(
                    "status" to status,
                    "handledAtMillis" to System.currentTimeMillis(),
                    "handledAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            )
            .await()
    }

    fun listenToCallSignals(
        callId: String,
        currentUserId: String,
        onSignal: (CallSignal) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration {
        return firestore.collection("calls")
            .document(callId)
            .collection("signals")
            .orderBy("createdAtMillis", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onError(error)
                    return@addSnapshotListener
                }
                snapshot?.documentChanges
                    ?.filter { it.type == com.google.firebase.firestore.DocumentChange.Type.ADDED }
                    ?.forEach { change ->
                        val doc = change.document
                        val fromUserId = doc.getString("fromUserId").orEmpty()
                        if (fromUserId == currentUserId) return@forEach
                        val type = runCatching { CallType.valueOf(doc.getString("type").orEmpty()) }
                            .getOrDefault(CallType.IceCandidate)
                        onSignal(
                            CallSignal(
                                callId = doc.getString("callId")
                                    ?: doc.reference.parent.parent?.id.orEmpty(),
                                fromUserId = fromUserId,
                                toUserId = doc.getString("toUserId").orEmpty(),
                                type = type,
                                sdpOrCandidateJson = doc.getString("sdpOrCandidateJson").orEmpty(),
                                createdAtMillis = doc.getLong("createdAtMillis") ?: 0L
                            )
                        )
                    }
            }
    }

    suspend fun uploadMedia(localUri: Uri, remotePath: String): String {
        val ref = storage.reference.child(remotePath)
        ref.putFile(localUri).await()
        return ref.downloadUrl.await().toString()
    }

    suspend fun uploadEncryptedMedia(bytes: ByteArray, remotePath: String): String {
        val ref = storage.reference.child(remotePath)
        ref.putBytes(bytes).await()
        return ref.downloadUrl.await().toString()
    }

    suspend fun downloadMediaBytes(mediaUrl: String): ByteArray {
        return storage.getReferenceFromUrl(mediaUrl).getBytes(25L * 1024L * 1024L).await()
    }

    suspend fun deleteMessageForEveryone(chatId: String, messageId: String) {
        firestore.collection("chats")
            .document(chatId)
            .collection("messages")
            .document(messageId)
            .delete()
            .await()
    }

    suspend fun updateTypingStatus(chatId: String, userId: String, isTyping: Boolean) {
        firestore.collection("chats")
            .document(chatId)
            .collection("typing")
            .document(userId)
            .set(
                mapOf(
                    "userId" to userId,
                    "isTyping" to isTyping,
                    "updatedAtMillis" to System.currentTimeMillis(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            )
            .await()
    }

    fun listenToTypingStatus(
        chatId: String,
        currentUserId: String,
        onTypingChanged: (Boolean) -> Unit,
        onError: (Throwable) -> Unit
    ): ListenerRegistration {
        return firestore.collection("chats")
            .document(chatId)
            .collection("typing")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onError(error)
                    return@addSnapshotListener
                }
                val now = System.currentTimeMillis()
                val someoneElseTyping = snapshot?.documents.orEmpty().any { doc ->
                    val typingUserId = doc.getString("userId").orEmpty().ifBlank { doc.id }
                    typingUserId != currentUserId &&
                        doc.getBoolean("isTyping") == true &&
                        now - (doc.getLong("updatedAtMillis") ?: 0L) < 8_000L
                }
                onTypingChanged(someoneElseTyping)
            }
    }

    suspend fun markChatRead(chatId: String, userId: String) {
        firestore.collection("chats")
            .document(chatId)
            .collection("readReceipts")
            .document(userId)
            .set(
                mapOf(
                    "userId" to userId,
                    "lastReadAtMillis" to System.currentTimeMillis(),
                    "lastReadAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            )
            .await()
    }

    suspend fun sendChatMessage(
        chatId: String,
        senderId: String,
        senderName: String,
        text: String,
        kind: String = "text",
        mediaUrl: String? = null
    ) {
        val now = System.currentTimeMillis()
        val chatRef = firestore.collection("chats").document(chatId)
        chatRef.set(
            mapOf(
                "lastMessageAt" to FieldValue.serverTimestamp(),
                "lastMessagePreview" to when (kind) {
                    "voice" -> "Voice note"
                    "image" -> "Photo"
                    else -> text.take(80)
                },
                "updatedAtMillis" to now
            ),
            SetOptions.merge()
        ).await()
        chatRef.collection("messages").add(
            mapOf(
                "senderId" to senderId,
                "senderName" to senderName,
                "text" to text,
                "kind" to kind,
                "mediaUrl" to mediaUrl,
                "sentAtMillis" to now,
                "sentAt" to FieldValue.serverTimestamp()
            )
        ).await()
    }

    suspend fun sendEncryptedChatMessage(
        chatId: String,
        senderId: String,
        senderName: String,
        senderPublicKey: String,
        recipientPublicKey: String,
        cipherText: String,
        nonce: String,
        kind: String = "text",
        mediaUrl: String? = null,
        mediaInlineBase64: String? = null
    ) {
        val now = System.currentTimeMillis()
        val chatRef = firestore.collection("chats").document(chatId)
        chatRef.set(
            mapOf(
                "lastMessageAt" to FieldValue.serverTimestamp(),
                "lastMessagePreview" to when (kind) {
                    "voice" -> "Encrypted voice note"
                    "image" -> "Encrypted photo"
                    else -> "Encrypted message"
                },
                "updatedAtMillis" to now
            ),
            SetOptions.merge()
        ).await()
        chatRef.collection("messages").add(
            mapOf(
                "senderId" to senderId,
                "senderName" to senderName,
                "text" to "",
                "kind" to kind,
                "mediaUrl" to mediaUrl,
                "mediaInlineBase64" to mediaInlineBase64,
                "encrypted" to true,
                "cipherText" to cipherText,
                "nonce" to nonce,
                "senderPublicKey" to senderPublicKey,
                "recipientPublicKey" to recipientPublicKey,
                "sentAtMillis" to now,
                "sentAt" to FieldValue.serverTimestamp()
            )
        ).await()
    }

    fun listenToChatMessages(
        chatId: String,
        onMessages: (List<CloudChatMessage>) -> Unit,
        onError: (Throwable) -> Unit
    ): ListenerRegistration {
        return firestore.collection("chats")
            .document(chatId)
            .collection("messages")
            .orderBy("sentAtMillis", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onError(error)
                    return@addSnapshotListener
                }
                if (snapshot?.metadata?.hasPendingWrites() == true) {
                    return@addSnapshotListener
                }
                val messages = snapshot?.documents.orEmpty().map { doc ->
                    CloudChatMessage(
                        id = doc.id,
                        chatId = chatId,
                        senderId = doc.getString("senderId").orEmpty(),
                        senderName = doc.getString("senderName").orEmpty(),
                        text = doc.getString("text").orEmpty(),
                        kind = doc.getString("kind") ?: "text",
                        mediaUrl = doc.getString("mediaUrl"),
                        mediaInlineBase64 = doc.getString("mediaInlineBase64"),
                        sentAtMillis = doc.getLong("sentAtMillis") ?: 0L,
                        encrypted = doc.getBoolean("encrypted") == true,
                        cipherText = doc.getString("cipherText"),
                        nonce = doc.getString("nonce"),
                        senderPublicKey = doc.getString("senderPublicKey"),
                        recipientPublicKey = doc.getString("recipientPublicKey")
                    )
                }
                onMessages(messages)
            }
    }

    fun listenToUserChats(
        userId: String,
        onChats: (List<CloudChatPreview>) -> Unit,
        onError: (Throwable) -> Unit
    ): ListenerRegistration {
        return firestore.collection("chats")
            .whereArrayContains("memberIds", userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onError(error)
                    return@addSnapshotListener
                }
                val chats = snapshot?.documents.orEmpty().map { doc ->
                    val memberNames = doc.get("memberNames") as? Map<*, *> ?: emptyMap<Any, Any>()
                    val title = memberNames
                        .filterKeys { it != userId }
                        .values
                        .firstOrNull()
                        ?.toString()
                        .orEmpty()
                        .ifBlank { "Private chat" }
                    CloudChatPreview(
                        id = doc.id,
                        title = title,
                        lastMessage = doc.getString("lastMessagePreview") ?: "Start secure chat",
                        updatedAtMillis = doc.getLong("updatedAtMillis") ?: 0L
                    )
                }.sortedByDescending { it.updatedAtMillis }
                onChats(chats)
            }
    }

    private fun genericPreview(payload: EncryptedMessagePayload): String = when (payload.mediaKind) {
        MediaKind.Image -> "Photo"
        MediaKind.VoiceNote -> "Voice note"
        null -> "New message"
    }
}

private fun String.sha256(): String {
    val bytes = MessageDigest.getInstance("SHA-256").digest(trim().uppercase(Locale.US).toByteArray())
    return bytes.joinToString("") { "%02x".format(it) }
}

private fun String.toMediaKind(): MediaKind? = when (lowercase(Locale.US)) {
    "image" -> MediaKind.Image
    "voicenote" -> MediaKind.VoiceNote
    else -> null
}

fun String.normalizeUsername(): String {
    return trim()
        .lowercase(Locale.US)
        .replace(Regex("[^a-z0-9_.]"), "")
}

fun directChatId(firstUserId: String, secondUserId: String): String {
    return listOf(firstUserId, secondUserId).sorted().joinToString(separator = "_", prefix = "direct_")
}
