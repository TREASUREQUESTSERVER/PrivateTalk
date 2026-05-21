# PrivateTalk Admin Panel

The admin panel lives in `admin-web/`. It is separate from the Android chat app and exists only to manage invite-only access.

## What It Does

- Google sign-in for admins.
- Add an invited email to `inviteEmails/{email}`.
- Disable an invited email by setting `active: false`.
- List current invite emails.

Invite changes are performed by Firebase Cloud Functions, not directly by public browser code.

## First Admin Setup

You need one manual Firestore document once:

Collection:

```txt
admins
```

Document ID:

```txt
YOUR_FIREBASE_AUTH_UID
```

Fields:

```txt
email   string   your-admin-email@gmail.com
active  boolean  true
```

You can find your UID in Firebase Console -> Authentication -> Users after signing in once.

## Deploy

From the project root:

```powershell
npm install -g firebase-tools
firebase login
firebase deploy --only functions,hosting,firestore:rules
```

After deploy, open the Hosting URL Firebase prints.

## Adding `rajprince4678@gmail.com`

After the admin panel is deployed and your admin UID is in `admins`, sign in and add:

```txt
rajprince4678@gmail.com
```

The Android app will allow that Google account after the invite document exists and `active` is true.
