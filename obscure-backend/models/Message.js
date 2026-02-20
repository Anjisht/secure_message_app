// models/Message.js
const mongoose = require('mongoose');

const envelopeSchema = new mongoose.Schema({
  userId: { type: mongoose.Schema.Types.ObjectId, ref: 'User', required: true },
  encKey: { type: String, required: true } // RSA-OAEP wrapped AES key (Base64/URL-safe)
}, { _id: false });

const messageSchema = new mongoose.Schema({
  roomId: { type: String, index: true, required: true },
  senderId: { type: mongoose.Schema.Types.ObjectId, ref: 'User', required: true },
  alias: { type: String, required: true },      // sender's alias inside this room
  type: { type: String, enum: ['text','media'], default: 'text' },

  // E2EE payload (opaque to server)
  ciphertext: { type: String, required: false }, // Base64/URL-safe
  iv: { type: String },                         // Base64 (for AES-GCM)
  keyEnvelope: { type: [envelopeSchema], required: true }, // one encKey per recipient

  // optional media metadata (for later)
  fileUrl: { type: String },
  fileKey: { type: String },
  fileMime: { type: String },

}, { timestamps: true });

messageSchema.index({ roomId: 1, createdAt: 1 });

module.exports = mongoose.model('Message', messageSchema);
