# Firebase Setup Steps

Firebase project creation must be done from your Google account.

## 1. Create Project

1. Open Firebase Console.
2. Create a new project named `PrivateTalk`.
3. Add an Android app.
4. Android package name:
   `com.privatetalk.app`
5. Download `google-services.json`.
6. Put it here:
   `app/google-services.json`

## 2. Enable Services

Enable these Firebase products:

- Authentication
- Cloud Firestore
- Storage
- Cloud Messaging

For Authentication, enable **Google**.

The current app login allows only emails listed in Firestore `inviteEmails`.

## 3. Firestore Collections

Create one invite document:

Collection: `invites`

Document fields:

- `codeHash`: SHA-256 hash of the uppercase invite code
- `createdAt`: timestamp
- `usedBy`: null
- `usedAt`: null
- `expiresAt`: null

Local test invite codes currently used in the app:

- `DEMO2026`
- `PRIVATE2026`
- `FOUNDERS`

Their `codeHash` values are:

- `DEMO2026`: `4aa05c62bc51969c5466a0bd884105fa76e806b034362effd5439368446aacba`
- `PRIVATE2026`: `0854560194a701f5f90e3ca9507c6165c82e2038fc6b6cb38cb212042e103157`
- `FOUNDERS`: `505db768afd00f5f80ad7d8ae3ce9e6c8bd3cdaebe956b0eea1d4573b251c9c6`

For each invite document, set:

- `codeHash`: one hash above
- `createdAt`: current timestamp
- `usedBy`: null
- `usedAt`: null
- `expiresAt`: null

## Invited Email Login

Create this Firestore document:

Collection: `inviteEmails`

Document ID:

`sbiographyof@gmail.com`

Fields:

- `email`: `sbiographyof@gmail.com`
- `active`: true
- `createdAt`: current timestamp
- `usedBy`: null
- `usedAt`: null

Then enable Firebase Authentication provider:

- **Google**

After enabling Google, download the updated `google-services.json` and replace:

`app/google-services.json`

The app signs in with Google, then checks `inviteEmails/{google_email}` before allowing access.

## 4. Firestore Rules Draft

Use strict rules before real users are added:

```txt
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    function signedIn() {
      return request.auth != null;
    }

    match /users/{userId} {
      allow read: if signedIn();
      allow write: if signedIn() && request.auth.uid == userId;
    }

    match /invites/{inviteId} {
      allow read: if signedIn();
      allow update: if signedIn();
      allow create, delete: if false;
    }

    match /chats/{chatId} {
      allow read, write: if signedIn() && request.auth.uid in resource.data.memberIds;

      match /messages/{messageId} {
        allow read, write: if signedIn();
      }
    }

    match /calls/{callId}/signals/{signalId} {
      allow read, write: if signedIn();
    }
  }
}
```

These are first-pass rules. Before production, message and call rules must verify chat/call membership more tightly.

## 5. Storage Rules Draft

```txt
rules_version = '2';
service firebase.storage {
  match /b/{bucket}/o {
    match /chat-media/{chatId}/{allPaths=**} {
      allow read, write: if request.auth != null;
    }

    match /profile-photos/{userId}/{allPaths=**} {
      allow read: if request.auth != null;
      allow write: if request.auth != null && request.auth.uid == userId;
    }
  }
}
```

## 6. App Code Already Added

Firebase-ready adapter:

`app/src/main/java/com/privatetalk/app/backend/FirebasePrivateTalkBackend.kt`

Local backend still used until Firebase config and login are connected:

`app/src/main/java/com/privatetalk/app/backend/LocalPrivateTalkBackend.kt`
