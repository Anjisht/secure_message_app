// sockets/chatSocket.js
const Room = require("../models/Room");
const Message = require("../models/Message");
const { verifySocketAuth } = require("../middleware/socketAuth");
const admin = require("../config/firebase");
const User = require("../models/User");

// Track which socket IDs belong to which user (for targeted emits)
const userSockets = new Map(); // userId -> Set<socketId>

function addUserSocket(userId, socketId) {
  const k = String(userId);
  if (!userSockets.has(k)) userSockets.set(k, new Set());
  userSockets.get(k).add(socketId);
}

function removeUserSocket(userId, socketId) {
  const k = String(userId);
  const set = userSockets.get(k);
  if (!set) return;
  set.delete(socketId);
  if (set.size === 0) userSockets.delete(k);
}

async function joinApprovedRooms(socket) {
  const userId = socket.user.id; // from verifySocketAuth
  const rooms = await Room.find({
    "members.userId": userId,
    "members.status": "approved",
  })
    .select("roomId")
    .lean();

  rooms.forEach((r) => socket.join(r.roomId));
}

async function ensureMembership(roomId, userId) {
  const room = await Room.findOne({ roomId }).lean();
  if (!room) return { ok: false, error: "Room not found" };

  const me = room.members?.find(
    (m) => String(m.userId) === String(userId) && m.status === "approved"
  );
  if (!me) return { ok: false, error: "Not a member" };

  const senderAlias = me.alias;
  const approvedMembers = room.members.filter((m) => m.status === "approved");
  return { ok: true, room, senderAlias, approvedMembers };
}

// ------------------ Push Notification Helper ------------------
async function pushToRoomMembers({ room, senderId, roomId }) {
  try {
    const recipientIds = room.members
      .map((m) => String(m.userId))
      .filter((uid) => uid !== String(senderId));

    //  Skip users who are currently online
    const offlineRecipientIds = recipientIds.filter(uid => userSockets.has(uid) === false);
    if (!offlineRecipientIds.length) return;

    const users = await User.find({ _id: { $in: recipientIds } }).lean();
    const tokens = [];

    for (const u of users) {
      const list = (u.fcmTokens || []).map((t) =>
        typeof t === "string" ? { token: t } : t
      );
      for (const entry of list) if (entry.token) tokens.push(entry.token);
    }

    if (!tokens.length) return;

    const message = {
      tokens,
      // privacy-safe — no message content here
      data: { type: "message", roomId: String(roomId) },
      notification: { title: "New message", body: "Tap to open" },
      android: { priority: "high", notification: { channelId: "messages" } },
      apns: { headers: { "apns-priority": "10" } },
    };

    const resp = await admin.messaging().sendEachForMulticast(message);

    // prune invalid tokens
    const invalid = [];
    resp.responses.forEach((r, idx) => {
      if (!r.success) {
        const code = r.error?.code || "";
        if (
          code === "messaging/registration-token-not-registered" ||
          code === "messaging/invalid-registration-token"
        ) {
          invalid.push(tokens[idx]);
        }
      }
    });

    if (invalid.length) {
      await User.updateMany(
        { _id: { $in: recipientIds } },
        { $pull: { fcmTokens: { $in: invalid } } }
      );
      await User.updateMany(
        { _id: { $in: recipientIds } },
        { $pull: { fcmTokens: { token: { $in: invalid } } } }
      );
    }
  } catch (e) {
    console.error("pushToRoomMembers error", e);
  }
}


module.exports.initChatSocket = (io) => {
  // JWT guard for sockets
  io.use((socket, next) => verifySocketAuth(socket, next));

  io.on("connection", async (socket) => {
    const userId = socket.user.id;
    addUserSocket(userId, socket.id);

    try {
      await joinApprovedRooms(socket);
    } catch (e) {
      // non-fatal
      console.error("joinApprovedRooms error", e.message);
    }

    // client can (re)join specific room on approval
    socket.on("rooms:join", async ({ roomId }, ack) => {
      try {
        const { ok, error } = await ensureMembership(roomId, userId);
        if (!ok) return ack?.({ ok: false, error });
        socket.join(roomId);
        return ack?.({ ok: true });
      } catch (e) {
        return ack?.({ ok: false, error: "Server error" });
      }
    });

    // send message (encrypted)
    // payload: { roomId, type?, ciphertext, iv?, keyEnvelope: [{ userId, encKey }] }
    socket.on("message:send", async (payload, ack) => {
      try {
        // include fileUrl + fileMime for media messages
        const { roomId, ciphertext, keyEnvelope, iv, type, fileUrl,fileKey , fileMime } =
          payload || {};
        if (
          !roomId ||
          !Array.isArray(keyEnvelope) ||
          keyEnvelope.length === 0
        ) {
          return ack?.({ ok: false, error: "Invalid payload" });
        }

        // membership + aliases
        const { ok, error, room, senderAlias, approvedMembers } =
          await ensureMembership(roomId, userId);
        if (!ok) return ack?.({ ok: false, error });

        // persist message (server never decrypts)
        const doc = await Message.create({
          roomId,
          senderId: userId,
          alias: senderAlias,
          type: type || "text",
          ciphertext: ciphertext || null,
          iv: iv || null,
          keyEnvelope,
          fileUrl: fileUrl || null,
          fileKey: fileKey || null,
          fileMime: fileMime || null,
        });

        // deliver to each approved member privately with *their* encKey only
        for (const member of approvedMembers) {
          const entry = Array.isArray(keyEnvelope)
            ? keyEnvelope.find(
                (e) => String(e.userId) === String(member.userId)
              )
            : null;

          // build the delivery payload per recipient
          const delivery = {
            _id: String(doc._id),
            roomId,
            alias: senderAlias,
            senderId: String(userId),
            type: doc.type || "text",
            createdAt: doc.createdAt,
            iv: doc.iv,
            ciphertext: doc.ciphertext,
            encKey: entry ? entry.encKey : null, // 👈 critical fix
            fileUrl: doc.fileUrl || null,
            fileKey: doc.fileKey || null,  
            fileMime: doc.fileMime || null,
          };

          const targets = userSockets.get(String(member.userId));
          if (targets && targets.size) {
            for (const sid of targets) {
              io.to(sid).emit("message:new", delivery);
            }
          }
        }

        // push notification to all other room members
        pushToRoomMembers({ room, senderId: userId, roomId }).catch(console.error); 
        
        return ack?.({
          ok: true,
          id: String(doc._id),
          createdAt: doc.createdAt,
        });
      } catch (e) {
        console.error("message:send error", e);
        return ack?.({ ok: false, error: "Server error" });
      }
    });

    socket.on("disconnect", () => {
      removeUserSocket(userId, socket.id);
    });
  });
};