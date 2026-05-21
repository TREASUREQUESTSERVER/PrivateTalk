# Firebase Schema Draft

## Collections

`invites/{inviteId}`

- `codeHash`: string
- `createdBy`: admin user id
- `createdAt`: timestamp
- `usedBy`: user id or null
- `usedAt`: timestamp or null
- `expiresAt`: timestamp or null

`users/{userId}`

- `displayName`: string
- `photoUrl`: string or null
- `username`: unique string
- `publicIdentityKey`: string
- `createdAt`: timestamp
- `lastSeenAt`: timestamp

`usernames/{username}`

- `userId`: user id
- `displayName`: string
- `email`: string
- `username`: string
- `updatedAt`: timestamp

`chats/{chatId}`

- `memberIds`: array of user ids
- `memberNames`: map of user id to display name
- `type`: `direct` or `group`
- `lastMessageAt`: timestamp
- `lastMessagePreview`: encrypted or generic text like "New message"

`chats/{chatId}/messages/{messageId}`

- `senderId`: user id
- `cipherText`: string
- `nonce`: string
- `mediaUrl`: string or null
- `mediaKind`: `image`, `voiceNote`, or null
- `sentAt`: timestamp

`calls/{callId}/signals/{signalId}`

- `fromUserId`: user id
- `toUserId`: user id
- `type`: `audioOffer`, `videoOffer`, `answer`, `iceCandidate`, `ended`
- `sdpOrCandidateJson`: string
- `createdAt`: timestamp

## Security Direction

- Only authenticated users can read their own user document.
- Only chat members can read/write messages inside a chat.
- Message bodies must be encrypted before Firestore upload.
- Invite codes should be stored as hashes, not plain text.
- Cloud Functions should consume invite codes atomically so the same code cannot be reused.
