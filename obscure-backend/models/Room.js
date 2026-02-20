const mongoose = require('mongoose');

const memberSchema = new mongoose.Schema({
  userId: { type: mongoose.Schema.Types.ObjectId, ref: 'User' },
  alias: { type: String, required: true },
  status: { type: String, enum: ['pending', 'approved'], default: 'pending' },
  joinNote: { type: String, default: null },         // 👈 note/code message from joiner
  requestedAt: { type: Date, default: Date.now },
  approvedAt: { type: Date, default: null }
}, { _id: false });

const roomSchema = new mongoose.Schema({
  roomId: { type: String, unique: true, index: true }, // short code (e.g. nanoid)
  type: { type: String, enum: ['room', 'dm'], default: 'room', index: true },
  pairKey: { type: String, default: null, index: true },
  adminId: { type: mongoose.Schema.Types.ObjectId, ref: 'User' },
  codePhraseHash: { type: String }, // optional hashed join code
  expiresAt: { type: Date },
  members: [memberSchema]
}, { timestamps: true });

// One DM per pair (enforced by code; index helps quick find)
roomSchema.index({ type: 1, pairKey: 1 }, { unique: true, partialFilterExpression: { type: 'dm', pairKey: { $type: 'string' } } });

module.exports = mongoose.model('Room', roomSchema);