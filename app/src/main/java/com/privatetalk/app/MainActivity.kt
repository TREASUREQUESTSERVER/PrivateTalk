package com.privatetalk.app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.app.NotificationCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.privatetalk.app.backend.FirebasePrivateTalkBackend
import com.privatetalk.app.backend.LocalSession
import com.privatetalk.app.backend.CloudChatMessage
import com.privatetalk.app.backend.CloudChatPreview
import com.privatetalk.app.backend.CloudUser
import com.privatetalk.app.backend.CallSignal
import com.privatetalk.app.backend.CallType
import com.privatetalk.app.crypto.EndToEndCrypto
import com.privatetalk.app.webrtc.PrivateTalkWebRtcSession
import com.onesignal.OneSignal
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AddIcCall
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VideoCall
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.gestures.detectTapGestures
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Base64

private fun Context.syncOneSignalUser(session: AppSession) {
    if (!PrivateTalkApplication.isConfigured(this)) return
    OneSignal.login(session.userId)
    if (session.email.isNotBlank()) {
        OneSignal.User.addEmail(session.email)
        OneSignal.User.addTag("email", session.email)
    }
    OneSignal.User.addTag("display_name", session.displayName)
}

private val DeepGreen = Color(0xFF075E54)
private val FreshGreen = Color(0xFF25D366)
private val WarmPaper = Color(0xFFECE5DD)
private val Ink = Color(0xFF17211F)
private val Muted = Color(0xFF63716D)
private val ChatDark = Color(0xFF0B141A)
private val ChatHeader = Color(0xFF111B21)
private val IncomingBubble = Color(0xFF202C33)
private val OutgoingBubble = Color(0xFF005C4B)
private val ChatMuted = Color(0xFF8696A0)
private const val FREE_PLAN_MAX_ENCRYPTED_MEDIA_BYTES = 620_000
private const val FREE_PLAN_MAX_PROFILE_PHOTO_BYTES = 520_000

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PrivateTalkTheme {
                PrivateTalkApp()
            }
        }
    }
}

private data class MediaTarget(val file: File, val uri: Uri)

private fun createMediaTarget(context: Context, prefix: String, extension: String): MediaTarget {
    val directory = File(context.getExternalFilesDir(null), "chat-media").apply { mkdirs() }
    val file = File(directory, "$prefix-${System.currentTimeMillis()}.$extension")
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    return MediaTarget(file = file, uri = uri)
}

private class VoiceNoteRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var outputUri: Uri? = null

    fun start(): Uri {
        val target = createMediaTarget(context, "voice", "m4a")
        recorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioEncodingBitRate(32_000)
            setAudioSamplingRate(22_050)
            setOutputFile(target.file.absolutePath)
            prepare()
            start()
        }
        outputUri = target.uri
        return target.uri
    }

    fun stop(): Uri? {
        val uri = outputUri
        recorder?.runCatching {
            stop()
            release()
        }
        recorder = null
        outputUri = null
        return uri
    }
}

@Composable
private fun PrivateTalkTheme(content: @Composable () -> Unit) {
    MaterialTheme {
        Surface(color = WarmPaper, content = content)
    }
}

private enum class AppStep {
    Invite,
    Home,
    ChatRoom,
    IncomingCall,
    CallRoom
}

private fun String.toAppStep(): AppStep {
    return AppStep.entries.firstOrNull { it.name == this } ?: AppStep.Invite
}

private data class AppSession(
    val userId: String,
    val displayName: String,
    val inviteId: String,
    val identityKeyPreview: String,
    val email: String = ""
) {
    companion object {
        fun fromLocal(session: LocalSession) = AppSession(
            userId = session.userId,
            displayName = session.displayName,
            inviteId = session.inviteId,
            identityKeyPreview = session.identityKeyPreview,
            email = session.inviteId.substringAfter("google:", "").substringAfter("email:", "")
        )
    }
}

private enum class HomeTab(val label: String, val icon: ImageVector) {
    Chats("Chats", Icons.Default.Chat),
    Status("Status", Icons.Default.PhotoCamera),
    Calls("Calls", Icons.Default.Call),
    Profile("Profile", Icons.Default.AccountCircle)
}

private data class ChatPreview(
    val id: String,
    val name: String,
    val lastMessage: String,
    val time: String,
    val unread: Int,
    val isEncrypted: Boolean = true
)

private data class Message(
    val text: String,
    val mine: Boolean,
    val meta: String,
    val voice: Boolean = false,
    val image: Boolean = false,
    val mediaUri: String? = null,
    val mediaInlineBase64: String? = null,
    val remoteId: String? = null,
    val encrypted: Boolean = false,
    val nonce: String? = null,
    val senderPublicKey: String? = null,
    val recipientPublicKey: String? = null
)

private data class ImageViewerContent(
    val uri: String,
    val title: String,
    val meta: String
)

private data class ResolvedMedia(
    val uri: String?,
    val status: String? = null
)

private fun Long.toChatTime(): String {
    val formatter = java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault())
    return formatter.format(java.util.Date(this))
}

private fun CloudChatMessage.toUiMessage(currentUserId: String, crypto: EndToEndCrypto): Message {
    val encryptedCipherText = cipherText
    val encryptedNonce = nonce
    val plainText = if (kind == "text" && encrypted && encryptedCipherText != null && encryptedNonce != null) {
        val peerPublicKey = if (senderId == currentUserId) recipientPublicKey else senderPublicKey
        peerPublicKey?.let {
            runCatching { crypto.decrypt(chatId, it, encryptedCipherText, encryptedNonce) }.getOrNull()
        } ?: "Encrypted message"
    } else {
        text
    }
    return Message(
        text = plainText.ifBlank {
            when (kind) {
                "voice" -> "Voice note"
                "image" -> "Photo"
                else -> "Encrypted message"
            }
        },
        mine = senderId == currentUserId,
        meta = sentAtMillis.toChatTime(),
        voice = kind == "voice",
        image = kind == "image",
        mediaUri = mediaUrl,
        mediaInlineBase64 = mediaInlineBase64,
        remoteId = id,
        encrypted = encrypted,
        nonce = nonce,
        senderPublicKey = senderPublicKey,
        recipientPublicKey = recipientPublicKey
    )
}

private fun CloudChatPreview.toUiChat(): ChatPreview {
    return ChatPreview(
        id = id,
        name = title,
        lastMessage = lastMessage,
        time = if (updatedAtMillis > 0L) "Now" else "",
        unread = 0
    )
}

private fun Context.readUriBytes(uri: Uri): ByteArray {
    return requireNotNull(contentResolver.openInputStream(uri)) { "Could not open media file." }.use { it.readBytes() }
}

private fun Context.prepareMediaBytes(uri: Uri, kind: String): ByteArray {
    val raw = readUriBytes(uri)
    if (kind != "image") return raw
    val bitmap = BitmapFactory.decodeByteArray(raw, 0, raw.size) ?: return raw
    val maxSide = maxOf(bitmap.width, bitmap.height)
    val scaledBitmap = if (maxSide > 900) {
        val scale = 900f / maxSide.toFloat()
        android.graphics.Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).toInt().coerceAtLeast(1),
            (bitmap.height * scale).toInt().coerceAtLeast(1),
            true
        )
    } else {
        bitmap
    }
    var quality = 64
    var compressed: ByteArray
    do {
        compressed = ByteArrayOutputStream().use { output ->
            scaledBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, quality, output)
            output.toByteArray()
        }
        quality -= 8
    } while (compressed.size > FREE_PLAN_MAX_PROFILE_PHOTO_BYTES && quality >= 40)
    return compressed
}

private fun ByteArray.requireFitsFreePlan(kind: String) {
    require(size <= FREE_PLAN_MAX_ENCRYPTED_MEDIA_BYTES) {
        if (kind == "image") {
            "Photo is still too large for the free Firebase plan. Crop it or send a smaller photo."
        } else {
            "Voice note is too long for the free Firebase plan. Keep it under about 2 minutes."
        }
    }
}

private fun Context.writeDecryptedMedia(messageId: String, bytes: ByteArray, extension: String): Uri {
    val file = decryptedMediaFile(messageId, extension)
    file.writeBytes(bytes)
    return Uri.fromFile(file)
}

private fun Context.cachedDecryptedMedia(messageId: String, extension: String): Uri? {
    val file = decryptedMediaFile(messageId, extension)
    return if (file.exists() && file.length() > 0L) Uri.fromFile(file) else null
}

private fun Context.decryptedMediaFile(messageId: String, extension: String): File {
    val safeId = messageId.replace(Regex("[^a-zA-Z0-9_-]"), "_")
    return File(cacheDir, "decrypted-$safeId.$extension")
}

private fun Context.shareImage(uri: String) {
    val parsedUri = Uri.parse(uri)
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "image/*"
        putExtra(Intent.EXTRA_STREAM, parsedUri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    startActivity(Intent.createChooser(shareIntent, "Share photo"))
}

private fun Context.saveImageToGallery(uri: String): Boolean {
    val parsedUri = Uri.parse(uri)
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "PrivateTalk-${System.currentTimeMillis()}.jpg")
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/PrivateTalk")
        }
    }
    val savedUri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
    return runCatching {
        contentResolver.openInputStream(parsedUri).use { input ->
            contentResolver.openOutputStream(savedUri).use { output ->
                requireNotNull(input) { "Image file unavailable." }
                requireNotNull(output) { "Gallery write unavailable." }
                input.copyTo(output)
            }
        }
    }.isSuccess
}

private fun Context.profilePhotoDisplayUri(source: String, cacheKey: String): String {
    if (!source.startsWith("data:image")) return source
    val base64 = source.substringAfter("base64,", "")
    if (base64.isBlank()) return ""
    val safeKey = cacheKey.replace(Regex("[^a-zA-Z0-9_-]"), "_")
    val file = File(cacheDir, "profile-photo-$safeKey.jpg")
    if (!file.exists() || file.length() == 0L) {
        file.writeBytes(Base64.getDecoder().decode(base64))
    }
    return Uri.fromFile(file).toString()
}

private fun Context.showPrivateTalkNotification(title: String, body: String) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) {
        return
    }
    val channelId = "private_messages"
    val notificationManager = getSystemService(NotificationManager::class.java)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        notificationManager.createNotificationChannel(
            NotificationChannel(channelId, "PrivateTalk alerts", NotificationManager.IMPORTANCE_HIGH)
        )
    }
    val pendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    val notification = NotificationCompat.Builder(this, channelId)
        .setSmallIcon(android.R.drawable.stat_notify_chat)
        .setContentTitle(title)
        .setContentText(body)
        .setAutoCancel(true)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setContentIntent(pendingIntent)
        .build()
    notificationManager.notify((System.currentTimeMillis() % Int.MAX_VALUE).toInt(), notification)
}

@Composable
private fun PrivateTalkApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val crypto = remember { EndToEndCrypto(context) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    var stepName by rememberSaveable { mutableStateOf(AppStep.Invite.name) }
    val currentStep = stepName.toAppStep()
    var selectedTab by rememberSaveable { mutableStateOf(HomeTab.Chats) }
    var selectedChatId by rememberSaveable { mutableStateOf("welcome") }
    var selectedChatName by rememberSaveable { mutableStateOf("PrivateTalk") }
    var activeCallId by rememberSaveable { mutableStateOf("") }
    var activeCallKind by rememberSaveable { mutableStateOf("video") }
    var activeCallPeerName by rememberSaveable { mutableStateOf("PrivateTalk") }
    var activeCallPeerId by rememberSaveable { mutableStateOf("") }
    var activeCallIsCaller by rememberSaveable { mutableStateOf(false) }
    var profileName by rememberSaveable { mutableStateOf("Demo User") }
    var profilePhotoUrl by rememberSaveable { mutableStateOf("") }
    var username by rememberSaveable { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val firebaseBackend = remember { FirebasePrivateTalkBackend() }
    var session by remember {
        mutableStateOf<AppSession?>(null)
    }
    val chats = remember { mutableStateListOf<ChatPreview>() }
    val messagesByChat = remember {
        mutableStateMapOf<String, androidx.compose.runtime.snapshots.SnapshotStateList<Message>>()
    }
    val statuses = remember { mutableStateListOf<String>() }
    val calls = remember { mutableStateListOf<String>() }
    val loadedMessageChats = remember { mutableStateListOf<String>() }
    val notifiedCallIds = remember { mutableStateListOf<String>() }
    var syncNotice by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        if (session == null) {
            runCatching { firebaseBackend.restoreCurrentSession() }
                .onSuccess { restored ->
                    if (restored != null) {
                        session = AppSession.fromLocal(restored)
                        profileName = restored.displayName
                        username = runCatching { firebaseBackend.getCurrentUsername(restored.userId) }.getOrDefault("")
                        profilePhotoUrl = runCatching { firebaseBackend.getCurrentProfilePhoto(restored.userId) }.getOrDefault("")
                        if (currentStep == AppStep.Invite) {
                            stepName = AppStep.Home.name
                        }
                    }
                }
        }
    }

    LaunchedEffect(session?.userId) {
        val activeSession = session ?: return@LaunchedEffect
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        runCatching {
            firebaseBackend.publishE2EPublicKey(activeSession.userId, crypto.publicKeyBase64())
        }
        runCatching {
            firebaseBackend.publishMessagingToken(activeSession.userId)
        }
        runCatching {
            context.syncOneSignalUser(activeSession)
        }
    }

    DisposableEffect(session?.userId) {
        val activeSession = session
        if (activeSession == null) {
            onDispose { }
        } else {
            val chatRegistration = firebaseBackend.listenToUserChats(
                userId = activeSession.userId,
                onChats = { cloudChats ->
                    syncNotice = null
                    chats.clear()
                    chats.addAll(cloudChats.map { it.toUiChat() })
                },
                onError = { syncNotice = "Firebase connection problem: ${it.message ?: "chat list unavailable"}" }
            )
            val callRegistration = firebaseBackend.listenToIncomingCallSignals(
                userId = activeSession.userId,
                onSignal = { signal ->
                    if (signal.callId.isNotBlank() && signal.callId !in notifiedCallIds) {
                        notifiedCallIds.add(signal.callId)
                        val kind = if (signal.type == CallType.VideoOffer) "video" else "audio"
                        if (signal.createdAtMillis > System.currentTimeMillis() - 60_000L) {
                            activeCallId = signal.callId
                            activeCallKind = kind
                            activeCallPeerId = signal.fromUserId
                            activeCallPeerName = "Incoming call"
                            activeCallIsCaller = false
                            scope.launch {
                                val callerName = runCatching { firebaseBackend.getUserDisplayName(signal.fromUserId) }
                                    .getOrDefault("")
                                if (callerName.isNotBlank() && activeCallId == signal.callId) {
                                    activeCallPeerName = callerName
                                }
                            }
                            stepName = AppStep.IncomingCall.name
                        }
                        calls.add(0, "Incoming $kind call")
                        context.showPrivateTalkNotification("Incoming PrivateTalk call", "Incoming $kind call")
                    }
                },
                onError = { syncNotice = "Firebase connection problem: ${it.message ?: "call alerts unavailable"}" }
            )
            onDispose {
                chatRegistration.remove()
                callRegistration.remove()
            }
        }
    }

    DisposableEffect(selectedChatId, session?.userId) {
        val activeSession = session
        if (activeSession == null) {
            onDispose { }
        } else {
            val registration = firebaseBackend.listenToChatMessages(
                chatId = selectedChatId,
                onMessages = { cloudMessages ->
                    syncNotice = null
                    val target = messagesByChat.getOrPut(selectedChatId) { mutableStateListOf() }
                    val hasLoadedBefore = selectedChatId in loadedMessageChats
                    target.clear()
                    target.addAll(cloudMessages.map { it.toUiMessage(activeSession.userId, crypto) })
                    if (!hasLoadedBefore) {
                        loadedMessageChats.add(selectedChatId)
                    }
                    cloudMessages.lastOrNull()?.let { last ->
                        val index = chats.indexOfFirst { it.id == selectedChatId }
                        if (index >= 0) {
                            chats[index] = chats[index].copy(
                                lastMessage = when (last.kind) {
                                    "voice" -> "Voice note"
                                    "image" -> "Photo"
                                    else -> last.text
                                },
                                time = "Now",
                                unread = 0
                            )
                        }
                    }
                },
                onError = { syncNotice = "Firebase connection problem: ${it.message ?: "messages unavailable"}" }
            )
            onDispose { registration.remove() }
        }
    }

    when (currentStep) {
        AppStep.Invite -> InviteScreen(onUnlocked = {
            session = AppSession.fromLocal(it)
            profileName = it.displayName
            stepName = AppStep.Home.name
        })
        AppStep.Home -> HomeScreen(
            session = session,
            chats = chats,
            statuses = statuses,
            calls = calls,
            profileName = profileName,
            onProfileNameChanged = { profileName = it },
            profilePhotoUrl = profilePhotoUrl,
            onProfilePhotoChanged = { profilePhotoUrl = it },
            username = username,
            onUsernameChanged = { username = it },
            firebaseBackend = firebaseBackend,
            onOpenDirectChat = { chatId, name ->
                if (chats.none { it.id == chatId }) {
                    chats.add(0, ChatPreview(chatId, name, "Start secure chat", "Now", 0))
                    messagesByChat[chatId] = mutableStateListOf()
                }
                selectedChatId = chatId
                selectedChatName = name
                stepName = AppStep.ChatRoom.name
            },
            selectedTab = selectedTab,
            onTabSelected = { selectedTab = it },
            onChatSelected = {
                selectedChatId = it.id
                selectedChatName = it.name
                stepName = AppStep.ChatRoom.name
            }
        )
        AppStep.ChatRoom -> ChatRoomScreen(
            session = session,
            chatName = selectedChatName,
            chatId = selectedChatId,
            currentUserId = session?.userId.orEmpty(),
            firebaseBackend = firebaseBackend,
            crypto = crypto,
            syncNotice = syncNotice,
            messages = messagesByChat.getOrPut(selectedChatId) { mutableStateListOf() },
            onBack = { stepName = AppStep.Home.name },
            onSend = { message ->
                val messages = messagesByChat.getOrPut(selectedChatId) { mutableStateListOf() }
                val activeSession = session
                if (activeSession == null) {
                    messages.add(message.copy(remoteId = "local-${System.currentTimeMillis()}"))
                }
                val index = chats.indexOfFirst { it.id == selectedChatId }
                if (index >= 0) {
                    chats[index] = chats[index].copy(
                        lastMessage = when {
                            message.voice -> "Voice note"
                            message.image -> "Image"
                            else -> message.text
                        },
                        time = "Now",
                        unread = 0
                    )
                }
                if (activeSession != null) {
                    scope.launch {
                        runCatching {
                            val kind = when {
                                message.voice -> "voice"
                                message.image -> "image"
                                else -> "text"
                            }
                            val peerPublicKey = firebaseBackend.getDirectChatPeerPublicKey(selectedChatId, activeSession.userId)
                            if (kind == "text") {
                                require(!peerPublicKey.isNullOrBlank()) { "Recipient encryption key is not ready. Ask them to open the updated app once." }
                                val encrypted = crypto.encrypt(selectedChatId, peerPublicKey, message.text)
                                firebaseBackend.sendEncryptedChatMessage(
                                    chatId = selectedChatId,
                                    senderId = activeSession.userId,
                                    senderName = activeSession.displayName,
                                    senderPublicKey = crypto.publicKeyBase64(),
                                    recipientPublicKey = peerPublicKey,
                                    cipherText = encrypted.cipherText,
                                    nonce = encrypted.nonce,
                                    kind = kind
                                )
                            } else {
                                require(!peerPublicKey.isNullOrBlank()) { "Recipient encryption key is not ready. Ask them to open the updated app once." }
                                val localUri = requireNotNull(message.mediaUri) { "Media file is missing." }
                                val encryptedMedia = crypto.encryptBytes(selectedChatId, peerPublicKey, context.prepareMediaBytes(Uri.parse(localUri), kind))
                                encryptedMedia.bytes.requireFitsFreePlan(kind)
                                val inlineMedia = Base64.getEncoder().encodeToString(encryptedMedia.bytes)
                                firebaseBackend.sendEncryptedChatMessage(
                                    chatId = selectedChatId,
                                    senderId = activeSession.userId,
                                    senderName = activeSession.displayName,
                                    senderPublicKey = crypto.publicKeyBase64(),
                                    recipientPublicKey = peerPublicKey,
                                    cipherText = "",
                                    nonce = encryptedMedia.nonce,
                                    kind = kind,
                                    mediaUrl = null,
                                    mediaInlineBase64 = inlineMedia
                                )
                            }
                        }.onFailure {
                            syncNotice = "Send failed: ${it.message ?: "check Firebase/phone internet"}"
                            messages.add(
                                message.copy(
                                    text = "${message.text} (not synced)",
                                    remoteId = "failed-${System.currentTimeMillis()}"
                                )
                            )
                        }
                    }
                }
            },
            onCall = { kind ->
                val activeSession = session
                if (activeSession == null) {
                    calls.add(0, "$selectedChatName - sign in required")
                } else {
                    scope.launch {
                        runCatching {
                            val peerUserId = firebaseBackend.getDirectChatPeerUserId(selectedChatId, activeSession.userId)
                                ?: throw IllegalStateException("Call peer is not ready.")
                            val callId = "$selectedChatId-${System.currentTimeMillis()}"
                            activeCallId = callId
                            activeCallKind = kind
                            activeCallPeerName = selectedChatName
                            activeCallPeerId = peerUserId
                            activeCallIsCaller = true
                            firebaseBackend.publishCallSignal(
                                CallSignal(
                                    callId = callId,
                                    fromUserId = activeSession.userId,
                                    toUserId = peerUserId,
                                    type = if (kind == "video") CallType.VideoOffer else CallType.AudioOffer,
                                    sdpOrCandidateJson = "{}",
                                    createdAtMillis = System.currentTimeMillis()
                                )
                            )
                            calls.add(0, "$selectedChatName - outgoing $kind call")
                            stepName = AppStep.CallRoom.name
                        }.onFailure {
                            calls.add(0, "$selectedChatName - call failed: ${it.message ?: "unknown error"}")
                        }
                    }
                }
            }
        )
        AppStep.IncomingCall -> IncomingCallScreen(
            peerName = activeCallPeerName,
            callKind = activeCallKind,
            onAccept = {
                activeCallIsCaller = false
                stepName = AppStep.CallRoom.name
            },
            onReject = {
                val activeSession = session
                if (activeSession != null && activeCallId.isNotBlank()) {
                    scope.launch {
                        runCatching {
                            firebaseBackend.publishCallSignal(
                                CallSignal(
                                    callId = activeCallId,
                                    fromUserId = activeSession.userId,
                                    toUserId = activeCallPeerId,
                                    type = CallType.Ended,
                                    sdpOrCandidateJson = "{}",
                                    createdAtMillis = System.currentTimeMillis()
                                )
                            )
                        }
                    }
                }
                stepName = AppStep.Home.name
            }
        )
        AppStep.CallRoom -> CallRoomScreen(
            peerName = activeCallPeerName,
            callKind = activeCallKind,
            callId = activeCallId,
            currentUserId = session?.userId.orEmpty(),
            peerUserId = activeCallPeerId,
            isCaller = activeCallIsCaller,
            firebaseBackend = firebaseBackend,
            onEndCall = { publishEnded ->
                val activeSession = session
                if (publishEnded && activeSession != null && activeCallId.isNotBlank()) {
                    scope.launch {
                        runCatching {
                            firebaseBackend.publishCallSignal(
                                CallSignal(
                                    callId = activeCallId,
                                    fromUserId = activeSession.userId,
                                    toUserId = activeCallPeerId,
                                    type = CallType.Ended,
                                    sdpOrCandidateJson = "{}",
                                    createdAtMillis = System.currentTimeMillis()
                                )
                            )
                        }
                    }
                }
                stepName = AppStep.ChatRoom.name
            }
        )
    }
}

@Composable
private fun InviteScreen(onUnlocked: (LocalSession) -> Unit) {
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var isLoading by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val firebaseBackend = remember { FirebasePrivateTalkBackend() }
    val context = androidx.compose.ui.platform.LocalContext.current
    val credentialManager = remember { CredentialManager.create(context) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepGreen)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(FreshGreen)
                    .padding(18.dp),
                tint = Color.White
            )
            Spacer(Modifier.height(24.dp))
            Text("PrivateTalk", color = Color.White, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Text("Invited email only", color = Color(0xFFD3E7DF), style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(32.dp))
            Card(
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(Modifier.padding(18.dp)) {
                    Text("Only approved Google accounts can enter this private network.", color = Ink)
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = {
                            isLoading = true
                            errorMessage = null
                            scope.launch {
                                runCatching {
                                    val googleIdOption = GetGoogleIdOption.Builder()
                                        .setFilterByAuthorizedAccounts(false)
                                        .setAutoSelectEnabled(false)
                                        .setServerClientId(context.getString(R.string.default_web_client_id))
                                        .build()
                                    val request = GetCredentialRequest.Builder()
                                        .addCredentialOption(googleIdOption)
                                        .build()
                                    val result = credentialManager.getCredential(context, request)
                                    val credential = result.credential
                                    if (credential !is CustomCredential || credential.type != TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                                        throw IllegalArgumentException("Google credential was not returned.")
                                    }
                                    val googleCredential = GoogleIdTokenCredential.createFrom(credential.data)
                                    firebaseBackend.unlockWithGoogleIdToken(googleCredential.idToken)
                                }
                                    .onSuccess {
                                        isLoading = false
                                        onUnlocked(it)
                                    }
                                    .onFailure {
                                        isLoading = false
                                        errorMessage = firebaseErrorMessage(it)
                                    }
                            }
                        },
                        enabled = !isLoading,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = DeepGreen)
                    ) {
                        Text(if (isLoading) "Checking Google account..." else "Sign in with Google")
                    }
                    errorMessage?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, color = Color(0xFFB3261E), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

private fun firebaseErrorMessage(error: Throwable): String {
    val message = error.message.orEmpty()
    return when {
        message.contains("CONFIGURATION_NOT_FOUND", ignoreCase = true) ->
            "Enable Google sign-in in Firebase Authentication, then download the updated google-services.json."
        message.contains("default_web_client_id", ignoreCase = true) ->
            "Download the updated google-services.json after enabling Google sign-in."
        message.contains("PERMISSION_DENIED", ignoreCase = true) ->
            "Firestore rules blocked email invite lookup. Update rules and add inviteEmails doc."
        message.contains("NOT_FOUND", ignoreCase = true) ->
            "Create Firestore database and inviteEmails document first."
        else -> message.ifBlank { "Could not unlock invite from Firebase." }
    }
}

@Composable
private fun HomeScreen(
    session: AppSession?,
    chats: List<ChatPreview>,
    statuses: MutableList<String>,
    calls: MutableList<String>,
    profileName: String,
    onProfileNameChanged: (String) -> Unit,
    profilePhotoUrl: String,
    onProfilePhotoChanged: (String) -> Unit,
    username: String,
    onUsernameChanged: (String) -> Unit,
    firebaseBackend: FirebasePrivateTalkBackend,
    onOpenDirectChat: (String, String) -> Unit,
    selectedTab: HomeTab,
    onTabSelected: (HomeTab) -> Unit,
    onChatSelected: (ChatPreview) -> Unit
) {
    var searchUsername by rememberSaveable { mutableStateOf("") }
    var searchMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var isSearchActive by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun addByUsername() {
        val activeSession = session ?: return
        scope.launch {
            searchMessage = "Searching..."
            runCatching {
                val other = firebaseBackend.findUserByUsername(searchUsername)
                    ?: throw IllegalArgumentException("No user found.")
                if (other.userId == activeSession.userId) {
                    throw IllegalArgumentException("That is your username.")
                }
                val current = CloudUser(
                    userId = activeSession.userId,
                    displayName = profileName,
                    email = activeSession.email,
                    username = username
                )
                val chatId = firebaseBackend.createOrOpenDirectChat(current, other)
                onOpenDirectChat(chatId, other.displayName)
            }.onSuccess {
                searchMessage = null
                searchUsername = ""
            }.onFailure {
                searchMessage = it.message ?: "Could not find user."
            }
        }
    }

    Scaffold(
        topBar = {
            MessengerTopBar(
                selectedTab = selectedTab,
                onTabSelected = onTabSelected,
                searchUsername = searchUsername,
                onSearchUsernameChanged = {
                    searchUsername = it
                    searchMessage = null
                },
                onAddUsername = { addByUsername() },
                searchMessage = searchMessage,
                isSearchActive = isSearchActive,
                onSearchActiveChanged = { active ->
                    isSearchActive = active
                    if (!active) {
                        searchUsername = ""
                        searchMessage = null
                    }
                }
            )
        },
        floatingActionButton = {
            if (selectedTab == HomeTab.Chats) {
                FloatingActionButton(
                    onClick = {},
                    containerColor = FreshGreen,
                    contentColor = Color.White,
                    shape = CircleShape
                ) {
                    Icon(Icons.Default.Chat, contentDescription = "New chat")
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
                .padding(padding)
        ) {
            when (selectedTab) {
                HomeTab.Chats -> ChatsTab(chats, onChatSelected)
                HomeTab.Status -> StatusTab(profileName, statuses)
                HomeTab.Calls -> CallsTab(calls)
                HomeTab.Profile -> ProfileTab(
                    session = session,
                    profileName = profileName,
                    onProfileNameChanged = onProfileNameChanged,
                    profilePhotoUrl = profilePhotoUrl,
                    onProfilePhotoChanged = onProfilePhotoChanged,
                    username = username,
                    onUsernameChanged = onUsernameChanged,
                    firebaseBackend = firebaseBackend
                )
            }
        }
    }
}

@Composable
private fun MessengerTopBar(
    selectedTab: HomeTab,
    onTabSelected: (HomeTab) -> Unit,
    searchUsername: String,
    onSearchUsernameChanged: (String) -> Unit,
    onAddUsername: () -> Unit,
    searchMessage: String?,
    isSearchActive: Boolean,
    onSearchActiveChanged: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(DeepGreen)
            .padding(top = 30.dp)
    ) {
        if (isSearchActive && selectedTab == HomeTab.Chats) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { onSearchActiveChanged(false) }) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Close search", tint = Color.White)
                }
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(Color.White)
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BasicTextField(
                        value = searchUsername,
                        onValueChange = onSearchUsernameChanged,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = Ink),
                        modifier = Modifier.weight(1f),
                        decorationBox = { innerTextField ->
                            if (searchUsername.isBlank()) {
                                Text("Search or add username", color = Muted)
                            }
                            innerTextField()
                        }
                    )
                }
                TextButton(onClick = onAddUsername, enabled = searchUsername.length >= 3) {
                    Text("Add", color = Color.White)
                }
            }
            searchMessage?.let {
                Text(
                    it,
                    color = Color(0xFFD3E7DF),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 64.dp, vertical = 2.dp)
                )
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "PrivateTalk",
                    color = Color.White,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { if (selectedTab == HomeTab.Chats) onSearchActiveChanged(true) }) {
                    Icon(Icons.Default.Search, contentDescription = "Search", tint = Color.White)
                }
                IconButton(onClick = {}) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More", tint = Color.White)
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HomeTab.entries.forEach { tab ->
                val selected = selectedTab == tab
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onTabSelected(tab) }
                        .padding(top = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        tab.label.uppercase(),
                        color = if (selected) Color.White else Color(0xFF9DC8BE),
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .background(if (selected) Color.White else Color.Transparent)
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatsTab(
    chats: List<ChatPreview>,
    onChatSelected: (ChatPreview) -> Unit
) {
    if (chats.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "Search a username to start a private chat.",
                color = Muted,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    } else {
        LazyColumn(contentPadding = PaddingValues(vertical = 2.dp)) {
            items(chats) { chat ->
                ChatRow(chat = chat, onClick = { onChatSelected(chat) })
            }
        }
    }
}

@Composable
private fun SecurityBanner(session: AppSession?) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Lock, contentDescription = null, tint = FreshGreen)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Invite verified", color = Ink, fontWeight = FontWeight.Bold)
                Text(
                    "${session?.displayName ?: "Member"} joined with ${session?.inviteId ?: "local invite"}",
                    color = Muted,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun ChatRow(chat: ChatPreview, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .clickable(onClick = onClick)
            .background(Color.White)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(chat.name, size = 54)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    chat.name,
                    color = Ink,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                Text(chat.time, color = Muted, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(chat.lastMessage, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (chat.unread > 0) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(FreshGreen),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(chat.unread.toString(), color = Color.White, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusTab(profileName: String, statuses: MutableList<String>) {
    var draftStatus by rememberSaveable { mutableStateOf("") }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { SectionTitle("Status Activity") }
        item {
            Card(shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.padding(14.dp)) {
                    Text("My status", color = Ink, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = draftStatus,
                        onValueChange = { draftStatus = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Post a text status") },
                        singleLine = true
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            statuses.add(0, "$profileName: $draftStatus")
                            draftStatus = ""
                        },
                        enabled = draftStatus.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = DeepGreen)
                    ) {
                        Text("Post status")
                    }
                }
            }
        }
        items(statuses) { status ->
            Card(shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(status)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(status.substringBefore(":"), fontWeight = FontWeight.Bold, color = Ink)
                        Text(status.substringAfter(":", "Updated today").trim(), color = Muted)
                    }
                    OutlinedButton(onClick = {}) { Text("View") }
                }
            }
        }
    }
}

@Composable
private fun CallsTab(calls: List<String>) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { SectionTitle("Calls") }
        items(calls) { call ->
            Card(shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AddIcCall, contentDescription = null, tint = DeepGreen)
                    Spacer(Modifier.width(12.dp))
                    Text(call, color = Ink, modifier = Modifier.weight(1f))
                    IconButton(onClick = {}) { Icon(Icons.Default.Call, contentDescription = "Call", tint = FreshGreen) }
                }
            }
        }
    }
}

@Composable
private fun ProfileTab(
    session: AppSession?,
    profileName: String,
    onProfileNameChanged: (String) -> Unit,
    profilePhotoUrl: String,
    onProfilePhotoChanged: (String) -> Unit,
    username: String,
    onUsernameChanged: (String) -> Unit,
    firebaseBackend: FirebasePrivateTalkBackend
) {
    var editingName by rememberSaveable { mutableStateOf(profileName) }
    var editingUsername by rememberSaveable { mutableStateOf(username) }
    var profileMessage by rememberSaveable { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val activeSession = session ?: return@rememberLauncherForActivityResult
        if (uri != null) {
            onProfilePhotoChanged(uri.toString())
            scope.launch {
                profileMessage = "Saving profile photo..."
                runCatching {
                    val compressed = context.prepareMediaBytes(uri, "image")
                    require(compressed.size <= FREE_PLAN_MAX_PROFILE_PHOTO_BYTES) {
                        "Profile photo is too large for the free Firebase plan. Pick a smaller or cropped photo."
                    }
                    val inline = Base64.getEncoder().encodeToString(compressed)
                    firebaseBackend.updateProfilePhotoInline(activeSession.userId, inline)
                    uri.toString()
                }
                    .onSuccess {
                        onProfilePhotoChanged(it)
                        profileMessage = "Profile photo updated."
                    }
                    .onFailure {
                        profileMessage = it.message ?: "Could not upload profile photo."
                    }
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            val name = profileName.ifBlank { session?.displayName ?: "Demo User" }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.animateContentSize()
            ) {
                Box(contentAlignment = Alignment.BottomEnd) {
                    ProfileAvatar(name = name, photoUrl = profilePhotoUrl, size = 112)
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(FreshGreen)
                            .clickable { photoPicker.launch("image/*") },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.PhotoCamera, contentDescription = "Change profile photo", tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Ink)
                Text(if (username.isBlank()) "Invite-only member" else "@$username", color = Muted)
            }
        }
        item {
            Card(shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFF7FAF8))) {
                Column(Modifier.padding(14.dp)) {
                    OutlinedTextField(
                        value = editingName,
                        onValueChange = { editingName = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Display name") },
                        singleLine = true
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = editingUsername,
                        onValueChange = { editingUsername = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Unique username") },
                        singleLine = true
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = {
                                val activeSession = session
                                val cleanName = editingName.trim()
                                onProfileNameChanged(cleanName)
                                if (activeSession != null) {
                                    scope.launch { runCatching { firebaseBackend.updateDisplayName(activeSession.userId, cleanName) } }
                                }
                            },
                            enabled = editingName.trim().length >= 2,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = DeepGreen)
                        ) {
                            Text("Save")
                        }
                        Button(
                            onClick = {
                                val activeSession = session ?: return@Button
                                scope.launch {
                                    profileMessage = "Saving username..."
                                    runCatching {
                                        firebaseBackend.claimUsername(
                                            userId = activeSession.userId,
                                            displayName = editingName.trim().ifBlank { profileName },
                                            email = activeSession.email.ifBlank { null },
                                            username = editingUsername
                                        )
                                    }.onSuccess {
                                        onUsernameChanged(it)
                                        profileMessage = "Username saved: @$it"
                                    }.onFailure {
                                        profileMessage = it.message ?: "Could not save username."
                                    }
                                }
                            },
                            enabled = session != null && editingUsername.length >= 3,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = FreshGreen)
                        ) {
                            Text("Username")
                        }
                    }
                    AnimatedVisibility(visible = profileMessage != null, enter = fadeIn(), exit = fadeOut()) {
                        Text(profileMessage.orEmpty(), color = Muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 10.dp))
                    }
                }
            }
        }
        item {
            Card(shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
                    ProfileLine("Profile photo", if (profilePhotoUrl.isBlank()) "Not set" else "Synced")
                    ProfileLine("Encryption identity", session?.identityKeyPreview ?: "Local key pending")
                    ProfileLine("Invite access", session?.inviteId ?: "demo")
                    ProfileLine("User id", session?.userId ?: "local-demo-user")
                }
            }
        }
    }
}

@Composable
private fun ProfileLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Ink, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Text(value, color = Muted)
    }
}

@Composable
private fun IncomingCallScreen(
    peerName: String,
    callKind: String,
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF071016))
            .padding(28.dp)
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Avatar(peerName, size = 112)
            Spacer(Modifier.height(24.dp))
            Text(
                peerName,
                color = Color.White,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(8.dp))
            Text(
                if (callKind == "video") "Incoming video call" else "Incoming voice call",
                color = Color(0xFFD3E7DF),
                style = MaterialTheme.typography.titleMedium
            )
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 38.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            FloatingActionButton(
                onClick = onReject,
                containerColor = Color(0xFFE53935),
                contentColor = Color.White,
                shape = CircleShape
            ) {
                Icon(Icons.Default.Call, contentDescription = "Reject call")
            }
            FloatingActionButton(
                onClick = onAccept,
                containerColor = FreshGreen,
                contentColor = Color.White,
                shape = CircleShape
            ) {
                Icon(
                    if (callKind == "video") Icons.Default.VideoCall else Icons.Default.Call,
                    contentDescription = "Accept call"
                )
            }
        }
    }
}

@Composable
private fun CallRoomScreen(
    peerName: String,
    callKind: String,
    callId: String,
    currentUserId: String,
    peerUserId: String,
    isCaller: Boolean,
    firebaseBackend: FirebasePrivateTalkBackend,
    onEndCall: (Boolean) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var callStatus by rememberSaveable(callId) { mutableStateOf("Starting call...") }
    val webRtcSession = remember(callId) {
        PrivateTalkWebRtcSession(
            context = context,
            onLocalSdp = { sdpJson ->
                val type = if (sdpJson.contains("\"type\":\"offer\"")) {
                    if (callKind == "video") CallType.VideoOffer else CallType.AudioOffer
                } else {
                    CallType.Answer
                }
                scope.launch {
                    runCatching {
                        firebaseBackend.publishCallSignal(
                            CallSignal(
                                callId = callId,
                                fromUserId = currentUserId,
                                toUserId = peerUserId,
                                type = type,
                                sdpOrCandidateJson = sdpJson,
                                createdAtMillis = System.currentTimeMillis()
                            )
                        )
                    }.onFailure { callStatus = "Could not send call signal" }
                }
            },
            onLocalIce = { candidateJson ->
                scope.launch {
                    runCatching {
                        firebaseBackend.publishCallSignal(
                            CallSignal(
                                callId = callId,
                                fromUserId = currentUserId,
                                toUserId = peerUserId,
                                type = CallType.IceCandidate,
                                sdpOrCandidateJson = candidateJson,
                                createdAtMillis = System.currentTimeMillis()
                            )
                        )
                    }
                }
            },
            onStateChanged = { callStatus = it }
        )
    }
    var muted by rememberSaveable { mutableStateOf(false) }
    var cameraEnabled by rememberSaveable { mutableStateOf(callKind == "video") }
    val remoteRenderer = remember(callId) { org.webrtc.SurfaceViewRenderer(context) }
    val localRenderer = remember(callId) { org.webrtc.SurfaceViewRenderer(context) }
    val requiredPermissions = remember(callKind) {
        if (callKind == "video") {
            arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
        } else {
            arrayOf(Manifest.permission.RECORD_AUDIO)
        }
    }
    var callPermissionsGranted by remember(callKind) {
        mutableStateOf(
            requiredPermissions.all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        callPermissionsGranted = requiredPermissions.all { grants[it] == true || ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
    }

    LaunchedEffect(callKind) {
        if (!callPermissionsGranted) {
            permissionLauncher.launch(requiredPermissions)
        }
    }

    LaunchedEffect(callId, callKind, callPermissionsGranted) {
        if (!callPermissionsGranted) {
            callStatus = "Camera/microphone permission needed"
            return@LaunchedEffect
        }
        webRtcSession.start(
            localRenderer = if (callKind == "video") localRenderer else null,
            remoteRenderer = if (callKind == "video") remoteRenderer else null,
            videoEnabled = callKind == "video",
            audioEnabled = true
        )
        if (isCaller) {
            delay(350)
            webRtcSession.createOffer()
            callStatus = "Calling..."
        } else {
            callStatus = "Answering..."
        }
    }

    LaunchedEffect(muted) {
        webRtcSession.setMuted(muted)
    }

    LaunchedEffect(cameraEnabled) {
        webRtcSession.setCameraEnabled(cameraEnabled)
    }

    DisposableEffect(callId, currentUserId) {
        if (callId.isBlank() || currentUserId.isBlank()) {
            onDispose { }
        } else {
            val registration = firebaseBackend.listenToCallSignals(
                callId = callId,
                currentUserId = currentUserId,
                onSignal = { signal ->
                    when (signal.type) {
                        CallType.AudioOffer,
                        CallType.VideoOffer -> {
                            if (!isCaller && signal.sdpOrCandidateJson.contains("\"sdp\"")) {
                                webRtcSession.acceptOffer(signal.sdpOrCandidateJson)
                                callStatus = "Connecting..."
                            }
                        }
                        CallType.Answer -> {
                            if (isCaller && signal.sdpOrCandidateJson.contains("\"sdp\"")) {
                                webRtcSession.acceptAnswer(signal.sdpOrCandidateJson)
                                callStatus = "Connecting..."
                            }
                        }
                        CallType.IceCandidate -> {
                            if (signal.sdpOrCandidateJson.isNotBlank()) {
                                runCatching { webRtcSession.addIceCandidate(signal.sdpOrCandidateJson) }
                            }
                        }
                        CallType.Ended -> {
                            callStatus = "Call ended"
                            onEndCall(false)
                        }
                    }
                },
                onError = { callStatus = "Call signal problem" }
            )
            onDispose { registration.remove() }
        }
    }

    DisposableEffect(webRtcSession) {
        onDispose { webRtcSession.stop() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B1F1B))
            .padding(24.dp)
    ) {
        if (callKind == "video" && cameraEnabled) {
            AndroidView(
                factory = { remoteRenderer },
                modifier = Modifier.fillMaxSize()
            )
            AndroidView(
                factory = { localRenderer },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 78.dp, end = 8.dp)
                    .width(112.dp)
                    .height(160.dp)
                    .clip(RoundedCornerShape(8.dp))
            )
        } else {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Avatar(peerName, size = 96)
                Spacer(Modifier.height(18.dp))
                Text(peerName, color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(
                    if (callKind == "video") "Camera paused" else "Secure audio call active",
                    color = Color(0xFFD3E7DF)
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(peerName, color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                callStatus,
                color = Color(0xFFD3E7DF),
                style = MaterialTheme.typography.bodySmall
            )
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            CallControlButton(
                active = !muted,
                onClick = { muted = !muted }
            ) {
                Icon(Icons.Default.Mic, contentDescription = "Mute", tint = Color.White)
            }
            if (callKind == "video") {
                CallControlButton(
                    active = cameraEnabled,
                    onClick = { cameraEnabled = !cameraEnabled }
                ) {
                    Icon(Icons.Default.VideoCall, contentDescription = "Camera", tint = Color.White)
                }
            }
            FloatingActionButton(
                onClick = { onEndCall(true) },
                containerColor = Color(0xFFE53935),
                contentColor = Color.White,
                shape = CircleShape
            ) {
                Icon(Icons.Default.Call, contentDescription = "End call")
            }
        }
    }
}

@Composable
private fun CallControlButton(
    active: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(if (active) Color(0xFF243F39) else Color(0xFF66736F))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatRoomScreen(
    session: AppSession?,
    chatName: String,
    chatId: String,
    currentUserId: String,
    firebaseBackend: FirebasePrivateTalkBackend,
    crypto: EndToEndCrypto,
    syncNotice: String?,
    messages: List<Message>,
    onBack: () -> Unit,
    onSend: (Message) -> Unit,
    onCall: (String) -> Unit
) {
    var draft by remember { mutableStateOf("") }
    var isRecording by remember { mutableStateOf(false) }
    var isRecordingLocked by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    var openImage by remember { mutableStateOf<ImageViewerContent?>(null) }
    var showBuiltInCamera by remember { mutableStateOf(false) }
    var peerTyping by remember { mutableStateOf(false) }
    val deletedForMeIds = remember { mutableStateListOf<String>() }
    val resolvedMediaById = remember { mutableStateMapOf<String, ResolvedMedia>() }
    val imageRatioById = remember { mutableStateMapOf<String, Float>() }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val recorder = remember { VoiceNoteRecorder(context) }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            showBuiltInCamera = true
        } else {
            notice = "Camera permission is required to take a photo."
        }
    }
    val audioPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            runCatching {
                recorder.start()
                isRecording = true
                isRecordingLocked = false
                notice = "Hold to record. Release to send."
            }.onFailure {
                notice = "Could not start voice recording."
            }
        } else {
            notice = "Microphone permission is required to record voice."
        }
    }
    fun startVoiceRecording() {
        if (isRecording) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            runCatching {
                recorder.start()
                isRecording = true
                isRecordingLocked = false
                notice = "Recording... release to send or tap lock."
            }.onFailure {
                notice = "Could not start voice recording."
            }
        } else {
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    fun sendVoiceRecording() {
        if (!isRecording) return
        recorder.stop()?.let {
            onSend(Message("Voice note", true, "Now", voice = true, mediaUri = it.toString()))
        }
        isRecording = false
        isRecordingLocked = false
        notice = "Voice note sent."
    }
    fun cancelVoiceRecording() {
        if (!isRecording) return
        recorder.stop()
        isRecording = false
        isRecordingLocked = false
        notice = "Voice note cancelled."
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size + 1)
            if (currentUserId.isNotBlank()) {
                runCatching { firebaseBackend.markChatRead(chatId, currentUserId) }
            }
        }
    }

    LaunchedEffect(chatId, currentUserId, draft) {
        if (chatId.isBlank() || currentUserId.isBlank()) return@LaunchedEffect
        runCatching { firebaseBackend.updateTypingStatus(chatId, currentUserId, draft.isNotBlank()) }
        if (draft.isNotBlank()) {
            delay(5_000)
            runCatching { firebaseBackend.updateTypingStatus(chatId, currentUserId, false) }
        }
    }

    DisposableEffect(chatId, currentUserId) {
        if (chatId.isBlank() || currentUserId.isBlank()) {
            onDispose { }
        } else {
            val registration = firebaseBackend.listenToTypingStatus(
                chatId = chatId,
                currentUserId = currentUserId,
                onTypingChanged = { peerTyping = it },
                onError = { }
            )
            onDispose {
                registration.remove()
                scope.launch { runCatching { firebaseBackend.updateTypingStatus(chatId, currentUserId, false) } }
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(chatName, size = 36)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(chatName, color = Color.White, fontWeight = FontWeight.Bold)
                            Text(
                                if (peerTyping && draft.isBlank()) "typing..." else "${session?.displayName ?: "Member"} secured chat",
                                color = if (peerTyping && draft.isBlank()) FreshGreen else Color(0xFFD3E7DF),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = { onCall("video") }) { Icon(Icons.Default.VideoCall, contentDescription = "Video call", tint = Color.White) }
                    IconButton(onClick = { onCall("audio") }) { Icon(Icons.Default.Call, contentDescription = "Audio call", tint = Color.White) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ChatHeader)
                )
            },
            bottomBar = {
                Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ChatDark)
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isRecording) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Mic, contentDescription = null, tint = Color(0xFFB3261E))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (isRecordingLocked) "Recording locked" else "Recording... release to send",
                            color = Ink,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { cancelVoiceRecording() }) {
                            Text("Cancel")
                        }
                        if (!isRecordingLocked) {
                            IconButton(onClick = {
                                isRecordingLocked = true
                                notice = "Recording locked. Tap send when done."
                            }) {
                                Icon(Icons.Default.Lock, contentDescription = "Lock recording", tint = DeepGreen)
                            }
                        }
                        IconButton(onClick = { sendVoiceRecording() }) {
                            Icon(Icons.Default.Send, contentDescription = "Send voice note", tint = DeepGreen)
                        }
                    }
                } else {
                    IconButton(onClick = {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                            showBuiltInCamera = true
                        } else {
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    }) { Icon(Icons.Default.PhotoCamera, contentDescription = "Camera", tint = Muted) }
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(Color(0xFF1F2C33))
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BasicTextField(
                            value = draft,
                            onValueChange = { draft = it },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.White),
                            modifier = Modifier.weight(1f),
                            decorationBox = { innerTextField ->
                                if (draft.isBlank()) {
                                    Text("Message", color = ChatMuted)
                                }
                                innerTextField()
                            }
                        )
                        Icon(Icons.Default.AttachFile, contentDescription = "Attach", tint = ChatMuted)
                    }
                    Spacer(Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(DeepGreen)
                            .clip(CircleShape)
                            .pointerInput(draft) {
                                detectTapGestures(
                                    onPress = {
                                        if (draft.isNotBlank()) {
                                            onSend(Message(draft.trim(), true, "Now"))
                                            draft = ""
                                        } else {
                                            val tapped = withTimeoutOrNull(320) { tryAwaitRelease() }
                                            if (tapped == true) {
                                                notice = "Hold mic to record."
                                            } else {
                                                startVoiceRecording()
                                                val released = tryAwaitRelease()
                                                if (released && isRecording && !isRecordingLocked) {
                                                    sendVoiceRecording()
                                                }
                                            }
                                        }
                                    }
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(if (draft.isBlank()) Icons.Default.Mic else Icons.Default.Send, contentDescription = "Send", tint = Color.White)
                    }
                }
                }
            }
        ) { padding ->
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .background(ChatDark)
                    .padding(padding),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    ChatNotice(notice ?: syncNotice)
                }
                item {
                    DateChip("Today")
                }
                items(
                    items = messages.filter { it.remoteId == null || it.remoteId !in deletedForMeIds },
                    key = { it.remoteId ?: "${it.meta}-${it.text}-${it.mediaUri}" }
                ) { message ->
                    MessageBubble(
                        message = message,
                        chatId = chatId,
                        currentUserId = currentUserId,
                        firebaseBackend = firebaseBackend,
                        crypto = crypto,
                        resolvedMediaById = resolvedMediaById,
                        imageRatioById = imageRatioById,
                        onOpenImage = { uri ->
                            openImage = ImageViewerContent(
                                uri = uri,
                                title = if (message.mine) "You" else chatName,
                                meta = message.meta
                            )
                        },
                        onDeleteForMe = {
                            message.remoteId?.let { deletedForMeIds.add(it) }
                        },
                        onDeleteForEveryone = {
                            val messageId = message.remoteId ?: return@MessageBubble
                            scope.launch {
                                runCatching { firebaseBackend.deleteMessageForEveryone(chatId, messageId) }
                                    .onFailure { notice = "Could not delete message: ${it.message ?: "try again"}" }
                            }
                        }
                    )
                }
            }
        }
        openImage?.let { viewer ->
            ImageViewerOverlay(
                content = viewer,
                onClose = { openImage = null },
                onNotice = { notice = it }
            )
        }
        if (showBuiltInCamera) {
            BuiltInCameraOverlay(
                onClose = { showBuiltInCamera = false },
                onPhotoCaptured = { uri ->
                    showBuiltInCamera = false
                    onSend(Message("Photo", true, "Now", image = true, mediaUri = uri.toString()))
                },
                onNotice = { notice = it }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    message: Message,
    chatId: String,
    @Suppress("UNUSED_PARAMETER") currentUserId: String,
    firebaseBackend: FirebasePrivateTalkBackend,
    crypto: EndToEndCrypto,
    resolvedMediaById: MutableMap<String, ResolvedMedia>,
    imageRatioById: MutableMap<String, Float>,
    onOpenImage: (String) -> Unit,
    onDeleteForMe: () -> Unit,
    onDeleteForEveryone: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var isPlaying by remember { mutableStateOf(false) }
    var playbackProgress by remember { mutableStateOf(0f) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    val mediaKey = message.remoteId ?: message.mediaUri ?: message.mediaInlineBase64?.hashCode()?.toString() ?: message.text
    val resolvedMedia = resolvedMediaById[mediaKey]
    val resolvedMediaUri = resolvedMedia?.uri ?: message.mediaUri
    val mediaStatus = resolvedMedia?.status

    LaunchedEffect(message.remoteId, message.mediaUri, message.mediaInlineBase64, message.encrypted) {
        val remoteMedia = message.mediaUri
        val inlineMedia = message.mediaInlineBase64
        val nonce = message.nonce
        if (!message.encrypted || (remoteMedia.isNullOrBlank() && inlineMedia.isNullOrBlank()) || nonce.isNullOrBlank()) {
            resolvedMediaById[mediaKey] = ResolvedMedia(remoteMedia)
            return@LaunchedEffect
        }
        val extension = if (message.image) "jpg" else "m4a"
        context.cachedDecryptedMedia(message.remoteId ?: (remoteMedia ?: inlineMedia).hashCode().toString(), extension)?.let {
            resolvedMediaById[mediaKey] = ResolvedMedia(it.toString())
            return@LaunchedEffect
        }
        val peerPublicKey = if (message.mine) message.recipientPublicKey else message.senderPublicKey
        if (peerPublicKey.isNullOrBlank()) {
            resolvedMediaById[mediaKey] = ResolvedMedia(null, "Encryption key missing")
            return@LaunchedEffect
        }
        resolvedMediaById[mediaKey] = ResolvedMedia(null, "Decrypting...")
        runCatching {
            val encryptedBytes = if (!inlineMedia.isNullOrBlank()) {
                Base64.getDecoder().decode(inlineMedia)
            } else {
                firebaseBackend.downloadMediaBytes(requireNotNull(remoteMedia))
            }
            val plainBytes = crypto.decryptBytes(chatId, peerPublicKey, encryptedBytes, nonce)
            context.writeDecryptedMedia(message.remoteId ?: (remoteMedia ?: inlineMedia).hashCode().toString(), plainBytes, extension)
        }.onSuccess {
            resolvedMediaById[mediaKey] = ResolvedMedia(it.toString())
        }.onFailure {
            resolvedMediaById[mediaKey] = ResolvedMedia(null, "Could not decrypt media")
        }
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            val activePlayer = player
            if (activePlayer != null && activePlayer.duration > 0) {
                playbackProgress = activePlayer.currentPosition.toFloat() / activePlayer.duration.toFloat()
            }
            delay(120)
        }
    }
    DisposableEffect(message.mediaUri) {
        onDispose {
            player?.release()
            player = null
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.mine) Arrangement.End else Arrangement.Start
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(if (message.image) 0.76f else 0.78f)
                .clip(RoundedCornerShape(8.dp))
                .background(if (message.mine) OutgoingBubble else IncomingBubble)
                .combinedClickable(
                    onClick = {},
                    onLongClick = { showDeleteDialog = true }
                )
                .padding(if (message.image) 4.dp else 9.dp)
        ) {
            if (message.voice) {
                Column(
                    modifier = Modifier.clickable {
                        resolvedMediaUri?.let {
                            if (isPlaying) {
                                player?.pause()
                                isPlaying = false
                            } else {
                                val activePlayer = player ?: MediaPlayer.create(context, Uri.parse(it)).also { newPlayer ->
                                    player = newPlayer
                                    newPlayer?.setOnCompletionListener { finished ->
                                        playbackProgress = 0f
                                        isPlaying = false
                                        finished.seekTo(0)
                                    }
                                }
                                activePlayer?.start()
                                isPlaying = true
                            }
                        }
                    }
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (isPlaying) Icons.Default.PauseCircle else Icons.Default.PlayCircle,
                            contentDescription = if (isPlaying) "Pause voice note" else "Play voice note",
                            tint = DeepGreen
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(if (isPlaying) "Playing voice note" else "Voice note", color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                    mediaStatus?.let {
                        Spacer(Modifier.height(4.dp))
                        Text(it, color = ChatMuted, style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF314248))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(playbackProgress.coerceIn(0f, 1f))
                                .height(6.dp)
                                .background(DeepGreen)
                        )
                    }
                }
            } else if (message.image) {
                Column {
                    if (resolvedMediaUri != null) {
                        val imagePainter = rememberAsyncImagePainter(
                            model = coil.request.ImageRequest.Builder(context)
                                .data(resolvedMediaUri)
                                .crossfade(false)
                                .build()
                        )
                        val imageState = imagePainter.state
                        val imageRatio = imageRatioById[mediaKey] ?: 1f
                        LaunchedEffect(imageState, mediaKey) {
                            if (imageState is AsyncImagePainter.State.Success) {
                                imageRatioById[mediaKey] = imageState.painter.intrinsicSize.safeImageAspectRatio()
                            }
                        }
                        Box {
                            Image(
                                painter = imagePainter,
                                contentDescription = "Shared photo",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(imageRatio)
                                    .clip(RoundedCornerShape(7.dp))
                                    .background(Color(0xFF111B21))
                                    .clickable { resolvedMediaUri?.let(onOpenImage) },
                                contentScale = ContentScale.Crop
                            )
                            Text(
                                message.meta,
                                color = Color.White.copy(alpha = 0.92f),
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .background(Color.Black.copy(alpha = 0.38f), RoundedCornerShape(10.dp))
                                    .padding(horizontal = 7.dp, vertical = 3.dp)
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(150.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF1F2C33)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.PhotoCamera, contentDescription = "Image", tint = DeepGreen, modifier = Modifier.size(42.dp))
                        }
                    }
                    mediaStatus?.let {
                        Spacer(Modifier.height(6.dp))
                        Text(it, color = ChatMuted, style = MaterialTheme.typography.bodySmall)
                    }
                }
            } else {
                Text(message.text, color = Color.White)
                Spacer(Modifier.height(3.dp))
                Text(message.meta, color = Color.White.copy(alpha = 0.68f), style = MaterialTheme.typography.labelSmall, modifier = Modifier.align(Alignment.End))
            }
            if (message.voice) {
                Spacer(Modifier.height(4.dp))
                Text(message.meta, color = Color.White.copy(alpha = 0.68f), style = MaterialTheme.typography.labelSmall, modifier = Modifier.align(Alignment.End))
            }
        }
    }

    if (showDeleteDialog) {
        DeleteMessageDialog(
            canDeleteForEveryone = message.mine && message.remoteId != null,
            onDismiss = { showDeleteDialog = false },
            onDeleteForMe = {
                showDeleteDialog = false
                onDeleteForMe()
            },
            onDeleteForEveryone = {
                showDeleteDialog = false
                onDeleteForEveryone()
            }
        )
    }
}

@Composable
private fun ChatNotice(message: String?) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF182229))
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFFE9D38B), modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                message ?: "Messages are end-to-end encrypted. No one outside this chat can read or listen to them.",
                color = Color(0xFFE9D38B),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun DateChip(label: String) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Text(
            label,
            color = ChatMuted,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF182229))
                .padding(horizontal = 12.dp, vertical = 5.dp)
        )
    }
}

private fun Size.safeImageAspectRatio(): Float {
    if (width <= 0f || height <= 0f) return 1f
    return (width / height).coerceIn(0.68f, 1.55f)
}

@Composable
private fun DeleteMessageDialog(
    canDeleteForEveryone: Boolean,
    onDismiss: () -> Unit,
    onDeleteForMe: () -> Unit,
    onDeleteForEveryone: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete message?") },
        text = { Text("Choose whether to remove it only here or erase it from the chat for everyone.") },
        confirmButton = {
            Column(Modifier.fillMaxWidth()) {
                TextButton(onClick = onDeleteForMe, modifier = Modifier.fillMaxWidth()) {
                    Text("Delete for me")
                }
                if (canDeleteForEveryone) {
                    TextButton(onClick = onDeleteForEveryone, modifier = Modifier.fillMaxWidth()) {
                        Text("Delete for everyone")
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun BuiltInCameraOverlay(
    onClose: () -> Unit,
    onPhotoCaptured: (Uri) -> Unit,
    onNotice: (String) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var lensFacing by remember { mutableStateOf(CameraSelector.LENS_FACING_BACK) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            factory = { viewContext ->
                PreviewView(viewContext).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = { previewView ->
                val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    val capture = ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .build()
                    val selector = CameraSelector.Builder()
                        .requireLensFacing(lensFacing)
                        .build()
                    runCatching {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(lifecycleOwner, selector, preview, capture)
                        imageCapture = capture
                    }.onFailure {
                        onNotice("Could not open camera.")
                    }
                }, ContextCompat.getMainExecutor(context))
            }
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 28.dp, start = 10.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Close camera", tint = Color.White)
            }
            Text("Camera", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            IconButton(onClick = {
                lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                    CameraSelector.LENS_FACING_FRONT
                } else {
                    CameraSelector.LENS_FACING_BACK
                }
            }) {
                Icon(Icons.Default.FlipCameraAndroid, contentDescription = "Flip camera", tint = Color.White)
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 28.dp)
                .size(78.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.92f))
                .clickable {
                    val target = createMediaTarget(context, "photo", "jpg")
                    val output = ImageCapture.OutputFileOptions.Builder(target.file).build()
                    imageCapture?.takePicture(
                        output,
                        ContextCompat.getMainExecutor(context),
                        object : ImageCapture.OnImageSavedCallback {
                            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                onPhotoCaptured(target.uri)
                            }

                            override fun onError(exception: ImageCaptureException) {
                                onNotice("Photo capture failed.")
                            }
                        }
                    ) ?: onNotice("Camera is not ready yet.")
                },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(62.dp)
                    .clip(CircleShape)
                    .background(Color.White)
            )
        }
    }
}

@Composable
private fun ImageViewerOverlay(
    content: ImageViewerContent,
    onClose: () -> Unit,
    onNotice: (String) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var scale by remember(content.uri) { mutableStateOf(1f) }
    var offsetX by remember(content.uri) { mutableStateOf(0f) }
    var offsetY by remember(content.uri) { mutableStateOf(0f) }
    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 5f)
        offsetX = if (scale == 1f) 0f else offsetX + panChange.x
        offsetY = if (scale == 1f) 0f else offsetY + panChange.y
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AsyncImage(
            model = content.uri,
            contentDescription = "Opened photo",
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 72.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offsetX
                    translationY = offsetY
                }
                .transformable(transformState),
            contentScale = ContentScale.Fit
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.72f))
                .padding(top = 24.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Close photo", tint = Color.White)
            }
            Column(Modifier.weight(1f)) {
                Text(content.title, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(content.meta, color = Color(0xFFD4D4D4), style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = { context.shareImage(content.uri) }) {
                Icon(Icons.Default.Share, contentDescription = "Share photo", tint = Color.White)
            }
            IconButton(onClick = {
                val saved = context.saveImageToGallery(content.uri)
                onNotice(if (saved) "Photo saved to gallery." else "Could not save photo.")
            }) {
                Icon(Icons.Default.FileDownload, contentDescription = "Save photo", tint = Color.White)
            }
            IconButton(onClick = { onNotice("More photo options coming next.") }) {
                Icon(Icons.Default.MoreVert, contentDescription = "More photo options", tint = Color.White)
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.72f))
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Photo", color = Color.White, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text(content.meta, color = Color(0xFFD4D4D4), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun Avatar(name: String, size: Int = 48) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(DeepGreen),
        contentAlignment = Alignment.Center
    ) {
        Text(name.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ProfileAvatar(name: String, photoUrl: String, size: Int = 48) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val displayPhotoUrl = remember(photoUrl) {
        runCatching { context.profilePhotoDisplayUri(photoUrl, name.ifBlank { "me" }) }.getOrDefault(photoUrl)
    }
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(DeepGreen),
        contentAlignment = Alignment.Center
    ) {
        if (displayPhotoUrl.isNotBlank()) {
            AsyncImage(
                model = displayPhotoUrl,
                contentDescription = "Profile photo",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Text(name.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, color = Ink, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
}
