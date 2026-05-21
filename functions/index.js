const admin = require("firebase-admin");
const { HttpsError, onCall } = require("firebase-functions/v2/https");
const { onDocumentCreated } = require("firebase-functions/v2/firestore");

admin.initializeApp();

const db = admin.firestore();

async function userTokens(userIds) {
  const docs = await Promise.all(
    [...new Set(userIds)].map((userId) => db.collection("users").doc(userId).get())
  );
  return docs
    .map((doc) => doc.get("fcmToken"))
    .filter((token) => typeof token === "string" && token.length > 0);
}

async function sendToTokens(tokens, payload) {
  if (tokens.length === 0) return;
  await admin.messaging().sendEachForMulticast({
    tokens,
    android: {
      priority: "high",
      notification: {
        channelId: "private_messages",
        sound: "default"
      }
    },
    data: payload
  });
}

function normalizeEmail(email) {
  return String(email || "").trim().toLowerCase();
}

async function assertAdmin(context) {
  const uid = context.auth && context.auth.uid;
  if (!uid) {
    throw new HttpsError("unauthenticated", "Sign in first.");
  }
  const adminDoc = await db.collection("admins").doc(uid).get();
  if (!adminDoc.exists || adminDoc.get("active") === false) {
    throw new HttpsError("permission-denied", "This account is not a PrivateTalk admin.");
  }
  return uid;
}

exports.addInviteEmail = onCall(async (request) => {
  const adminUid = await assertAdmin(request);
  const email = normalizeEmail(request.data && request.data.email);
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
    throw new HttpsError("invalid-argument", "Enter a valid email address.");
  }

  await db.collection("inviteEmails").doc(email).set(
    {
      email,
      active: true,
      createdBy: adminUid,
      updatedBy: adminUid,
      updatedAt: admin.firestore.FieldValue.serverTimestamp(),
      createdAt: admin.firestore.FieldValue.serverTimestamp()
    },
    { merge: true }
  );

  return { ok: true, email };
});

exports.disableInviteEmail = onCall(async (request) => {
  const adminUid = await assertAdmin(request);
  const email = normalizeEmail(request.data && request.data.email);
  if (!email) {
    throw new HttpsError("invalid-argument", "Email is required.");
  }

  await db.collection("inviteEmails").doc(email).set(
    {
      email,
      active: false,
      updatedBy: adminUid,
      updatedAt: admin.firestore.FieldValue.serverTimestamp()
    },
    { merge: true }
  );

  return { ok: true, email };
});

exports.notifyNewMessage = onDocumentCreated(
  "chats/{chatId}/messages/{messageId}",
  async (event) => {
    const message = event.data && event.data.data();
    if (!message) return;

    const chatDoc = await db.collection("chats").doc(event.params.chatId).get();
    const memberIds = chatDoc.get("memberIds") || [];
    const recipientIds = memberIds.filter((id) => id !== message.senderId);
    const tokens = await userTokens(recipientIds);
    const kind = message.kind || "text";
    const body = kind === "image" ? "Encrypted photo" : kind === "voice" ? "Encrypted voice note" : "Encrypted message";

    await sendToTokens(tokens, {
      type: "message",
      chatId: event.params.chatId,
      messageId: event.params.messageId,
      senderId: message.senderId || "",
      title: message.senderName || "PrivateTalk",
      body
    });
  }
);

exports.notifyIncomingCall = onDocumentCreated(
  "calls/{callId}/signals/{signalId}",
  async (event) => {
    const signal = event.data && event.data.data();
    if (!signal) return;
    if (signal.type !== "AudioOffer" && signal.type !== "VideoOffer") return;
    if (!signal.toUserId || signal.toUserId === signal.fromUserId) return;

    const callerDoc = await db.collection("users").doc(signal.fromUserId).get();
    const callerName = callerDoc.get("displayName") || "PrivateTalk";
    const tokens = await userTokens([signal.toUserId]);
    const callKind = signal.type === "VideoOffer" ? "video" : "audio";

    await sendToTokens(tokens, {
      type: "call",
      callId: event.params.callId,
      fromUserId: signal.fromUserId || "",
      toUserId: signal.toUserId || "",
      title: callerName,
      body: `Incoming ${callKind} call`,
      callKind
    });
  }
);
