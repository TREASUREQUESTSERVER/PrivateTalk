# PrivateTalk Push Notifications

The Android app already saves each signed-in device token to `users/{userId}.fcmToken`.

This repo now includes Firebase Cloud Functions in `functions/`:

- `notifyNewMessage`: triggers when a chat message is created and sends a private FCM alert to the other chat members.
- `notifyIncomingCall`: triggers when a call offer signal is created and sends an incoming call alert to the receiver.

Message notification bodies stay generic (`Encrypted message`, `Encrypted photo`, `Encrypted voice note`) so encrypted content is not exposed to Firebase Cloud Messaging.

## Deploy Later

From the project root:

```powershell
cd functions
npm install
cd ..
firebase deploy --only functions
```

The configured Firebase project id is `privatetalk-7e2e7` in `.firebaserc`.
