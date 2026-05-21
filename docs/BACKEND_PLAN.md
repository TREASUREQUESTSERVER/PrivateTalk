# Backend Plan

Recommended first backend: Firebase.

## Phase 1

- Firebase Authentication with phone OTP or email login.
- Firestore collection `invites` stores invite codes.
- A user can create an account only when the invite code is valid and unused.
- Firestore collection `users` stores display name, photo URL, public encryption identity key, and activity fields.
- Firestore collection `chats` stores chat metadata.
- Firestore collection `messages` stores encrypted message payloads only.
- Firebase Storage stores encrypted image and voice-note files.
- Firebase Cloud Messaging sends push notifications without exposing message text.

## Phase 2

- Status/activity posts with expiry time.
- Read receipts and delivery receipts.
- Contact blocking.
- Admin invite-code dashboard.

## Phase 3

- WebRTC audio and video calls.
- Firestore or Realtime Database for call signaling.
- TURN server for reliable calls when users are behind strict networks.

## Encryption Notes

Do not store plain chat text on the backend.

Start with a per-device key pair and encrypt message bodies on the phone before upload. For production-level security, move toward the Signal Protocol using a vetted implementation instead of writing cryptography manually.
