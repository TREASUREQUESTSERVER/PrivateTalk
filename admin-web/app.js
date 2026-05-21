import { initializeApp } from "https://www.gstatic.com/firebasejs/10.12.5/firebase-app.js";
import {
  getAuth,
  GoogleAuthProvider,
  onAuthStateChanged,
  signInWithPopup,
  signOut
} from "https://www.gstatic.com/firebasejs/10.12.5/firebase-auth.js";
import {
  collection,
  collectionGroup,
  doc,
  getDoc,
  getFirestore,
  limit,
  onSnapshot,
  orderBy,
  query,
  serverTimestamp,
  setDoc,
  updateDoc
} from "https://www.gstatic.com/firebasejs/10.12.5/firebase-firestore.js";

const firebaseConfig = {
  apiKey: "AIzaSyAIBQIhY6WO-6TOI2kLQkm3Xfo_pqGZ5Sw",
  authDomain: "privatetalk-7e2e7.firebaseapp.com",
  projectId: "privatetalk-7e2e7",
  storageBucket: "privatetalk-7e2e7.firebasestorage.app",
  messagingSenderId: "950668517317",
  appId: "1:950668517317:web:admin"
};

const app = initializeApp(firebaseConfig);
const auth = getAuth(app);
const db = getFirestore(app);

const signInButton = document.querySelector("#signInButton");
const signOutButton = document.querySelector("#signOutButton");
const adminPanel = document.querySelector("#adminPanel");
const accountName = document.querySelector("#accountName");
const adminState = document.querySelector("#adminState");
const inviteForm = document.querySelector("#inviteForm");
const emailInput = document.querySelector("#emailInput");
const message = document.querySelector("#message");
const inviteList = document.querySelector("#inviteList");
const refreshButton = document.querySelector("#refreshButton");
const inviteSearch = document.querySelector("#inviteSearch");
const inviteFilter = document.querySelector("#inviteFilter");
const userSearch = document.querySelector("#userSearch");
const userList = document.querySelector("#userList");
const chatList = document.querySelector("#chatList");
const callList = document.querySelector("#callList");
const activeInviteCount = document.querySelector("#activeInviteCount");
const userCount = document.querySelector("#userCount");
const chatCount = document.querySelector("#chatCount");
const callCount = document.querySelector("#callCount");
const bulkInviteInput = document.querySelector("#bulkInviteInput");
const bulkInviteButton = document.querySelector("#bulkInviteButton");
const exportInvitesButton = document.querySelector("#exportInvitesButton");
const exportUsersButton = document.querySelector("#exportUsersButton");
const updateForm = document.querySelector("#updateForm");
const updateVersionCode = document.querySelector("#updateVersionCode");
const updateVersionName = document.querySelector("#updateVersionName");
const updateUrl = document.querySelector("#updateUrl");
const updateNotes = document.querySelector("#updateNotes");
const announcementForm = document.querySelector("#announcementForm");
const announcementTitle = document.querySelector("#announcementTitle");
const announcementBody = document.querySelector("#announcementBody");
const announcementActive = document.querySelector("#announcementActive");

let unsubscribers = [];
let invites = [];
let users = [];
let chats = [];
let calls = [];

function setMessage(text, isError = false) {
  message.textContent = text;
  message.style.color = isError ? "#b3261e" : "#075e54";
}

function setSignedIn(user) {
  signInButton.classList.toggle("hidden", Boolean(user));
  signOutButton.classList.toggle("hidden", !user);
  adminPanel.classList.toggle("hidden", !user);
  accountName.textContent = user ? `${user.displayName || "Admin"} (${user.email})` : "-";
}

function setEmpty(element, text) {
  element.innerHTML = `<p class="subtle">${text}</p>`;
}

function formatTime(millis) {
  if (!millis) return "No activity";
  return new Date(millis).toLocaleString([], {
    month: "short",
    day: "numeric",
    hour: "2-digit",
    minute: "2-digit"
  });
}

function initials(text) {
  return (text || "?").trim().slice(0, 1).toUpperCase();
}

function safeCsv(value) {
  return `"${String(value ?? "").replaceAll("\"", "\"\"")}"`;
}

function downloadText(filename, text, type = "text/plain") {
  const blob = new Blob([text], { type });
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}

async function copyText(text, label) {
  await navigator.clipboard.writeText(text);
  setMessage(`${label} copied.`);
}

function renderStats() {
  activeInviteCount.textContent = invites.filter((invite) => invite.active !== false).length;
  userCount.textContent = users.length;
  chatCount.textContent = chats.length;
  callCount.textContent = calls.length;
}

function renderInvites() {
  const term = inviteSearch.value.trim().toLowerCase();
  const filter = inviteFilter.value;
  const visible = invites.filter((invite) => {
    const active = invite.active !== false;
    const matchesTerm = !term || invite.email.toLowerCase().includes(term);
    const matchesFilter =
      filter === "all" ||
      (filter === "active" && active) ||
      (filter === "disabled" && !active);
    return matchesTerm && matchesFilter;
  });

  inviteList.innerHTML = "";
  if (!visible.length) {
    setEmpty(inviteList, "No invites match this view.");
    return;
  }

  visible.forEach((invite) => {
    const active = invite.active !== false;
    const row = document.createElement("div");
    row.className = "data-row";
    row.innerHTML = `
      <div>
        <strong>${invite.email}</strong>
        <small>${active ? "Active" : "Disabled"} - updated ${formatTime(invite.updatedAtMillis || invite.createdAtMillis)}</small>
      </div>
      <div class="row-actions">
        <button class="button ghost" type="button" data-action="copy">Copy</button>
        <button class="button ${active ? "danger" : "success"}" type="button">
          ${active ? "Disable" : "Enable"}
        </button>
      </div>
    `;
    row.querySelector("[data-action='copy']").addEventListener("click", async () => {
      await copyText(invite.email, "Email");
    });
    row.querySelector(".row-actions button:last-child").addEventListener("click", async () => {
      await runAction(
        () => setInviteActive(invite.email, !active),
        active ? "Invite disabled." : "Invite enabled."
      );
    });
    inviteList.appendChild(row);
  });
}

function renderUsers() {
  const term = userSearch.value.trim().toLowerCase();
  const visible = users.filter((user) => {
    const haystack = `${user.displayName} ${user.email} ${user.username || ""}`.toLowerCase();
    return !term || haystack.includes(term);
  });

  userList.innerHTML = "";
  if (!visible.length) {
    setEmpty(userList, "No users match this search.");
    return;
  }

  visible.forEach((user) => {
    const row = document.createElement("div");
    row.className = "data-row";
    row.innerHTML = `
      <div class="row-title">
        <span class="avatar">${initials(user.displayName || user.email)}</span>
        <div>
          <strong>${user.displayName || "Unnamed user"}</strong>
          <small>${user.email || "No email"}${user.username ? ` - @${user.username}` : ""}</small>
        </div>
      </div>
      <div class="row-actions">
        <button class="button ghost" type="button" data-copy-email>Copy email</button>
        <button class="button ghost" type="button" data-copy-uid>Copy UID</button>
      </div>
    `;
    row.querySelector("[data-copy-email]").addEventListener("click", () => copyText(user.email || "", "Email"));
    row.querySelector("[data-copy-uid]").addEventListener("click", () => copyText(user.userId || user.id, "UID"));
    userList.appendChild(row);
  });
}

function renderChats() {
  chatList.innerHTML = "";
  if (!chats.length) {
    setEmpty(chatList, "No chats yet.");
    return;
  }

  chats.slice(0, 20).forEach((chat) => {
    const row = document.createElement("div");
    row.className = "data-row";
    row.innerHTML = `
      <div>
        <strong>${chat.title || chat.id}</strong>
        <small>${chat.lastMessagePreview || "No messages"} - ${formatTime(chat.updatedAtMillis)}</small>
      </div>
    `;
    chatList.appendChild(row);
  });
}

function renderCalls() {
  callList.innerHTML = "";
  if (!calls.length) {
    setEmpty(callList, "No call signals yet.");
    return;
  }

  calls.slice(0, 20).forEach((call) => {
    const row = document.createElement("div");
    row.className = "data-row";
    row.innerHTML = `
      <div>
        <strong>${call.type || "Call signal"}</strong>
        <small>${call.fromUserId?.slice(0, 8) || "unknown"} to ${call.toUserId?.slice(0, 8) || "unknown"} - ${formatTime(call.createdAtMillis)}</small>
      </div>
    `;
    callList.appendChild(row);
  });
}

function renderAll() {
  renderStats();
  renderInvites();
  renderUsers();
  renderChats();
  renderCalls();
}

function snapshotToRows(snapshot) {
  return snapshot.docs.map((item) => ({ id: item.id, ...item.data() }));
}

function subscribeAdminData() {
  unsubscribeAdminData();
  adminState.textContent = "Admin ready";

  unsubscribers = [
    onSnapshot(
      query(collection(db, "inviteEmails"), orderBy("email")),
      (snapshot) => {
        invites = snapshotToRows(snapshot).map((invite) => ({
          ...invite,
          email: invite.email || invite.id,
          updatedAtMillis: invite.updatedAtMillis || invite.updatedAt?.toMillis?.() || 0,
          createdAtMillis: invite.createdAtMillis || invite.createdAt?.toMillis?.() || 0
        }));
        renderAll();
      },
      showAdminError
    ),
    onSnapshot(
      query(collection(db, "users"), orderBy("displayName"), limit(100)),
      (snapshot) => {
        users = snapshotToRows(snapshot);
        renderAll();
      },
      showAdminError
    ),
    onSnapshot(
      query(collection(db, "chats"), orderBy("updatedAtMillis", "desc"), limit(50)),
      (snapshot) => {
        chats = snapshotToRows(snapshot);
        renderAll();
      },
      showAdminError
    ),
    onSnapshot(
      query(collectionGroup(db, "signals"), orderBy("createdAtMillis", "desc"), limit(50)),
      (snapshot) => {
        calls = snapshotToRows(snapshot);
        renderAll();
      },
      showAdminError
    )
  ];
  loadAdminSettings();
}

function showAdminError(error) {
  adminState.textContent = "Rules needed";
  setMessage(error.message || "Could not load admin data.", true);
}

function unsubscribeAdminData() {
  unsubscribers.forEach((unsubscribe) => unsubscribe());
  unsubscribers = [];
}

async function runAction(action, successMessage) {
  setMessage("Working...");
  try {
    await action();
    setMessage(successMessage);
    emailInput.value = "";
  } catch (error) {
    setMessage(error.message || "Action failed.", true);
  }
}

async function addInviteEmail(email) {
  if (!email || !email.includes("@")) {
    throw new Error("Enter a valid email address.");
  }

  await setDoc(
    doc(db, "inviteEmails", email),
    {
      email,
      active: true,
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp()
    },
    { merge: true }
  );
}

async function addBulkInvites(raw) {
  const emails = [...new Set(
    raw
      .split(/[\s,;]+/)
      .map((email) => email.trim().toLowerCase())
      .filter((email) => email.includes("@"))
  )];
  if (!emails.length) {
    throw new Error("Paste at least one valid email.");
  }
  await Promise.all(emails.map((email) => addInviteEmail(email)));
  return emails.length;
}

async function setInviteActive(email, active) {
  await setDoc(
    doc(db, "inviteEmails", email),
    {
      email,
      active,
      updatedAt: serverTimestamp()
    },
    { merge: true }
  );
}

async function loadAdminSettings() {
  const updateSnap = await getDoc(doc(db, "adminConfig", "androidUpdate"));
  if (updateSnap.exists()) {
    const update = updateSnap.data();
    updateVersionCode.value = update.versionCode || "";
    updateVersionName.value = update.versionName || "";
    updateUrl.value = update.apkUrl || "";
    updateNotes.value = update.notes || "";
  }

  const noticeSnap = await getDoc(doc(db, "adminConfig", "announcement"));
  if (noticeSnap.exists()) {
    const notice = noticeSnap.data();
    announcementTitle.value = notice.title || "";
    announcementBody.value = notice.body || "";
    announcementActive.checked = notice.active === true;
  }
}

async function saveUpdateInfo() {
  await setDoc(
    doc(db, "adminConfig", "androidUpdate"),
    {
      versionCode: Number(updateVersionCode.value || 0),
      versionName: updateVersionName.value.trim(),
      apkUrl: updateUrl.value.trim(),
      notes: updateNotes.value.trim(),
      updatedAt: serverTimestamp()
    },
    { merge: true }
  );
}

async function saveAnnouncement() {
  await setDoc(
    doc(db, "adminConfig", "announcement"),
    {
      title: announcementTitle.value.trim(),
      body: announcementBody.value.trim(),
      active: announcementActive.checked,
      updatedAt: serverTimestamp()
    },
    { merge: true }
  );
}

document.querySelectorAll(".tab").forEach((tab) => {
  tab.addEventListener("click", () => {
    document.querySelectorAll(".tab").forEach((item) => item.classList.remove("active"));
    document.querySelectorAll(".tab-panel").forEach((panel) => panel.classList.add("hidden"));
    tab.classList.add("active");
    document.querySelector(`[data-panel="${tab.dataset.tab}"]`)?.classList.remove("hidden");
  });
});

signInButton.addEventListener("click", () => signInWithPopup(auth, new GoogleAuthProvider()));
signOutButton.addEventListener("click", () => signOut(auth));
refreshButton.addEventListener("click", subscribeAdminData);
inviteSearch.addEventListener("input", renderInvites);
inviteFilter.addEventListener("change", renderInvites);
userSearch.addEventListener("input", renderUsers);
bulkInviteButton.addEventListener("click", async () => {
  await runAction(async () => {
    const count = await addBulkInvites(bulkInviteInput.value);
    bulkInviteInput.value = "";
    return count;
  }, "Bulk invites added.");
});
exportInvitesButton.addEventListener("click", () => {
  const csv = [
    ["email", "active"].map(safeCsv).join(","),
    ...invites.map((invite) => [invite.email, invite.active !== false].map(safeCsv).join(","))
  ].join("\n");
  downloadText("privatetalk-invites.csv", csv, "text/csv");
});
exportUsersButton.addEventListener("click", () => {
  const csv = [
    ["uid", "name", "email", "username"].map(safeCsv).join(","),
    ...users.map((user) => [user.userId || user.id, user.displayName, user.email, user.username || ""].map(safeCsv).join(","))
  ].join("\n");
  downloadText("privatetalk-users.csv", csv, "text/csv");
});
updateForm.addEventListener("submit", async (event) => {
  event.preventDefault();
  await runAction(saveUpdateInfo, "Update info saved.");
});
announcementForm.addEventListener("submit", async (event) => {
  event.preventDefault();
  await runAction(saveAnnouncement, "Announcement saved.");
});

inviteForm.addEventListener("submit", async (event) => {
  event.preventDefault();
  const email = emailInput.value.trim().toLowerCase();
  await runAction(() => addInviteEmail(email), `Invite added: ${email}`);
});

onAuthStateChanged(auth, (user) => {
  setSignedIn(user);
  setMessage("");
  if (!user) {
    unsubscribeAdminData();
    invites = [];
    users = [];
    chats = [];
    calls = [];
    renderAll();
    return;
  }
  subscribeAdminData();
});
