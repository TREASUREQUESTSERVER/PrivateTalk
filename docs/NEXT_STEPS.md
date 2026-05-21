# Next Steps For PrivateTalk

## Done In This Version

- Android Studio installed.
- Android SDK installed.
- PrivateTalk builds into an APK.
- App installed and launched on the connected phone.
- Login now uses Google Sign-In through Firebase Authentication.
- Only emails listed in Firestore `inviteEmails` can enter.
- Text messages can now be sent locally.
- Mic button records a real local voice-note file, then sends it.
- Camera button opens the phone camera directly and sends the captured photo.
- Captured photos render inside the chat.
- Voice-note bubbles can play the recorded audio locally.
- Status updates can now be posted locally.
- Profile display name can now be edited locally.
- Audio/video call buttons add local call history entries.
- Profile displays local user id, invite id, and identity preview.
- Backend contracts exist for invite, user, encrypted message, media, and call signaling.

## Invited Test Email

- `sbiographyof@gmail.com`

## Next Development Order

1. Firebase project setup.
2. Add `google-services.json` into `app/`.
3. Enable Firebase Authentication.
4. Add Firestore invite validation.
5. Save real user profiles.
6. Add real chat messages.
7. Encrypt message text before saving.
8. Add image and voice-note upload.
9. Add push notifications. Backend scaffold added in `functions/`; deploy with Firebase CLI.
10. Add WebRTC call signaling. Firestore offer/answer/ICE signaling is wired; TURN server is still needed for strict networks.

## Backend Swap Point

Current local file:

`app/src/main/java/com/privatetalk/app/backend/LocalPrivateTalkBackend.kt`

Firebase implementation should replace this local behavior with:

- `validateInvite(code)`
- `createUser(displayName, inviteId, publicIdentityKey)`
- `sendEncryptedMessage(payload)`
- `observeEncryptedMessages(chatId)`
- `publishCallSignal(signal)`

## Why Firebase Comes Next

The app is installed and running, but it is still local-only. Firebase gives us login, database, storage, and push notifications. After that, the app can work between two real phones.

## What I Need From You Later

For Firebase connection, you will need a Firebase project. The app package name is:

`com.privatetalk.app`

When Firebase gives a `google-services.json` file, place it in:

`app/google-services.json`

Detailed setup guide:

`docs/FIREBASE_SETUP_STEPS.md`
