package com.privatetalk.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class PrivateTalkMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        val uid = Firebase.auth.currentUser?.uid ?: return
        Firebase.firestore.collection("users").document(uid).set(
            mapOf("fcmToken" to token),
            SetOptions.merge()
        )
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val currentUid = Firebase.auth.currentUser?.uid
        val senderId = message.data["senderId"]
        if (!senderId.isNullOrBlank() && senderId == currentUid) {
            return
        }
        val toUserId = message.data["toUserId"]
        if (!toUserId.isNullOrBlank() && toUserId != currentUid) {
            return
        }
        val title = message.notification?.title ?: message.data["title"] ?: "PrivateTalk"
        val body = message.notification?.body ?: message.data["body"] ?: "New private message"
        showNotification(title, body)
    }

    private fun showNotification(title: String, body: String) {
        val channelId = "private_messages"
        val notificationManager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(
                NotificationChannel(channelId, "Private messages", NotificationManager.IMPORTANCE_HIGH)
            )
        }
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }
}
